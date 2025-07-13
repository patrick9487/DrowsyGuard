package com.patrick.camera

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.patrick.detection.FatigueDetectionManager
import com.patrick.core.FatigueUiCallback
import com.patrick.core.FatigueLevel
import androidx.lifecycle.LifecycleOwner

class CameraViewModel(
    application: Application
) : AndroidViewModel(application), FatigueUiCallback {
    private val cameraUseCase: CameraUseCase = CameraModule.createCameraModule(application)
    private val fatigueDetectionManager = FatigueDetectionManager(application, this)

    private val _fatigueLevel = MutableStateFlow(FatigueLevel.NORMAL)
    val fatigueLevel: StateFlow<FatigueLevel> = _fatigueLevel

    private val _faceLandmarks = MutableStateFlow<FaceLandmarkerResult?>(null)
    val faceLandmarks: StateFlow<FaceLandmarkerResult?> = _faceLandmarks

    private val _calibrationProgress = MutableStateFlow(0)
    val calibrationProgress: StateFlow<Int> = _calibrationProgress
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating
    private val _showFatigueDialog = MutableStateFlow(false)
    val showFatigueDialog: StateFlow<Boolean> = _showFatigueDialog

    fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            cameraUseCase.setFaceLandmarksCallback { result ->
                _faceLandmarks.value = result
                fatigueDetectionManager.processFaceLandmarks(result)
            }
            cameraUseCase.initializeCamera(previewView, lifecycleOwner)
            
            // 啟動疲勞檢測
            fatigueDetectionManager.startDetection()
            
            // 開始校正流程
            fatigueDetectionManager.startCalibration()
        }
    }

    fun releaseCamera() {
        viewModelScope.launch {
            cameraUseCase.releaseCamera()
        }
    }

    // FatigueUiCallback 實現
    override fun onBlink() {}
    override fun onCalibrationStarted() {
        _isCalibrating.value = true
        _calibrationProgress.value = 0
    }
    override fun onCalibrationProgress(progress: Int, currentEar: Float) {
        _calibrationProgress.value = progress
    }
    override fun onCalibrationCompleted(newThreshold: Float, minEar: Float, maxEar: Float, avgEar: Float) {
        _isCalibrating.value = false
        _calibrationProgress.value = 100
    }
    override fun onModerateFatigue() {
        _fatigueLevel.value = FatigueLevel.MODERATE
        _showFatigueDialog.value = true
    }
    override fun onUserAcknowledged() {
        _fatigueLevel.value = FatigueLevel.NORMAL
        _showFatigueDialog.value = false
    }
    override fun onUserRequestedRest() {
        _fatigueLevel.value = FatigueLevel.SEVERE
        _showFatigueDialog.value = true
    }
    override fun onFatigueAlert(message: String) {
        // 可根據需求處理警告，例如寫入 log、更新狀態流、觸發 UI 等
    }
} 