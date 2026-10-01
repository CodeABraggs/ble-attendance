package com.example.ble_app.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** What the teacher's phone broadcasts: which class and session, plus the current rotating code. */
data class Beacon(val classId: Int, val sessionId: Int, val code: Int)

object BeaconPayload {
    // Manufacturer ID 0xFFFF is reserved for internal testing/development
    const val MANUFACTURER_ID = 0xFFFF
    private const val MAGIC_0: Byte = 0x42 // 'B'
    private const val MAGIC_1: Byte = 0x41 // 'A'
    private const val SIZE = 14

    fun encode(beacon: Beacon): ByteArray = ByteBuffer.allocate(SIZE)
        .put(MAGIC_0).put(MAGIC_1)
        .putInt(beacon.classId)
        .putInt(beacon.sessionId)
        .putInt(beacon.code)
        .array()

    fun decode(bytes: ByteArray?): Beacon? {
        if (bytes == null || bytes.size != SIZE || bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) return null
        val buffer = ByteBuffer.wrap(bytes, 2, SIZE - 2)
        return Beacon(buffer.int, buffer.int, buffer.int)
    }
}

/**
 * Rotating beacon codes. Must match BeaconCodes.java on the server:
 * HMAC-SHA256(secret, sessionId || window), first 4 bytes, with 30-second windows.
 */
object BeaconCodes {
    const val WINDOW_MILLIS = 30_000L

    fun windowAt(epochMillis: Long): Long = epochMillis / WINDOW_MILLIS

    fun code(secretHex: String, sessionId: Long, window: Long): Int {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hexToBytes(secretHex), "HmacSHA256"))
        val hash = mac.doFinal(ByteBuffer.allocate(16).putLong(sessionId).putLong(window).array())
        return ByteBuffer.wrap(hash, 0, 4).int
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex secret" }
        return ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
}

object BlePermissions {
    val required: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun missing(context: Context): List<String> = required.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun canScan(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun canAdvertise(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(context, Manifest.permission.BLUETOOTH_ADVERTISE)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
