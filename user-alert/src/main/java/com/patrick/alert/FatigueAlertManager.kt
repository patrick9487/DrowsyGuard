package com.patrick.alert

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.patrick.core.FatigueLevel
import com.patrick.core.FatigueDetectionResult
import com.patrick.core.FatigueEvent
import com.patrick.core.FatigueUiCallback

/**
 * 疲劳提醒管理器
 * 负责处理声音、视觉警报和对话框
 */
class FatigueAlertManager(private val context: Context) : FatigueDialogManager.FatigueDialogCallback {
    
    companion object {
        private const val TAG = "FatigueAlertManager"
        
        // 警报配置
        const val ALERT_DURATION_MS = 3000L // 警报显示3秒
        const val SOUND_ALERT_DURATION_MS = 2000L // 声音警报2秒
        const val VIBRATION_DURATION_MS = 500L // 震动0.5秒
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var alertHandler = Handler(Looper.getMainLooper())
    private var isAlertActive = false
    private val dialogManager = FatigueDialogManager(context)
    private var dialogCallback: FatigueDialogCallback? = null
    private var moderateFatigueCallback: ModerateFatigueCallback? = null
    private var uiCallback: FatigueUiCallback? = null

    /**
     * 疲勞對話框回調介面
     */
    interface FatigueDialogCallback {
        fun onUserAcknowledged() // 使用者點擊「我已清醒」
        fun onUserRequestedRest() // 使用者點擊「我會找地方休息」
    }

    interface ModerateFatigueCallback {
        fun onModerateFatigueCleared()
    }
    
    // 警报文本
    private val alertMessages = mapOf(
        FatigueLevel.MODERATE to "⚠️ 檢測到疲勞跡象，請注意休息！",
        FatigueLevel.SEVERE to "🚨 嚴重疲勞警告！請立即停止駕駛或工作！"
    )
    
    /**
     * 处理疲劳检测结果并触发相应警报
     */
    fun handleFatigueDetection(result: FatigueDetectionResult) {
        if (!result.isFatigueDetected) {
            return
        }
        
        Log.d(TAG, "检测到疲劳，级别: ${result.fatigueLevel}")
        
        when (result.fatigueLevel) {
            FatigueLevel.MODERATE -> {
                triggerModerateFatigueAlert(result.events)
            }
            FatigueLevel.SEVERE -> {
                triggerSevereFatigueAlert(result.events)
            }
            else -> {
                // 正常状态，不触发警报
            }
        }
    }
    
    /**
     * 設置對話框回調
     */
    fun setDialogCallback(callback: FatigueDialogCallback) {
        this.dialogCallback = callback
    }

    fun setModerateFatigueCallback(callback: ModerateFatigueCallback) {
        this.moderateFatigueCallback = callback
    }

    fun setUiCallback(callback: FatigueUiCallback) {
        this.uiCallback = callback
    }
    
    /**
     * 触发中度疲劳警报
     */
    private fun triggerModerateFatigueAlert(events: List<FatigueEvent>) {
        if (isAlertActive) return
        
        isAlertActive = true
        
        // 播放警告声音
        playWarningSound()
        
        // 显示Toast消息
        uiCallback?.onModerateFatigue()
        
        // 震动提醒
        triggerVibration()
        
        // 3秒后重置警报状态
        alertHandler.postDelayed({
            isAlertActive = false
        }, ALERT_DURATION_MS)
    }
    
    /**
     * 触发严重疲劳警报
     */
    private fun triggerSevereFatigueAlert(events: List<FatigueEvent>) {
        if (isAlertActive) return
        
        isAlertActive = true
        
        // 播放紧急警告声音
        playEmergencySound()
        
        // 显示紧急Toast消息
        // showToastMessage(alertMessages[FatigueLevel.SEVERE] ?: "") // 移除此行
        
        // 强烈震动提醒
        triggerStrongVibration()
        
        // 顯示疲勞警示對話框
        dialogManager.showFatigueDialog(FatigueLevel.SEVERE, this)
        
        // 5秒后重置警报状态
        alertHandler.postDelayed({
            isAlertActive = false
        }, ALERT_DURATION_MS * 2)
    }
    
    /**
     * 播放警告声音
     */
    private fun playWarningSound() {
        try {
            // 释放之前的MediaPlayer
            releaseMediaPlayer()
            
            // 创建新的MediaPlayer
            val soundResourceId = getWarningSoundResource()
            if (soundResourceId == 0) {
                Log.w(TAG, "警告音效資源未找到")
                return
            }
            
            mediaPlayer = MediaPlayer.create(context, soundResourceId)
            mediaPlayer?.let { player ->
                player.isLooping = false
                player.setOnCompletionListener {
                    releaseMediaPlayer()
                }
                player.start()
                
                // 2秒后停止声音
                alertHandler.postDelayed({
                    if (player.isPlaying) {
                        player.stop()
                    }
                }, SOUND_ALERT_DURATION_MS)
            } ?: run {
                Log.w(TAG, "MediaPlayer 創建失敗")
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放警告声音失败", e)
        }
    }
    
    /**
     * 播放紧急警告声音
     */
    private fun playEmergencySound() {
        try {
            // 释放之前的MediaPlayer
            releaseMediaPlayer()
            
            // 创建新的MediaPlayer
            val soundResourceId = getEmergencySoundResource()
            if (soundResourceId == 0) {
                Log.w(TAG, "緊急音效資源未找到")
                return
            }
            
            mediaPlayer = MediaPlayer.create(context, soundResourceId)
            mediaPlayer?.let { player ->
                player.isLooping = true // 紧急声音循环播放
                player.setOnCompletionListener {
                    releaseMediaPlayer()
                }
                player.start()
                
                // 3秒后停止声音
                alertHandler.postDelayed({
                    if (player.isPlaying) {
                        player.stop()
                    }
                }, (SOUND_ALERT_DURATION_MS * 1.5).toLong())
            } ?: run {
                Log.w(TAG, "MediaPlayer 創建失敗")
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放紧急警告声音失败", e)
        }
    }
    
    /**
     * 显示Toast消息
     */
    private fun showToastMessage(message: String) {
        alertHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 触发震动提醒
     */
    private fun triggerVibration() {
        try {
            // 檢查震動權限
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.VIBRATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "缺少震動權限")
                    return
                }
            }
            
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = android.os.VibrationEffect.createOneShot(
                    VIBRATION_DURATION_MS,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "触发震动失败", e)
        }
    }
    
    /**
     * 触发强烈震动提醒
     */
    private fun triggerStrongVibration() {
        try {
            // 檢查震動權限
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.VIBRATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "缺少震動權限")
                    return
                }
            }
            
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // 创建震动模式：震动500ms，暂停200ms，再震动500ms
                val pattern = longArrayOf(0, 500, 200, 500)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                val effect = android.os.VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "触发强烈震动失败", e)
        }
    }
    
    /**
     * 在TextView上显示警报消息
     */
    fun showAlertOnTextView(textView: TextView, result: FatigueDetectionResult) {
        if (!result.isFatigueDetected) {
            textView.visibility = View.GONE
            return
        }
        
        val message = alertMessages[result.fatigueLevel] ?: ""
        alertHandler.post {
            textView.text = message
            textView.visibility = View.VISIBLE
            
            // 设置文本颜色
            textView.setTextColor(when (result.fatigueLevel) {
                FatigueLevel.MODERATE -> android.graphics.Color.parseColor("#FFA500") // 橙色
                FatigueLevel.SEVERE -> android.graphics.Color.parseColor("#FF0000")   // 红色
                else -> android.graphics.Color.BLACK
            })
        }
        
        // 3秒后隐藏消息
        alertHandler.postDelayed({
            textView.visibility = View.GONE
        }, ALERT_DURATION_MS)
    }
    
    /**
     * 获取警告声音资源ID
     */
    private fun getWarningSoundResource(): Int {
        return context.resources.getIdentifier("warning", "raw", "com.patrick.drowsyguard")
    }
    
    /**
     * 获取紧急警告声音资源ID
     */
    private fun getEmergencySoundResource(): Int {
        return context.resources.getIdentifier("emergency", "raw", "com.patrick.drowsyguard")
    }
    
    /**
     * 释放MediaPlayer资源
     */
    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
    }
    
    /**
     * 停止所有警报
     */
    fun stopAllAlerts() {
        isAlertActive = false
        releaseMediaPlayer()
        alertHandler.removeCallbacksAndMessages(null)
    }

    fun onModerateFatigueCleared() {
        releaseMediaPlayer()
        isAlertActive = false
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopAllAlerts()
        dialogManager.cleanup()
    }
    
    // FatigueDialogManager.FatigueDialogCallback 實現
    
    override fun onUserAcknowledged() {
        Log.d(TAG, "使用者確認已清醒")
        // 停止所有警報
        stopAllAlerts()
        // 通知外部回調
        dialogCallback?.onUserAcknowledged()
    }
    
    override fun onUserRequestedRest() {
        Log.d(TAG, "使用者要求休息")
        // 停止所有警報
        stopAllAlerts()
        // 顯示休息提醒
        dialogManager.showRestReminder()
        // 通知外部回調
        dialogCallback?.onUserRequestedRest()
    }
} 