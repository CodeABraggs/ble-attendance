package com.example.ble_app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ble_app.bluetooth.BlePermissions
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository
import com.example.ble_app.ui.*
import com.example.ble_app.ui.theme.BLE_APPTheme

sealed class Screen {
    open val requiresLogin: Boolean = true

    object RoleSelection : Screen() { override val requiresLogin = false }
    object StudentLogin : Screen() { override val requiresLogin = false }
    object TeacherLogin : Screen() { override val requiresLogin = false }
    object StudentRegister : Screen() { override val requiresLogin = false }
    object TeacherRegister : Screen() { override val requiresLogin = false }
    object TeacherDashboard : Screen()
    object StudentDashboard : Screen()
    object CreateClassroom : Screen()
    object JoinClassroom : Screen()
    object FaceEnrollment : Screen()
    data class ClassroomDetails(val classroom: Classroom) : Screen()
    data class AttendanceSessionConfig(val classroom: Classroom) : Screen()
    data class AttendanceRecords(val sessionId: Int, val classroom: Classroom) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // On Android 12+ enabling Bluetooth needs BLUETOOTH_CONNECT, so ask only once it's granted.
            checkBluetoothEnabled()
        } else {
            Toast.makeText(this, "Permissions denied. BLE functionality may not work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repository.init(applicationContext)

        // Only on a fresh start, not after rotation.
        if (savedInstanceState == null && ensureBlePermissions()) {
            checkBluetoothEnabled()
        }

        val vm = viewModel
        setContent {
            BLE_APPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppNavigation(vm)
                    }
                }
            }
        }
    }

    /** Returns true if BLE permissions are granted; otherwise asks for them and returns false. */
    private fun ensureBlePermissions(): Boolean {
        val missing = BlePermissions.missing(this)
        if (missing.isEmpty()) return true
        requestPermissionLauncher.launch(missing.toTypedArray())
        return false
    }

    private fun checkBluetoothEnabled() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled && BlePermissions.missing(this).isEmpty()) {
            try {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not request Bluetooth enable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppNavigation(vm: MainViewModel) {
        val leaveClassroom = {
            vm.stopBle()
            vm.goToDashboard()
        }

        // System back mirrors the toolbar back buttons; on the start screen and dashboards it exits the app.
        when (val screen = vm.currentScreen) {
            is Screen.StudentLogin, is Screen.TeacherLogin -> BackHandler { vm.navigate(Screen.RoleSelection) }
            is Screen.StudentRegister -> BackHandler { vm.navigate(Screen.StudentLogin) }
            is Screen.TeacherRegister -> BackHandler { vm.navigate(Screen.TeacherLogin) }
            is Screen.CreateClassroom, is Screen.JoinClassroom, is Screen.FaceEnrollment -> BackHandler { vm.goToDashboard() }
            is Screen.ClassroomDetails -> BackHandler { leaveClassroom() }
            is Screen.AttendanceSessionConfig -> BackHandler { vm.navigate(Screen.ClassroomDetails(screen.classroom)) }
            is Screen.AttendanceRecords -> BackHandler { vm.navigate(Screen.ClassroomDetails(screen.classroom)) }
            else -> Unit
        }

        when (val screen = vm.currentScreen) {
            is Screen.RoleSelection -> HomeScreen(
                onTeacherModeClick = { vm.navigate(Screen.TeacherLogin) },
                onStudentModeClick = { vm.navigate(Screen.StudentLogin) }
            )
            is Screen.StudentLogin -> LoginScreen(
                role = "STUDENT",
                onLoginSuccess = { vm.goToDashboard() },
                onNavigateToRegister = { vm.navigate(Screen.StudentRegister) },
                onBack = { vm.navigate(Screen.RoleSelection) }
            )
            is Screen.TeacherLogin -> LoginScreen(
                role = "TEACHER",
                onLoginSuccess = { vm.navigate(Screen.TeacherDashboard) },
                onNavigateToRegister = { vm.navigate(Screen.TeacherRegister) },
                onBack = { vm.navigate(Screen.RoleSelection) }
            )
            is Screen.StudentRegister -> RegisterScreen(
                role = "STUDENT",
                onRegisterSuccess = { vm.goToDashboard() },
                onBack = { vm.navigate(Screen.StudentLogin) }
            )
            is Screen.TeacherRegister -> RegisterScreen(
                role = "TEACHER",
                onRegisterSuccess = { vm.navigate(Screen.TeacherDashboard) },
                onBack = { vm.navigate(Screen.TeacherLogin) }
            )
            is Screen.TeacherDashboard -> TeacherDashboard(
                onClassroomClick = { vm.openClassroom(it) },
                onCreateClassroom = { vm.navigate(Screen.CreateClassroom) },
                onLogout = { vm.logout() }
            )
            is Screen.StudentDashboard -> StudentDashboard(
                onClassroomClick = { vm.openClassroom(it) },
                onJoinClassroom = { vm.navigate(Screen.JoinClassroom) },
                onSetUpFace = { vm.navigate(Screen.FaceEnrollment) },
                onLogout = { vm.logout() }
            )
            is Screen.FaceEnrollment -> FaceEnrollmentScreen(
                onEnrolled = { vm.navigate(Screen.StudentDashboard) },
                onSkip = { vm.navigate(Screen.StudentDashboard) }
            )
            is Screen.CreateClassroom -> CreateClassroomScreen(
                onClassroomCreated = { vm.navigate(Screen.TeacherDashboard) },
                onBack = { vm.navigate(Screen.TeacherDashboard) }
            )
            is Screen.JoinClassroom -> JoinClassroomScreen(
                onJoined = { vm.navigate(Screen.StudentDashboard) },
                onBack = { vm.navigate(Screen.StudentDashboard) }
            )
            is Screen.ClassroomDetails -> ClassroomDetailsScreen(
                classroom = screen.classroom,
                bleAdvertiser = vm.bleAdvertiser,
                bleScanner = vm.bleScanner,
                ensureBlePermissions = { ensureBlePermissions() },
                onStartSessionClick = { vm.navigate(Screen.AttendanceSessionConfig(screen.classroom)) },
                onSessionClick = { sessionId -> vm.navigate(Screen.AttendanceRecords(sessionId, screen.classroom)) },
                onBack = leaveClassroom
            )
            is Screen.AttendanceSessionConfig -> AttendanceSessionConfigScreen(
                classroom = screen.classroom,
                onSessionCreated = { session ->
                    if (ensureBlePermissions()) {
                        vm.bleAdvertiser.startSessionBeacon(session)
                    }
                    vm.navigate(Screen.ClassroomDetails(screen.classroom))
                },
                onBack = { vm.navigate(Screen.ClassroomDetails(screen.classroom)) }
            )
            is Screen.AttendanceRecords -> AttendanceRecordsScreen(
                sessionId = screen.sessionId,
                classroom = screen.classroom,
                onBack = { vm.navigate(Screen.ClassroomDetails(screen.classroom)) }
            )
        }
    }
}
