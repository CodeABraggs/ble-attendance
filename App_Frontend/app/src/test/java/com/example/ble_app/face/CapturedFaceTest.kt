package com.example.ble_app.face

import com.example.ble_app.security.DeviceKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CapturedFaceTest {
    @Test
    fun embeddingIsSentAsLittleEndianFloats() {
        val embedding = FloatArray(FaceModels.EMBEDDING_LENGTH) { it / 1000f }
        val bytes = CapturedFace(embedding, ByteArray(0), 0f).embeddingBytes()

        assertEquals(FaceModels.EMBEDDING_LENGTH * 4, bytes.size)
        val decoded = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val roundTrip = FloatArray(embedding.size).also { decoded.get(it) }
        assertArrayEquals(embedding, roundTrip, 0f)
    }

    @Test
    fun spoofScoreIsSignedAsBasisPoints() {
        assertEquals(1234, CapturedFace(FloatArray(1), ByteArray(0), 0.12344f).spoofScoreBp)
        assertEquals(0, CapturedFace(FloatArray(1), ByteArray(0), -0.5f).spoofScoreBp)
    }

    @Test
    fun sha256HexMatchesServerFormat() {
        // Same lowercase hex as java.util.HexFormat on the server.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DeviceKey.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun cosineAndNormalizeBehave() {
        val a = FaceModels.l2Normalize(floatArrayOf(3f, 4f))
        assertEquals(1f, FaceModels.cosine(a, a), 1e-6f)
        assertEquals(0.6f, a[0], 1e-6f)
    }
}
