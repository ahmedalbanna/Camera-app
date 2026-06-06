package com.example

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CameraMode {
    PHOTO, VIDEO
}

enum class CustomFlashMode {
    AUTO, ON, OFF
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_BACK_CAMERA)
    val cameraSelector = _cameraSelector.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.PHOTO)
    val cameraMode = _cameraMode.asStateFlow()

    private val _flashMode = MutableStateFlow(CustomFlashMode.AUTO)
    val flashMode = _flashMode.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio = _zoomRatio.asStateFlow()

    private val _exposureIndex = MutableStateFlow(0)
    val exposureIndex = _exposureIndex.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration = _recordingDuration.asStateFlow()

    private val _lastCapturedUri = MutableStateFlow<Uri?>(null)
    val lastCapturedUri = _lastCapturedUri.asStateFlow()

    private val _isFlashMenuVisible = MutableStateFlow(false)
    val isFlashMenuVisible = _isFlashMenuVisible.asStateFlow()

    private val _showShotAnimation = MutableStateFlow(false)
    val showShotAnimation = _showShotAnimation.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Automatically fetch the last snapped photo or video on initialize
        loadLastCapturedMedia()
    }

    fun toggleCamera() {
        _cameraSelector.value = if (_cameraSelector.value == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun setCameraMode(mode: CameraMode) {
        if (_isRecording.value) return // Block switching modes while recording is active
        _cameraMode.value = mode
    }

    fun setFlashMode(mode: CustomFlashMode) {
        _flashMode.value = mode
        _isFlashMenuVisible.value = false
    }

    fun toggleFlashMenu() {
        _isFlashMenuVisible.value = !_isFlashMenuVisible.value
    }

    fun closeFlashMenu() {
        _isFlashMenuVisible.value = false
    }

    fun startRecordingState() {
        _isRecording.value = true
        _recordingDuration.value = 0
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _recordingDuration.value += 1
            }
        }
    }

    fun stopRecordingState() {
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun triggerShutterAnimation() {
        viewModelScope.launch {
            _showShotAnimation.value = true
            delay(100)
            _showShotAnimation.value = false
        }
    }

    fun updateLastCapturedUri(uri: Uri?) {
        _lastCapturedUri.value = uri
    }

    fun loadLastCapturedMedia() {
        viewModelScope.launch {
            val lastMedia = queryLastCapturedMedia(getApplication())
            _lastCapturedUri.value = lastMedia
        }
    }

    private fun queryLastCapturedMedia(context: Context): Uri? {
        val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videosUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        )

        var lastImageUri: Uri? = null
        var lastImageDate: Long = 0

        var lastVideoUri: Uri? = null
        var lastVideoDate: Long = 0

        // Query last image
        try {
            context.contentResolver.query(
                imagesUri,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val id = cursor.getLong(idColumn)
                    lastImageDate = cursor.getLong(dateColumn)
                    lastImageUri = ContentUris.withAppendedId(imagesUri, id)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraVM", "Error querying last image", e)
        }

        // Query last video
        try {
            context.contentResolver.query(
                videosUri,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val id = cursor.getLong(idColumn)
                    lastVideoDate = cursor.getLong(dateColumn)
                    lastVideoUri = ContentUris.withAppendedId(videosUri, id)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraVM", "Error querying last video", e)
        }

        return if (lastImageUri != null && lastVideoUri != null) {
            if (lastImageDate >= lastVideoDate) lastImageUri else lastVideoUri
        } else {
            lastImageUri ?: lastVideoUri
        }
    }

    fun setZoomRatio(ratio: Float) {
        _zoomRatio.value = ratio
    }

    fun setExposureIndex(index: Int) {
        _exposureIndex.value = index
    }

    fun getCameraXFlashMode(): Int {
        return when (_flashMode.value) {
            CustomFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            CustomFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            CustomFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }
}
