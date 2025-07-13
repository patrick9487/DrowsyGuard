package com.patrick.core

/**
 * 疲劳检测结果
 */
data class FatigueDetectionResult(
    val isFatigueDetected: Boolean,
    val fatigueLevel: FatigueLevel,
    val events: List<FatigueEvent>
)

/**
 * 疲劳级别
 */
enum class FatigueLevel {
    NORMAL,     // 正常
    MODERATE,   // 中度疲劳
    SEVERE      // 严重疲劳
}

/**
 * 疲劳事件
 */
sealed class FatigueEvent {
    data class EyeClosure(val duration: Long) : FatigueEvent()
    data class Yawn(val duration: Long) : FatigueEvent()
    data class HighBlinkFrequency(val frequency: Int) : FatigueEvent()
}

/**
 * 疲劳检测监听器
 */
interface FatigueDetectionListener {
    fun onFatigueDetected(result: FatigueDetectionResult)
    fun onFatigueLevelChanged(level: FatigueLevel)
    fun onBlink()
    // 校正相關回調
    fun onCalibrationStarted() {}
    fun onCalibrationProgress(progress: Int, currentEar: Float) {}
    fun onCalibrationCompleted(newThreshold: Float, minEar: Float, maxEar: Float, avgEar: Float) {}
} 