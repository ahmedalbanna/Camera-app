package com.example.ui.screens

import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CameraPreview
import com.example.CameraViewModel
import com.example.ui.components.ControlDeck
import com.example.ui.components.ExposureSlider
import com.example.ui.components.HorizonLevel
import com.example.ui.components.MediaViewer
import com.example.ui.components.ProControls
import com.example.ui.components.TopBar
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onTakePhoto: (ImageCapture?) -> Unit,
    onToggleVideo: (VideoCapture<Recorder>?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraSelector by viewModel.cameraSelector.collectAsStateWithLifecycle()
    val cameraMode by viewModel.cameraMode.collectAsStateWithLifecycle()
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val zoomRatio by viewModel.zoomRatio.collectAsStateWithLifecycle()
    val exposureIndex by viewModel.exposureIndex.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val lastCapturedUri by viewModel.lastCapturedUri.collectAsStateWithLifecycle()
    val isFlashMenuVisible by viewModel.isFlashMenuVisible.collectAsStateWithLifecycle()
    val showShotAnimation by viewModel.showShotAnimation.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val aperture by viewModel.aperture.collectAsStateWithLifecycle()
    val isHdrEnabled by viewModel.isHdrEnabled.collectAsStateWithLifecycle()
    val manualFocus by viewModel.manualFocusDistance.collectAsStateWithLifecycle()
    val panoramaProgress by viewModel.panoramaProgress.collectAsStateWithLifecycle()
    val isGalleryOpen by viewModel.isGalleryOpen.collectAsStateWithLifecycle()
    val isPortraitAvailable by viewModel.isPortraitExtensionAvailable.collectAsStateWithLifecycle()

    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureInstance by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    var isViewerOpen by remember { mutableStateOf(false) }
    var viewerUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showGridLines by remember { mutableStateOf(false) }
    var announcementText by remember { mutableStateOf<String?>(null) }
    var rotationAngle by remember { mutableStateOf(0f) }

    var exposureRange by remember { mutableStateOf<IntRange?>(null) }
    var zoomRange by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    LaunchedEffect(announcementText) {
        if (announcementText != null) {
            delay(2000)
            announcementText = null
        }
    }

    // Open viewer for last captured when gallery item clicked
    fun openViewer(uri: android.net.Uri) {
        viewerUri = uri
        isViewerOpen = true
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview — Phase 2: pro params + extension probe
        CameraPreview(
            cameraSelector = cameraSelector,
            cameraMode = cameraMode,
            zoomRatio = zoomRatio,
            exposureIndex = exposureIndex,
            onZoomRatioChanged = { viewModel.setZoomRatio(it) },
            onImageCaptureReady = { imageCaptureInstance = it },
            onVideoCaptureReady = { videoCaptureInstance = it },
            onExposureRangeChanged = { exposureRange = it },
            onZoomRangeChanged = { zoomRange = it },
            aperture = aperture,
            isHdrEnabled = isHdrEnabled,
            manualFocusDistance = manualFocus,
            onExtensionsAvailability = { portrait, panorama -> viewModel.setExtensionAvailability(portrait, panorama) },
            modifier = Modifier.fillMaxSize()
        )

        // Grid
        if (showGridLines) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(Color.White.copy(alpha = 0.25f), Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.25f), Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1.dp.toPx())
            }
        }

        // Focus bracket
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.Center)
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            Box(modifier = Modifier.size(16.dp).align(Alignment.TopStart).border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(topStart = 8.dp)))
            Box(modifier = Modifier.size(16.dp).align(Alignment.TopEnd).border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(topEnd = 8.dp)))
            Box(modifier = Modifier.size(16.dp).align(Alignment.BottomStart).border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(bottomStart = 8.dp)))
            Box(modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(bottomEnd = 8.dp)))
        }

        // Horizon level for Panorama
        HorizonLevel(
            enabled = cameraMode == com.example.CameraMode.PANORAMA,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp)
        )

        // Panorama progress bar
        if (cameraMode == com.example.CameraMode.PANORAMA) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp)
                    .size(width = 200.dp, height = 6.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFBBF24), RoundedCornerShape(3.dp))
                        .padding(0.dp)
                )
                // Simple sweep progress simulation
                Box(
                    modifier = Modifier
                        .fillMaxSize(fraction = panoramaProgress)
                        .background(Color(0xFFFBBF24), RoundedCornerShape(3.dp))
                )
            }
            // Auto simulate sweep when in panorama and not yet finished
            LaunchedEffect(cameraMode) {
                if (cameraMode == com.example.CameraMode.PANORAMA) {
                    viewModel.setPanoramaProgress(0f)
                    for (i in 1..100) {
                        delay(80)
                        if (viewModel.cameraMode.value != com.example.CameraMode.PANORAMA) break
                        viewModel.setPanoramaProgress(i / 100f)
                    }
                }
            }
        }

        // Portrait bokeh hint
        if (cameraMode == com.example.CameraMode.PORTRAIT) {
            val hint = if (isPortraitAvailable == true) "تم تفعيل تمويه الخلفية" else "تمويه برمجي (الجهاز لا يدعم BOKEH)"
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = hint, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Exposure slider — hidden in PRO? keep visible but ProControls below handles more
        ExposureSlider(
            exposureIndex = exposureIndex,
            exposureRange = exposureRange,
            onExposureChange = { viewModel.setExposureIndex(it) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
        )

        if (showShotAnimation) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.85f)))
        }

        // Top bar
        TopBar(
            flashMode = flashMode,
            isFlashMenuVisible = isFlashMenuVisible,
            isRecording = isRecording,
            recordingDuration = recordingDuration,
            showGridLines = showGridLines,
            onToggleFlashMenu = { viewModel.toggleFlashMenu() },
            onCloseFlashMenu = { viewModel.closeFlashMenu() },
            onSetFlashMode = { viewModel.setFlashMode(it) },
            onToggleGrid = { showGridLines = !showGridLines },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp))

        AnimatedVisibility(
            visible = announcementText != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 260.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF131D31).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text = announcementText ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Pro controls overlay — Phase 2
        if (cameraMode == com.example.CameraMode.PRO) {
            ProControls(
                aperture = aperture,
                isHdrEnabled = isHdrEnabled,
                manualFocus = manualFocus,
                onApertureChange = { viewModel.setAperture(it) },
                onHdrToggle = { viewModel.toggleHdr() },
                onManualFocusChange = { viewModel.setManualFocus(it) },
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp).padding(bottom = 320.dp)
            )
        }

        // Bottom control deck
        ControlDeck(
            cameraMode = cameraMode,
            zoomRatio = zoomRatio,
            zoomRange = zoomRange,
            lastCapturedUri = lastCapturedUri,
            isRecording = isRecording,
            snackbarHostState = snackbarHostState,
            onSetZoomRatio = { viewModel.setZoomRatio(it) },
            onSetCameraMode = { viewModel.setCameraMode(it) },
            onAnnounce = { announcementText = it },
            onGalleryClick = { viewModel.setGalleryOpen(true) },
            onShutterClick = {
                if (cameraMode == com.example.CameraMode.VIDEO) {
                    onToggleVideo(videoCaptureInstance)
                } else {
                    // For panorama, simulate announcement
                    if (cameraMode == com.example.CameraMode.PANORAMA) {
                        announcementText = "تم التقاط إطار بانورامي — اسحب ببطء"
                    }
                    onTakePhoto(imageCaptureInstance)
                }
            },
            onToggleCamera = {
                rotationAngle += 180f
                viewModel.toggleCamera()
            },
            rotationAngle = rotationAngle,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Fullscreen viewer for single item (last or gallery selection)
        MediaViewer(
            isVisible = isViewerOpen,
            uri = viewerUri ?: lastCapturedUri,
            viewModel = viewModel,
            onClose = { isViewerOpen = false; viewerUri = null },
            modifier = Modifier.fillMaxSize()
        )

        // Gallery grid — Phase 2
        if (isGalleryOpen) {
            GalleryScreen(
                onItemClick = { uri -> openViewer(uri) },
                onClose = { viewModel.setGalleryOpen(false) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
