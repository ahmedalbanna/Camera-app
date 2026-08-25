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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.CameraMode
import com.example.CameraPreview
import com.example.CameraViewModel
import com.example.ui.components.CaptureAssistCard
import com.example.ui.components.ConsentSheet
import com.example.ui.components.ControlDeck
import com.example.ui.components.ExposureSlider
import com.example.ui.components.HorizonLevel
import com.example.ui.components.MediaViewer
import com.example.ui.components.ProControls
import com.example.ui.components.SceneBadge
import com.example.ui.components.TopBar
import com.example.ui.theme.AmberGolden
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
    // Phase 3
    val aiConsent by viewModel.aiConsent.collectAsStateWithLifecycle()
    val geminiEnabled by viewModel.geminiEnabled.collectAsStateWithLifecycle()
    val smartOrganizeEnabled by viewModel.smartOrganizeEnabled.collectAsStateWithLifecycle()
    val captureAssistEnabled by viewModel.captureAssistEnabled.collectAsStateWithLifecycle()
    val sceneResult by viewModel.sceneResult.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val captureAssistText by viewModel.captureAssistText.collectAsStateWithLifecycle()
    val geminiDescription by viewModel.geminiDescription.collectAsStateWithLifecycle()
    val isDescribing by viewModel.isDescribing.collectAsStateWithLifecycle()
    val lastTags by viewModel.lastTags.collectAsStateWithLifecycle()

    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureInstance by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    var isViewerOpen by remember { mutableStateOf(false) }
    var viewerUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showGridLines by remember { mutableStateOf(false) }
    var announcementText by remember { mutableStateOf<String?>(null) }
    var rotationAngle by remember { mutableStateOf(0f) }
    var showConsentSheet by remember { mutableStateOf(false) }

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

    // Phase 3: auto-analyze last captured image when AI consented (on-device first)
    LaunchedEffect(lastCapturedUri) {
        val uri = lastCapturedUri
        if (uri != null && aiConsent && !viewModel.isVideo(uri)) {
            viewModel.analyzeScene(uri)
        }
    }

    fun openViewer(uri: android.net.Uri) {
        viewerUri = uri
        isViewerOpen = true
        // Phase 3: clear previous gemini description
        viewModel.clearGeminiDescription()
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Camera preview
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

        // Phase 3: Capture assist + Scene badge (center top)
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizon for panorama
            HorizonLevel(enabled = cameraMode == CameraMode.PANORAMA, modifier = Modifier.padding(bottom = 8.dp))
            if (cameraMode == CameraMode.PANORAMA) {
                Box(
                    modifier = Modifier
                        .size(width = 200.dp, height = 6.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize(fraction = panoramaProgress).background(Color(0xFFFBBF24), RoundedCornerShape(3.dp)))
                }
                LaunchedEffect(cameraMode) {
                    if (cameraMode == CameraMode.PANORAMA) {
                        viewModel.setPanoramaProgress(0f)
                        for (i in 1..100) {
                            delay(80)
                            if (viewModel.cameraMode.value != CameraMode.PANORAMA) break
                            viewModel.setPanoramaProgress(i / 100f)
                        }
                    }
                }
            }
            // Scene badge (Phase 3)
            if (aiConsent && captureAssistEnabled) {
                SceneBadge(
                    sceneResult = sceneResult,
                    isAnalyzing = isAnalyzing,
                    onApplySuggestion = { suggested ->
                        val mode = when (suggested) {
                            "PORTRAIT" -> CameraMode.PORTRAIT
                            "PANORAMA" -> CameraMode.PANORAMA
                            "PRO" -> CameraMode.PRO
                            else -> null
                        }
                        mode?.let { viewModel.setCameraMode(it) }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
                CaptureAssistCard(text = captureAssistText, modifier = Modifier.padding(top = 6.dp))
            }
            // Smart organize tags
            if (aiConsent && smartOrganizeEnabled && lastTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberGolden, modifier = Modifier.size(12.dp))
                    lastTags.take(3).forEach { tag ->
                        Text(text = "#$tag", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }

        // Portrait hint
        if (cameraMode == CameraMode.PORTRAIT) {
            val hint = if (isPortraitAvailable == true) "تم تفعيل تمويه الخلفية" else "تمويه برمجي (الجهاز لا يدعم BOKEH)"
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = hint, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Exposure slider
        ExposureSlider(
            exposureIndex = exposureIndex,
            exposureRange = exposureRange,
            onExposureChange = { viewModel.setExposureIndex(it) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
        )

        // AI consent FAB (top end below TopBar)
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)
        ) {
            val aiActive = aiConsent
            androidx.compose.material3.FloatingActionButton(
                onClick = { showConsentSheet = true },
                containerColor = if (aiActive) AmberGolden else Color.White.copy(alpha = 0.14f),
                contentColor = if (aiActive) Color.Black else Color.White,
                modifier = Modifier.size(44.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI settings", modifier = Modifier.size(20.dp))
            }
            if (aiActive) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(10.dp).background(Color(0xFF22C55E), CircleShape).border(1.dp, Color.Black, CircleShape)
                )
            }
        }

        if (showShotAnimation) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.85f)))
        }

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
                modifier = Modifier.background(Color(0xFF131D31).copy(alpha = 0.9f), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text = announcementText ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (cameraMode == CameraMode.PRO) {
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
                if (cameraMode == CameraMode.VIDEO) onToggleVideo(videoCaptureInstance)
                else {
                    if (cameraMode == CameraMode.PANORAMA) announcementText = "تم التقاط إطار بانورامي — اسحب ببطء"
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

        // Viewer
        MediaViewer(
            isVisible = isViewerOpen,
            uri = viewerUri ?: lastCapturedUri,
            viewModel = viewModel,
            onClose = { isViewerOpen = false; viewerUri = null; viewModel.clearGeminiDescription() },
            modifier = Modifier.fillMaxSize()
        )

        // Phase 3: Gemini description overlay inside viewer
        if (isViewerOpen && (viewerUri ?: lastCapturedUri) != null) {
            val uri = viewerUri ?: lastCapturedUri
            val isVideo = uri?.let { viewModel.isVideo(it) } ?: false
            if (!isVideo) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp).padding(horizontal = 16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (geminiDescription != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp)).border(1.dp, AmberGolden.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp)
                            ) {
                                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberGolden, modifier = Modifier.size(14.dp))
                                        Text(text = "وصف ذكي", color = AmberGolden, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.clearGeminiDescription() }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(text = geminiDescription ?: "", color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        } else {
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                // Describe button (opt-in)
                                androidx.compose.material3.Button(
                                    onClick = { uri?.let { viewModel.describeWithGemini(it) } },
                                    enabled = !isDescribing && aiConsent,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AmberGolden, contentColor = Color.Black, disabledContainerColor = Color.Gray.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    if (isDescribing) {
                                        androidx.compose.material3.CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "جاري الوصف...", fontSize = 11.sp)
                                    } else {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = if (aiConsent) "وصف ذكي" else "فعّل الذكاء للوصف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (!aiConsent) {
                                    TextButton(onClick = { showConsentSheet = true }) {
                                        Text(text = "تفعيل", color = AmberGolden, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gallery
        if (isGalleryOpen) {
            GalleryScreen(
                onItemClick = { uri -> openViewer(uri) },
                onClose = { viewModel.setGalleryOpen(false) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Consent sheet
        ConsentSheet(
            isVisible = showConsentSheet,
            aiConsent = aiConsent,
            geminiEnabled = geminiEnabled,
            smartOrganize = smartOrganizeEnabled,
            captureAssist = captureAssistEnabled,
            hasValidKey = viewModel.hasValidGeminiKey(),
            onConsentChange = { viewModel.setAiConsent(it) },
            onGeminiChange = { viewModel.setGeminiEnabled(it) },
            onSmartOrganizeChange = { viewModel.setSmartOrganize(it) },
            onCaptureAssistChange = { viewModel.setCaptureAssistEnabled(it) },
            onDismiss = { showConsentSheet = false }
        )
    }
}
