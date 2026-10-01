package com.example.ble_app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * On-device face models (see assets/MODELS_LICENSE.txt):
 * - MobileFaceNet: 192-value face embedding for matching against the enrolled face. It was trained on
 *   insightface's 5-point-aligned 112x112 faces, so it is fed crops from [FaceAligner.alignForRecognition].
 * - FaceAntiSpoofing: scores whether a face crop is a real face or a photo/screen; fed [FaceAligner.boxCrop].
 */
class FaceModels private constructor(context: Context) {
    private val recognizer = Interpreter(loadModel(context, "MobileFaceNet.tflite"), Interpreter.Options().setNumThreads(4))
    private val antiSpoof = Interpreter(loadModel(context, "FaceAntiSpoofing.tflite"), Interpreter.Options().setNumThreads(4))

    /**
     * Embeds an aligned 112x112 face. The model's batch is fixed at 2, which fits its standard test-time
     * setup: embed the face and its mirror image and add them, which steadies the result.
     */
    @Synchronized
    fun embed(alignedFace: Bitmap): FloatArray {
        val face = if (alignedFace.width == EMBED_SIZE && alignedFace.height == EMBED_SIZE) {
            alignedFace
        } else {
            Bitmap.createScaledBitmap(alignedFace, EMBED_SIZE, EMBED_SIZE, true)
        }
        val input = ByteBuffer.allocateDirect(2 * EMBED_SIZE * EMBED_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        for (bitmap in listOf(face, mirror(face))) {
            writePixels(input, bitmap) { channel -> (channel - 127.5f) / 128f }
        }
        input.rewind()
        val output = Array(2) { FloatArray(EMBEDDING_LENGTH) }
        recognizer.run(input, output)
        return l2Normalize(FloatArray(EMBEDDING_LENGTH) { output[0][it] + output[1][it] })
    }

    /** Lower is more likely a real face; above [SPOOF_THRESHOLD] looks like a photo or screen. */
    @Synchronized
    fun spoofScore(face: Bitmap): Float {
        val input = ByteBuffer.allocateDirect(SPOOF_SIZE * SPOOF_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        writePixels(input, Bitmap.createScaledBitmap(face, SPOOF_SIZE, SPOOF_SIZE, true)) { channel -> channel / 255f }
        input.rewind()
        val classPrediction = Array(1) { FloatArray(8) }
        val leafNodeMask = Array(1) { FloatArray(8) }
        val outputs = mapOf(
            antiSpoof.getOutputIndex("Identity") to classPrediction,
            antiSpoof.getOutputIndex("Identity_1") to leafNodeMask
        )
        antiSpoof.runForMultipleInputsOutputs(arrayOf(input), outputs)
        var score = 0f
        for (index in 0 until 8) {
            score += kotlin.math.abs(classPrediction[0][index]) * leafNodeMask[0][index]
        }
        return score
    }

    /** Laplacian edge count; low values mean a blurry crop that the models can't judge reliably. */
    fun sharpness(face: Bitmap): Int {
        val grey = greyPixels(face)
        var score = 0
        for (y in 1 until SPOOF_SIZE - 1) {
            for (x in 1 until SPOOF_SIZE - 1) {
                val center = y * SPOOF_SIZE + x
                val laplacian = grey[center - SPOOF_SIZE] + grey[center + SPOOF_SIZE] +
                    grey[center - 1] + grey[center + 1] - 4 * grey[center]
                if (laplacian > LAPLACE_EDGE_THRESHOLD) score++
            }
        }
        return score
    }

    private fun greyPixels(face: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(face, SPOOF_SIZE, SPOOF_SIZE, true)
        val pixels = IntArray(SPOOF_SIZE * SPOOF_SIZE)
        scaled.getPixels(pixels, 0, SPOOF_SIZE, 0, 0, SPOOF_SIZE, SPOOF_SIZE)
        return IntArray(pixels.size) { index -> luma(pixels[index]) }
    }

    private inline fun writePixels(buffer: ByteBuffer, bitmap: Bitmap, normalize: (Float) -> Float) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            buffer.putFloat(normalize(Color.red(pixel).toFloat()))
            buffer.putFloat(normalize(Color.green(pixel).toFloat()))
            buffer.putFloat(normalize(Color.blue(pixel).toFloat()))
        }
    }

    companion object {
        /**
         * Version of the face pipeline (alignment + models). The server only compares samples with templates
         * of the same version; bump it whenever a change here makes embeddings incompatible.
         */
        const val MODEL_VERSION = 2

        // Frames averaged into one sample. Enrollment uses more for a steadier reference.
        const val ATTENDANCE_SAMPLES = 5
        const val ENROLLMENT_SAMPLES = 8

        const val EMBEDDING_LENGTH = 192
        // Must not be stricter than the server's attendance.face.spoof-threshold.
        const val SPOOF_THRESHOLD = 0.2f
        // Minimum Laplacian edge count for a usable face crop; lower it if dim rooms never pass.
        const val MIN_SHARPNESS = 600
        private const val EMBED_SIZE = 112
        private const val SPOOF_SIZE = 256
        private const val LAPLACE_EDGE_THRESHOLD = 50

        @Volatile
        private var instance: FaceModels? = null

        fun get(context: Context): FaceModels =
            instance ?: synchronized(this) {
                instance ?: FaceModels(context.applicationContext).also { instance = it }
            }

        /** Loads the models in the background so the first face check doesn't wait for them. */
        suspend fun preload(context: Context) {
            withContext(Dispatchers.Default) {
                try {
                    get(context)
                } catch (e: Exception) {
                    // Not fatal here; the face check loads (and reports) the models itself.
                    android.util.Log.w("FaceModels", "Could not preload face models", e)
                }
            }
        }

        fun l2Normalize(values: FloatArray): FloatArray {
            val norm = sqrt(values.fold(0f) { sum, value -> sum + value * value }).coerceAtLeast(1e-10f)
            return FloatArray(values.size) { values[it] / norm }
        }

        /** Normalized average of several embeddings of the same face. */
        fun mean(embeddings: List<FloatArray>): FloatArray {
            val sum = FloatArray(embeddings.first().size)
            for (embedding in embeddings) {
                for (index in sum.indices) sum[index] += embedding[index]
            }
            return l2Normalize(sum)
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (index in a.indices) {
                dot += a[index] * b[index]
                normA += a[index] * a[index]
                normB += b[index] * b[index]
            }
            return dot / sqrt(normA * normB)
        }

        /** Average brightness (0-255), sampled sparsely. */
        fun brightness(bitmap: Bitmap): Int {
            val step = 4
            var total = 0L
            var count = 0
            for (y in 0 until bitmap.height step step) {
                for (x in 0 until bitmap.width step step) {
                    total += luma(bitmap.getPixel(x, y))
                    count++
                }
            }
            return if (count == 0) 0 else (total / count).toInt()
        }

        private fun luma(pixel: Int): Int =
            (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000

        private fun mirror(bitmap: Bitmap): Bitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { preScale(-1f, 1f) }, false)

        private fun loadModel(context: Context, assetName: String): MappedByteBuffer {
            context.assets.openFd(assetName).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    return stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                }
            }
        }
    }
}
