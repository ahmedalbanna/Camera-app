package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val permissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ) + if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        emptyList()
                    }
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsState.allPermissionsGranted) {
                        CameraAppScreen(
                            viewModel = viewModel,
                            onTakePhoto = { imageCapture ->
                                takePhoto(this, imageCapture, viewModel)
                            },
                            onToggleVideo = { videoCapture ->
                                toggleVideoRecording(this, videoCapture, viewModel)
                            }
                        )
                    } else {
                        PermissionPlaceholderScreen(permissionsState = permissionsState)
                    }
                }
            }
        }
    }

    private fun takePhoto(
        context: Context,
        imageCapture: ImageCapture?,
        viewModel: CameraViewModel
    ) {
        if (imageCapture == null) {
            Log.e("CameraCapture", "ImageCapture instance not ready")
            viewModel.setErrorMessage("الكاميرا غير جاهزة")
            return
        }

        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        imageCapture.flashMode = viewModel.getCameraXFlashMode()

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        viewModel.triggerShutterAnimation()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri ?: queryLastSavedMediaHack(context)
                    Log.d("CameraCapture", "Photo saved successfully: $savedUri")
                    viewModel.updateLastCapturedUri(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraCapture", "Photo capture failed", exception)
                    viewModel.setErrorMessage("فشل التقاط الصورة: ${exception.message}")
                }
            }
        )
    }

    private fun toggleVideoRecording(
        context: Context,
        videoCapture: VideoCapture<Recorder>?,
        viewModel: CameraViewModel
    ) {
        if (videoCapture == null) {
            Log.e("CameraCapture", "VideoCapture instance not ready")
            viewModel.setErrorMessage("الكاميرا غير جاهزة للفيديو")
            return
        }

        // If already recording, stop — ViewModel holds the active Recording
        if (viewModel.isRecording.value) {
            viewModel.getActiveRecording()?.stop()
            // Do NOT call stopRecordingState here — it will be handled on Finalize
            // But ensure timer stops if Finalize never arrives (fallback after 1s)
            // ViewModel will handle via onRecordingFinalized
            return
        }

        val name = "VID_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        @SuppressLint("MissingPermission")
        val pendingRecording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .withAudioEnabled()

        // IMPORTANT: do NOT set recording state here; wait for Start event
        var recording: androidx.camera.video.Recording? = null
        recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    Log.d("CameraCapture", "Video recording started")
                    recording?.let { viewModel.onRecordingStarted(it) }
                }
                is VideoRecordEvent.Finalize -> {
                    if (!recordEvent.hasError()) {
                        val savedUri = recordEvent.outputResults.outputUri
                        Log.d("CameraCapture", "Video saved successfully: $savedUri")
                        viewModel.updateLastCapturedUri(savedUri)
                        viewModel.onRecordingFinalized()
                    } else {
                        Log.e("CameraCapture", "Video finalization failed: ${recordEvent.error}")
                        viewModel.onRecordingFinalized()
                        viewModel.setErrorMessage("فشل حفظ الفيديو: ${recordEvent.error}")
                    }
                }
            }
        }
        // Store pending recording immediately so stop() can be called even before Start event
        recording?.let { viewModel.setActiveRecording(it) }
    }

    // Fallback helper to grab the last added item in media store if savedUri returns null on older configs
    // HOTFIX: avoids undocumented "LIMIT" in sortOrder; runs on Main but query is lightweight (DESC + first row)
    private fun queryLastSavedMediaHack(context: Context): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val colIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = it.getLong(colIndex)
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }
}

@Composable
fun CameraAppScreen(
    viewModel: CameraViewModel,
    onTakePhoto: (ImageCapture?) -> Unit,
    onToggleVideo: (VideoCapture<Recorder>?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureInstance by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    var isViewerOpen by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showGridLines by remember { mutableStateOf(false) }
    var announcementText by remember { mutableStateOf<String?>(null) }

    // Exposure and zoom ranges reported by CameraPreview
    var exposureRange by remember { mutableStateOf<IntRange?>(null) }
    var zoomRange by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }

    // Snackbar for errors
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    // Delete launcher for scoped storage (RecoverableSecurityException)
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uri = pendingDeleteUri
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // User approved — system has deleted the file
            viewModel.updateLastCapturedUri(null)
            viewModel.loadLastCapturedMedia()
            isViewerOpen = false
            showDeleteConfirmDialog = false
        } else {
            viewModel.setErrorMessage("تم إلغاء الحذف")
        }
        pendingDeleteUri = null
    }

    // Rotate animation state for flip camera button
    var rotationAngle by remember { mutableStateOf(0f) }

    LaunchedEffect(announcementText) {
        if (announcementText != null) {
            delay(2000)
            announcementText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Live Camera Preview Stream
        CameraPreview(
            cameraSelector = cameraSelector,
            cameraMode = cameraMode,
            zoomRatio = zoomRatio,
            exposureIndex = exposureIndex,
            onZoomRatioChanged = { ratio -> viewModel.setZoomRatio(ratio) },
            onImageCaptureReady = { imageCaptureInstance = it },
            onVideoCaptureReady = { videoCaptureInstance = it },
            onExposureRangeChanged = { range -> exposureRange = range },
            onZoomRangeChanged = { range -> zoomRange = range },
            modifier = Modifier.fillMaxSize()
        )

        // 1b. Real Grid Lines (Drawn precisely on top of preview)
        if (showGridLines) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Vertical grid lines
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(width / 3f, 0f),
                    end = Offset(width / 3f, height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(width * 2f / 3f, 0f),
                    end = Offset(width * 2f / 3f, height),
                    strokeWidth = 1.dp.toPx()
                )

                // Horizontal grid lines
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, height / 3f),
                    end = Offset(width, height / 3f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, height * 2f / 3f),
                    end = Offset(width, height * 2f / 3f),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // 1c. Focus Bracket (Immersive HUD styled layout)
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.Center)
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            // Gold corners matching tailor-made viewfinder bracket
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopStart)
                    .border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(topStart = 8.dp))
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(topEnd = 8.dp))
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomStart)
                    .border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(bottomStart = 8.dp))
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .border(2.dp, Color(0xFFFBBF24), RoundedCornerShape(bottomEnd = 8.dp))
            )
        }

        // 1d. Exposure control vertical HUD slider on right edge — dynamic range
        val currentExposureRange = exposureRange
        if (currentExposureRange != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .width(42.dp)
                    .height(240.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Brightness",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(20.dp)
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val range = currentExposureRange
                        val steps = (range.last - range.first - 1).coerceAtLeast(0)
                        Slider(
                            value = exposureIndex.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
                            onValueChange = { newValue ->
                                viewModel.setExposureIndex(newValue.toInt())
                            },
                            valueRange = range.first.toFloat()..range.last.toFloat(),
                            steps = steps,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFFFBBF24),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .graphicsLayer { rotationZ = 270f }
                                .width(130.dp)
                        )
                    }

                    Text(
                        text = if (exposureIndex >= 0) "+$exposureIndex" else "$exposureIndex",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            // Fallback when camera not ready — show disabled placeholder with default range
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .width(42.dp)
                    .height(240.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Brightness",
                        tint = Color(0xFFFBBF24).copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Slider(
                            value = 0f,
                            onValueChange = {},
                            enabled = false,
                            valueRange = -4f..4f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White.copy(alpha = 0.5f),
                                activeTrackColor = Color(0xFFFBBF24).copy(alpha = 0.5f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .graphicsLayer { rotationZ = 270f }
                                .width(130.dp)
                        )
                    }
                    Text(
                        text = if (exposureIndex >= 0) "+$exposureIndex" else "$exposureIndex",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 2. Shutter Flash Feedback Animation
        if (showShotAnimation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }

        // 3. Status Bar/Top Panel Overlay (Immersive gradient for readability)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Action: Flash selection pill layout
                Box {
                    IconButton(
                        onClick = { viewModel.toggleFlashMenu() },
                        modifier = Modifier
                            .testTag("flash_toggle_trigger")
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                CustomFlashMode.AUTO -> Icons.Default.FlashAuto
                                CustomFlashMode.ON -> Icons.Default.FlashOn
                                CustomFlashMode.OFF -> Icons.Default.FlashOff
                            },
                            contentDescription = "Flash mode selector",
                            tint = if (flashMode == CustomFlashMode.OFF) Color.White else Color(0xFFFBBF24)
                        )
                    }

                    DropdownMenu(
                        expanded = isFlashMenuVisible,
                        onDismissRequest = { viewModel.closeFlashMenu() },
                        modifier = Modifier.background(Color(0xFF131D31).copy(alpha = 0.95f))
                    ) {
                        DropdownMenuItem(
                            text = { Text("⚡ تلقائي (Auto)", color = Color.White, textAlign = TextAlign.Right) },
                            onClick = { viewModel.setFlashMode(CustomFlashMode.AUTO) }
                        )
                        DropdownMenuItem(
                            text = { Text("⚡ تشغيل (On)", color = Color(0xFFFBBF24), textAlign = TextAlign.Right) },
                            onClick = { viewModel.setFlashMode(CustomFlashMode.ON) }
                        )
                        DropdownMenuItem(
                            text = { Text("⚡ إيقاف (Off)", color = Color.LightGray, textAlign = TextAlign.Right) },
                            onClick = { viewModel.setFlashMode(CustomFlashMode.OFF) }
                        )
                    }
                }

                // Middle Action: Immersive recording tracker pill — HOTFIX: infiniteTransition for blink
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // HOTFIX: rememberInfiniteTransition must be called unconditionally
                    val blinkTransition = rememberInfiniteTransition(label = "blink")
                    val blinkAlpha by blinkTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "blinkAlpha"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = Color.Red.copy(alpha = blinkAlpha),
                                        shape = CircleShape
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color.Gray.copy(alpha = 0.7f), CircleShape)
                            )
                        }

                        Text(
                            text = formatDuration(recordingDuration),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Right Action: Grid helper toggle button
                IconButton(
                    onClick = { showGridLines = !showGridLines },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (showGridLines) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = "Toggle Grid Layout",
                        tint = if (showGridLines) Color(0xFFFBBF24) else Color.White
                    )
                }
            }
        }

        // Snackbar host for errors
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp)
        )

        // Announcement Banner
        AnimatedVisibility(
            visible = announcementText != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 260.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF131D31).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = announcementText ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. Control Deck Panel At the Bottom (Horizontal mode selector & Zoom level chips)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black)
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Zoom Selection pills: HUD style chips [0.6, 1.0, 2.0] — dynamic clamp
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val zoomChips = listOf(0.6f, 1.0f, 2.0f)
                    zoomChips.forEach { ratio ->
                        val isSelected = zoomRatio == ratio
                        val isAvailable = zoomRange?.let { ratio in it } ?: true
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable(enabled = isAvailable) {
                                    // Clamp to device range before setting
                                    val clamped = zoomRange?.let { ratio.coerceIn(it.start, it.endInclusive) } ?: ratio
                                    viewModel.setZoomRatio(clamped)
                                    if (!isAvailable) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("مستوى التقريب غير مدعوم على هذا الجهاز")
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", ratio),
                                color = when {
                                    !isAvailable -> Color.Gray
                                    isSelected -> Color.Black
                                    else -> Color.White
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Interactive horizontal Mode Selection List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Panorama Preset
                        Text(
                            text = "بانوراما",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                announcementText = "تطبيق البانوراما سيتم تفعيله لاحقاً بذكاء"
                            }
                        )

                        // 2. Photo Mode Indicator (Fully Functional)
                        val isPhotoSelected = cameraMode == CameraMode.PHOTO
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable {
                                viewModel.setCameraMode(CameraMode.PHOTO)
                            }
                        ) {
                            Text(
                                text = "صورة",
                                color = if (isPhotoSelected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.4f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPhotoSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFFBBF24), CircleShape)
                                )
                            }
                        }

                        // 3. Video Mode Indicator (Fully Functional)
                        val isVideoSelected = cameraMode == CameraMode.VIDEO
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable {
                                viewModel.setCameraMode(CameraMode.VIDEO)
                            }
                        ) {
                            Text(
                                text = "فيديو",
                                color = if (isVideoSelected) Color(0xFFEF4444) else Color.White.copy(alpha = 0.4f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isVideoSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                )
                            }
                        }

                        // 4. Portrait Preset
                        Text(
                            text = "بورتريه",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                announcementText = "تطبيق وضع البورتريه السينمائي قريباً جداً"
                            }
                        )
                    }
                }

                // Shutter Button, Gallery Preview & Swap Camera Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Gallery Button Frame
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .padding(2.dp)
                            .background(Color(0xFF1E293B))
                            .clickable {
                                if (lastCapturedUri != null) {
                                    isViewerOpen = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (lastCapturedUri != null) {
                            AsyncImage(
                                model = lastCapturedUri,
                                contentDescription = "آخر لقطة ملتقطة",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "معرض فارغ",
                                tint = Color.LightGray.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // CENTER: Perfect HUD Styled Shutter Button — HOTFIX: rememberInfiniteTransition
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (cameraMode == CameraMode.PHOTO) {
                                    onTakePhoto(imageCaptureInstance)
                                } else {
                                    onToggleVideo(videoCaptureInstance)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(4.dp, Color.White, CircleShape)
                        )
                        val isVideoMode = cameraMode == CameraMode.VIDEO
                        // HOTFIX: infinite transition only when recording
                        val pulseTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by pulseTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.25f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "recordPulse"
                        )
                        val scaleValue = if (isRecording && isVideoMode) pulseScale else 1f
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .scale(scaleValue)
                                .clip(if (isRecording && isVideoMode) RoundedCornerShape(10.dp) else CircleShape)
                                .background(
                                    color = if (isVideoMode) Color(0xFFEF4444) else Color.White
                                )
                        )
                    }

                    // RIGHT: Elegant double-arrow Flip icon button
                    IconButton(
                        onClick = {
                            rotationAngle += 180f
                            viewModel.toggleCamera()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "التبديل بين الكاميرات",
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer { rotationZ = rotationAngle }
                        )
                    }
                }
            }
        }

        // 5. IMMERSIVE IN-APP FULL SCREEN MEDIA VIEWER
        AnimatedVisibility(
            visible = isViewerOpen && lastCapturedUri != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val fileUri = lastCapturedUri
            if (fileUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // HOTFIX: strict mime-type check, not string contains
                    val mimeType = remember(fileUri) { context.contentResolver.getType(fileUri) }
                    val isVideoFile = mimeType?.startsWith("video", ignoreCase = true) == true

                    if (isVideoFile) {
                        // Play Video Loop using local native VideoView nested in AndroidView
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(fileUri)
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            update = { view ->
                                view.setVideoURI(fileUri)
                                view.start()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Display full high quality Photo
                        AsyncImage(
                            model = fileUri,
                            contentDescription = "معاينة الصورة ملء الشاشة",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Viewer Header Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Close Viewer Button
                                IconButton(
                                    onClick = { isViewerOpen = false },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close viewer", tint = Color.White)
                                }
                            }

                            Text(
                                text = if (isVideoFile) "مقطع فيديو" else "لقطة مصورة",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Viewer Bottom control bar (Delete / Close Actions)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("حذف اللقطة", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { isViewerOpen = false },
                                border = BorderStroke(1.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("الرجوع للكاميرا", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // CONFIRM DELETE POPUP DIALOG — HOTFIX: handles RecoverableSecurityException
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("حذف اللقطة؟", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                text = { Text("هل أنت متأكد من رغبتك في حذف هذا الملف نهائياً من ذاكرة جهازك؟", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    Button(
                        onClick = {
                            val uriToDelete = lastCapturedUri
                            if (uriToDelete != null) {
                                try {
                                    val rows = context.contentResolver.delete(uriToDelete, null, null)
                                    if (rows > 0) {
                                        viewModel.updateLastCapturedUri(null)
                                        viewModel.loadLastCapturedMedia()
                                        showDeleteConfirmDialog = false
                                        isViewerOpen = false
                                    } else {
                                        // Some providers return 0 but still deleted — treat as success
                                        viewModel.updateLastCapturedUri(null)
                                        viewModel.loadLastCapturedMedia()
                                        showDeleteConfirmDialog = false
                                        isViewerOpen = false
                                    }
                                } catch (e: RecoverableSecurityException) {
                                    // HOTFIX: request user consent via IntentSender
                                    try {
                                        pendingDeleteUri = uriToDelete
                                        val intentSender: IntentSender = e.userAction.actionIntent.intentSender
                                        val request = IntentSenderRequest.Builder(intentSender).build()
                                        deleteLauncher.launch(request)
                                    } catch (ex: Exception) {
                                        Log.e("DeleteMedia", "Failed to launch delete consent", ex)
                                        viewModel.setErrorMessage("تعذر طلب إذن الحذف")
                                    }
                                } catch (e: SecurityException) {
                                    // Fallback for other SecurityException wrapping RecoverableSecurityException
                                    val cause = e.cause
                                    if (cause is RecoverableSecurityException) {
                                        try {
                                            pendingDeleteUri = uriToDelete
                                            val intentSender = cause.userAction.actionIntent.intentSender
                                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                        } catch (ex: Exception) {
                                            Log.e("DeleteMedia", "Failed to launch delete consent", ex)
                                        }
                                    } else {
                                        Log.e("DeleteMedia", "Security exception on delete", e)
                                        viewModel.setErrorMessage("لا يمكن حذف الملف: ليس لديك إذن")
                                    }
                                } catch (e: Exception) {
                                    Log.e("DeleteMedia", "Failed to delete from media provider", e)
                                    viewModel.setErrorMessage("فشل الحذف: ${e.message}")
                                }
                            } else {
                                showDeleteConfirmDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("نعم، حذف", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("إلغاء", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF131D31),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionPlaceholderScreen(permissionsState: MultiplePermissionsState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B121F))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Elegant Vector style Camera Frame placeholder to represent App Icon fallback
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF131D31))
                    .border(3.dp, Color(0xFFFBBF24), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "العدسة",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = "الوصول إلى الكاميرا",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "تطبيق العدسة بحاجة إلى صلاحيات الكاميرا والميكروفون لعرض البث الحي والتقاط الصور والفيديوهات وحفظها بجهازك.",
                color = Color.LightGray,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "منح الصلاحيات لبدء التصوير",
                    color = Color(0xFF0B121F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
