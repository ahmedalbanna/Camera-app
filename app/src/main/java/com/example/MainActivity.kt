package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ui.screens.CameraScreen
import com.example.ui.components.PermissionScreen
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

// Phase 1.1: Slim Activity — ~120 LOC, delegates UI to ui.screens.CameraScreen and components
class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CameraPermissionWrapper(viewModel = viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Phase 1.3: stop recording on pause to avoid corrupted files
        if (viewModel.isRecording.value) {
            try {
                viewModel.getActiveRecording()?.stop()
            } catch (_: Exception) {}
        }
    }

    @Composable
    private fun CameraPermissionWrapper(viewModel: CameraViewModel) {
        val context = LocalContext.current

        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            )
        }
        var hasAudioPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            )
        }
        var shouldShowRationale by remember { mutableStateOf(false) }

        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            hasCameraPermission = perms[Manifest.permission.CAMERA] == true
            hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true
            shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        }

        // Also handle legacy storage permission on API <=28
        val needsStorage = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

        val allGranted = hasCameraPermission && hasAudioPermission && !needsStorage

        if (allGranted) {
            CameraScreen(
                viewModel = viewModel,
                onTakePhoto = { imageCapture -> takePhoto(this, imageCapture, viewModel) },
                onToggleVideo = { videoCapture -> toggleVideoRecording(this, videoCapture, viewModel) }
            )
        } else {
            PermissionScreen(
                hasCameraPermission = hasCameraPermission,
                hasAudioPermission = hasAudioPermission,
                shouldShowRationale = shouldShowRationale,
                onRequestPermissions = {
                    val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    if (needsStorage) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    permissionLauncher.launch(perms.toTypedArray())
                }
            )
        }

        // Initial check on first composition
        LaunchedEffect(Unit) {
            hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun takePhoto(context: Context, imageCapture: ImageCapture?, viewModel: CameraViewModel) {
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

    private fun toggleVideoRecording(context: Context, videoCapture: VideoCapture<Recorder>?, viewModel: CameraViewModel) {
        if (videoCapture == null) {
            Log.e("CameraCapture", "VideoCapture instance not ready")
            viewModel.setErrorMessage("الكاميرا غير جاهزة للفيديو")
            return
        }
        if (viewModel.isRecording.value) {
            viewModel.getActiveRecording()?.stop()
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
        ).setContentValues(contentValues).build()

        @SuppressLint("MissingPermission")
        val pendingRecording = videoCapture.output.prepareRecording(context, mediaStoreOutputOptions).withAudioEnabled()

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
        recording?.let { viewModel.setActiveRecording(it) }
    }

    private fun queryLastSavedMediaHack(context: Context): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
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
