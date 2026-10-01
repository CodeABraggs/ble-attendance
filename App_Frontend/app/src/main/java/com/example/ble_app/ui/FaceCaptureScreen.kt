package com.example.ble_app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ble_app.face.CapturedFace
import com.example.ble_app.face.FaceCaptureAnalyzer
import com.example.ble_app.face.FaceCaptureState
import com.example.ble_app.face.LivenessAction
import java.util.concurrent.Executors

/**
 * Full-screen front-camera face capture. Calls [onCaptured] with the face sample once [samples] good frames
 * (and any [actions]) are done; nothing leaves the phone until the caller sends the result to the server.
 * Changing [restartToken] takes a new sample on the running camera, showing [hint] (e.g. why the server
 * asked for another try).
 */
@Composable
fun FaceCaptureScreen(
    title: String,
    actions: List<LivenessAction>,
    samples: Int,
    onCaptured: (CapturedFace) -> Unit,
    onCancel: () -> Unit,
    hint: String? = null,
    restartToken: Int = 0
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    BackHandler(onBack = onCancel)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        if (!hasCameraPermission) {
            Text("Camera permission is needed to verify your face.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        } else {
            CameraFaceCapture(actions, samples, hint, restartToken, onCaptured)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun CameraFaceCapture(
    actions: List<LivenessAction>,
    samples: Int,
    hint: String?,
    restartToken: Int,
    onCaptured: (CapturedFace) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer = remember { FaceCaptureAnalyzer(context, actions, samples) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val state by analyzer.state.collectAsState()

    // Retries reuse the open camera, so they skip the camera start-up and exposure warm-up.
    val initialRestartToken = remember { restartToken }
    LaunchedEffect(restartToken) {
        if (restartToken != initialRestartToken) analyzer.restart()
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            provider = providerFuture.get().also { cameraProvider ->
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(state) {
        (state as? FaceCaptureState.Done)?.let { onCaptured(it.face) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.size(260.dp).clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(16.dp))
        hint?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
        }
        when (val current = state) {
            is FaceCaptureState.Instruct -> {
                LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.width(200.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(current.message, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            FaceCaptureState.Processing, is FaceCaptureState.Done -> {
                CircularProgressIndicator()
                Text("Verifying...", style = MaterialTheme.typography.bodyLarge)
            }
            is FaceCaptureState.Failed -> {
                Text(current.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { analyzer.restart() }) { Text("Try again") }
            }
        }
    }
}
