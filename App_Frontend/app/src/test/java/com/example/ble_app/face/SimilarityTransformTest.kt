package com.example.ble_app.face

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SimilarityTransformTest {
    @Test
    fun recoversAKnownRotationScaleAndShift() {
        val angle = Math.toRadians(17.0)
        val scale = 0.43
        val a = (scale * cos(angle)).toFloat()
        val b = (scale * sin(angle)).toFloat()
        val tx = 12.5f
        val ty = -7.25f
        val src = floatArrayOf(210f, 300f, 330f, 290f, 225f, 420f, 318f, 415f)
        val dst = FloatArray(src.size)
        for (index in 0 until src.size / 2) {
            val x = src[2 * index]
            val y = src[2 * index + 1]
            dst[2 * index] = a * x - b * y + tx
            dst[2 * index + 1] = b * x + a * y + ty
        }

        val (estA, estB, estTx, estTy) = SimilarityTransform.estimate(src, dst)

        assertEquals(a, estA, 1e-4f)
        assertEquals(b, estB, 1e-4f)
        assertEquals(tx, estTx, 1e-2f)
        assertEquals(ty, estTy, 1e-2f)
    }

    @Test
    fun mapsLandmarksOntoTheTemplate() {
        // A level face twice the template size, shifted: alignment must land on the template points.
        val template = FaceAligner.ARCFACE_TEMPLATE
        val src = FloatArray(template.size) { index -> template[index] * 2f + if (index % 2 == 0) 100f else 50f }

        val (a, b, tx, ty) = SimilarityTransform.estimate(src, template)

        for (index in 0 until src.size / 2) {
            val x = src[2 * index]
            val y = src[2 * index + 1]
            assertEquals(template[2 * index], a * x - b * y + tx, 1e-3f)
            assertEquals(template[2 * index + 1], b * x + a * y + ty, 1e-3f)
        }
    }
}
