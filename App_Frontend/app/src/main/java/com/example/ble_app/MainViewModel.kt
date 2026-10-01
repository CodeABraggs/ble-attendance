package com.example.ble_app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ble_app.bluetooth.BleAdvertiser
import com.example.ble_app.bluetooth.BleScanner
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository
import kotlinx.coroutines.launch

/**
 * Survives configuration changes (rotation), so navigation and any running BLE beacon/scan are kept.
 * The BLE objects are released only when the activity finishes for good.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val bleAdvertiser = BleAdvertiser(application)
    val bleScanner = BleScanner(application)

    var currentScreen by mutableStateOf(homeScreenFor(Repository.currentUser.value?.role))
        private set

    init {
        viewModelScope.launch { Repository.validateSession() }
        // Return to the start screen whenever the session ends (logout, expiry, or a 401 from the server).
        viewModelScope.launch {
            Repository.currentUser.collect { user ->
                if (user == null && currentScreen.requiresLogin) {
                    stopBle()
                    currentScreen = Screen.RoleSelection
                }
            }
        }
    }

    fun navigate(screen: Screen) {
        currentScreen = screen
    }

    fun openClassroom(classroom: Classroom) {
        // Drop any detection left over from another classroom.
        bleScanner.reset()
        currentScreen = Screen.ClassroomDetails(classroom)
    }

    fun goToDashboard() {
        currentScreen = homeScreenFor(Repository.currentUser.value?.role)
    }

    fun logout() {
        stopBle()
        currentScreen = Screen.RoleSelection
        viewModelScope.launch { Repository.logout() }
    }

    fun stopBle() {
        bleAdvertiser.stopAdvertising()
        bleScanner.reset()
    }

    override fun onCleared() {
        bleAdvertiser.release()
        bleScanner.release()
    }

    private fun homeScreenFor(role: String?): Screen = when (role) {
        "TEACHER" -> Screen.TeacherDashboard
        "STUDENT" -> Screen.StudentDashboard
        else -> Screen.RoleSelection
    }
}
