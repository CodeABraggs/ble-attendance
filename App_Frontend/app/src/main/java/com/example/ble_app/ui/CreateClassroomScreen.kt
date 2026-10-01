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
fun CreateClassroomScreen(
    onClassroomCreated: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var createdClassroom by remember { mutableStateOf<Classroom?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Classroom") },
                navigationIcon = {
                    if (createdClassroom == null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (createdClassroom != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Classroom Created Successfully!", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Name: ${createdClassroom?.name}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Classroom Code:", style = MaterialTheme.typography.bodyMedium)
                        Text("${createdClassroom?.code}", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF1B5E20))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Share this code with your students so they can join.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onClassroomCreated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go to Dashboard")
                }
            } else {
                Text("Enter the name of the new classroom. The system will automatically generate a unique entry code for students.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Classroom Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading
                )
                
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                loading = true
                                error = null
                                scope.launch {
                                    try {
                                        val classroom = Repository.createClassroom(name)
                                        createdClassroom = classroom
                                    } catch (e: Exception) {
                                        error = e.message ?: "Failed to create classroom."
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = name.isNotBlank()
                    ) {
                        Text("Generate Classroom & Code")
                    }
                }
            }
        }
    }
}
