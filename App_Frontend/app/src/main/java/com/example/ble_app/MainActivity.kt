package com.example.ble_app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.example.ble_app.bluetooth.BleAdvertiser
import com.example.ble_app.bluetooth.BleScanner
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository
import com.example.ble_app.ui.*
import com.example.ble_app.ui.theme.BLE_APPTheme

sealed class Screen {
    object RoleSelection : Screen()
    object StudentLogin : Screen()
    object TeacherLogin : Screen()
    object StudentRegister : Screen()
    object TeacherRegister : Screen()
    object TeacherDashboard : Screen()
    object StudentDashboard : Screen()
    object CreateClassroom : Screen()
    object JoinClassroom : Screen()
    data class ClassroomDetails(val classroom: Classroom, val initialActiveSessionId: Int? = null) : Screen()
    data class AttendanceSessionConfig(val classroom: Classroom) : Screen()
    data class AttendanceRecords(val sessionId: Int, val classroom: Classroom) : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var bleAdvertiser: BleAdvertiser
    private lateinit var bleScanner: BleScanner

    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Permissions denied. BLE functionality may not work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        bleAdvertiser = BleAdvertiser(this)
        bleScanner = BleScanner(this)

        checkPermissions()
        checkBluetoothEnabled()

        setContent {
            BLE_APPTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.RoleSelection) }
                val user by Repository.currentUser.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (val screen = currentScreen) {
                            is Screen.RoleSelection -> HomeScreen(
                                onTeacherModeClick = { currentScreen = Screen.TeacherLogin },
                                onStudentModeClick = { currentScreen = Screen.StudentLogin }
                            )
                            is Screen.StudentLogin -> LoginScreen(
                                role = "STUDENT",
                                onLoginSuccess = { currentScreen = Screen.StudentDashboard },
                                onNavigateToRegister = { currentScreen = Screen.StudentRegister },
                                onBack = { currentScreen = Screen.RoleSelection }
                            )
                            is Screen.TeacherLogin -> LoginScreen(
                                role = "TEACHER",
                                onLoginSuccess = { currentScreen = Screen.TeacherDashboard },
                                onNavigateToRegister = { currentScreen = Screen.TeacherRegister },
                                onBack = { currentScreen = Screen.RoleSelection }
                            )
                            is Screen.StudentRegister -> RegisterScreen(
                                role = "STUDENT",
                                onRegisterSuccess = { currentScreen = Screen.StudentLogin },
                                onBack = { currentScreen = Screen.StudentLogin }
                            )
                            is Screen.TeacherRegister -> RegisterScreen(
                                role = "TEACHER",
                                onRegisterSuccess = { currentScreen = Screen.TeacherLogin },
                                onBack = { currentScreen = Screen.TeacherLogin }
                            )
                            is Screen.TeacherDashboard -> TeacherDashboard(
                                onClassroomClick = { currentScreen = Screen.ClassroomDetails(it) },
                                onCreateClassroom = { currentScreen = Screen.CreateClassroom },
                                onLogout = {
                                    Repository.logout()
                                    currentScreen = Screen.RoleSelection
                                }
                            )
                            is Screen.StudentDashboard -> StudentDashboard(
                                onClassroomClick = { currentScreen = Screen.ClassroomDetails(it) },
                                onJoinClassroom = { currentScreen = Screen.JoinClassroom },
                                onLogout = {
                                    Repository.logout()
                                    currentScreen = Screen.RoleSelection
                                }
                            )
                            is Screen.CreateClassroom -> CreateClassroomScreen(
                                onClassroomCreated = { currentScreen = Screen.TeacherDashboard },
                                onBack = { currentScreen = Screen.TeacherDashboard }
                            )
                            is Screen.JoinClassroom -> JoinClassroomScreen(
                                onJoined = { currentScreen = Screen.StudentDashboard },
                                onBack = { currentScreen = Screen.StudentDashboard }
                            )
                            is Screen.ClassroomDetails -> ClassroomDetailsScreen(
                                classroom = screen.classroom,
                                initialActiveSessionId = screen.initialActiveSessionId,
                                bleAdvertiser = bleAdvertiser,
                                bleScanner = bleScanner,
                                onStartSessionClick = { currentScreen = Screen.AttendanceSessionConfig(screen.classroom) },
                                onSessionClick = { sessionId -> currentScreen = Screen.AttendanceRecords(sessionId, screen.classroom) },
                                onBack = {
                                    currentScreen = if (user?.role == "TEACHER") {
                                        Screen.TeacherDashboard
                                    } else {
                                        Screen.StudentDashboard
                                    }
                                }
                            )
                            is Screen.AttendanceSessionConfig -> AttendanceSessionConfigScreen(
                                classroom = screen.classroom,
                                onSessionCreated = { session -> 
                                    currentScreen = Screen.ClassroomDetails(screen.classroom, session.sessionId) 
                                },
                                onBack = { currentScreen = Screen.ClassroomDetails(screen.classroom) }
                            )
                            is Screen.AttendanceRecords -> AttendanceRecordsScreen(
                                sessionId = screen.sessionId,
                                classroom = screen.classroom,
                                onBack = { currentScreen = Screen.ClassroomDetails(screen.classroom) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun checkBluetoothEnabled() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        startActivity(enableBtIntent)
                    }
                } else {
                    startActivity(enableBtIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Could not request Bluetooth enable", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
