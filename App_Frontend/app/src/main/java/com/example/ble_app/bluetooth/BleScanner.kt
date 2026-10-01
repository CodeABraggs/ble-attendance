package com.example.ble_app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.ble_app.data.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Scans for the attendance beacon of one classroom; beacons from other classrooms are ignored. */
class BleScanner(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timeoutJob: Job? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _detectedBeacon = MutableStateFlow<Beacon?>(null)
    val detectedBeacon: StateFlow<Beacon?> = _detectedBeacon

    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo

    private var scanCallback: ScanCallback? = null

    // Looked up on every call: the system returns null while Bluetooth is off.
    private fun leScanner(): BluetoothLeScanner? =
        bluetoothManager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner

    @SuppressLint("MissingPermission") // Checked via BlePermissions.canScan
    fun startScanning(expectedClassId: Int) {
        stopScanning()
        _detectedBeacon.value = null

        if (!BlePermissions.canScan(appContext)) {
            _debugInfo.value = "Bluetooth permission is required to scan"
            return
        }
        val scanner = leScanner()
        if (scanner == null) {
            _debugInfo.value = if (bluetoothManager?.adapter?.isEnabled == true) "BLE Scanner not supported" else "Bluetooth is disabled"
            return
        }
        _debugInfo.value = "Starting scan..."

        // Filter by the fixed Service UUID
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(NetworkConfig.ATTENDANCE_SERVICE_UUID)))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val beacon = BeaconPayload.decode(
                    result.scanRecord?.getManufacturerSpecificData(BeaconPayload.MANUFACTURER_ID)
                ) ?: return
                if (beacon.classId != expectedClassId) {
                    _debugInfo.value = "Ignoring a beacon from another classroom"
                    return
                }
                Log.d("BleScanner", "Beacon for session ${beacon.sessionId} from ${result.device.address}")
                _debugInfo.value = "Attendance beacon detected! Session ID: ${beacon.sessionId}"
                _detectedBeacon.value = beacon
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
                _debugInfo.value = "Scan failed: $errorCode"
            }
        }

        try {
            scanner.startScan(listOf(filter), settings, callback)
        } catch (e: SecurityException) {
            _debugInfo.value = "Bluetooth permission is required to scan"
            return
        }
        scanCallback = callback
        _isScanning.value = true
        timeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MILLIS)
            stopScanning()
            if (_detectedBeacon.value == null) {
                _debugInfo.value = "No beacon found. Make sure you are near your teacher's device and try again."
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        timeoutJob?.cancel()
        timeoutJob = null
        scanCallback?.let { callback ->
            try {
                leScanner()?.stopScan(callback)
            } catch (e: SecurityException) {
                Log.w("BleScanner", "Missing permission to stop scanning", e)
            }
        }
        scanCallback = null
        _isScanning.value = false
    }

    /** Marks the current detection as handled so it isn't acted on again. */
    fun consumeDetection() {
        _detectedBeacon.value = null
    }

    /** Clears all state; call when entering or leaving a classroom. */
    fun reset() {
        stopScanning()
        _detectedBeacon.value = null
        _debugInfo.value = ""
    }

    fun release() {
        stopScanning()
        scope.cancel()
    }

    private companion object {
        const val SCAN_TIMEOUT_MILLIS = 60_000L
    }
}
