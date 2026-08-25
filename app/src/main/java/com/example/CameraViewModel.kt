package com.example

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recording
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CameraRepository
import com.example.data.ConsentRepository
import com.example.data.GeminiRepository
import com.example.data.SceneAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CameraMode {
    PHOTO, VIDEO, PORTRAIT, PANORAMA, PRO
}

enum class CustomFlashMode {
    AUTO, ON, OFF
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CameraRepository(application.applicationContext)
    private val consentRepository = ConsentRepository(application.applicationContext)
    private val sceneAnalyzer = SceneAnalyzer(application.applicationContext)
    private val geminiRepository = GeminiRepository(application.applicationContext)

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

    // Phase 2: Pro + Portrait + Panorama states
    private val _aperture = MutableStateFlow(2.8f) // f/1.4 .. f/16
    val aperture = _aperture.asStateFlow()

    private val _isHdrEnabled = MutableStateFlow(false)
    val isHdrEnabled = _isHdrEnabled.asStateFlow()

    private val _isPortraitExtensionAvailable = MutableStateFlow<Boolean?>(null)
    val isPortraitExtensionAvailable = _isPortraitExtensionAvailable.asStateFlow()

    private val _isPanoramaExtensionAvailable = MutableStateFlow<Boolean?>(null)
    val isPanoramaExtensionAvailable = _isPanoramaExtensionAvailable.asStateFlow()

    private val _showProControls = MutableStateFlow(false)
    val showProControls = _showProControls.asStateFlow()

    private val _manualFocusDistance = MutableStateFlow<Float?>(null) // null = auto, 0f..1f
    val manualFocusDistance = _manualFocusDistance.asStateFlow()

    private val _panoramaProgress = MutableStateFlow(0f) // 0..1 sweep
    val panoramaProgress = _panoramaProgress.asStateFlow()

    private val _isGalleryOpen = MutableStateFlow(false)
    val isGalleryOpen = _isGalleryOpen.asStateFlow()

    // Phase 3: Intelligence
    private val _aiConsent = MutableStateFlow(false)
    val aiConsent = _aiConsent.asStateFlow()
    private val _geminiEnabled = MutableStateFlow(false)
    val geminiEnabled = _geminiEnabled.asStateFlow()
    private val _smartOrganizeEnabled = MutableStateFlow(false)
    val smartOrganizeEnabled = _smartOrganizeEnabled.asStateFlow()
    private val _captureAssistEnabled = MutableStateFlow(true)
    val captureAssistEnabled = _captureAssistEnabled.asStateFlow()

    private val _sceneResult = MutableStateFlow<SceneAnalyzer.SceneResult?>(null)
    val sceneResult = _sceneResult.asStateFlow()
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()
    private val _captureAssistText = MutableStateFlow<String?>(null)
    val captureAssistText = _captureAssistText.asStateFlow()

    private val _geminiDescription = MutableStateFlow<String?>(null)
    val geminiDescription = _geminiDescription.asStateFlow()
    private val _isDescribing = MutableStateFlow(false)
    val isDescribing = _isDescribing.asStateFlow()

    private val _lastTags = MutableStateFlow<List<String>>(emptyList())
    val lastTags = _lastTags.asStateFlow()

    init {
        // Automatically fetch the last snapped photo or video on initialize
        loadLastCapturedMedia()
        observeConsent()
    }

    private fun observeConsent() {
        viewModelScope.launch {
            consentRepository.aiConsentFlow.collectLatest { _aiConsent.value = it }
        }
        viewModelScope.launch {
            consentRepository.geminiEnabledFlow.collectLatest { _geminiEnabled.value = it }
        }
        viewModelScope.launch {
            consentRepository.smartOrganizeFlow.collectLatest { _smartOrganizeEnabled.value = it }
        }
        viewModelScope.launch {
            consentRepository.captureAssistFlow.collectLatest { _captureAssistText.value = null; _captureAssistEnabled.value = it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activeRecording?.stop()
            activeRecording?.close()
        } catch (_: Exception) {}
        activeRecording = null
        timerJob?.cancel()
        try { sceneAnalyzer.close() } catch (_: Exception) {}
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

    // Phase 2: Pro controls
    fun setAperture(value: Float) {
        _aperture.value = value.coerceIn(1.4f, 16f)
    }

    fun toggleHdr() {
        _isHdrEnabled.value = !_isHdrEnabled.value
    }

    fun setHdrEnabled(enabled: Boolean) {
        _isHdrEnabled.value = enabled
    }

    fun setExtensionAvailability(portrait: Boolean?, panorama: Boolean?) {
        _isPortraitExtensionAvailable.value = portrait
        _isPanoramaExtensionAvailable.value = panorama
    }

    fun toggleProControls() {
        _showProControls.value = !_showProControls.value
    }

    fun setShowProControls(show: Boolean) {
        _showProControls.value = show
    }

    fun setManualFocus(distance: Float?) {
        _manualFocusDistance.value = distance?.coerceIn(0f, 1f)
    }

    fun setPanoramaProgress(progress: Float) {
        _panoramaProgress.value = progress.coerceIn(0f, 1f)
    }

    fun setGalleryOpen(open: Boolean) {
        _isGalleryOpen.value = open
    }

    fun isPhotoLikeMode(): Boolean {
        return _cameraMode.value == CameraMode.PHOTO ||
                _cameraMode.value == CameraMode.PORTRAIT ||
                _cameraMode.value == CameraMode.PANORAMA ||
                _cameraMode.value == CameraMode.PRO
    }

    // Phase 3: Consent
    fun setAiConsent(consented: Boolean) {
        viewModelScope.launch { consentRepository.setAiConsent(consented) }
    }
    fun setGeminiEnabled(enabled: Boolean) {
        viewModelScope.launch { consentRepository.setGeminiEnabled(enabled) }
    }
    fun setSmartOrganize(enabled: Boolean) {
        viewModelScope.launch { consentRepository.setSmartOrganize(enabled) }
    }
    fun setCaptureAssistEnabled(enabled: Boolean) {
        viewModelScope.launch { consentRepository.setCaptureAssist(enabled) }
    }

    // Phase 3: Scene analyze (on-device ML Kit, no network)
    fun analyzeScene(uri: Uri) {
        if (_isAnalyzing.value) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val result = sceneAnalyzer.analyze(uri)
                _sceneResult.value = result
                // Capture assist suggestion (offline rule, Gemini would re-rank)
                val suggestion = result?.let { geminiRepository.captureAssistSuggestion(it.rawTopLabel, _aiConsent.value) }
                _captureAssistText.value = suggestion
                // Smart organize tags if enabled
                if (_smartOrganizeEnabled.value && _aiConsent.value) {
                    val tagRes = geminiRepository.tagMedia(uri, _aiConsent.value)
                    if (tagRes.isSuccess) _lastTags.value = tagRes.getOrDefault(emptyList())
                }
                Log.d("CameraVM", "Scene analyzed: ${result?.labels} -> ${result?.suggestedMode}")
            } catch (e: Exception) {
                Log.e("CameraVM", "Scene analyze failed", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearScene() {
        _sceneResult.value = null
        _captureAssistText.value = null
    }

    // Phase 3: Gemini describe (opt-in, wifi, key required)
    fun describeWithGemini(uri: Uri) {
        if (_isDescribing.value) return
        viewModelScope.launch {
            _isDescribing.value = true
            _geminiDescription.value = null
            try {
                val result = geminiRepository.describeImage(
                    uri = uri,
                    isConsented = _aiConsent.value,
                    isGeminiEnabled = _geminiEnabled.value
                )
                if (result.isSuccess) {
                    _geminiDescription.value = result.getOrNull()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "غير متاح"
                    _geminiDescription.value = null
                    setErrorMessage("Gemini: $err")
                }
            } catch (e: Exception) {
                Log.e("CameraVM", "Gemini describe failed", e)
                setErrorMessage("فشل الوصف: ${e.message}")
            } finally {
                _isDescribing.value = false
            }
        }
    }

    fun clearGeminiDescription() {
        _geminiDescription.value = null
    }

    fun hasValidGeminiKey(): Boolean = geminiRepository.hasValidKey()

    fun getCameraXFlashMode(): Int {
        return when (_flashMode.value) {
            CustomFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            CustomFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            CustomFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }
}
