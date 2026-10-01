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
import kotlin.math.ceil
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
    /** [progress] is the share of face frames captured so far, from 0 to 1. */
    data class Instruct(val message: String, val progress: Float) : FaceCaptureState()
    object Processing : FaceCaptureState()
    data class Done(val face: CapturedFace) : FaceCaptureState()
    data class Failed(val message: String) : FaceCaptureState()
}

/**
 * Builds one face sample from camera frames: [samplesNeeded] sharp, straight-on frames (with any liveness
 * [actions] performed halfway through), each aligned and embedded, outliers dropped, the rest averaged.
 * Averaging several aligned frames gives a much steadier embedding than a single frame, so the server's
 * match usually succeeds on the first try. Photos and screens are caught by the anti-spoofing model.
 */
class FaceCaptureAnalyzer(
    context: Context,
    private val actions: List<LivenessAction>,
    private val samplesNeeded: Int,
    private val timeoutMillis: Long = 30_000L
) : ImageAnalysis.Analyzer {
    private sealed class Phase {
        class Capture(val count: Int) : Phase()
        class Act(val action: LivenessAction) : Phase()
    }

    private class Sample(val recognitionCrop: Bitmap, val boxCrop: Bitmap, val sharpness: Int, val spoofScore: Float)

    private val appContext = context.applicationContext
    // Normally preloaded by the screen; otherwise loaded here, on the analysis thread.
    private val models by lazy { FaceModels.get(appContext) }
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // Accurate mode gives better landmarks, which the alignment depends on.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private val phases: List<Phase> = run {
        require(samplesNeeded >= 2) { "Need at least 2 face frames" }
        if (actions.isEmpty()) {
            listOf(Phase.Capture(samplesNeeded))
        } else {
            val before = (samplesNeeded + 1) / 2
            listOf(Phase.Capture(before)) + actions.map { Phase.Act(it) } + Phase.Capture(samplesNeeded - before)
        }
    }

    // Only touched on the analysis thread.
    private var phaseIndex = 0
    private var capturedInPhase = 0
    private var sawEyesClosed = false
    private val samples = mutableListOf<Sample>()
    private var firstFrameAt = 0L
    private var startedAt = 0L
    private var lastCaptureAt = 0L
    private var lastProblem: String? = null

    // Set from the main thread. Only the analysis thread clears [finished], when it handles a restart.
    @Volatile
    private var finished = false
    @Volatile
    private var restartRequested = false

    private val _state = MutableStateFlow<FaceCaptureState>(FaceCaptureState.Instruct(GETTING_READY, 0f))
    val state: StateFlow<FaceCaptureState> = _state

    /** Takes a new sample with the camera still running, e.g. when the server asks for another try. */
    fun restart() {
        _state.value = FaceCaptureState.Instruct(FRONTAL_INSTRUCTION, 0f)
        restartRequested = true
    }

    fun close() {
        finished = true
        detector.close()
    }

    override fun analyze(image: ImageProxy) {
        if (restartRequested) {
            restartRequested = false
            finished = false
            resetCapture()
            startedAt = 0L
        }
        if (finished) {
            image.close()
            return
        }
        val frame = try {
            upright(image)
        } finally {
            image.close()
        }

        val now = System.currentTimeMillis()
        if (firstFrameAt == 0L) firstFrameAt = now
        // The first frames after the camera opens are often dark or soft while exposure and focus settle.
        if (now - firstFrameAt < WARMUP_MILLIS) return
        if (startedAt == 0L) startedAt = now
        if (now - startedAt > timeoutMillis) {
            fail(lastProblem?.let { "Couldn't get a clear picture ($it). Face a light source and try again." } ?: TIMEOUT_MESSAGE)
            return
        }

        val faces = try {
            Tasks.await(detector.process(InputImage.fromBitmap(frame, 0)))
        } catch (e: Exception) {
            return
        }
        if (faces.size != 1) {
            problem(if (faces.isEmpty()) "Look at the camera" else "Only one face should be in view")
            return
        }
        val face = faces[0]
        if (face.boundingBox.width() < frame.width * MIN_FACE_FRACTION) {
            problem("Move closer to the camera")
            return
        }

        if (phaseIndex >= phases.size) return
        when (val phase = phases[phaseIndex]) {
            is Phase.Capture -> capture(frame, face, phase, now)
            is Phase.Act -> if (actionCompleted(phase.action, face)) nextPhase() else instruct(phase.action.instruction)
        }
        if (phaseIndex == phases.size) finish()
    }

    private fun capture(frame: Bitmap, face: Face, phase: Phase.Capture, now: Long) {
        if (!isFrontal(face)) {
            problem(FRONTAL_INSTRUCTION)
            return
        }
        if (now - lastCaptureAt < MIN_CAPTURE_GAP_MILLIS) return
        val recognitionCrop = FaceAligner.alignForRecognition(frame, face) ?: return
        val boxCrop = FaceAligner.boxCrop(frame, face) ?: return
        val sharpness = models.sharpness(boxCrop)
        if (sharpness < FaceModels.MIN_SHARPNESS) {
            problem(if (FaceModels.brightness(boxCrop) < DARK_BRIGHTNESS) TOO_DARK else "Hold the phone still")
            return
        }
        samples += Sample(recognitionCrop, boxCrop, sharpness, models.spoofScore(boxCrop))
        lastCaptureAt = now
        lastProblem = null
        capturedInPhase++
        if (capturedInPhase >= phase.count) nextPhase() else instruct(HOLD_STILL)
    }

    /** Combines the captured frames into one sample, or quietly starts over if they don't agree. */
    private fun finish() {
        _state.value = FaceCaptureState.Processing
        val embeddings = samples.map { models.embed(it.recognitionCrop) }
        val center = FaceModels.mean(embeddings)
        // Frames that disagree with the rest (a blink, motion blur, a bad landmark fit) are dropped.
        val inliers = samples.indices.filter { FaceModels.cosine(embeddings[it], center) >= INLIER_SIMILARITY }
        if (inliers.size < ceil(samples.size * MIN_INLIER_SHARE).toInt()) {
            retryCapture("Hold still and keep looking at the camera")
            return
        }
        val spoofScore = median(inliers.map { samples[it].spoofScore })
        if (spoofScore > FaceModels.SPOOF_THRESHOLD) {
            // Usually glare or backlight on a real face; the server would reject it, so try again first.
            retryCapture("Face a light source and hold still")
            return
        }
        val embedding = FaceModels.mean(inliers.map { embeddings[it] })
        val sharpest = samples[inliers.maxBy { samples[it].sharpness }]
        val photo = ByteArrayOutputStream().use { stream ->
            Bitmap.createScaledBitmap(sharpest.boxCrop, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
                .compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        }
        finished = true
        _state.value = FaceCaptureState.Done(CapturedFace(embedding, photo, spoofScore))
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

    private fun nextPhase() {
        phaseIndex++
        capturedInPhase = 0
        sawEyesClosed = false
        if (phaseIndex < phases.size) {
            val next = phases[phaseIndex]
            instruct(if (next is Phase.Act) next.action.instruction else HOLD_STILL)
        }
    }

    private fun retryCapture(message: String) {
        resetCapture()
        problem(message)
    }

    private fun resetCapture() {
        phaseIndex = 0
        capturedInPhase = 0
        sawEyesClosed = false
        samples.clear()
        lastCaptureAt = 0L
        lastProblem = null
        instruct(FRONTAL_INSTRUCTION)
    }

    private fun problem(message: String) {
        lastProblem = message
        instruct(if (message == TOO_DARK) "Too dark. Face a light source" else message)
    }

    private fun instruct(message: String) {
        _state.value = FaceCaptureState.Instruct(message, samples.size / samplesNeeded.toFloat())
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
        const val GETTING_READY = "Getting the camera ready..."
        const val FRONTAL_INSTRUCTION = "Look at the camera and hold still"
        const val HOLD_STILL = "Hold still..."
        const val TOO_DARK = "too dark"
        const val TIMEOUT_MESSAGE = "Couldn't get a clear picture. Face a light source, hold the phone at eye level, and try again."
        const val WARMUP_MILLIS = 600L
        const val MIN_CAPTURE_GAP_MILLIS = 150L
        const val MIN_FACE_FRACTION = 0.2f
        const val FRONTAL_DEGREES = 20f
        const val TURN_DEGREES = 25f
        // ML Kit reports a positive Y angle when the person turns to their own left. Flip to -1 if a
        // device test shows the opposite.
        const val TURN_LEFT_SIGN = 1f
        // Lenient so glasses and squinting in bright light still count as eyes open.
        const val EYES_OPEN = 0.4f
        const val EYES_CLOSED = 0.2f
        const val SMILING = 0.8f
        const val DARK_BRIGHTNESS = 50
        // A frame must be this similar to the average of all frames to be kept.
        const val INLIER_SIMILARITY = 0.6f
        const val MIN_INLIER_SHARE = 0.6
        const val THUMBNAIL_SIZE = 160

        fun median(values: List<Float>): Float {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}

/** Least-squares 2D similarity transform (rotation, uniform scale, translation) between point sets. */
object SimilarityTransform {
    /**
     * Points are packed as [x0, y0, x1, y1, ...]. Returns [a, b, tx, ty] for the transform that best maps
     * [src] onto [dst]: x' = a*x - b*y + tx, y' = b*x + a*y + ty.
     */
    fun estimate(src: FloatArray, dst: FloatArray): FloatArray {
        require(src.size == dst.size && src.size >= 4 && src.size % 2 == 0) { "Need matching point pairs" }
        val count = src.size / 2
        var srcX = 0f
        var srcY = 0f
        var dstX = 0f
        var dstY = 0f
        for (index in 0 until count) {
            srcX += src[2 * index]
            srcY += src[2 * index + 1]
            dstX += dst[2 * index]
            dstY += dst[2 * index + 1]
        }
        srcX /= count
        srcY /= count
        dstX /= count
        dstY /= count
        var dot = 0f
        var cross = 0f
        var norm = 0f
        for (index in 0 until count) {
            val sx = src[2 * index] - srcX
            val sy = src[2 * index + 1] - srcY
            val dx = dst[2 * index] - dstX
            val dy = dst[2 * index + 1] - dstY
            dot += sx * dx + sy * dy
            cross += sx * dy - sy * dx
            norm += sx * sx + sy * sy
        }
        val a = dot / norm
        val b = cross / norm
        return floatArrayOf(a, b, dstX - (a * srcX - b * srcY), dstY - (b * srcX + a * srcY))
    }
}

/** Face crops for the two models, each matching how that model expects faces to be framed. */
object FaceAligner {
    private const val RECOGNITION_SIZE = 112
    private const val BOX_CROP_SIZE = 256

    // Standard insightface/ArcFace positions in a 112x112 face (MobileFaceNet's training alignment):
    // eye on the image's left, eye on the image's right, mouth corner left, mouth corner right.
    // The nose point is left out because ML Kit's NOSE_BASE is not the nose tip the template uses.
    val ARCFACE_TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f
    )

    /** 112x112 face with eyes and mouth moved onto the template, as MobileFaceNet was trained. */
    fun alignForRecognition(frame: Bitmap, face: Face): Bitmap? {
        val eyes = listOf(FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE)
            .map { face.getLandmark(it)?.position ?: return null }
            .sortedBy { it.x }
        val mouth = listOf(FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT)
            .map { face.getLandmark(it)?.position ?: return null }
            .sortedBy { it.x }
        val src = floatArrayOf(
            eyes[0].x, eyes[0].y, eyes[1].x, eyes[1].y,
            mouth[0].x, mouth[0].y, mouth[1].x, mouth[1].y
        )
        val (a, b, tx, ty) = SimilarityTransform.estimate(src, ARCFACE_TEMPLATE)
        val matrix = Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }
        val output = Bitmap.createBitmap(RECOGNITION_SIZE, RECOGNITION_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(frame, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    /** Square, eye-level crop around the detected face box, used for anti-spoofing and the thumbnail. */
    fun boxCrop(frame: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val (first, second) = if (leftEye.x <= rightEye.x) leftEye to rightEye else rightEye to leftEye
        val angle = Math.toDegrees(atan2((second.y - first.y).toDouble(), (second.x - first.x).toDouble())).toFloat()

        val box = face.boundingBox
        val side = max(box.width(), box.height()).toFloat()
        val center = PointF(box.exactCenterX(), box.exactCenterY())
        val output = Bitmap.createBitmap(BOX_CROP_SIZE, BOX_CROP_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            translate(BOX_CROP_SIZE / 2f, BOX_CROP_SIZE / 2f)
            rotate(-angle)
            scale(BOX_CROP_SIZE / side, BOX_CROP_SIZE / side)
            translate(-center.x, -center.y)
            drawBitmap(frame, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return output
    }
}
