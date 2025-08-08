package com.patrick.detection

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.patrick.alert.FatigueAlertManager
import com.patrick.core.FatigueDialogCallback
import com.patrick.core.FatigueDetectionListener
import com.patrick.core.FatigueDetectionLogger
import com.patrick.core.FatigueDetectionResult
import com.patrick.core.FatigueLevel
import com.patrick.core.FatigueUiCallback
import com.patrick.detection.DetectionState

/**
 * 疲勞檢測管理器
 * 協調疲勞檢測器、警報管理器和 UI 回調
 */
class FatigueDetectionManager(
    private val context: Context,
    private val uiCallback: FatigueUiCallback,
) : FatigueDetectionListener {
    companion object {
        private const val TAG = "FatigueDetectionManager"
        private const val NO_FACE_FRAME_THRESHOLD = 5 // 連續 5 幀沒臉才進入 NO_FACE (更敏感)
    }

    private val fatigueDetector = FatigueDetector(context)
    private val alertManager = FatigueAlertManager(context)

    private var lastKnownState: DetectionState = DetectionState.DETECTING
    private var lastError: Exception? = null

    private var currentState: DetectionState = DetectionState.INITIALIZING
        set(value) {
            if (field != value) {
                handleStateExit(field)
                field = value
                handleStateEnter(value)
            }
        }

    // --- 新增臉部消失緩衝計數器 ---
    private var noFaceFrameCount = 0

    // --- 新增影像識別處理頻率限制（節流）---
    // 預設每幀處理間隔 50ms ≈ 20 FPS；可透過 setter 調整
    private var minProcessIntervalMs: Long = 50L
    private var lastProcessedTimestamp: Long = 0L
    private var skippedFrameCount: Long = 0L

    private fun transitionToState(newState: DetectionState) {
        val previousState = currentState
        if (newState == DetectionState.NO_FACE && previousState != DetectionState.NO_FACE) {
            lastKnownState = previousState
        }
        currentState = newState
        Log.d(TAG, "Transitioned to state: $previousState -> $newState")
        
        // 執行狀態進入處理
        handleStateEnter(newState)
    }

    private fun handleStateEnter(state: DetectionState) {
        when(state) {
            DetectionState.NO_FACE -> {
                uiCallback.onNoFaceDetected()
                alertManager.stopAllAlerts()
            }
            DetectionState.WARNING -> {
                uiCallback.onWarningFatigue()
                uiCallback.setWarningDialogActive(true)
            }
            DetectionState.NOTICE -> uiCallback.onNoticeFatigue()
            DetectionState.ERROR -> {
                Log.e(TAG, "進入 ERROR 狀態")
                alertManager.stopAllAlerts()
                uiCallback.setWarningDialogActive(false)
                // 可擴充：顯示錯誤提示
            }
            DetectionState.CALIBRATING -> {
                uiCallback.onCalibrationStarted()
                alertManager.stopAllAlerts()
            }
            DetectionState.DETECTING -> {
                uiCallback.onNormalDetection()
                // 不在進入 DETECTING 時自動清除對話框活躍旗標，避免瞬時波動關閉對話框
            }
            DetectionState.REST_MODE -> {
                alertManager.stopAllAlerts()
                uiCallback.setWarningDialogActive(false)
                // 可擴充：顯示休息提示
            }
            DetectionState.SHUTDOWN -> {
                alertManager.stopAllAlerts()
                uiCallback.setWarningDialogActive(false)
            }
            else -> {}
        }
    }

    /**
     * 設定最小處理間隔（毫秒）。例如：50ms 約 20FPS，100ms 約 10FPS。
     */
    fun setMinProcessIntervalMs(intervalMs: Long) {
        minProcessIntervalMs = intervalMs.coerceAtLeast(0L)
        Log.d(TAG, "更新最小處理間隔: ${minProcessIntervalMs}ms")
    }

    /**
     * 以 FPS 方式設定處理頻率。傳入期望 FPS，將自動換算為最小間隔。
     */
    fun setProcessingRateFps(fps: Int) {
        val safeFps = fps.coerceIn(1, 60)
        val interval = 1000L / safeFps
        setMinProcessIntervalMs(interval)
        Log.d(TAG, "更新處理頻率: ${safeFps} FPS (間隔 ${interval}ms)")
    }

    /**
     * 取得目前累積略過的幀數（僅供觀察/調試）。
     */
    fun getSkippedFrameCount(): Long = skippedFrameCount

    private fun handleStateExit(state: DetectionState) {
        when(state) {
            // 不在離開 WARNING 時自動清除，改由使用者操作清除
            DetectionState.WARNING -> {}
            else -> {}
        }
    }

    init {
        fatigueDetector.setFatigueListener(this)
        alertManager.setDialogCallback(
            object : FatigueDialogCallback {
                override fun onUserAcknowledged() {
                    FatigueDetectionLogger.logReset(
                        "使用者確認已清醒，重置疲勞檢測狀態",
                        resetType = "UserAcknowledged",
                    )
                    // 完全重置疲勞檢測器狀態
                    resetFatigueEvents()
                    fatigueDetector.reset()  // 重置 FatigueDetector 內部狀態
                    alertManager.stopAllAlerts()
                    uiCallback.setWarningDialogActive(false)
                    uiCallback.onUserAcknowledged()
                    // 直接回到偵測狀態，而不是休息模式
                    transitionToState(DetectionState.DETECTING)
                    FatigueDetectionLogger.logReset("疲勞檢測狀態已重置")
                }
                override fun onUserRequestedRest() {
                    FatigueDetectionLogger.logReset(
                        "使用者要求休息",
                        resetType = "UserRequestedRest",
                    )
                    alertManager.stopAllAlerts()
                    uiCallback.setWarningDialogActive(false)
                    uiCallback.onUserRequestedRest()
                    transitionToState(DetectionState.REST_MODE)
                }
            },
        )
        alertManager.setUiCallback(uiCallback)
    }

    fun processFaceLandmarks(result: FaceLandmarkerResult) {
        Log.d(TAG, "[FatigueDetectionManager] processFaceLandmarks called, currentState=$currentState")
        if (currentState == DetectionState.SHUTDOWN || currentState == DetectionState.ERROR) return

        // 節流：限制處理頻率，避免在 AVD 上造成卡頓
        val now = System.currentTimeMillis()
        val elapsed = now - lastProcessedTimestamp
        if (elapsed in 0 until minProcessIntervalMs) {
            skippedFrameCount++
            if (skippedFrameCount % 30L == 0L) { // 每略過 30 幀記錄一次，避免刷屏
                Log.d(TAG, "節流略過幀，已略過: $skippedFrameCount, 間隔=${minProcessIntervalMs}ms")
            }
            return
        }
        lastProcessedTimestamp = now

        try {
            val fatigueResult = fatigueDetector.processFaceLandmarks(result)
            Log.d(
                TAG,
                "[FatigueDetectionManager] fatigueDetector.processFaceLandmarks returned, fatigueLevel=${fatigueResult.fatigueLevel}, faceDetected=${fatigueResult.faceDetected}",
            )

            // --- 簡化的臉部偵測邏輯 ---
            if (fatigueResult.faceDetected) {
                noFaceFrameCount = 0
                // 臉部回復時，若當前是 NO_FACE，回到上次狀態
                if (currentState == DetectionState.NO_FACE) {
                    Log.d(TAG, "臉部回復，從 NO_FACE 回到 $lastKnownState")
                    transitionToState(lastKnownState)
                }
                // 只在非 NO_FACE 狀態下處理警告/提醒
                if (currentState != DetectionState.NO_FACE) {
                    handleAlerts(fatigueResult)
                }
            } else {
                noFaceFrameCount++
                if (noFaceFrameCount % 100 == 0) {
                    Log.w(TAG, "長時間無臉部偵測，計數: $noFaceFrameCount/$NO_FACE_FRAME_THRESHOLD，當前狀態: $currentState")
                } else {
                    Log.d(TAG, "無臉部偵測，計數: $noFaceFrameCount/$NO_FACE_FRAME_THRESHOLD")
                }
                
                if (noFaceFrameCount >= NO_FACE_FRAME_THRESHOLD && currentState != DetectionState.NO_FACE) {
                    Log.w(TAG, "觸發 NO_FACE 狀態轉換，計數: $noFaceFrameCount，當前狀態: $currentState")
                    transitionToState(DetectionState.NO_FACE)
                    // 重置計數避免重複觸發
                    noFaceFrameCount = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理疲劳检测时发生错误", e)
            lastError = e
            transitionToState(DetectionState.ERROR)
        }
    }

    // 移除重複的 updateFaceDetectionState 函數，邏輯已整合到 processFaceLandmarks 中

    private fun handleAlerts(result: FatigueDetectionResult) {
        if (currentState == DetectionState.CALIBRATING || currentState == DetectionState.NO_FACE || currentState == DetectionState.ERROR || currentState == DetectionState.SHUTDOWN) {
            Log.d(TAG, "狀態為 $currentState，跳過疲勞警報處理")
            return
        }
        Log.d(
            TAG,
            "處理疲勞警報: isFatigueDetected=${result.isFatigueDetected}, fatigueLevel=${result.fatigueLevel}, events.size=${result.events.size}, 當前疲勞事件計數=${fatigueDetector.getFatigueEventCount()}",
        )
        if (result.isFatigueDetected) {
            alertManager.handleFatigueDetection(result)
            when (result.fatigueLevel) {
                com.patrick.core.FatigueLevel.NOTICE -> transitionToState(DetectionState.NOTICE)
                com.patrick.core.FatigueLevel.WARNING -> transitionToState(DetectionState.WARNING)
                else -> transitionToState(DetectionState.DETECTING)
            }
        } else {
            transitionToState(DetectionState.DETECTING)
        }
    }

    fun startDetection() {
        resetFatigueEvents()
        transitionToState(DetectionState.DETECTING)
        Log.d(TAG, "疲劳检测已启动，所有状态已重置")
    }

    fun stopDetection() {
        fatigueDetector.reset()
        alertManager.stopAllAlerts()
        transitionToState(DetectionState.SHUTDOWN)
        Log.d(TAG, "疲劳检测已停止")
    }

    fun startCalibration() {
        fatigueDetector.startCalibration()
        transitionToState(DetectionState.CALIBRATING)
        Log.d(TAG, "校正已開始")
    }

    fun stopCalibration() {
        fatigueDetector.stopCalibration()
        transitionToState(DetectionState.DETECTING)
        Log.d(TAG, "校正已停止")
    }

    fun resetCalibrationState() {
        fatigueDetector.resetCalibrationState()
        Log.d(TAG, "校正狀態已重置")
    }

    fun setDetectionParameters(
        earThreshold: Float,
        marThreshold: Float,
        fatigueEventThreshold: Int,
    ) {
        fatigueDetector.setDetectionParameters(earThreshold, marThreshold, fatigueEventThreshold)
    }

    fun getFatigueEventCount(): Int = fatigueDetector.getFatigueEventCount()

    fun resetFatigueEvents() {
        fatigueDetector.resetFatigueEvents()
        Log.d(TAG, "重置疲勞事件計數: ${fatigueDetector.getFatigueEventCount()}")
    }

    /**
     * 供 UI 直接呼叫的完整重置方法（不觸發 UI 回調）
     * 用於用戶在 Compose 對話框點擊「我已清醒」時，確保與 AlertManager 回呼一致的重置效果。
     */
    fun fullResetDetectorAndAlerts() {
        FatigueDetectionLogger.logReset(
            "使用者確認已清醒，重置疲勞檢測狀態",
            resetType = "UserAcknowledged(ViewModel)",
        )
        // 完整重置偵測相關狀態
        resetFatigueEvents()
        fatigueDetector.reset()
        alertManager.stopAllAlerts()
        uiCallback.setWarningDialogActive(false)
        // 回到偵測狀態
        transitionToState(DetectionState.DETECTING)
        FatigueDetectionLogger.logReset("疲勞檢測狀態已重置")
    }

    fun getDetectionParameters(): Map<String, Any> = fatigueDetector.getDetectionParameters()
    fun generateSensitivityReport(): String = fatigueDetector.generateSensitivityReport()
    fun setLogEnabled(
        sensitivity: Boolean = true,
        trigger: Boolean = true,
        calibration: Boolean = true,
        event: Boolean = true,
        reset: Boolean = true,
    ) {
        fatigueDetector.setLogEnabled(sensitivity, trigger, calibration, event, reset)
    }
    fun getRecentBlinkCount(windowMs: Long): Int = fatigueDetector.getRecentBlinkCount(windowMs)
    fun isCalibrating(): Boolean = fatigueDetector.isCalibrating()
    fun getCalibrationProgress(): Int = fatigueDetector.getCalibrationProgress()
    fun isFaceDetected(): Boolean = fatigueDetector.isFaceDetected()

    // 校正相關回調實現
    override fun onCalibrationStarted() {
        Log.d(TAG, "校正已開始")
        transitionToState(DetectionState.CALIBRATING)
    }
    override fun onCalibrationProgress(progress: Int, currentEar: Float) {
        Log.d(TAG, "[FatigueDetectionManager] onCalibrationProgress: progress=$progress, currentEar=$currentEar")
        uiCallback.onCalibrationProgress(progress, currentEar)
    }
    override fun onCalibrationCompleted(newThreshold: Float, minEar: Float, maxEar: Float, avgEar: Float) {
        transitionToState(DetectionState.DETECTING)
        uiCallback.onCalibrationCompleted(newThreshold, minEar, maxEar, avgEar)
    }
    // FatigueDetectionListener 實現
    override fun onFatigueDetected(result: FatigueDetectionResult) {
        Log.d(TAG, "檢測到疲勞: ${result.fatigueLevel}")
    }
    override fun onFatigueLevelChanged(level: FatigueLevel) {
        Log.d(TAG, "疲劳级别变化: $level")
    }
    override fun onBlink() {
        uiCallback.onBlink()
    }
}
