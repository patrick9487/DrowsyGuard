package com.patrick.alert

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.patrick.core.FatigueLevel

/**
 * 疲勞警示對話框管理器
 * 負責顯示強制回應的疲勞警示對話框
 */
class FatigueDialogManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FatigueDialogManager"
    }
    
    private var currentDialog: AlertDialog? = null
    private var dialogCallback: FatigueDialogCallback? = null
    
    /**
     * 疲勞對話框回調介面
     */
    interface FatigueDialogCallback {
        fun onUserAcknowledged() // 使用者點擊「我已清醒」
        fun onUserRequestedRest() // 使用者點擊「我會找地方休息」
    }
    
    /**
     * 顯示疲勞警示對話框
     */
    fun showFatigueDialog(fatigueLevel: FatigueLevel, callback: FatigueDialogCallback) {
        // 如果已有對話框顯示中，先關閉
        dismissCurrentDialog()
        
        this.dialogCallback = callback
        
        val title = "疲勞偵測警示"
        val message = "系統偵測到您可能處於疲勞狀態。為了您的安全，請選擇一個行動方案。"
        
        try {
            currentDialog = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert) // 使用系統警告圖示
                .setPositiveButton("✅ 我已清醒") { dialog, _ ->
                    Log.d(TAG, "使用者選擇：我已清醒")
                    dialogCallback?.onUserAcknowledged()
                    dialog.dismiss()
                    currentDialog = null
                }
                .setNegativeButton("🛑 我會找地方休息") { dialog, _ ->
                    Log.d(TAG, "使用者選擇：我會找地方休息")
                    dialogCallback?.onUserRequestedRest()
                    dialog.dismiss()
                    currentDialog = null
                }
                .setCancelable(false) // 防止使用者按返回鍵或點擊背景關閉
                .create()
            
            // 確保對話框顯示在主執行緒
            if (context is Activity) {
                if (!context.isFinishing && !context.isDestroyed) {
                    currentDialog?.show()
                    Log.d(TAG, "疲勞警示對話框已顯示")
                } else {
                    Log.w(TAG, "Activity 已結束，無法顯示對話框")
                }
            } else {
                Log.w(TAG, "Context 不是 Activity，無法顯示對話框")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "顯示疲勞警示對話框失敗", e)
        }
    }
    
    /**
     * 顯示休息提醒
     */
    fun showRestReminder() {
        try {
            val restDialog = MaterialAlertDialogBuilder(context)
                .setTitle("休息提醒")
                .setMessage("請盡快找地方休息，確保您的安全。")
                .setPositiveButton("確定") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()
            
            if (context is Activity) {
                if (!context.isFinishing && !context.isDestroyed) {
                    restDialog.show()
                    Log.d(TAG, "休息提醒對話框已顯示")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "顯示休息提醒對話框失敗", e)
        }
    }
    
    /**
     * 關閉當前對話框
     */
    fun dismissCurrentDialog() {
        currentDialog?.let { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
                Log.d(TAG, "關閉當前疲勞警示對話框")
            }
        }
        currentDialog = null
        dialogCallback = null
    }
    
    /**
     * 檢查是否有對話框正在顯示
     */
    fun isDialogShowing(): Boolean {
        return currentDialog?.isShowing == true
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        dismissCurrentDialog()
    }
} 