package com.example.ble_app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ble_app.data.AttendanceSession
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceSessionConfigScreen(
    classroom: Classroom,
    onSessionCreated: (AttendanceSession) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    
    var date by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Initialize with current values
    LaunchedEffect(Unit) {
        val y = calendar.get(Calendar.YEAR)
        val m = calendar.get(Calendar.MONTH) + 1
        val d = calendar.get(Calendar.DAY_OF_MONTH)
        date = String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
        
        val h = calendar.get(Calendar.HOUR_OF_DAY)
        val min = calendar.get(Calendar.MINUTE)
        startTime = String.format(Locale.US, "%02d:%02d", h, min)
        
        // Default end time 1 hour later
        endTime = String.format(Locale.US, "%02d:%02d", (h + 1) % 24, min)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start Session: ${classroom.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Configure Attendance Session", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(24.dp))

            // Date Picker
            OutlinedButton(
                onClick = {
                    val y = calendar.get(Calendar.YEAR)
                    val m = calendar.get(Calendar.MONTH)
                    val d = calendar.get(Calendar.DAY_OF_MONTH)
                    DatePickerDialog(context, { _, year, monthOfYear, dayOfMonth ->
                        date = String.format(Locale.US, "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                    }, y, m, d).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Date: $date")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start Time Picker
            OutlinedButton(
                onClick = {
                    val h = calendar.get(Calendar.HOUR_OF_DAY)
                    val min = calendar.get(Calendar.MINUTE)
                    TimePickerDialog(context, { _, hourOfDay, minute ->
                        startTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                    }, h, min, true).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Time: $startTime")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // End Time Picker
            OutlinedButton(
                onClick = {
                    val h = calendar.get(Calendar.HOUR_OF_DAY)
                    val min = calendar.get(Calendar.MINUTE)
                    TimePickerDialog(context, { _, hourOfDay, minute ->
                        endTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                    }, (h + 1) % 24, min, true).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("End Time: $endTime")
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (startTime >= endTime) {
                            error = "Validation error: Start time must be before end time."
                            return@Button
                        }
                        loading = true
                        error = null
                        scope.launch {
                            try {
                                val session = Repository.createAttendanceSession(classroom.classId, date, startTime, endTime)
                                onSessionCreated(session)
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to create session"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Attendance")
                }
            }
        }
    }
}
