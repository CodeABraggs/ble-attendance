package com.example.ble_app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/** Liveness actions; names match AttendanceService.LIVENESS_ACTIONS on the server. */
enum class LivenessAction(val instruction: String) {
    BLINK("Blink your eyes"),
    TURN_LEFT("Slowly turn your head to your LEFT"),
    TURN_RIGHT("Slowly turn your head to your RIGHT"),
    SMILE("Smile");

    companion object {
        fun parse(names: List<String>): List<LivenessAction> = names.map { valueOf(it) }
        fun random(count: Int): List<LivenessAction> = entries.shuffled().take(count)
    }
}

/** A live face sample ready to send to the server. */
class CapturedFace(val embedding: FloatArray, val photoJpeg: ByteArray, val spoofScore: Float) {
    /** Anti-spoofing score in basis points, the integer form that is signed and sent. */
    val spoofScoreBp: Int get() = (spoofScore * 10_000).roundToInt().coerceIn(0, 100_000)

    /** 192 little-endian float32 values, as the server expects. */
    fun embeddingBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}

sealed class FaceCaptureState {
    data class Instruct(val message: String, val step: Int, val totalSteps: Int) : FaceCaptureState()
    object Processing : FaceCaptureState()
    data class Done(val face: CapturedFace) : FaceCaptureState()
    data class Failed(val message: String) : FaceCaptureState()
}

/**
 * Captures a face from camera frames: two sharp, straight-on captures of the same tracked face, with any
 * [actions] performed in between. Then it runs the face models on the two captures. With no actions the
 * user just looks at the camera; the anti-spoofing model still rejects printed photos and screens.
 * Actions (blink/turn/smile on request) add protection against photos and videos at the cost of effort.
 */
class FaceCaptureAnalyzer(
    context: Context,
    private val actions: List<LivenessAction>,
    private val timeoutMillis: Long = 30_000L
) : ImageAnalysis.Analyzer {
    private val models = FaceModels.get(context)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
    )

    // Steps: frontal capture, each action, frontal capture.
    private val totalSteps = actions.size + 2
    private var step = 0
    private var trackingId: Int? = null
    private var sawEyesClosed = false
    private val captures = mutableListOf<Bitmap>()
    private var startedAt = 0L
    private var lastCaptureAt = 0L

    // Written from the main thread by close(), read on the analysis thread.
    @Volatile
    private var finished = false

    private val _state = MutableStateFlow<FaceCaptureState>(FaceCaptureState.Instruct(FRONTAL_INSTRUCTION, 1, totalSteps))
    val state: StateFlow<FaceCaptureState> = _state

    override fun analyze(image: ImageProxy) {
        if (finished) {
            image.close()
            return
        }
        val frame = try {
            upright(image)
        } finally {
            image.close()
        }
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        if (System.currentTimeMillis() - startedAt > timeoutMillis) {
            fail("Couldn't get a clear picture. Face a light source, hold the phone at eye level, and try again.")
            return
        }

        val faces = try {
            Tasks.await(detector.process(InputImage.fromBitmap(frame, 0)))
        } catch (e: Exception) {
            return
        }
        if (faces.size != 1) {
            instruct(if (faces.isEmpty()) "Look at the camera" else "Only one face should be in view")
            return
        }
        val face = faces[0]
        if (trackingId == null) {
            trackingId = face.trackingId
        } else if (face.trackingId != null && face.trackingId != trackingId) {
            // A different face appeared: both captures must be of the same person.
            restart()
            return
        }
        if (face.boundingBox.width() < frame.width * MIN_FACE_FRACTION) {
            instruct("Move closer to the camera")
            return
        }

        val frontalCapture = step == 0 || step == totalSteps - 1
        if (frontalCapture) {
            if (!isFrontal(face)) {
                instruct(FRONTAL_INSTRUCTION)
                return
            }
            // Use two distinct moments, not two copies of the same instant.
            if (System.currentTimeMillis() - lastCaptureAt < MIN_CAPTURE_GAP_MILLIS) return
            val crop = FaceAligner.alignedCrop(frame, face) ?: return
            // Blurry frames are skipped rather than failing the whole check.
            if (models.sharpness(crop) < FaceModels.MIN_SHARPNESS) {
                instruct("Hold still")
                return
            }
            captures += crop
            lastCaptureAt = System.currentTimeMillis()
            advance()
            if (step == totalSteps) process()
            return
        }

        val action = actions[step - 1]
        if (actionCompleted(action, face)) advance()
    }

    fun close() {
        finished = true
        detector.close()
    }

    private fun actionCompleted(action: LivenessAction, face: Face): Boolean = when (action) {
        LivenessAction.BLINK -> {
            val left = face.leftEyeOpenProbability ?: 1f
            val right = face.rightEyeOpenProbability ?: 1f
            if (left < EYES_CLOSED && right < EYES_CLOSED) sawEyesClosed = true
            sawEyesClosed && left > EYES_OPEN && right > EYES_OPEN
        }
        LivenessAction.TURN_LEFT -> face.headEulerAngleY * TURN_LEFT_SIGN > TURN_DEGREES
        LivenessAction.TURN_RIGHT -> face.headEulerAngleY * TURN_LEFT_SIGN < -TURN_DEGREES
        LivenessAction.SMILE -> (face.smilingProbability ?: 0f) > SMILING
    }

    private fun isFrontal(face: Face): Boolean =
        abs(face.headEulerAngleY) < FRONTAL_DEGREES && abs(face.headEulerAngleZ) < FRONTAL_DEGREES &&
            (face.leftEyeOpenProbability ?: 0f) > EYES_OPEN && (face.rightEyeOpenProbability ?: 0f) > EYES_OPEN

    private fun advance() {
        step++
        sawEyesClosed = false
        if (step < totalSteps) instruct(currentInstruction())
    }

    private fun currentInstruction(): String =
        if (step == 0 || step == totalSteps - 1) FRONTAL_INSTRUCTION else actions[step - 1].instruction

    private fun instruct(message: String) {
        _state.value = FaceCaptureState.Instruct(message, (step + 1).coerceAtMost(totalSteps), totalSteps)
    }

    private fun restart() {
        step = 0
        trackingId = null
        captures.clear()
        instruct(FRONTAL_INSTRUCTION)
    }

    private fun process() {
        finished = true
        _state.value = FaceCaptureState.Processing
        val (first, second) = captures[0] to captures[1]
        val spoofScore = max(models.spoofScore(first), models.spoofScore(second))
        val (embeddingA, embeddingB) = models.embed(first, second)
        if (FaceModels.cosine(embeddingA, embeddingB) < SAME_PERSON_SIMILARITY) {
            fail("Your face changed during the check. Try again.")
            return
        }
        val embedding = FaceModels.l2Normalize(FloatArray(embeddingA.size) { embeddingA[it] + embeddingB[it] })
        val photo = ByteArrayOutputStream().use { stream ->
            Bitmap.createScaledBitmap(first, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
                .compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        }
        _state.value = FaceCaptureState.Done(CapturedFace(embedding, photo, spoofScore))
    }

    private fun fail(message: String) {
        finished = true
        _state.value = FaceCaptureState.Failed(message)
    }

    private fun upright(image: ImageProxy): Bitmap {
        val bitmap = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val FRONTAL_INSTRUCTION = "Look at the camera and hold still"
        const val MIN_FACE_FRACTION = 0.2f
        const val FRONTAL_DEGREES = 20f
        const val MIN_CAPTURE_GAP_MILLIS = 400L
        const val TURN_DEGREES = 25f
        // ML Kit reports a positive Y angle when the person turns to their own left. Flip to -1 if a
        // device test shows the opposite.
        const val TURN_LEFT_SIGN = 1f
        // Lenient so glasses and squinting in bright light still count as eyes open.
        const val EYES_OPEN = 0.4f
        const val EYES_CLOSED = 0.2f
        const val SMILING = 0.8f
        // Both frontal captures must be the same person, or someone swapped in mid-check.
        const val SAME_PERSON_SIMILARITY = 0.5f
        const val THUMBNAIL_SIZE = 160
    }
}

/** Crops a square, eye-level face from a frame, matching the alignment the face models were used with. */
object FaceAligner {
    private const val OUTPUT_SIZE = 256

    fun alignedCrop(frame: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val (first, second) = if (leftEye.x <= rightEye.x) leftEye to rightEye else rightEye to leftEye
        val angle = Math.toDegrees(atan2((second.y - first.y).toDouble(), (second.x - first.x).toDouble())).toFloat()

        val box = face.boundingBox
        val side = max(box.width(), box.height()).toFloat()
        val center = PointF(box.exactCenterX(), box.exactCenterY())
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            translate(OUTPUT_SIZE / 2f, OUTPUT_SIZE / 2f)
            rotate(-angle)
            scale(OUTPUT_SIZE / side, OUTPUT_SIZE / side)
            translate(-center.x, -center.y)
            drawBitmap(frame, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return output
    }
}
