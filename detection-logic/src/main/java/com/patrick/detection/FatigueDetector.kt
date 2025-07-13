package com.patrick.detection

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt
import com.patrick.core.FatigueDetectionResult
import com.patrick.core.FatigueLevel
import com.patrick.core.FatigueEvent
import com.patrick.core.FatigueDetectionListener

/**
 * 疲劳检测器 - 核心疲劳检测逻辑
 * 基于MediaPipe面部特征点检测结果进行疲劳分析
 */
class FatigueDetector {
    
    companion object {
        private const val TAG = "FatigueDetector"
        
        // EAR (Eye Aspect Ratio) 阈值 - 根據實際 EAR 值調整
        const val DEFAULT_EAR_THRESHOLD = 0.20f  // 標準閾值：睜眼 0.28-0.35，閉眼 0.08-0.14，閾值 0.20
        const val DEFAULT_EAR_CLOSURE_DURATION_THRESHOLD = 1500L // 1.5秒
        
        // MAR (Mouth Aspect Ratio) 阈值
        const val DEFAULT_MAR_THRESHOLD = 0.7f
        const val DEFAULT_MAR_DURATION_THRESHOLD = 1000L // 1秒
        
        // 眨眼频率阈值 (每分钟)
        const val DEFAULT_BLINK_FREQUENCY_THRESHOLD = 20 // 每分钟20次以上为异常
        
        // 疲劳事件累积阈值
        const val DEFAULT_FATIGUE_EVENT_THRESHOLD = 3
        
        // 面部特征点索引 (MediaPipe Face Landmarker)
        private object LandmarkIndices {
            // 根據 MediaPipe 官方 468 點模型的眼睛特徵點索引
            // 左眼：362, 385, 387, 263, 373, 380
            val LEFT_EYE = listOf(362, 385, 387, 263, 373, 380)
            // 右眼：33, 160, 158, 133, 153, 144
            val RIGHT_EYE = listOf(33, 160, 158, 133, 153, 144)
            // 嘴巴特征点
            val MOUTH = listOf(61, 84, 17, 314, 405, 320, 307, 375, 321, 308, 324, 318)
        }
    }
    
    // 疲劳检测状态
    private var currentEarThreshold = DEFAULT_EAR_THRESHOLD
    private var currentMarThreshold = DEFAULT_MAR_THRESHOLD
    private var currentFatigueEventThreshold = DEFAULT_FATIGUE_EVENT_THRESHOLD
    
    // 时间跟踪
    private var lastEyeClosureStartTime: Long = 0
    private var lastMouthOpenStartTime: Long = 0
    private var lastBlinkTime: Long = 0
    
    // 事件计数
    private var fatigueEventCount = 0
    private var blinkCount = 0
    private var lastMinuteStartTime: Long = System.currentTimeMillis()
    // 新增：眨眼時間戳記錄
    private val blinkTimestamps = mutableListOf<Long>()
    
    // 状态标志
    private var isEyeClosed = false
    private var isMouthOpen = false
    
    // 校正功能
    private var isCalibrating = false
    private var calibrationStartTime: Long = 0
    private val calibrationEarValues = mutableListOf<Float>()
    private val calibrationDuration = 15000L // 15秒校正時間
    
    // 监听器
    private var fatigueListener: FatigueDetectionListener? = null
    
    /**
     * 处理面部特征点检测结果
     */
    fun processFaceLandmarks(result: FaceLandmarkerResult): FatigueDetectionResult {
        Log.d(TAG, "[FatigueDetector] processFaceLandmarks called, isCalibrating=$isCalibrating, time=${System.currentTimeMillis()}")
        if (result.faceLandmarks().isEmpty()) {
            Log.d(TAG, "[FatigueDetector] processFaceLandmarks: no face detected")
            return FatigueDetectionResult(
                isFatigueDetected = false,
                fatigueLevel = FatigueLevel.NORMAL,
                events = emptyList()
            )
        }
        val faceLandmarks = result.faceLandmarks()[0]
        Log.d(TAG, "[FatigueDetector] landmarks.size=${faceLandmarks.size}")
        val currentTime = System.currentTimeMillis()
        
        // 只在狀態變化時記錄 log
        // Log.d(TAG, "processFaceLandmarks: 開始處理，特徵點數量=${faceLandmarks.size}")
        
        // 校正模式處理
        if (isCalibrating) {
            Log.d(TAG, "[FatigueDetector] handleCalibration called, elapsed=${currentTime - calibrationStartTime}")
            handleCalibration(faceLandmarks, currentTime)
            return FatigueDetectionResult(
                isFatigueDetected = false,
                fatigueLevel = FatigueLevel.NORMAL,
                events = emptyList()
            )
        }
        
        val events = mutableListOf<FatigueEvent>()
        
        // 1. 检测眼睛闭合
        val eyeClosureEvent = detectEyeClosure(faceLandmarks, currentTime)
        eyeClosureEvent?.let { events.add(it) }
        
        // 2. 检测打哈欠
        val yawnEvent = detectYawn(faceLandmarks, currentTime)
        yawnEvent?.let { events.add(it) }
        
        // 3. 检测眨眼频率
        val blinkFrequencyEvent = detectBlinkFrequency(faceLandmarks, currentTime)
        blinkFrequencyEvent?.let { events.add(it) }
        
        // 4. 更新疲劳事件计数
        updateFatigueEventCount(events)
        
        // 5. 确定疲劳级别
        val fatigueLevel = determineFatigueLevel()
        // 只在有事件或疲勞級別變化時記錄
        if (events.isNotEmpty() || fatigueLevel != FatigueLevel.NORMAL) {
            Log.d(TAG, "processFaceLandmarks: 疲勞級別=$fatigueLevel，事件數量=${events.size}")
        }
        
        return FatigueDetectionResult(
            isFatigueDetected = fatigueLevel != FatigueLevel.NORMAL,
            fatigueLevel = fatigueLevel,
            events = events
        )
    }
    
    /**
     * 检测眼睛闭合
     */
    private fun detectEyeClosure(landmarks: List<NormalizedLandmark>, currentTime: Long): FatigueEvent? {
        val leftEar = calculateEAR(landmarks, LandmarkIndices.LEFT_EYE)
        val rightEar = calculateEAR(landmarks, LandmarkIndices.RIGHT_EYE)
        val averageEar = (leftEar + rightEar) / 2f
        
        // 更靈敏的眨眼偵測：任一眼睛閉合就觸發
        val leftEyeClosed = leftEar < currentEarThreshold
        val rightEyeClosed = rightEar < currentEarThreshold
        val anyEyeClosed = leftEyeClosed || rightEyeClosed
        
        // 只在狀態變化時記錄詳細 log
        // Log.d(TAG, "detectEyeClosure: leftEar=$leftEar, rightEar=$rightEar, averageEar=$averageEar")
        // Log.d(TAG, "detectEyeClosure: threshold=$currentEarThreshold, isEyeClosed=$isEyeClosed")
        // Log.d(TAG, "detectEyeClosure: leftEyeClosed=$leftEyeClosed, rightEyeClosed=$rightEyeClosed, anyEyeClosed=$anyEyeClosed")
        
        return when {
            anyEyeClosed && !isEyeClosed -> {
                Log.d(TAG, "眼睛開始閉合 (左眼=$leftEyeClosed, 右眼=$rightEyeClosed)")
                isEyeClosed = true
                lastEyeClosureStartTime = currentTime
                null
            }
            anyEyeClosed && isEyeClosed -> {
                val closureDuration = currentTime - lastEyeClosureStartTime
                if (closureDuration >= DEFAULT_EAR_CLOSURE_DURATION_THRESHOLD) {
                    isEyeClosed = false
                    Log.d(TAG, "眼睛閉合超時: ${closureDuration}ms")
                    FatigueEvent.EyeClosure(closureDuration)
                } else {
                    null
                }
            }
            !anyEyeClosed && isEyeClosed -> {
                val closureDuration = currentTime - lastEyeClosureStartTime
                isEyeClosed = false
                if (closureDuration >= DEFAULT_EAR_CLOSURE_DURATION_THRESHOLD) {
                    Log.d(TAG, "眼睛閉合超時: ${closureDuration}ms")
                    FatigueEvent.EyeClosure(closureDuration)
                } else {
                    Log.d(TAG, "眨眼檢測: ${closureDuration}ms")
                    detectBlink(currentTime)
                    null
                }
            }
            else -> {
                null
            }
        }
    }
    
    /**
     * 检测打哈欠
     */
    private fun detectYawn(landmarks: List<NormalizedLandmark>, currentTime: Long): FatigueEvent? {
        val mar = calculateMAR(landmarks, LandmarkIndices.MOUTH)
        
        return when {
            mar > currentMarThreshold && !isMouthOpen -> {
                // 嘴巴开始张开
                isMouthOpen = true
                lastMouthOpenStartTime = currentTime
                null
            }
            mar > currentMarThreshold && isMouthOpen -> {
                // 嘴巴持续张开
                val openDuration = currentTime - lastMouthOpenStartTime
                if (openDuration >= DEFAULT_MAR_DURATION_THRESHOLD) {
                    isMouthOpen = false
                    FatigueEvent.Yawn(openDuration)
                } else {
                    null
                }
            }
            mar <= currentMarThreshold && isMouthOpen -> {
                // 嘴巴闭合
                isMouthOpen = false
                val openDuration = currentTime - lastMouthOpenStartTime
                if (openDuration >= DEFAULT_MAR_DURATION_THRESHOLD) {
                    FatigueEvent.Yawn(openDuration)
                } else {
                    null
                }
            }
            else -> null
        }
    }
    
    /**
     * 检测眨眼频率
     */
    private fun detectBlinkFrequency(landmarks: List<NormalizedLandmark>, currentTime: Long): FatigueEvent? {
        // 检查是否超过1分钟，重置计数
        if (currentTime - lastMinuteStartTime >= 60000) {
            if (blinkCount > DEFAULT_BLINK_FREQUENCY_THRESHOLD) {
                val event = FatigueEvent.HighBlinkFrequency(blinkCount)
                blinkCount = 0
                lastMinuteStartTime = currentTime
                return event
            }
            blinkCount = 0
            lastMinuteStartTime = currentTime
        }
        return null
    }
    
    /**
     * 检测眨眼
     */
    private fun detectBlink(currentTime: Long) {
        // 避免重复计数（眨眼间隔至少200ms）
        val timeSinceLastBlink = currentTime - lastBlinkTime
        
        if (timeSinceLastBlink > 200) {
            blinkCount++
            lastBlinkTime = currentTime
            blinkTimestamps.add(currentTime) // 新增：記錄眨眼時間
            Log.d(TAG, "眨眼檢測成功！總次數=$blinkCount")
            fatigueListener?.onBlink()
        }
        // 移除跳過眨眼的 log，減少噪音
    }
    
    /**
     * 计算EAR (Eye Aspect Ratio)
     * 根据 MediaPipe 官方文档，使用6个关键点计算眼睛纵横比
     * p1: 上眼睑外角, p2: 上眼睑内角, p3: 上眼睑中点
     * p4: 下眼睑外角, p5: 下眼睑内角, p6: 下眼睑中点
     * EAR = (A + B) / (2.0 * C)
     * 其中 A = |p2-p6|, B = |p3-p5|, C = |p1-p4|
     */
    private fun calculateEAR(landmarks: List<NormalizedLandmark>, eyeIndices: List<Int>): Float {
        if (eyeIndices.size < 6) {
            Log.w(TAG, "calculateEAR: 特徵點數量不足，需要6個，實際只有${eyeIndices.size}個")
            return 0f
        }
        try {
            val points = eyeIndices.map { idx ->
                if (idx < landmarks.size) landmarks[idx] else null
            }
            if (points.any { it == null }) {
                Log.w(TAG, "calculateEAR: landmark index超出範圍，eyeIndices=$eyeIndices, landmarks.size=${landmarks.size}")
                return 0f
            }
            // log 六個點的座標
            points.forEachIndexed { i, p ->
                Log.d(TAG, "EAR landmark[$i] idx=${eyeIndices[i]} x=${p!!.x()} y=${p.y()}")
            }
            val p1 = points[0]!!
            val p2 = points[1]!!
            val p3 = points[2]!!
            val p4 = points[3]!!
            val p5 = points[4]!!
            val p6 = points[5]!!
            val A = euclideanDistance(p2, p6)
            val B = euclideanDistance(p3, p5)
            val C = euclideanDistance(p1, p4)
            val ear = (A + B) / (2.0f * C)
            if (ear > 2.0f || ear < 0.1f) {
                Log.w(TAG, "EAR 值異常: $ear")
            }
            return ear
        } catch (e: Exception) {
            Log.e(TAG, "calculateEAR: 計算過程中發生錯誤", e)
            return 0f
        }
    }
    
    /**
     * 计算MAR (Mouth Aspect Ratio)
     */
    private fun calculateMAR(landmarks: List<NormalizedLandmark>, mouthIndices: List<Int>): Float {
        if (mouthIndices.size < 6) return 0f
        
        // 使用6个关键点计算MAR
        val p1 = landmarks[mouthIndices[0]]
        val p2 = landmarks[mouthIndices[1]]
        val p3 = landmarks[mouthIndices[2]]
        val p4 = landmarks[mouthIndices[3]]
        val p5 = landmarks[mouthIndices[4]]
        val p6 = landmarks[mouthIndices[5]]
        
        val A = euclideanDistance(p2, p6)
        val B = euclideanDistance(p3, p5)
        val C = euclideanDistance(p1, p4)
        
        return (A + B) / (2.0f * C)
    }
    
    /**
     * 计算欧几里得距离
     */
    private fun euclideanDistance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * 測試不同的眼睛特徵點索引組合
     */
    private fun testDifferentEyeIndices(landmarks: List<NormalizedLandmark>) {
        // 測試官方 MediaPipe 眼睛特徵點索引組合
        val testIndices = listOf(
            "官方 MediaPipe 索引" to (LandmarkIndices.LEFT_EYE to LandmarkIndices.RIGHT_EYE),
            "舊索引組合" to (listOf(362, 382, 381, 380, 374, 373) to listOf(33, 7, 163, 144, 145, 153))
        )
        
        testIndices.forEach { (name, indices) ->
            val leftEar = calculateEAR(landmarks, indices.first)
            val rightEar = calculateEAR(landmarks, indices.second)
            Log.d(TAG, "testDifferentEyeIndices: $name - 左眼EAR=$leftEar, 右眼EAR=$rightEar")
        }
    }
    
    /**
     * 更新疲劳事件计数
     */
    private fun updateFatigueEventCount(events: List<FatigueEvent>) {
        events.forEach { event ->
            when (event) {
                is FatigueEvent.EyeClosure -> {
                    fatigueEventCount++
                    Log.d(TAG, "检测到眼睛闭合事件，持续时间: ${event.duration}ms")
                }
                is FatigueEvent.Yawn -> {
                    fatigueEventCount++
                    Log.d(TAG, "检测到打哈欠事件，持续时间: ${event.duration}ms")
                }
                is FatigueEvent.HighBlinkFrequency -> {
                    fatigueEventCount++
                    Log.d(TAG, "检测到高频眨眼事件，频率: ${event.frequency}/分钟")
                }
            }
        }
    }
    
    /**
     * 确定疲劳级别
     */
    private fun determineFatigueLevel(): FatigueLevel {
        return when {
            fatigueEventCount >= currentFatigueEventThreshold * 2 -> FatigueLevel.SEVERE
            fatigueEventCount >= currentFatigueEventThreshold -> FatigueLevel.MODERATE
            else -> FatigueLevel.NORMAL
        }
    }
    
    /**
     * 重置疲劳检测器状态
     */
    fun reset() {
        fatigueEventCount = 0
        blinkCount = 0
        isEyeClosed = false
        isMouthOpen = false
        lastEyeClosureStartTime = 0
        lastMouthOpenStartTime = 0
        lastBlinkTime = 0
        lastMinuteStartTime = System.currentTimeMillis()
        blinkTimestamps.clear() // 新增：清空
        stopCalibration() // 停止校正
    }
    
    /**
     * 重置疲勞事件計數（不影響校正狀態）
     */
    fun resetFatigueEvents() {
        fatigueEventCount = 0
        blinkCount = 0
        isEyeClosed = false
        isMouthOpen = false
        lastEyeClosureStartTime = 0
        lastMouthOpenStartTime = 0
        lastBlinkTime = 0
        lastMinuteStartTime = System.currentTimeMillis()
        blinkTimestamps.clear()
        Log.d(TAG, "疲勞事件計數已重置")
    }
    
    /**
     * 设置疲劳检测参数
     */
    fun setDetectionParameters(
        earThreshold: Float = currentEarThreshold,
        marThreshold: Float = currentMarThreshold,
        fatigueEventThreshold: Int = currentFatigueEventThreshold
    ) {
        currentEarThreshold = earThreshold
        currentMarThreshold = marThreshold
        currentFatigueEventThreshold = fatigueEventThreshold
    }
    
    /**
     * 设置疲劳检测监听器
     */
    fun setFatigueListener(listener: FatigueDetectionListener) {
        this.fatigueListener = listener
    }
    
    /**
     * 获取当前疲劳事件计数
     */
    fun getFatigueEventCount(): Int = fatigueEventCount

    // 新增：取得最近 windowMs 毫秒內的眨眼次數
    fun getRecentBlinkCount(windowMs: Long): Int {
        val now = System.currentTimeMillis()
        blinkTimestamps.removeAll { now - it > windowMs }
        return blinkTimestamps.size
    }
    
    /**
     * 開始校正
     */
    fun startCalibration() {
        isCalibrating = true
        calibrationStartTime = System.currentTimeMillis()
        calibrationEarValues.clear()
        Log.d(TAG, "開始校正，持續時間：${calibrationDuration}ms, time=${calibrationStartTime}")
        fatigueListener?.onCalibrationStarted()
    }
    
    /**
     * 停止校正
     */
    fun stopCalibration() {
        isCalibrating = false
        calibrationEarValues.clear()
        Log.d(TAG, "校正已停止")
    }
    
    /**
     * 處理校正過程
     */
    private fun handleCalibration(landmarks: List<NormalizedLandmark>, currentTime: Long) {
        val elapsedTime = currentTime - calibrationStartTime
        Log.d(TAG, "[FatigueDetector] handleCalibration: elapsedTime=$elapsedTime, calibrationEarValues.size=${calibrationEarValues.size}")
        if (elapsedTime >= calibrationDuration) {
            Log.d(TAG, "[FatigueDetector] handleCalibration: elapsedTime >= calibrationDuration, finishCalibration")
            finishCalibration()
            return
        }
        
        // 計算當前 EAR 值並記錄
        val leftEar = calculateEAR(landmarks, LandmarkIndices.LEFT_EYE)
        val rightEar = calculateEAR(landmarks, LandmarkIndices.RIGHT_EYE)
        val averageEar = (leftEar + rightEar) / 2f
        
        Log.d(TAG, "[FatigueDetector] handleCalibration: progress=${(elapsedTime * 100 / calibrationDuration).toInt()}, averageEar=$averageEar")
        calibrationEarValues.add(averageEar)
        
        // 通知校正進度
        val progress = (elapsedTime * 100 / calibrationDuration).toInt()
        fatigueListener?.onCalibrationProgress(progress, averageEar)
        
        // 每 5 秒記錄一次進度，減少 log 噪音
        if (progress % 20 == 0 && progress > 0) {
            Log.d(TAG, "[FatigueDetector] 校正進度: ${progress}%")
        }
    }
    
    /**
     * 完成校正並計算新閾值
     */
    private fun finishCalibration() {
        Log.d(TAG, "finishCalibration called, calibrationEarValues.size=${calibrationEarValues.size}")
        if (calibrationEarValues.isEmpty()) {
            Log.w(TAG, "校正數據為空，使用默認閾值")
            stopCalibration()
            return
        }
        
        // 計算 EAR 值的統計數據
        val sortedValues = calibrationEarValues.sorted()
        val minEar = sortedValues.first()
        val maxEar = sortedValues.last()
        val avgEar = calibrationEarValues.average().toFloat()
        
        // 計算新的閾值：使用平均值的一定比例作為閾值
        val newThreshold = avgEar * 0.7f // 使用平均值的 70% 作為閾值
        
        // 更新閾值
        currentEarThreshold = newThreshold
        
        Log.d(TAG, "校正完成！")
        Log.d(TAG, "EAR 統計: 最小值=$minEar, 最大值=$maxEar, 平均值=$avgEar")
        Log.d(TAG, "新閾值: $newThreshold (原閾值: ${DEFAULT_EAR_THRESHOLD})")
        
        // 通知校正完成
        fatigueListener?.onCalibrationCompleted(newThreshold, minEar, maxEar, avgEar)
        
        // 停止校正
        stopCalibration()
    }
    
    /**
     * 獲取校正狀態
     */
    fun isCalibrating(): Boolean = isCalibrating
    
    /**
     * 獲取校正進度
     */
    fun getCalibrationProgress(): Int {
        if (!isCalibrating) return 0
        val elapsedTime = System.currentTimeMillis() - calibrationStartTime
        return (elapsedTime * 100 / calibrationDuration).toInt().coerceIn(0, 100)
    }
} 