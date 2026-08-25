package com.example

import android.annotation.SuppressLint
import android.util.Log
import android.view.ScaleGestureDetector
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraPreview(
    cameraSelector: CameraSelector,
    cameraMode: CameraMode,
    zoomRatio: Float,
    exposureIndex: Int,
    onZoomRatioChanged: (Float) -> Unit,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onVideoCaptureReady: (VideoCapture<Recorder>?) -> Unit,
    onExposureRangeChanged: (IntRange?) -> Unit = {},
    onZoomRangeChanged: (ClosedFloatingPointRange<Float>?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusRingScale = remember { Animatable(1.5f) }
    val focusRingAlpha = remember { Animatable(0f) }

    // Use cases
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder().build()
        VideoCapture.withOutput(recorder)
    }

    // Pass the capture blocks to parent whenever they are recreated/changed
    LaunchedEffect(cameraMode, imageCapture, videoCapture) {
        if (cameraMode == CameraMode.PHOTO) {
            onImageCaptureReady(imageCapture)
            onVideoCaptureReady(null)
        } else {
            onImageCaptureReady(null)
            onVideoCaptureReady(videoCapture)
        }
    }

    // Automatically apply zoom ratio on the active camera controller — clamp to device range
    LaunchedEffect(camera, zoomRatio) {
        try {
            val zoomState = camera?.cameraInfo?.zoomState?.value
            val min = zoomState?.minZoomRatio ?: 1f
            val max = zoomState?.maxZoomRatio ?: 10f
            val clamped = zoomRatio.coerceIn(min, max)
            if (clamped != zoomRatio) {
                // Report clamped value back to ViewModel to keep UI in sync
                onZoomRatioChanged(clamped)
            } else {
                camera?.cameraControl?.setZoomRatio(clamped)
            }
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to set zoom ratio $zoomRatio", e)
        }
    }

    // Automatically apply exposure index on the active camera controller
    LaunchedEffect(camera, exposureIndex) {
        try {
            val range = camera?.cameraInfo?.exposureState?.exposureCompensationRange
            if (range != null && range.contains(exposureIndex)) {
                camera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
            }
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to set exposure compensation index $exposureIndex", e)
        }
    }

    // Report exposure and zoom capabilities once camera is bound
    LaunchedEffect(camera) {
        val cam = camera
        if (cam != null) {
            try {
                val exposureRange = cam.cameraInfo.exposureState.exposureCompensationRange
                onExposureRangeChanged(exposureRange?.let { it.lower..it.upper })
                val zoomState = cam.cameraInfo.zoomState.value
                if (zoomState != null) {
                    onZoomRangeChanged(zoomState.minZoomRatio..zoomState.maxZoomRatio)
                }
                // Observe zoomState continuously for dynamic updates
                // Note: we snapshot once; pinch will query live state
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to report camera ranges", e)
                onExposureRangeChanged(null)
            }
        } else {
            onExposureRangeChanged(null)
            onZoomRangeChanged(null)
        }
    }

    // Set up pinch gesture detector for zooming on native view — dynamic min/max
    val scaleGestureDetector = remember {
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val zoomState = camera?.cameraInfo?.zoomState?.value
                val min = zoomState?.minZoomRatio ?: 1f
                val max = zoomState?.maxZoomRatio ?: 10f
                val currentZoomRatio = zoomState?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                val targetZoom = (currentZoomRatio * delta).coerceIn(min, max)
                onZoomRatioChanged(targetZoom)
                return true
            }
        })
    }

    // Bind Use Cases dynamically based on the current Mode (Photo vs Video)
    // HOTFIX: use Dispatchers.IO for get() to avoid blocking Main, and ensure unbind on dispose
    LaunchedEffect(cameraSelector, cameraMode) {
        val cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        cameraProvider.unbindAll()

        try {
            preview.setSurfaceProvider(previewView.surfaceProvider)

            camera = if (cameraMode == CameraMode.PHOTO) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            }
        } catch (exc: Exception) {
            Log.e("CameraPreview", "Use case binding failed", exc)
        }
    }

    // Cleanup when composable leaves composition
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    // Setup tap gesture on ViewFinder for Focus & Metering
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(cameraSelector, cameraMode) {
                detectTapGestures(
                    onTap = { offset ->
                        camera?.let { cam ->
                            val factory = previewView.meteringPointFactory
                            // Create point with safe bounds
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                                .build()

                            focusPoint = offset
                            coroutineScope.launch {
                                // Reset ring animation
                                launch {
                                    focusRingScale.snapTo(1.8f)
                                    focusRingScale.animateTo(1.0f, animationSpec = tween(300))
                                }
                                launch {
                                    focusRingAlpha.snapTo(1.0f)
                                    delay(1200)
                                    focusRingAlpha.animateTo(0f, animationSpec = tween(500))
                                    focusPoint = null
                                }
                            }

                            cam.cameraControl.startFocusAndMetering(action)
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Wire hardware scale gestures to previewView standard touch interception
                view.setOnTouchListener { _, event ->
                    scaleGestureDetector.onTouchEvent(event)
                    // Continue dispatching event
                    false
                }
            }
        )

        // Draw clean yellow ripple for Tap-To-Focus indicator
        focusPoint?.let { point ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFFBBF24), // Vibrant gold theme
                    radius = 45f * focusRingScale.value,
                    center = point,
                    alpha = focusRingAlpha.value,
                    style = Stroke(width = 3.5f)
                )
                drawCircle(
                    color = Color(0xFFFFFFFF),
                    radius = 6f,
                    center = point,
                    alpha = focusRingAlpha.value
                )
            }
        }
    }
}
