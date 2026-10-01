package com.example.ble_app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class BleScanner(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _detectedAttendance = MutableStateFlow<Boolean>(false)
    val detectedAttendance: StateFlow<Boolean> = _detectedAttendance

    private val _detectedSessionId = MutableStateFlow<Int?>(null)
    val detectedSessionId: StateFlow<Int?> = _detectedSessionId

    private val _nearbyDevicesCount = MutableStateFlow(0)
    val nearbyDevicesCount: StateFlow<Int> = _nearbyDevicesCount

    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo

    private val MANUFACTURER_ID = 0xFFFF

    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScanning(expectedUuid: String) {
        if (scanner == null) {
            _debugInfo.value = "BLE Scanner not supported"
            return
        }

        _detectedAttendance.value = false
        _detectedSessionId.value = null
        _nearbyDevicesCount.value = 0
        _debugInfo.value = "Starting scan..."

        // Filter by the fixed Service UUID
        val filter = try {
            val uuid = UUID.fromString(expectedUuid)
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(uuid))
                .build()
        } catch (e: Exception) {
            _debugInfo.value = "Invalid UUID filter"
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                _nearbyDevicesCount.value++
                
                // Get data from Manufacturer Specific Data (0xFFFF)
                val manufacturerData = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)
                val receivedData = manufacturerData?.let { String(it, Charsets.UTF_8) }

                Log.d("BleScanner", "Found device: ${result.device.address}, Data: $receivedData")
                
                if (receivedData != null && receivedData.startsWith("BLE_ATTENDANCE:")) {
                    val idStr = receivedData.substringAfter("BLE_ATTENDANCE:")
                    val sessionId = idStr.toIntOrNull()
                    if (sessionId != null) {
                        _detectedSessionId.value = sessionId
                        _detectedAttendance.value = true
                        _debugInfo.value = "Attendance beacon detected! Session ID: $sessionId"
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                _isScanning.value = false
                _debugInfo.value = "Scan failed: $errorCode"
            }
        }

        scanner.startScan(listOf(filter), settings, scanCallback)
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        scanCallback?.let {
            scanner?.stopScan(it)
            scanCallback = null
        }
        _isScanning.value = false
    }
}
