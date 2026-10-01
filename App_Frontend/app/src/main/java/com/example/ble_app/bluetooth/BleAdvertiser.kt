package com.example.ble_app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class BleAdvertiser(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Manufacturer ID 0xFFFF is reserved for internal testing/development
    private val MANUFACTURER_ID = 0xFFFF 

    private var advertiseCallback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun startAdvertising(serviceUuid: String, attendanceData: String) {
        if (adapter == null || !adapter.isEnabled) {
            _errorMessage.value = "Bluetooth is disabled"
            return
        }
        
        if (advertiser == null) {
            _errorMessage.value = "BLE Advertising not supported"
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val uuid = try {
            UUID.fromString(serviceUuid)
        } catch (e: Exception) {
            _errorMessage.value = "Invalid UUID format"
            return
        }

        // 1. Primary Packet: Only Service UUID to save space
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(uuid))
            .build()

        // 2. Scan Response Packet: Attendance Data
        val scanResponse = AdvertiseData.Builder()
            .addManufacturerData(MANUFACTURER_ID, attendanceData.toByteArray(Charsets.UTF_8))
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                _isAdvertising.value = true
                _errorMessage.value = null
                Log.d("BleAdvertiser", "Broadcasting UUID: $serviceUuid with Data: $attendanceData")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
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

        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        advertiseCallback?.let {
            advertiser?.stopAdvertising(it)
            advertiseCallback = null
        }
        _isAdvertising.value = false
    }
}
