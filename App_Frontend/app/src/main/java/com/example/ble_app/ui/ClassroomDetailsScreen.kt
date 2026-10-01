package com.example.ble_app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ble_app.bluetooth.BleAdvertiser
import com.example.ble_app.bluetooth.BleScanner
import com.example.ble_app.data.AttendanceChallenge
import com.example.ble_app.data.ApiException
import com.example.ble_app.data.AttendanceSession
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository
import com.example.ble_app.data.StudentAttendanceHistoryRecord
import com.example.ble_app.face.FaceModels
import com.example.ble_app.face.LivenessAction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomDetailsScreen(
    classroom: Classroom,
    bleAdvertiser: BleAdvertiser,
    bleScanner: BleScanner,
    ensureBlePermissions: () -> Boolean,
    onStartSessionClick: () -> Unit,
    onSessionClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val user by Repository.currentUser.collectAsState()
    val isTeacher = user?.role == "TEACHER"

    // BLE States
    val isAdvertising by bleAdvertiser.isAdvertising.collectAsState()
    val activeSessionId by bleAdvertiser.activeSessionId.collectAsState()
    val errorMsg by bleAdvertiser.errorMessage.collectAsState()
    val isScanning by bleScanner.isScanning.collectAsState()
    val detectedBeacon by bleScanner.detectedBeacon.collectAsState()
    val debugInfo by bleScanner.debugInfo.collectAsState()

    // Backend session/history data lists
    var sessions by remember { mutableStateOf<List<AttendanceSession>>(emptyList()) }
    var studentHistory by remember { mutableStateOf<List<StudentAttendanceHistoryRecord>>(emptyList()) }
    var loadingData by remember { mutableStateOf(false) }
    var studentStatusMessage by rememberSaveable { mutableStateOf("Click the button to scan for the classroom session.") }
    var attendanceMarkedSuccess by rememberSaveable { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    // Liveness challenge for the detected session; while set, the camera check replaces this screen.
    var pendingChallenge by remember { mutableStateOf<Pair<Int, AttendanceChallenge>?>(null) }
    // Set when the server asks for another face sample on the same challenge.
    var faceRetryHint by remember { mutableStateOf<String?>(null) }
    var faceRetryToken by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    fun refreshData() {
        loadingData = true
        scope.launch {
            try {
                if (isTeacher) {
                    sessions = Repository.getSessionsForClassroom(classroom.classId)
                } else {
                    studentHistory = Repository.getStudentAttendanceHistory(classroom.classId)
                }
            } catch (e: Exception) {
                // handle error smoothly
            } finally {
                loadingData = false
            }
        }
    }

    fun withBlePermissions(action: () -> Unit) {
        if (ensureBlePermissions()) {
            permissionMessage = null
            action()
        } else {
            permissionMessage = "Allow the Bluetooth permissions, then try again."
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Load the face models while the student scans, so the camera check starts instantly.
    LaunchedEffect(isTeacher) {
        if (!isTeacher) FaceModels.preload(context)
    }

    // Student step 1: the scanner only reports beacons for this classroom. Trade the beacon code for a
    // liveness challenge, which proves the phone is in the room; the face check then proves who holds it.
    LaunchedEffect(detectedBeacon) {
        val beacon = detectedBeacon ?: return@LaunchedEffect
        if (isTeacher) return@LaunchedEffect
        bleScanner.stopScanning()
        studentStatusMessage = "Teacher beacon detected. Preparing face verification..."
        try {
            pendingChallenge = beacon.sessionId to Repository.requestChallenge(beacon.sessionId, beacon.code)
        } catch (e: Exception) {
            studentStatusMessage = e.message ?: "Failed to start verification."
        }
        bleScanner.consumeDetection()
    }

    // Student step 2: look at the camera (plus any actions), then submit the signed face sample.
    // If the face doesn't match clearly, the server allows a few more tries on the same challenge,
    // so the camera simply takes another sample instead of making the student scan again.
    pendingChallenge?.let { (sessionId, challenge) ->
        FaceCaptureScreen(
            title = "Verify it's you",
            actions = LivenessAction.parse(challenge.actions),
            samples = FaceModels.ATTENDANCE_SAMPLES,
            hint = faceRetryHint,
            restartToken = faceRetryToken,
            onCaptured = { face ->
                scope.launch {
                    try {
                        val record = Repository.markAttendance(sessionId, challenge.nonce, face)
                        studentStatusMessage = if (record.status == "PENDING_REVIEW") {
                            "Your face match was not certain, so your teacher will review it."
                        } else {
                            "Attendance marked successfully."
                        }
                        attendanceMarkedSuccess = true
                        pendingChallenge = null
                        faceRetryHint = null
                        refreshData()
                    } catch (e: Exception) {
                        if ((e as? ApiException)?.isFaceRetry == true) {
                            faceRetryHint = e.message
                            faceRetryToken++
                        } else {
                            pendingChallenge = null
                            faceRetryHint = null
                            studentStatusMessage = e.message ?: "Failed to mark attendance on the server."
                        }
                    }
                }
            },
            onCancel = {
                pendingChallenge = null
                faceRetryHint = null
                studentStatusMessage = "Verification cancelled. Scan again to retry."
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(classroom.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Classroom Code: ${classroom.code}", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Classroom ID: ${classroom.classId}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
            }

            if (isTeacher) {
                item {
                    Text("Attendance Control", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (activeSessionId != null) {
                        Button(
                            onClick = { bleAdvertiser.stopAdvertising() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("STOP ADVERTISING BEACON")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isAdvertising) {
                            Text("Session BLE Status: BROADCASTING ACTIVE", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("Session BLE Status: WAITING FOR SESSION START", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Session ID: $activeSessionId (code rotates every 30s)", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Button(
                            onClick = onStartSessionClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("START NEW ATTENDANCE SESSION")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Session BLE Status: INACTIVE", style = MaterialTheme.typography.bodyMedium)
                    }

                    errorMsg?.let { Text(it, color = Color.Red) }
                    permissionMessage?.let { Text(it, color = Color.Red) }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Previous Attendance Sessions", style = MaterialTheme.typography.titleMedium)
                    Text("Click on a session to view/edit student attendance records", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (loadingData) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (sessions.isEmpty()) {
                    item {
                        Text("No attendance sessions created yet for this class.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(sessions) { session ->
                        val now = System.currentTimeMillis()
                        val status = when {
                            now < session.startEpochMillis -> "UPCOMING"
                            now < session.endEpochMillis -> "LIVE"
                            else -> "ENDED"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSessionClick(session.sessionId) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Date: ${session.date}", style = MaterialTheme.typography.titleMedium)
                                Text("Time: ${session.startTime} - ${session.endTime}", style = MaterialTheme.typography.bodyMedium)
                                Text("Session ID: ${session.sessionId} | Status: $status", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                // Lets the teacher resume broadcasting, e.g. after restarting the app.
                                if (status != "ENDED" && activeSessionId != session.sessionId) {
                                    TextButton(onClick = { withBlePermissions { bleAdvertiser.startSessionBeacon(session) } }) {
                                        Text("BROADCAST THIS SESSION")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Student View
                item {
                    Text("Attendance Check", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isScanning) {
                        Button(
                            onClick = { bleScanner.stopScanning() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("STOP SCANNING")
                        }
                    } else {
                        Button(
                            onClick = {
                                withBlePermissions {
                                    attendanceMarkedSuccess = false
                                    studentStatusMessage = "Searching for teacher's device..."
                                    bleScanner.startScanning(classroom.classId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !attendanceMarkedSuccess
                        ) {
                            Text("SCAN FOR ATTENDANCE")
                        }
                    }
                    permissionMessage?.let { Text(it, color = Color.Red) }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (attendanceMarkedSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (attendanceMarkedSuccess) {
                                Text("✓ ATTENDANCE RECORDED", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2E7D32))
                                Text(studentStatusMessage)
                            } else {
                                Text("✕ STATUS", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFC62828))
                                Text(studentStatusMessage)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Debug Info: $debugInfo", style = MaterialTheme.typography.bodySmall)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("My Attendance History", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (loadingData) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (studentHistory.isEmpty()) {
                    item {
                        Text("No attendance history found for you in this class.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(studentHistory) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when (record.status) {
                                    "PRESENT" -> Color(0xFFE8F5E9)
                                    "PENDING_REVIEW" -> Color(0xFFFFF3E0)
                                    else -> Color(0xFFFFEBEE)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(record.date, style = MaterialTheme.typography.titleMedium)
                                    Text("${record.startTime} - ${record.endTime}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    text = if (record.status == "PENDING_REVIEW") "IN REVIEW" else record.status,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = statusColor(record.status)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun statusColor(status: String): Color = when (status) {
    "PRESENT" -> Color(0xFF2E7D32)
    "PENDING_REVIEW" -> Color(0xFFE65100)
    else -> Color(0xFFC62828)
}
