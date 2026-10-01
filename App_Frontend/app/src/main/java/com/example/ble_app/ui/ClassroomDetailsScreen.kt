package com.example.ble_app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ble_app.bluetooth.BleAdvertiser
import com.example.ble_app.bluetooth.BleScanner
import com.example.ble_app.data.AttendanceSession
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.NetworkConfig
import com.example.ble_app.data.Repository
import com.example.ble_app.data.StudentAttendanceHistoryRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomDetailsScreen(
    classroom: Classroom,
    initialActiveSessionId: Int? = null,
    bleAdvertiser: BleAdvertiser,
    bleScanner: BleScanner,
    onStartSessionClick: () -> Unit,
    onSessionClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val user by Repository.currentUser.collectAsState()
    val isTeacher = user?.role == "TEACHER"

    // BLE States
    val isAdvertising by bleAdvertiser.isAdvertising.collectAsState()
    val isScanning by bleScanner.isScanning.collectAsState()
    val detected by bleScanner.detectedAttendance.collectAsState()
    val detectedSessionId by bleScanner.detectedSessionId.collectAsState()
    val debugInfo by bleScanner.debugInfo.collectAsState()
    val errorMsg by bleAdvertiser.errorMessage.collectAsState()

    // Backend session/history data lists
    var sessions by remember { mutableStateOf<List<AttendanceSession>>(emptyList()) }
    var studentHistory by remember { mutableStateOf<List<StudentAttendanceHistoryRecord>>(emptyList()) }
    var loadingData by remember { mutableStateOf(false) }
    var studentStatusMessage by remember { mutableStateOf("Click the button to scan for the classroom session.") }
    var attendanceMarkedSuccess by remember { mutableStateOf(false) }
    
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

    LaunchedEffect(Unit) {
        refreshData()
        
        // Auto-start advertiser if teacher just configured a new session
        if (isTeacher && initialActiveSessionId != null) {
            val payload = "${NetworkConfig.BLE_PAYLOAD_PREFIX}$initialActiveSessionId"
            bleAdvertiser.startAdvertising(NetworkConfig.ATTENDANCE_SERVICE_UUID, payload)
        }
    }

    // Monitor for student BLE scanner target matching
    LaunchedEffect(detected, detectedSessionId) {
        if (!isTeacher && detected && detectedSessionId != null) {
            val targetSessionId = detectedSessionId!!
            bleScanner.stopScanning()
            studentStatusMessage = "Teacher beacon detected. Verifying attendance..."
            
            // Dummy face-verification delay layer
            delay(1500)
            
            try {
                Repository.markAttendance(targetSessionId)
                studentStatusMessage = "Attendance marked successfully."
                attendanceMarkedSuccess = true
                refreshData()
            } catch (e: Exception) {
                studentStatusMessage = e.message ?: "Failed to mark attendance on the server."
            }
        }
    }

    // Coroutine to automatically stop teacher BLE advertiser at session end time
    LaunchedEffect(isAdvertising, sessions, initialActiveSessionId) {
        if (isTeacher && isAdvertising) {
            val currentActiveId = initialActiveSessionId ?: sessions.firstOrNull()?.sessionId
            val activeSession = sessions.find { it.sessionId == currentActiveId } ?: sessions.firstOrNull()
            if (activeSession != null) {
                scope.launch {
                    while (bleAdvertiser.isAdvertising.value) {
                        try {
                            val sdf = SimpleDateFormat("HH:mm", Locale.US)
                            val nowStr = sdf.format(Date())
                            if (nowStr >= activeSession.endTime) {
                                bleAdvertiser.stopAdvertising()
                                break
                            }
                        } catch (e: Exception) {
                            // ignore errors in time check
                        }
                        delay(30000) // check every 30 seconds
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(classroom.name) },
                navigationIcon = {
                    IconButton(onClick = {
                        bleAdvertiser.stopAdvertising()
                        bleScanner.stopScanning()
                        onBack()
                    }) {
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
                Divider()
            }

            if (isTeacher) {
                item {
                    Text("Attendance Control", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isAdvertising) {
                        Button(
                            onClick = { bleAdvertiser.stopAdvertising() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("STOP ADVERTISING BEACON")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Session BLE Status: BROADCASTING ACTIVE", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
                        Text("Payload: ${NetworkConfig.BLE_PAYLOAD_PREFIX}${initialActiveSessionId ?: sessions.firstOrNull()?.sessionId ?: ""}", style = MaterialTheme.typography.bodySmall)
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSessionClick(session.sessionId) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Date: ${session.date}", style = MaterialTheme.typography.titleMedium)
                                Text("Time: ${session.startTime} - ${session.endTime}", style = MaterialTheme.typography.bodyMedium)
                                Text("Session ID: ${session.sessionId} | Status: ${session.status ?: "INACTIVE"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                                attendanceMarkedSuccess = false
                                studentStatusMessage = "Searching for teacher's device..."
                                bleScanner.startScanning(NetworkConfig.ATTENDANCE_SERVICE_UUID) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !attendanceMarkedSuccess
                        ) {
                            Text("SCAN FOR ATTENDANCE")
                        }
                    }

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
                                containerColor = if (record.status == "PRESENT") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
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
                                    text = record.status,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (record.status == "PRESENT") Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
