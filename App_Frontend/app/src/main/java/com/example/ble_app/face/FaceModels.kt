package com.example.ble_app.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * On-device face models (see assets/MODELS_LICENSE.txt):
 * - MobileFaceNet: 192-value face embedding for matching against the enrolled face.
 * - FaceAntiSpoofing: scores whether a face crop is a real face or a photo/screen.
 * Both take an aligned square face crop produced by [FaceAligner].
 */
class FaceModels private constructor(context: Context) {
    private val recognizer = Interpreter(loadModel(context, "MobileFaceNet.tflite"), Interpreter.Options().setNumThreads(4))
    private val antiSpoof = Interpreter(loadModel(context, "FaceAntiSpoofing.tflite"), Interpreter.Options().setNumThreads(4))

    /** Embeds two crops of the same person in one pass (the model's batch size is fixed at 2). */
    @Synchronized
    fun embed(first: Bitmap, second: Bitmap): Pair<FloatArray, FloatArray> {
        val input = ByteBuffer.allocateDirect(2 * EMBED_SIZE * EMBED_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        for (bitmap in listOf(first, second)) {
            writePixels(input, Bitmap.createScaledBitmap(bitmap, EMBED_SIZE, EMBED_SIZE, true)) { channel ->
                (channel - 127.5f) / 128f
            }
        }
        input.rewind()
        val output = Array(2) { FloatArray(EMBEDDING_LENGTH) }
        recognizer.run(input, output)
        return l2Normalize(output[0]) to l2Normalize(output[1])
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
        val scaled = Bitmap.createScaledBitmap(face, SPOOF_SIZE, SPOOF_SIZE, true)
        val pixels = IntArray(SPOOF_SIZE * SPOOF_SIZE)
        scaled.getPixels(pixels, 0, SPOOF_SIZE, 0, 0, SPOOF_SIZE, SPOOF_SIZE)
        val grey = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
        }
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
        const val EMBEDDING_LENGTH = 192
        const val SPOOF_THRESHOLD = 0.2f
        const val MIN_SHARPNESS = 1000
        private const val EMBED_SIZE = 112
        private const val SPOOF_SIZE = 256
        private const val LAPLACE_EDGE_THRESHOLD = 50

        @Volatile
        private var instance: FaceModels? = null

        fun get(context: Context): FaceModels =
            instance ?: synchronized(this) {
                instance ?: FaceModels(context.applicationContext).also { instance = it }
            }

        fun l2Normalize(values: FloatArray): FloatArray {
            val norm = sqrt(values.fold(0f) { sum, value -> sum + value * value }).coerceAtLeast(1e-10f)
            return FloatArray(values.size) { values[it] / norm }
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

        private fun loadModel(context: Context, assetName: String): MappedByteBuffer {
            context.assets.openFd(assetName).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    return stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
                }
            }
        }
    }
}
