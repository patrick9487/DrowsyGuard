package com.patrick.ui.fatigue

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.patrick.core.FatigueDetectionDebugger
import com.patrick.core.FatigueLevel
import com.patrick.core.FatigueUiCallback
import com.patrick.detection.FatigueDetectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 統一的疲勞檢測 ViewModel
 * 負責所有疲勞檢測相關的 UI 狀態管理
 * 包括疲勞級別、校正狀態、對話框等
 */
class FatigueViewModel(
    application: Application,
) : AndroidViewModel(application), FatigueUiCallback {
    companion object {
        private const val TAG = "FatigueViewModel"
        private const val AUTO_ENABLE_DEBUG = false // 可配置是否自動啟用調試模式
    }

    // ========== 新增一次性事件流 ==========
    sealed class FatigueUiEvent {
        object ShowWarningDialog : FatigueUiEvent()
        object ShowNoticeDialog : FatigueUiEvent()
        // 可擴充其他一次性事件
    }
    private val _uiEvent = MutableSharedFlow<FatigueUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    // 疲勞檢測管理器
    private val fatigueDetectionManager = FatigueDetectionManager(application, this)

    // UI 狀態管理器
    private val fatigueUiStateManager = FatigueUiStateManager()

    // 調試器
    private val debugger = FatigueDetectionDebugger(application)

    // ========== 疲勞檢測相關狀態 ==========

    // 當前疲勞級別
    private val _fatigueLevel = MutableStateFlow(FatigueLevel.NORMAL)
    val fatigueLevel: StateFlow<FatigueLevel> = _fatigueLevel

    // 是否顯示疲勞對話框
    private val _showFatigueDialog = MutableStateFlow(false)
    val showFatigueDialog: StateFlow<Boolean> = _showFatigueDialog

    // 狀態文字（AppBar 中間顯示）
    private val _statusText = MutableStateFlow("持續偵測中…")
    val statusText: StateFlow<String> = _statusText

    // 是否偵測到臉部
    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected

    // ========== 校正相關狀態 ==========

    // 是否正在校正
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating

    // 校正進度
    private val _calibrationProgress = MutableStateFlow(0)
    val calibrationProgress: StateFlow<Int> = _calibrationProgress

    // 校正完成的 EAR 值
    private val _calibrationEarValue = MutableStateFlow(0f)
    val calibrationEarValue: StateFlow<Float> = _calibrationEarValue

    // ========== 眨眼頻率相關狀態 ==========

    // 眨眼頻率（五秒更新一次）
    private val _blinkFrequency = MutableStateFlow(0)
    val blinkFrequency: StateFlow<Int> = _blinkFrequency

    // 是否顯示眨眼頻率（可由設定控制）
    private val _showBlinkFrequency = MutableStateFlow(true)
    val showBlinkFrequency: StateFlow<Boolean> = _showBlinkFrequency

    init {
        // 根據環境設置調試模式
        // 在 ui-components 模組中無法直接訪問 BuildConfig，使用其他方式判斷
        // 您可以通過設置 AUTO_ENABLE_DEBUG 來控制是否自動啟用調試模式
        if (AUTO_ENABLE_DEBUG) {
            debugger.enableQuickDebugMode()
            Log.d(TAG, "調試模式已自動啟用")
        } else {
            Log.d(TAG, "調試模式未自動啟用，請手動調用 setDebugMode()")
        }
    }

    /**
     * 處理面部特徵點結果
     */
    fun processFaceLandmarks(result: FaceLandmarkerResult) {
        fatigueDetectionManager.processFaceLandmarks(result)
    }

    /**
     * 啟動疲勞檢測
     */
    fun startDetection() {
        fatigueDetectionManager.startDetection()
        Log.d(TAG, "疲勞檢測已啟動")
    }

    /**
     * 停止疲勞檢測
     */
    fun stopDetection() {
        fatigueDetectionManager.stopDetection()
        Log.d(TAG, "疲勞檢測已停止")
    }

    /**
     * 開始校正
     */
    fun startCalibration() {
        _isCalibrating.value = true
        _calibrationProgress.value = 0
        _statusText.value = "校正中，請自然眨眼 15 秒…"
        fatigueDetectionManager.startCalibration()
        Log.d(TAG, "校正已開始")
    }

    /**
     * 停止校正
     */
    fun stopCalibration() {
        _isCalibrating.value = false
        fatigueDetectionManager.stopCalibration()
        Log.d(TAG, "校正已停止")
    }

    /**
     * 用戶確認已清醒（公開方法）
     */
    fun handleUserAcknowledged() {
        Log.d(TAG, "用戶確認已清醒，開始重置流程")

        // 1. 重置 UI 狀態管理器
        fatigueUiStateManager.onUserAcknowledged()

        // 2. 完整重置偵測器與警報（確保與 AlertManager 回呼一致）
        fatigueDetectionManager.fullResetDetectorAndAlerts()

        // 3. 更新 UI 狀態
        updateUIState(FatigueLevel.NORMAL, false, "持續偵測中…")

        Log.d(TAG, "重置完成，當前疲勞事件計數: ${fatigueDetectionManager.getFatigueEventCount()}")
    }

    /**
     * 用戶要求休息（公開方法）
     */
    fun handleUserRequestedRest() {
        Log.d(TAG, "用戶要求休息，開始重置流程")

        // 1. 重置 UI 狀態管理器
        fatigueUiStateManager.onUserRequestedRest()

        // 2. 重置疲勞檢測器的事件計數
        fatigueDetectionManager.resetFatigueEvents()

        // 3. 更新 UI 狀態
        updateUIState(FatigueLevel.NORMAL, false, "持續偵測中…")

        Log.d(TAG, "休息重置完成，當前疲勞事件計數: ${fatigueDetectionManager.getFatigueEventCount()}")
    }

    /**
     * 生成調試報告
     */
    fun generateDebugReport(): String {
        return try {
            val report =
                debugger.generateDebugReport(
                    parameters = fatigueDetectionManager.getDetectionParameters(),
                    sensitivityReport = fatigueDetectionManager.generateSensitivityReport(),
                    uiStateInfo = fatigueUiStateManager.getResetStatusInfo(),
                )
            Log.d(TAG, "調試報告已生成")
            report
        } catch (e: Exception) {
            Log.e(TAG, "生成調試報告失敗", e)
            "生成調試報告失敗: ${e.message}"
        }
    }

    /**
     * 保存調試報告到文件
     */
    fun saveDebugReport(): String {
        return try {
            val report = generateDebugReport()
            val filePath = debugger.saveDebugReport(report)
            Log.d(TAG, "調試報告已保存: $filePath")
            filePath
        } catch (e: Exception) {
            Log.e(TAG, "保存調試報告失敗", e)
            "保存失敗: ${e.message}"
        }
    }

    /**
     * 設置調試模式
     */
    fun setDebugMode(mode: String) {
        when (mode.lowercase()) {
            "quick" -> debugger.enableQuickDebugMode()
            "sensitivity" -> debugger.enableSensitivityDebugMode()
            "performance" -> debugger.enablePerformanceDebugMode()
            "off" -> debugger.disableAllLogs()
            else -> Log.w(TAG, "未知的調試模式: $mode")
        }
        Log.d(TAG, "調試模式已設置為: $mode")
    }

    /**
     * 設定影像處理的最小間隔（毫秒）。例如 50ms 約 20FPS。
     */
    fun setMinProcessIntervalMs(intervalMs: Long) {
        fatigueDetectionManager.setMinProcessIntervalMs(intervalMs)
    }

    /**
     * 以 FPS 方式設定影像處理頻率（1~60）。
     */
    fun setProcessingRateFps(fps: Int) {
        fatigueDetectionManager.setProcessingRateFps(fps)
    }

    /**
     * 檢查自動報告
     */
    fun checkAutoReport(): String? {
        return try {
            val report =
                debugger.checkAutoGenerateReport(
                    parameters = fatigueDetectionManager.getDetectionParameters(),
                    sensitivityReport = fatigueDetectionManager.generateSensitivityReport(),
                    uiStateInfo = fatigueUiStateManager.getResetStatusInfo(),
                )
            report?.let {
                Log.d(TAG, "自動報告已生成")
            }
            report
        } catch (e: Exception) {
            Log.e(TAG, "檢查自動報告失敗", e)
            null
        }
    }

    // ========== FatigueUiCallback 實現 ==========

    override fun onBlink() {
        // 眨眼事件處理（如果需要）
    }

    override fun onCalibrationStarted() {
        _isCalibrating.value = true
        _calibrationProgress.value = 0
        _statusText.value = "正在校正中..."
    }

    override fun onCalibrationProgress(
        progress: Int,
        currentEar: Float,
    ) {
        _calibrationProgress.value = progress
        _statusText.value = "正在校正中... $progress%"
    }

    override fun onCalibrationCompleted(
        newThreshold: Float,
        minEar: Float,
        maxEar: Float,
        avgEar: Float,
    ) {
        _isCalibrating.value = false
        _calibrationProgress.value = 100
        _calibrationEarValue.value = avgEar
        _statusText.value = "校正完成！EAR: ${String.format("%.3f", avgEar)}, 閾值: ${String.format("%.3f", newThreshold)}"
    }

    override fun onNoticeFatigue() {
        // 若已有活躍的警告視窗，維持顯示，不要用 NOTICE 覆蓋關閉
        if (fatigueUiStateManager.hasActiveWarningDialog()) {
            Log.d(TAG, "已有活躍警告視窗，忽略 NOTICE 以維持視窗")
            return
        }
        val processedLevel =
            fatigueUiStateManager.processFatigueResult(
                FatigueLevel.NOTICE,
                fatigueDetectionManager.getFatigueEventCount(),
            )
        val statusMessage = generateStatusMessage(processedLevel, true)
        updateUIState(processedLevel, false, statusMessage)
        // 發送一次性事件
        CoroutineScope(Dispatchers.Main).launch {
            _uiEvent.emit(FatigueUiEvent.ShowNoticeDialog)
        }
    }

    override fun onNormalDetection() {
        // 若已有活躍的警告視窗，維持顯示，不要用 NORMAL 覆蓋關閉
        if (fatigueUiStateManager.hasActiveWarningDialog()) {
            Log.d(TAG, "已有活躍警告視窗，忽略 NORMAL 以維持視窗")
            return
        }
        val processedLevel =
            fatigueUiStateManager.processFatigueResult(
                FatigueLevel.NORMAL,
                fatigueDetectionManager.getFatigueEventCount(),
            )
        val statusMessage = generateStatusMessage(processedLevel, true)
        updateUIState(processedLevel, false, statusMessage)
    }

    override fun onNoFaceDetected() {
        // 只有在沒有警告視窗時，才切換 UI 狀態
        // 這樣可以避免警告視窗彈出後，臉部暫時消失導致視窗被關閉
        val hasActiveDialog = _showFatigueDialog.value || fatigueUiStateManager.hasActiveWarningDialog()
        
        if (!hasActiveDialog) {
            Log.d(TAG, "臉部消失且無警告視窗，切換 UI 狀態")
            updateUIState(FatigueLevel.NORMAL, false, "請面對鏡頭", false)
        } else {
            Log.d(TAG, "臉部消失但有警告視窗活躍，維持警告視窗狀態")
            // 如果有警告視窗顯示，則不做任何 UI 切換，維持警告視窗
            // 只更新臉部偵測狀態，但不影響警告視窗
            _isFaceDetected.value = false
        }
    }

    override fun onWarningFatigue() {
        val processedLevel =
            fatigueUiStateManager.processFatigueResult(
                FatigueLevel.WARNING,
                fatigueDetectionManager.getFatigueEventCount(),
            )
        val statusMessage = generateStatusMessage(processedLevel, true)
        updateUIState(processedLevel, processedLevel == FatigueLevel.WARNING, statusMessage)
        fatigueUiStateManager.setWarningDialogActive(processedLevel == FatigueLevel.WARNING)
        // 發送一次性事件
        CoroutineScope(Dispatchers.Main).launch {
            _uiEvent.emit(FatigueUiEvent.ShowWarningDialog)
        }
    }

    override fun onUserAcknowledged() {
        fatigueUiStateManager.onUserAcknowledged()
        val statusMessage = generateStatusMessage(FatigueLevel.NORMAL, true)
        updateUIState(FatigueLevel.NORMAL, false, statusMessage)
    }

    override fun onUserRequestedRest() {
        fatigueUiStateManager.onUserRequestedRest()
        val statusMessage = generateStatusMessage(FatigueLevel.NORMAL, true)
        updateUIState(FatigueLevel.NORMAL, false, statusMessage)
    }

    override fun setWarningDialogActive(active: Boolean) {
        // FatigueViewModel 若無需處理可留空
    }

    // ========== 私有方法 ==========

    /**
     * 更新 UI 狀態的輔助方法
     */
    private fun updateUIState(
        level: FatigueLevel,
        showDialog: Boolean,
        status: String,
        faceDetected: Boolean = true,
    ) {
        _fatigueLevel.value = level
        _showFatigueDialog.value = showDialog
        _statusText.value = status
        _isFaceDetected.value = faceDetected
    }

    /**
     * 根據偵測狀態生成狀態訊息
     */
    private fun generateStatusMessage(
        fatigueLevel: FatigueLevel,
        faceDetected: Boolean,
        isCalibrating: Boolean = _isCalibrating.value
    ): String {
        return when {
            isCalibrating -> "正在校正中..."
            !faceDetected -> "請面對鏡頭"
            fatigueLevel == FatigueLevel.WARNING -> "⚠️ 疲勞警告"
            fatigueLevel == FatigueLevel.NOTICE -> "⚠️ 疲勞提醒"
            else -> "持續偵測中…"
        }
    }

    /**
     * 獲取重置狀態信息（用於調試）
     */
    fun getResetStatusInfo(): String {
        return fatigueUiStateManager.getResetStatusInfo()
    }
} 
