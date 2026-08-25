package com.example

import android.app.Application
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recording
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CameraRepository
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

    private val repository = CameraRepository(application.applicationContext)

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
            _lastCapturedUri.value = repository.getLastCapturedMedia()
        }
    }

    suspend fun getLastImageFallback(): Uri? = repository.getLastImageUriFallback()

    fun isVideo(uri: Uri): Boolean = repository.isVideo(uri)

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
