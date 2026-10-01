package com.example.ble_app.ui

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
import com.example.ble_app.data.AttendanceRecord
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceRecordsScreen(
    sessionId: Int,
    classroom: Classroom,
    onBack: () -> Unit
) {
    var records by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadRecords() {
        loading = true
        error = null
        scope.launch {
            try {
                records = Repository.getAttendanceRecords(sessionId)
            } catch (e: Exception) {
                error = "Failed to load records"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(sessionId) {
        loadRecords()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance: ${classroom.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("Session ID: $sessionId", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { loadRecords() }) {
                    Text("Retry")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(records) { record ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(record.email, style = MaterialTheme.typography.bodyLarge)
                                    Text("Status: ${record.status}", color = if (record.status == "PRESENT") Color(0xFF2E7D32) else Color(0xFFC62828))
                                }
                                
                                Row {
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                Repository.updateAttendanceManually(sessionId, record.studentId, "PRESENT")
                                                loadRecords()
                                            } catch (e: Exception) {
                                                error = e.message ?: "Failed to update attendance"
                                            }
                                        }
                                    }) {
                                        Text("P", color = Color(0xFF2E7D32))
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                Repository.updateAttendanceManually(sessionId, record.studentId, "ABSENT")
                                                loadRecords()
                                            } catch (e: Exception) {
                                                error = e.message ?: "Failed to update attendance"
                                            }
                                        }
                                    }) {
                                        Text("A", color = Color(0xFFC62828))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
