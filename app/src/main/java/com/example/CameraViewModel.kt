package com.example

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recording
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var activeRecording: Recording? = null

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        // Automatically fetch the last snapped photo or video on initialize
        loadLastCapturedMedia()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activeRecording?.stop()
            activeRecording?.close()
        } catch (_: Exception) {}
        activeRecording = null
        timerJob?.cancel()
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

    // Called ONLY when VideoRecordEvent.Start is received
    fun onRecordingStarted(recording: Recording) {
        activeRecording = recording
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
        try {
            activeRecording?.stop()
        } catch (_: Exception) {}
        // keep reference until Finalize closes it, but clear timer
    }

    fun onRecordingFinalized() {
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
        try {
            activeRecording?.close()
        } catch (_: Exception) {}
        activeRecording = null
    }

    fun setActiveRecording(recording: Recording?) {
        activeRecording = recording
    }

    fun getActiveRecording(): Recording? = activeRecording

    fun clearActiveRecording() {
        try {
            activeRecording?.close()
        } catch (_: Exception) {}
        activeRecording = null
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

    fun setErrorMessage(msg: String?) {
        _errorMessage.value = msg
    }

    fun consumeErrorMessage() {
        _errorMessage.value = null
    }

    fun loadLastCapturedMedia() {
        viewModelScope.launch {
            val lastMedia = queryLastCapturedMedia(getApplication())
            _lastCapturedUri.value = lastMedia
        }
    }

    private suspend fun queryLastCapturedMedia(context: Context): Uri? = withContext(Dispatchers.IO) {
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

        // Query last image — API 30+ uses Bundle with LIMIT, pre-30 falls back to DESC sort
        try {
            querySingleMedia(
                context = context,
                uri = imagesUri,
                projection = imageProjection,
                idColumnName = MediaStore.Images.Media._ID,
                dateColumnName = MediaStore.Images.Media.DATE_ADDED
            )?.let { (uri, date) ->
                lastImageUri = uri
                lastImageDate = date
            }
        } catch (e: Exception) {
            Log.e("CameraVM", "Error querying last image", e)
        }

        // Query last video
        try {
            querySingleMedia(
                context = context,
                uri = videosUri,
                projection = videoProjection,
                idColumnName = MediaStore.Video.Media._ID,
                dateColumnName = MediaStore.Video.Media.DATE_ADDED
            )?.let { (uri, date) ->
                lastVideoUri = uri
                lastVideoDate = date
            }
        } catch (e: Exception) {
            Log.e("CameraVM", "Error querying last video", e)
        }

        if (lastImageUri != null && lastVideoUri != null) {
            if (lastImageDate >= lastVideoDate) lastImageUri else lastVideoUri
        } else {
            lastImageUri ?: lastVideoUri
        }
    }

    private fun querySingleMedia(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        idColumnName: String,
        dateColumnName: String
    ): Pair<Uri, Long>? {
        val sortColumn = dateColumnName // DATE_ADDED
        // Try modern Bundle query on API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bundle = Bundle().apply {
                        putString(
                            android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                            "$sortColumn DESC"
                        )
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
                    }
                    context.contentResolver.query(uri, projection, bundle, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumnName))
                            val date = cursor.getLong(cursor.getColumnIndexOrThrow(dateColumnName))
                            return Pair(ContentUris.withAppendedId(uri, id), date)
                        }
                    }
                    // If bundle query returns null/empty, fall through
                }
            } catch (e: Exception) {
                Log.w("CameraVM", "Bundle query failed, falling back to legacy", e)
            }
        }
        // Legacy fallback — no LIMIT in sortOrder; we just take first row after DESC sort
        // This avoids undocumented "DESC LIMIT 1" which fails on some OEMs
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "$sortColumn DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumnName))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(dateColumnName))
                return Pair(ContentUris.withAppendedId(uri, id), date)
            }
        }
        return null
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
