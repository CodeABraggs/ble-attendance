package com.example.ble_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ble_app.bluetooth.BleAdvertiser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(
    bleAdvertiser: BleAdvertiser,
    onBack: () -> Unit
) {
    var serviceUuid by remember { mutableStateOf("12345678-1234-1234-1234-123456789001") }
    var advertisementData by remember { mutableStateOf("ATTENDANCE_001") }
    
    val isAdvertising by bleAdvertiser.isAdvertising.collectAsState()
    val errorMessage by bleAdvertiser.errorMessage.collectAsState()
    
    val sampleUuids = listOf(
        "12345678-1234-1234-1234-123456789001",
        "12345678-1234-1234-1234-123456789002",
        "12345678-1234-1234-1234-123456789003",
        "12345678-1234-1234-1234-123456789004"
    )
    
    val sampleData = listOf(
        "ATTENDANCE_001",
        "ATTENDANCE_002",
        "ATTENDANCE_003",
        "ATTENDANCE_004"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Mode") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Text("Service UUID", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = serviceUuid,
                onValueChange = { serviceUuid = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter UUID") }
            )
            
            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sampleUuids.forEachIndexed { index, uuid ->
                    AssistChip(
                        onClick = { serviceUuid = uuid },
                        label = { Text("U${index + 1}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Advertisement Data", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = advertisementData,
                onValueChange = { advertisementData = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter Session Code") }
            )
            
            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sampleData.forEachIndexed { index, data ->
                    AssistChip(
                        onClick = { advertisementData = data },
                        label = { Text("D${index + 1}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isAdvertising) {
                Button(
                    onClick = { bleAdvertiser.stopAdvertising() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("STOP ADVERTISING")
                }
            } else {
                Button(
                    onClick = { bleAdvertiser.startAdvertising(serviceUuid, advertisementData) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("START BLE ADVERTISING")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdvertising) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isAdvertising) "BLE ADVERTISING: ACTIVE" else "BLE ADVERTISING: STOPPED",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isAdvertising) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    if (isAdvertising) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Service UUID: $serviceUuid")
                        Text("Advertisement Data: $advertisementData")
                    }
                }
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = Color.Red)
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            var showDebug by remember { mutableStateOf(false) }
            TextButton(onClick = { showDebug = !showDebug }) {
                Text("BLE DEBUG INFORMATION")
            }
            if (showDebug) {
                Text("Advertising Status: ${if (isAdvertising) "Active" else "Stopped"}")
                // Add more debug info as needed
            }
        }
    }
}
