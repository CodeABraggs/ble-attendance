package com.example.ble_app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.ble_app.data.AttendanceSession
import com.example.ble_app.data.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Broadcasts the attendance beacon for one session. The payload's code rotates every
 * [BeaconCodes.WINDOW_MILLIS], and broadcasting starts/stops at the session's start/end time.
 */
class BleAdvertiser(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var beaconJob: Job? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising

    // Session the beacon is running (or waiting to start) for; null when idle.
    private val _activeSessionId = MutableStateFlow<Int?>(null)
    val activeSessionId: StateFlow<Int?> = _activeSessionId

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var advertiseCallback: AdvertiseCallback? = null

    fun startSessionBeacon(session: AttendanceSession) {
        stopAdvertising()
        if (System.currentTimeMillis() >= session.endEpochMillis) {
            _errorMessage.value = "This session has already ended"
            return
        }
        _errorMessage.value = null
        _activeSessionId.value = session.sessionId
        beaconJob = scope.launch {
            val untilStart = session.startEpochMillis - System.currentTimeMillis()
            if (untilStart > 0) delay(untilStart)
            while (isActive && System.currentTimeMillis() < session.endEpochMillis) {
                val now = System.currentTimeMillis()
                val window = BeaconCodes.windowAt(now)
                val code = BeaconCodes.code(session.beaconSecret, session.sessionId.toLong(), window)
                if (!advertise(Beacon(session.classId, session.sessionId, code))) break
                val nextChangeAt = minOf((window + 1) * BeaconCodes.WINDOW_MILLIS, session.endEpochMillis)
                delay(nextChangeAt - now)
            }
            // Reached only when the session ended or advertising failed; stopAdvertising() cancels instead.
            stopCurrentAdvertisement()
            _activeSessionId.value = null
        }
    }

    fun stopAdvertising() {
        beaconJob?.cancel()
        beaconJob = null
        stopCurrentAdvertisement()
        _activeSessionId.value = null
    }

    fun release() {
        stopAdvertising()
        scope.cancel()
    }

    // Looked up on every call: the system returns null while Bluetooth is off.
    private fun leAdvertiser(): BluetoothLeAdvertiser? =
        bluetoothManager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser

    @SuppressLint("MissingPermission") // Checked via BlePermissions.canAdvertise
    private fun advertise(beacon: Beacon): Boolean {
        if (!BlePermissions.canAdvertise(appContext)) {
            _errorMessage.value = "Bluetooth permission is required to broadcast attendance"
            return false
        }
        if (bluetoothManager?.adapter?.isEnabled != true) {
            _errorMessage.value = "Bluetooth is disabled"
            return false
        }
        val advertiser = leAdvertiser()
        if (advertiser == null) {
            _errorMessage.value = "BLE Advertising not supported"
            return false
        }
        stopCurrentAdvertisement()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // 1. Primary Packet: Only Service UUID to save space
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(UUID.fromString(NetworkConfig.ATTENDANCE_SERVICE_UUID)))
            .build()

        // 2. Scan Response Packet: class, session and rotating code
        val scanResponse = AdvertiseData.Builder()
            .addManufacturerData(BeaconPayload.MANUFACTURER_ID, BeaconPayload.encode(beacon))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _isAdvertising.value = true
                _errorMessage.value = null
                Log.d("BleAdvertiser", "Broadcasting beacon for session ${beacon.sessionId}")
            }

            override fun onStartFailure(errorCode: Int) {
                _isAdvertising.value = false
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large. Try shorter data string."
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                    else -> "Error code: $errorCode"
                }
                _errorMessage.value = reason
                Log.e("BleAdvertiser", "Start failed: $reason")
            }
        }
        return try {
            advertiser.startAdvertising(settings, data, scanResponse, callback)
            advertiseCallback = callback
            true
        } catch (e: SecurityException) {
            _errorMessage.value = "Bluetooth permission is required to broadcast attendance"
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopCurrentAdvertisement() {
        advertiseCallback?.let { callback ->
            try {
                leAdvertiser()?.stopAdvertising(callback)
            } catch (e: SecurityException) {
                Log.w("BleAdvertiser", "Missing permission to stop advertising", e)
            }
        }
        advertiseCallback = null
        _isAdvertising.value = false
    }
}
