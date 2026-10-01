package com.example.ble_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ble_app.data.Repository
import com.example.ble_app.face.FaceModels
import com.example.ble_app.face.LivenessAction
import kotlinx.coroutines.launch

/**
 * One-time face enrollment. The enrolled face is what every attendance check is compared against,
 * so it can only be changed by the student's teacher resetting it.
 */
@Composable
fun FaceEnrollmentScreen(
    onEnrolled: () -> Unit,
    onSkip: () -> Unit
) {
    var capturing by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Enrollment is just "look at the camera"; the anti-spoofing model still checks for photos/screens.
    val actions = remember { emptyList<LivenessAction>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { FaceModels.preload(context) }

    if (capturing) {
        FaceCaptureScreen(
            title = "Set up face verification",
            actions = actions,
            // More frames than attendance, for a steadier reference face.
            samples = FaceModels.ENROLLMENT_SAMPLES,
            onCaptured = { face ->
                capturing = false
                submitting = true
                error = null
                scope.launch {
                    try {
                        Repository.enrollFace(face)
                        onEnrolled()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save your face. Try again."
                    } finally {
                        submitting = false
                    }
                }
            },
            onCancel = { capturing = false }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Set up face verification", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Attendance is only accepted when your face matches the one you enroll now, so nobody else can " +
                "mark attendance with your phone. Face a light source, remove sunglasses, and look at the camera. " +
                "Only your teacher can reset this later.",
            textAlign = TextAlign.Center
        )
        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (submitting) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { capturing = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Start")
            }
            TextButton(onClick = onSkip) { Text("Later") }
        }
    }
}
