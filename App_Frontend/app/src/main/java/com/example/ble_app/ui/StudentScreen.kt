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
import com.example.ble_app.bluetooth.BleScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(
    bleScanner: BleScanner,
    onBack: () -> Unit
) {
    var expectedUuid by remember { mutableStateOf("12345678-1234-1234-1234-123456789001") }
    var expectedData by remember { mutableStateOf("ATTENDANCE_001") }
    
    val isScanning by bleScanner.isScanning.collectAsState()
    val detectedAttendance by bleScanner.detectedAttendance.collectAsState()
    val nearbyCount by bleScanner.nearbyDevicesCount.collectAsState()
    val debugInfo by bleScanner.debugInfo.collectAsState()
    
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
                title = { Text("Student Mode (Debug)") },
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
            Text("Expected Service UUID", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = expectedUuid,
                onValueChange = { expectedUuid = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter Expected UUID") }
            )
            
            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sampleUuids.forEachIndexed { index, uuid ->
                    AssistChip(
                        onClick = { expectedUuid = uuid },
                        label = { Text("U${index + 1}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Expected Advertisement Data", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = expectedData,
                onValueChange = { expectedData = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter Expected Data") }
            )
            
            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sampleData.forEachIndexed { index, data ->
                    AssistChip(
                        onClick = { expectedData = data },
                        label = { Text("D${index + 1}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                    onClick = { bleScanner.startScanning(expectedUuid) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("START SCANNING")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (detectedAttendance) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (detectedAttendance) {
                        Text("✓ DETECTED", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2E7D32))
                        Text("Matching BLE Attendance Beacon Found", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Service UUID: $expectedUuid")
                        Text("Advertisement Data: $expectedData")
                    } else {
                        Text(
                            text = if (isScanning) "Scanning..." else "✕ NOT DETECTED",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isScanning) Color(0xFFE65100) else Color(0xFFC62828)
                        )
                        if (!isScanning) {
                            Text("No matching attendance beacon found nearby.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nearby BLE devices: $nearbyCount", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            var showDebug by remember { mutableStateOf(false) }
            TextButton(onClick = { showDebug = !showDebug }) {
                Text("BLE DEBUG INFORMATION")
            }
            if (showDebug) {
                Text("Scanning Status: ${if (isScanning) "Active" else "Stopped"}")
                Text("Debug Output: $debugInfo")
            }
        }
    }
}
