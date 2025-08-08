package com.patrick.core

/**
 * 疲勞對話框回調介面
 * 定義使用者對疲勞警報的回應
 */
interface FatigueDialogCallback {
    /**
     * 使用者確認已清醒
     */
    fun onUserAcknowledged()
    
    /**
     * 使用者要求休息
     */
    fun onUserRequestedRest()
}



