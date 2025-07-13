package com.patrick.detection

import android.content.Context
import android.util.Log
import com.patrick.detection.FatigueDetector
import com.patrick.core.FatigueLevel
import com.patrick.core.FatigueDetectionResult
import com.patrick.core.FatigueEvent
import com.patrick.core.FatigueDetectionListener
import com.patrick.alert.FatigueAlertManager
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.patrick.core.FatigueUiCallback

/**
 * 疲劳检测管理器
 * 整合疲劳检测、警报和UI更新功能
 */
class FatigueDetectionManager(
    private val context: Context,
    private val uiCallback: FatigueUiCallback
) : FatigueDetectionListener {
    
    companion object {
        private const val TAG = "FatigueDetectionManager"
    }
    
    private val fatigueDetector = FatigueDetector()
    private val alertManager = FatigueAlertManager(context)
    
    // 疲劳检测状态
    private var isDetectionActive = false
    private var currentFatigueLevel = FatigueLevel.NORMAL
    
    init {
        // 设置疲劳检测监听器
        fatigueDetector.setFatigueListener(this)
        
        // 设置默认检测参数
        fatigueDetector.setDetectionParameters(
            earThreshold = com.patrick.core.Constants.FatigueDetection.DEFAULT_EAR_THRESHOLD,
            marThreshold = com.patrick.core.Constants.FatigueDetection.DEFAULT_MAR_THRESHOLD,
            fatigueEventThreshold = com.patrick.core.Constants.FatigueDetection.FATIGUE_EVENT_THRESHOLD
        )
        
        // 設置疲勞對話框回調
        alertManager.setDialogCallback(object : com.patrick.alert.FatigueAlertManager.FatigueDialogCallback {
            override fun onUserAcknowledged() {
                Log.d(TAG, "使用者確認已清醒，重置疲勞檢測狀態")
                // 重置疲勞檢測狀態
                fatigueDetector.reset()
                currentFatigueLevel = com.patrick.core.FatigueLevel.NORMAL
                // 通知 UI 回調
                uiCallback?.onUserAcknowledged()
            }
            
            override fun onUserRequestedRest() {
                Log.d(TAG, "使用者要求休息")
                // 通知 UI 回調
                uiCallback?.onUserRequestedRest()
            }
        })

        if (uiCallback is com.patrick.alert.FatigueAlertManager.ModerateFatigueCallback) {
            alertManager.setModerateFatigueCallback(uiCallback)
        }
        alertManager.setUiCallback(uiCallback)
    }
    
    /**
     * 处理面部特征点检测结果
     */
    fun processFaceLandmarks(result: FaceLandmarkerResult) {
        Log.d(TAG, "[FatigueDetectionManager] processFaceLandmarks called, isDetectionActive=$isDetectionActive")
        if (!isDetectionActive) return
        
        try {
            // 使用疲劳检测器处理结果
            val fatigueResult = fatigueDetector.processFaceLandmarks(result)
            Log.d(TAG, "[FatigueDetectionManager] fatigueDetector.processFaceLandmarks returned, fatigueLevel=${fatigueResult.fatigueLevel}")
            
            // 更新UI
            updateUI(fatigueResult)
            
            // 处理警报
            handleAlerts(fatigueResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "处理疲劳检测时发生错误", e)
        }
    }
    
    /**
     * 更新UI显示
     */
    private fun updateUI(result: FatigueDetectionResult) {
        // 可選：如需其他 UI 更新可在此處理
        if (result.fatigueLevel != currentFatigueLevel) {
            Log.d(TAG, "疲劳级别变化: $currentFatigueLevel -> ${result.fatigueLevel}")
            currentFatigueLevel = result.fatigueLevel
        }
    }
    
    /**
     * 处理警报
     */
    private fun handleAlerts(result: FatigueDetectionResult) {
        if (result.isFatigueDetected) {
            alertManager.handleFatigueDetection(result)
        }
    }
    
    /**
     * 启动疲劳检测
     */
    fun startDetection() {
        isDetectionActive = true
        // 不要重置校正狀態，只重置疲勞事件計數
        fatigueDetector.resetFatigueEvents()
        Log.d(TAG, "疲劳检测已启动")
    }
    
    /**
     * 停止疲劳检测
     */
    fun stopDetection() {
        isDetectionActive = false
        fatigueDetector.reset()
        alertManager.stopAllAlerts()
        Log.d(TAG, "疲劳检测已停止")
    }
    
    /**
     * 设置疲劳检测参数
     */
    fun setDetectionParameters(
        earThreshold: Float? = null,
        marThreshold: Float? = null,
        fatigueEventThreshold: Int? = null
    ) {
        fatigueDetector.setDetectionParameters(
            earThreshold = earThreshold ?: com.patrick.core.Constants.FatigueDetection.DEFAULT_EAR_THRESHOLD,
            marThreshold = marThreshold ?: com.patrick.core.Constants.FatigueDetection.DEFAULT_MAR_THRESHOLD,
            fatigueEventThreshold = fatigueEventThreshold ?: com.patrick.core.Constants.FatigueDetection.FATIGUE_EVENT_THRESHOLD
        )
        
        Log.d(TAG, "疲劳检测参数已更新: EAR=$earThreshold, MAR=$marThreshold, 阈值=$fatigueEventThreshold")
    }
    
    /**
     * 获取当前疲劳事件计数
     */
    fun getFatigueEventCount(): Int {
        return fatigueDetector.getFatigueEventCount()
    }
    
    /**
     * 获取当前疲劳级别
     */
    fun getCurrentFatigueLevel(): FatigueLevel {
        return currentFatigueLevel
    }
    
    /**
     * 重置疲劳检测器
     */
    fun reset() {
        fatigueDetector.reset()
        alertManager.stopAllAlerts()
        currentFatigueLevel = FatigueLevel.NORMAL
        Log.d(TAG, "疲劳检测器已重置")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopDetection()
        alertManager.cleanup()
        Log.d(TAG, "疲劳检测管理器已清理")
    }
    
    // FatigueDetectionListener 实现
    
    override fun onFatigueDetected(result: FatigueDetectionResult) {
        Log.d(TAG, "检测到疲劳: ${result.fatigueLevel}, 事件数: ${result.events.size}")
        if (result.isFatigueDetected) {
            if (result.fatigueLevel == FatigueLevel.MODERATE) {
                uiCallback.onModerateFatigue()
            }
            // SEVERE 狀態可根據需求呼叫其他 callback
        }
    }
    
    override fun onFatigueLevelChanged(level: FatigueLevel) {
        Log.d(TAG, "疲劳级别变化: $level")
        
        // 这里可以添加疲劳级别变化的处理逻辑
    }

    override fun onBlink() {
        uiCallback?.onBlink()
    }

    // 新增：取得最近 windowMs 毫秒內的眨眼次數
    fun getRecentBlinkCount(windowMs: Long): Int {
        return fatigueDetector.getRecentBlinkCount(windowMs)
    }
    
    /**
     * 開始校正
     */
    fun startCalibration() {
        Log.d(TAG, "開始校正流程")
        fatigueDetector.startCalibration()
    }
    
    /**
     * 停止校正
     */
    fun stopCalibration() {
        Log.d(TAG, "停止校正流程")
        fatigueDetector.stopCalibration()
    }
    
    /**
     * 檢查是否正在校正
     */
    fun isCalibrating(): Boolean {
        return fatigueDetector.isCalibrating()
    }
    
    /**
     * 獲取校正進度
     */
    fun getCalibrationProgress(): Int {
        return fatigueDetector.getCalibrationProgress()
    }
    
    // 校正相關回調實現
    override fun onCalibrationStarted() {
        Log.d(TAG, "校正已開始")
        uiCallback?.onCalibrationStarted()
    }
    
    override fun onCalibrationProgress(progress: Int, currentEar: Float) {
        Log.d(TAG, "[FatigueDetectionManager] onCalibrationProgress: progress=$progress, currentEar=$currentEar")
        // 減少 log 輸出頻率
        // Log.d(TAG, "校正進度: $progress%, EAR: $currentEar")
        uiCallback?.onCalibrationProgress(progress, currentEar)
    }
    
    override fun onCalibrationCompleted(newThreshold: Float, minEar: Float, maxEar: Float, avgEar: Float) {
        Log.d(TAG, "校正完成！新閾值: $newThreshold")
        uiCallback?.onCalibrationCompleted(newThreshold, minEar, maxEar, avgEar)
    }

    fun clearModerateFatigue() {
        alertManager.onModerateFatigueCleared()
    }
} 