package com.patrick.alert

import android.util.Log
import android.widget.TextView
import android.content.Context
import com.patrick.core.AsyncTaskManager
import com.patrick.core.FatigueDetectionResult
import com.patrick.core.FatigueLevel
import com.patrick.core.FatigueUiCallback
import com.patrick.core.PerformanceMonitor
import com.patrick.core.FatigueDialogCallback

/**
 * 疲勞提醒管理器
 * 只負責分發警告事件，不再直接操作 Dialog 或持有 Context
 */
class FatigueAlertManager(private val context: Context) {
    companion object {
        private const val TAG = "FatigueAlertManager"
    }

    private val performanceMonitor = PerformanceMonitor.getInstance(context)

    private var uiCallback: FatigueUiCallback? = null
    private var dialogCallback: FatigueDialogCallback? = null

    fun setUiCallback(callback: FatigueUiCallback) {
        this.uiCallback = callback
    }

    fun setDialogCallback(callback: FatigueDialogCallback) {
        this.dialogCallback = callback
    }

    fun onUserAcknowledged() {
        dialogCallback?.onUserAcknowledged()
    }

    fun onUserRequestedRest() {
        dialogCallback?.onUserRequestedRest()
    }

    /**
     * 處理疲勞檢測結果並分發事件
     */
    fun handleFatigueDetection(result: FatigueDetectionResult) {
        performanceMonitor.logPerformance(
            "Fatigue detection handled",
            mapOf(
                "fatigueLevel" to result.fatigueLevel.name,
                "isFatigueDetected" to result.isFatigueDetected,
            ),
        )

        if (!result.isFatigueDetected) return

        Log.d(TAG, "檢測到疲勞，級別: ${result.fatigueLevel}")

        when (result.fatigueLevel) {
            FatigueLevel.NOTICE -> uiCallback?.onNoticeFatigue()
            FatigueLevel.WARNING -> uiCallback?.onWarningFatigue()
            else -> {}
        }
    }

    fun showAlertOnTextView(
        @Suppress("UNUSED_PARAMETER") textView: TextView,
        @Suppress("UNUSED_PARAMETER") result: FatigueDetectionResult,
    ) {
        // 保留原有視覺警告顯示
    }

    fun stopAllAlerts() {
        Log.d(TAG, "停止所有警報")
    }

    fun cleanup() {
        stopAllAlerts()
    }
}

