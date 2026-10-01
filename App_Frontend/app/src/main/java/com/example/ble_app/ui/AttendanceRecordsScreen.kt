package com.example.ble_app.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.ble_app.data.AttendanceRecord
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.FaceVerification
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
    // Record whose face-verification photos the teacher is reviewing.
    var reviewing by remember { mutableStateOf<AttendanceRecord?>(null) }

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

    fun setStatus(record: AttendanceRecord, status: String) {
        scope.launch {
            try {
                Repository.updateAttendanceManually(sessionId, record.studentId, status)
                loadRecords()
            } catch (e: Exception) {
                error = e.message ?: "Failed to update attendance"
            }
        }
    }

    reviewing?.let { record ->
        VerificationDialog(
            sessionId = sessionId,
            classId = classroom.classId,
            record = record,
            onDecision = { status ->
                reviewing = null
                setStatus(record, status)
            },
            onDismiss = {
                reviewing = null
                loadRecords()
            }
        )
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
                                    Text(
                                        if (record.status == "PENDING_REVIEW") "Status: NEEDS REVIEW" else "Status: ${record.status}",
                                        color = statusColor(record.status)
                                    )
                                    if (record.hasVerification) {
                                        TextButton(onClick = { reviewing = record }, contentPadding = PaddingValues(0.dp)) {
                                            Text(if (record.status == "PENDING_REVIEW") "REVIEW FACE" else "VIEW FACE")
                                        }
                                    }
                                }

                                Row {
                                    TextButton(onClick = { setStatus(record, "PRESENT") }) {
                                        Text("P", color = Color(0xFF2E7D32))
                                    }
                                    TextButton(onClick = { setStatus(record, "ABSENT") }) {
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

/** Shows the enrolled face next to the attendance selfie so the teacher can approve or reject the mark. */
@Composable
private fun VerificationDialog(
    sessionId: Int,
    classId: Int,
    record: AttendanceRecord,
    onDecision: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var verification by remember { mutableStateOf<FaceVerification?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var faceReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(record.studentId) {
        try {
            verification = Repository.getVerification(sessionId, record.studentId)
        } catch (e: Exception) {
            message = e.message ?: "Could not load the photos"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.email) },
        text = {
            Column {
                val current = verification
                if (current == null) {
                    if (message == null) CircularProgressIndicator()
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledPhoto("Enrolled", current.referencePhoto?.let(::decodeJpeg), Modifier.weight(1f))
                        LabeledPhoto("At attendance", decodeJpeg(current.attendancePhoto), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Face match: ${(current.similarity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    current.reviewReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    enabled = !faceReset,
                    onClick = {
                        scope.launch {
                            message = try {
                                Repository.resetStudentFace(classId, record.studentId)
                                faceReset = true
                                "Face enrollment reset. The student must enroll again."
                            } catch (e: Exception) {
                                e.message ?: "Could not reset face enrollment"
                            }
                        }
                    }
                ) {
                    Text("RESET STUDENT'S ENROLLED FACE", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision("PRESENT") }) { Text("Approve (Present)") }
        },
        dismissButton = {
            TextButton(onClick = { onDecision("ABSENT") }) { Text("Reject (Absent)") }
        }
    )
}

@Composable
private fun LabeledPhoto(label: String, image: ImageBitmap?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (image != null) {
            Image(bitmap = image, contentDescription = label, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Text("No photo", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun decodeJpeg(base64: String): ImageBitmap? {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}
