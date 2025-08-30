package com.patrick.main.ui

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.patrick.camera.CameraViewModel
import com.patrick.ui.fatigue.FatigueViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 疲勞檢測屏幕 ViewModel
 * 協調相機和疲勞檢測功能
 * 作為 UI 層的協調器
 *
 * 更新：使用統一的 ui-components FatigueViewModel
 */
class FatigueScreenViewModel(
    application: Application,
) : AndroidViewModel(application) {
    // 子 ViewModel - 遵循單一職責原則
    private val cameraViewModel = CameraViewModel(application)
    private val fatigueViewModel = FatigueViewModel(application)

    // 暴露子 ViewModel 的狀態
    val faceLandmarks: StateFlow<com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult?> = cameraViewModel.faceLandmarks
    val errorMessage: StateFlow<String?> = cameraViewModel.errorMessage
    val isCameraReady: StateFlow<Boolean> = cameraViewModel.isCameraReady

    // 疲勞檢測相關狀態（統一來自 ui-components FatigueViewModel）
    val fatigueLevel: StateFlow<com.patrick.core.FatigueLevel> = fatigueViewModel.fatigueLevel
    val showFatigueDialog: StateFlow<Boolean> = fatigueViewModel.showFatigueDialog
    val statusText: StateFlow<String> = fatigueViewModel.statusText
    val isFaceDetected: StateFlow<Boolean> = fatigueViewModel.isFaceDetected
    val uiEvent = fatigueViewModel.uiEvent

    // 校正相關狀態
    val calibrationProgress: StateFlow<Int> = fatigueViewModel.calibrationProgress
    val isCalibrating: StateFlow<Boolean> = fatigueViewModel.isCalibrating
    val calibrationEarValue: StateFlow<Float> = fatigueViewModel.calibrationEarValue

    // 眨眼頻率相關狀態
    val blinkFrequency: StateFlow<Int> = fatigueViewModel.blinkFrequency
    val showBlinkFrequency: StateFlow<Boolean> = fatigueViewModel.showBlinkFrequency

    // 打哈欠和閉眼時間相關狀態
    val yawnCount: StateFlow<Int> = fatigueViewModel.yawnCount
    val eyeClosureDuration: StateFlow<Long> = fatigueViewModel.eyeClosureDuration

    /**
     * 初始化疲勞檢測功能
     */
    fun initializeFatigueDetection(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
    ) {
        viewModelScope.launch {
            // 初始化相機，並設置面部特徵點回調
            cameraViewModel.initializeCamera(
                previewView = previewView,
                lifecycleOwner = lifecycleOwner,
                onFaceLandmarksResult = { result ->
                    // 將相機結果傳遞給疲勞檢測
                    fatigueViewModel.processFaceLandmarks(result)
                },
            )

            // 啟動疲勞檢測
            fatigueViewModel.startDetection()
        }
    }

    /**
     * 釋放資源
     */
    fun release() {
        viewModelScope.launch {
            cameraViewModel.releaseCamera()
            fatigueViewModel.stopDetection()
        }
    }

    /**
     * 開始校正
     */
    fun startCalibration() {
        fatigueViewModel.startCalibration()
    }

    /**
     * 停止校正
     */
    fun stopCalibration() {
        fatigueViewModel.stopCalibration()
    }

    /**
     * 用戶確認已清醒
     */
    fun onUserAcknowledged() {
        fatigueViewModel.handleUserAcknowledged()
    }

    /**
     * 用戶要求休息
     */
    fun onUserRequestedRest() {
        fatigueViewModel.handleUserRequestedRest()
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        cameraViewModel.clearError()
    }

    /**
     * 檢查相機狀態
     */
    fun isCameraReady(): Boolean {
        return cameraViewModel.isCameraReady()
    }

    /**
     * 獲取相機狀態信息
     */
    fun getCameraStatus(): String {
        return cameraViewModel.getCameraStatus()
    }

    /**
     * 獲取重置狀態信息（用於調試）
     */
    fun getResetStatusInfo(): String {
        return fatigueViewModel.getResetStatusInfo()
    }

    /**
     * 生成調試報告
     */
    fun generateDebugReport(): String {
        return fatigueViewModel.generateDebugReport()
    }

    /**
     * 保存調試報告
     */
    fun saveDebugReport(): String {
        return fatigueViewModel.saveDebugReport()
    }

    /**
     * 設置調試模式
     */
    fun setDebugMode(mode: String) {
        fatigueViewModel.setDebugMode(mode)
    }

    /**
     * 檢查自動報告
     */
    fun checkAutoReport(): String? {
        return fatigueViewModel.checkAutoReport()
    }

    /**
     * 設定影像處理最小間隔（毫秒），例如 50ms 約 20FPS。
     */
    fun setMinProcessIntervalMs(intervalMs: Long) {
        fatigueViewModel.setMinProcessIntervalMs(intervalMs)
    }

    /**
     * 以 FPS 設定影像處理頻率（1~60）。
     */
    fun setProcessingRateFps(fps: Int) {
        fatigueViewModel.setProcessingRateFps(fps)
    }
}
