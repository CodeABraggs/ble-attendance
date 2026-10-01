package com.example.ble_app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeaconCodesTest {
    private val secret = "00112233445566778899aabbccddeeff"

    // Same vectors as the server's BeaconCodesTest.java, so both sides are known to agree.
    @Test
    fun matchesSharedTestVectors() {
        assertEquals(-1399218303, BeaconCodes.code(secret, 42, 58_000_000))
        assertEquals(172204245, BeaconCodes.code(secret, 7, 1))
    }

    @Test
    fun windowMatchesServer() {
        // Server: epochSecond / 30
        assertEquals(58_000_000L, BeaconCodes.windowAt(58_000_000L * 30_000 + 29_999))
    }

    @Test
    fun payloadRoundTripsAndFitsScanResponse() {
        val beacon = Beacon(classId = 123456, sessionId = 987654, code = -1399218303)
        val bytes = BeaconPayload.encode(beacon)
        // Scan response is 31 bytes; manufacturer data adds 4 bytes of header.
        assert(bytes.size + 4 <= 31)
        assertEquals(beacon, BeaconPayload.decode(bytes))
        assertNull(BeaconPayload.decode("BLE_ATTENDANCE:1".toByteArray()))
    }
}
