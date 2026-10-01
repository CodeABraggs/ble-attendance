package com.example.ble_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinClassroomScreen(
    onJoined: () -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var verifiedClassroom by remember { mutableStateOf<Classroom?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Classroom") },
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
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (verifiedClassroom == null) {
                Text("Enter the 6-digit classroom code provided by your teacher.", style = MaterialTheme.typography.bodyMedium)
                
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.uppercase() },
                    label = { Text("Classroom Code") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    enabled = !loading
                )
                
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    val classroom = Repository.getClassroomByCode(code)
                                    verifiedClassroom = classroom
                                } catch (e: Exception) {
                                    error = e.message ?: "Invalid classroom code or connection error."
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = code.length == 6
                    ) {
                        Text("Verify Code")
                    }
                }
            } else {
                // Confirm and join
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Classroom Found!", style = MaterialTheme.typography.titleLarge, color = Color(0xFF1E88E5))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Name: ${verifiedClassroom?.name}", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Code: ${verifiedClassroom?.code}", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    Repository.joinClassroom(code)
                                    onJoined()
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to join classroom."
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirm & Join Classroom")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { verifiedClassroom = null }) {
                        Text("Cancel")
                    }
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
