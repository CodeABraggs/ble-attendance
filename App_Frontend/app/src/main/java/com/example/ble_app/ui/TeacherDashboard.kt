package com.example.ble_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ble_app.data.Classroom
import com.example.ble_app.data.Repository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboard(
    onClassroomClick: (Classroom) -> Unit,
    onCreateClassroom: () -> Unit,
    onLogout: () -> Unit
) {
    val user by Repository.currentUser.collectAsState()
    val classrooms by Repository.classrooms.collectAsState()
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            Repository.fetchClassrooms()
        } catch (e: Exception) {
            // handle error
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Dashboard") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClassroom) {
                Icon(Icons.Default.Add, contentDescription = "Create Classroom")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("Welcome, ${user?.email?.substringBefore("@") ?: "Teacher"}", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text("My Classes", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (classrooms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No classrooms created yet.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(classrooms) { classroom ->
                        Card(
                            onClick = { onClassroomClick(classroom) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(classroom.name, style = MaterialTheme.typography.titleLarge)
                                Text("Code: ${classroom.code}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
