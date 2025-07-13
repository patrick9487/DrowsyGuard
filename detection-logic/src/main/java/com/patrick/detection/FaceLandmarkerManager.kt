package com.patrick.detection

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.patrick.core.Constants

object FaceLandmarkerManager {
    // 單例 FaceLandmarker
    @Volatile
    private var faceLandmarker: FaceLandmarker? = null
    private var lastUsedTimestamp: Long = 0L
    private const val IDLE_TIMEOUT_MS = 10_000L // 10 秒
    private val handler = Handler(Looper.getMainLooper())
    private var idleCheckRunnable: Runnable? = null

    /**
     * 取得 FaceLandmarker 實例，若不存在則初始化。
     * 每次呼叫都會更新最後使用時間。
     */
    @Synchronized
    fun get(context: Context): FaceLandmarker {
        lastUsedTimestamp = System.currentTimeMillis()
        if (faceLandmarker == null) {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(Constants.FACE_LANDMARKER_MODEL_PATH)
                .build()
            val options = FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .build()
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            scheduleIdleCheck()
        }
        return faceLandmarker!!
    }

    /**
     * 創建支持實時處理的 FaceLandmarker
     * 使用 IMAGE 模式進行逐幀處理
     */
    @Synchronized
    fun createForRealTime(context: Context): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setDelegate(Delegate.CPU)
            .setModelAssetPath(Constants.FACE_LANDMARKER_MODEL_PATH)
            .build()
        val options = FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    /**
     * 立即釋放模型資源。
     */
    @Synchronized
    fun release() {
        faceLandmarker?.close()
        faceLandmarker = null
        idleCheckRunnable?.let { handler.removeCallbacks(it) }
        idleCheckRunnable = null
    }

    /**
     * 若超過閒置時間自動釋放，可在 onPause() 呼叫。
     */
    @Synchronized
    fun maybeReleaseIfIdle() {
        val idleTime = System.currentTimeMillis() - lastUsedTimestamp
        if (idleTime >= IDLE_TIMEOUT_MS) {
            release()
        }
    }

    /**
     * App 關閉時釋放所有資源。
     */
    @Synchronized
    fun releaseOnAppExit() {
        release()
    }

    private fun scheduleIdleCheck() {
        idleCheckRunnable?.let { handler.removeCallbacks(it) }
        idleCheckRunnable = Runnable {
            maybeReleaseIfIdle()
            // 若尚未釋放，繼續排程
            if (faceLandmarker != null) {
                handler.postDelayed(idleCheckRunnable!!, IDLE_TIMEOUT_MS)
            }
        }
        handler.postDelayed(idleCheckRunnable!!, IDLE_TIMEOUT_MS)
    }
} 