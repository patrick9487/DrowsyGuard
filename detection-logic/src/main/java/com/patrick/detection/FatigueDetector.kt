package com.patrick.detection

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.patrick.core.CalibrationStateManager
import com.patrick.core.FatigueDetectionListener
import com.patrick.core.FatigueDetectionLogger
import com.patrick.core.FatigueDetectionResult
import com.patrick.core.FatigueEvent
import com.patrick.core.FatigueLevel
import kotlin.math.sqrt

/**
 * 疲劳检测器 - 核心疲劳检测逻辑
 * 基于MediaPipe面部特征点检测结果进行疲劳分析
 */
class FatigueDetector(private val context: Context) {
    companion object {
        private const val TAG = "FatigueDetector"

        // 默认阈值
        private const val DEFAULT_EAR_THRESHOLD = 0.15f // 根據實際 EAR 值調整，睜眼約 0.13-0.15，閉眼約 0.07-0.10
        private const val DEFAULT_MAR_THRESHOLD = 0.6f // 降低一點以提高敏感度，但用更嚴格的時間和峰值驗證
        private const val DEFAULT_FATIGUE_EVENT_THRESHOLD = 2 // 調整為 2，符合用戶需求

        // 时间阈值 - 根據用戶需求調整
        private const val DEFAULT_EAR_CLOSURE_DURATION_THRESHOLD = 1500L // 調整為 1.5 秒（警告條件）
        private const val DEFAULT_YAWN_DURATION_THRESHOLD = 2500L // 打哈欠：2.5秒，避免說話誤判
        private const val DEFAULT_YAWN_MIN_DURATION = 1000L // 打哈欠最小時間：1秒
        private const val DEFAULT_BLINK_FREQUENCY_THRESHOLD = 25 // 調整為 25 次/分鐘

        // 眼睛特征点索引 (MediaPipe 官方文档)
        private object LandmarkIndices {
            // 左眼：33, 160, 158, 133, 153, 144
            val LEFT_EYE = listOf(33, 160, 158, 133, 153, 144)

            // 右眼：362, 385, 387, 263, 373, 380
            val RIGHT_EYE = listOf(362, 385, 387, 263, 373, 380)

            val MOUTH = listOf(61, 84, 17, 314, 405, 320, 307, 375, 321, 308, 324, 318)
        }

        // 重置保護期 - 移除，這應該是 UI 層的職責
        const val IS_IN_RESET_PROTECTION = false
        const val IS_IN_COOLDOWN_PERIOD = false
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
    private var yawnCount = 0 // 新增：打哈欠計數
    private var blinkFrequencyWarningCount = 0 // 新增：眨眼頻率警告計數
    private var lastMinuteStartTime: Long = System.currentTimeMillis()

    // 新增：眨眼時間戳記錄
    private val blinkTimestamps = mutableListOf<Long>()

    // 狀態标志
    private var isEyeClosed = false
    private var isMouthOpen = false

    // 校正功能
    private var isCalibrating = false
    private var calibrationStartTime: Long = 0
    private val calibrationEarValues = mutableListOf<Float>()
    private val calibrationDuration = 15000L // 15秒校正時間
    private val calibrationStateManager = CalibrationStateManager(context) // 使用持久化的校正狀態管理器

    // 臉部偵測狀態
    private var isFaceDetected = false
    private var faceDetectionStartTime: Long = 0
    private val faceDetectionDelay = 1000L // 偵測到臉部後延遲1秒開始校正
    
    // 校正容錯機制
    private var lastFaceDetectionTime: Long = 0
    private val faceDetectionTolerance = 3000L // 允許3秒的臉部偵測丟失

    // 监听器
    private var fatigueListener: FatigueDetectionListener? = null

    // 日誌數據收集
    private val earValues = mutableListOf<Float>()
    private val marValues = mutableListOf<Float>()
    private val fatigueEvents = mutableListOf<FatigueEvent>()

    /**
     * 处理面部特征点检测结果
     */
    fun processFaceLandmarks(result: FaceLandmarkerResult): FatigueDetectionResult {
        val startTime = System.currentTimeMillis()
        val currentTime = System.currentTimeMillis()
        val hasFace = result.faceLandmarks().isNotEmpty()

        // 更新臉部偵測狀態
        updateFaceDetectionState(hasFace, currentTime)

        if (!hasFace) {
            FatigueDetectionLogger.logEvent("無臉部偵測")
            return FatigueDetectionResult(
                isFatigueDetected = false,
                fatigueLevel = FatigueLevel.NORMAL,
                events = emptyList(),
                faceDetected = false,
            )
        }

        val faceLandmarks = result.faceLandmarks()[0]

        // 校正模式處理
        if (isCalibrating) {
            val elapsed = currentTime - calibrationStartTime
            FatigueDetectionLogger.logCalibration(
                "校正進行中",
                progress = ((elapsed.toFloat() / calibrationDuration) * 100).toInt(),
                sampleCount = calibrationEarValues.size,
            )
            handleCalibration(faceLandmarks, currentTime)
            return FatigueDetectionResult(
                isFatigueDetected = false,
                fatigueLevel = FatigueLevel.NORMAL,
                events = emptyList(),
                faceDetected = true,
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

                // 4. 更新疲劳事件计数並直接確定疲勞級別
        updateFatigueEventCount(events)

        // 5. 根據更新後的 fatigueEventCount 確定疲勞級別
        val fatigueLevel = when {
            fatigueEventCount >= currentFatigueEventThreshold -> FatigueLevel.WARNING
            fatigueEventCount > 0 -> FatigueLevel.NOTICE
            else -> FatigueLevel.NORMAL
        }
        
        // 記錄疲勞級別判斷結果
        FatigueDetectionLogger.logTrigger(
            "疲勞級別判斷",
            triggerType = "FatigueLevel",
            count = fatigueEventCount,
            threshold = currentFatigueEventThreshold,
            isTriggered = fatigueLevel != FatigueLevel.NORMAL,
        )

        // 記錄靈敏度數據
        val combinedEar = calculateCombinedEAR(faceLandmarks)
        val mar = calculateMAR(faceLandmarks, LandmarkIndices.MOUTH)
        earValues.add(combinedEar)
        marValues.add(mar)

        // 限制數據量，避免內存溢出
        if (earValues.size > 500) earValues.removeAt(0)
        if (marValues.size > 500) marValues.removeAt(0)

        FatigueDetectionLogger.logSensitivity(
            "檢測結果",
            earValue = combinedEar,
            marValue = mar,
            earThreshold = currentEarThreshold,
            marThreshold = currentMarThreshold,
        )

        // 只在有事件或疲勞級別變化時記錄
        if (events.isNotEmpty() || fatigueLevel != FatigueLevel.NORMAL) {
            val triggerReason = when {
                events.isNotEmpty() -> events.first().javaClass.simpleName
                fatigueLevel == FatigueLevel.WARNING -> "累積疲勞警告"
                fatigueLevel == FatigueLevel.NOTICE -> "累積疲勞提醒"
                else -> "正常狀態"
            }
            FatigueDetectionLogger.logTrigger(
                "疲勞級別變化",
                triggerType = triggerReason,
                isTriggered = fatigueLevel != FatigueLevel.NORMAL,
            )
        }

        // 性能記錄已移除，使用 PerformanceMonitor 替代
        // val processingTime = System.currentTimeMillis() - startTime

        return FatigueDetectionResult(
            isFatigueDetected = fatigueLevel != FatigueLevel.NORMAL,
            fatigueLevel = fatigueLevel,
            events = events,
            faceDetected = true,
        )
    }

    /**
     * 更新疲劳事件计数
     * 根據用戶需求：
     * - 眼睛閉合超過1.5秒：直接觸發警告
     * - 打哈欠：首次觸發提醒，達2次以上觸發警告
     * - 眨眼頻率異常：首次觸發提醒，達2次提醒後觸發警告
     */
    private fun updateFatigueEventCount(events: List<FatigueEvent>) {
        // 校正期間不更新疲勞事件計數，避免影響校正過程
        if (isCalibrating || !calibrationStateManager.hasCalibrated()) {
            FatigueDetectionLogger.logEvent(
                "校正期間，跳過疲勞事件計數更新",
                eventType = "CalibrationSkip",
            )
            return
        }

        val previousCount = fatigueEventCount
        
        // 重新計算疲勞級別：基於實際的計數器狀態而不是當前事件
        fatigueEventCount = when {
            // 眼睛閉合超過1.5秒 -> 直接警告
            events.any { it is FatigueEvent.EyeClosure } -> {
                FatigueDetectionLogger.logEvent(
                    "檢測到眼睛閉合事件，設置警告級別",
                    eventType = "EyeClosureTriggered"
                )
                currentFatigueEventThreshold
            }
            // 打哈欠次數判斷
            yawnCount >= 2 -> {
                FatigueDetectionLogger.logEvent(
                    "打哈欠次數達 $yawnCount，觸發警告級別",
                    eventType = "YawnWarningTriggered"
                )
                currentFatigueEventThreshold
            }
            yawnCount >= 1 -> {
                FatigueDetectionLogger.logEvent(
                    "打哈欠次數 $yawnCount，觸發提醒級別",
                    eventType = "YawnNoticeTriggered"
                )
                1
            }
            // 眨眼頻率異常判斷
            blinkFrequencyWarningCount >= 2 -> {
                FatigueDetectionLogger.logEvent(
                    "眨眼頻率警告次數達 $blinkFrequencyWarningCount，觸發警告級別",
                    eventType = "BlinkFreqWarningTriggered"
                )
                currentFatigueEventThreshold
            }
            blinkFrequencyWarningCount >= 1 -> {
                FatigueDetectionLogger.logEvent(
                    "眨眼頻率警告次數 $blinkFrequencyWarningCount，觸發提醒級別",
                    eventType = "BlinkFreqNoticeTriggered"
                )
                1
            }
            else -> {
                FatigueDetectionLogger.logEvent(
                    "無疲勞指標，正常狀態",
                    eventType = "NormalState"
                )
                0
            }
        }
        
        // 記錄更新後的疲勞事件計數
        FatigueDetectionLogger.logEvent(
            "疲勞級別更新: $previousCount -> $fatigueEventCount (yawn=$yawnCount, blinkFreq=$blinkFrequencyWarningCount, events=${events.size})",
            eventType = "FatigueLevelUpdate"
        )
    }

    /**
     * 更新臉部偵測狀態
     */
    private fun updateFaceDetectionState(
        hasFace: Boolean,
        currentTime: Long,
    ) {
        when {
            hasFace && !isFaceDetected -> {
                // 首次偵測到臉部
                isFaceDetected = true
                faceDetectionStartTime = currentTime
                lastFaceDetectionTime = currentTime
                FatigueDetectionLogger.logEvent(
                    "首次偵測到臉部，準備開始校正",
                    eventType = "FaceDetectionStart",
                )
            }
            hasFace && isFaceDetected -> {
                // 持續偵測到臉部，更新最後偵測時間
                lastFaceDetectionTime = currentTime
                
                // 檢查是否應該開始校正
                // 只有在未校正過且臉部偵測穩定的情況下才開始校正
                if (!calibrationStateManager.hasCalibrated() && !isCalibrating && currentTime - faceDetectionStartTime >= faceDetectionDelay) {
                    FatigueDetectionLogger.logEvent(
                        "臉部偵測穩定，開始校正流程",
                        eventType = "CalibrationStart",
                    )
                    startCalibration()
                }
            }
            !hasFace && isFaceDetected -> {
                // 暫時失去臉部偵測，檢查是否在容錯時間內
                val timeSinceLastDetection = currentTime - lastFaceDetectionTime
                
                if (timeSinceLastDetection > faceDetectionTolerance) {
                    // 超過容錯時間，真正失去臉部偵測
                    isFaceDetected = false
                    if (isCalibrating) {
                        FatigueDetectionLogger.logEvent(
                            "長時間失去臉部偵測，停止校正",
                            eventType = "CalibrationStop",
                        )
                        stopCalibration()
                    }
                    FatigueDetectionLogger.logEvent(
                        "長時間失去臉部偵測",
                        eventType = "FaceDetectionLost",
                    )
                } else {
                    // 在容錯時間內，保持校正狀態
                    FatigueDetectionLogger.logEvent(
                        "短暫失去臉部偵測，校正繼續進行",
                        eventType = "FaceDetectionTemporary",
                    )
                }
            }
        }
    }

    /**
     * 检测眼睛闭合
     * 根據用戶需求：眼睛閉合超過1.5秒才觸發警告
     */
    private fun detectEyeClosure(
        landmarks: List<NormalizedLandmark>,
        currentTime: Long,
    ): FatigueEvent? {
        // 兩眼一起計算 EAR
        val combinedEar = calculateCombinedEAR(landmarks)

        // 檢查眼睛是否閉合
        val eyesClosed = combinedEar < currentEarThreshold

        // 記錄每次檢測的眼睛狀態
        FatigueDetectionLogger.logSensitivity(
            "眼睛狀態檢測",
            earValue = combinedEar,
            earThreshold = currentEarThreshold,
        )
        
        // 記錄詳細的眼睛狀態變化
        val eyeStatus = if (eyesClosed) "閉合" else "睜開"
        val previousStatus = if (isEyeClosed) "閉合" else "睜開"
        
        FatigueDetectionLogger.logEvent(
            "眼睛狀態: $previousStatus -> $eyeStatus",
            eventType = "EyeStatus",
            duration = if (isEyeClosed) currentTime - lastEyeClosureStartTime else null,
        )

        return when {
            eyesClosed && !isEyeClosed -> {
                // 眼睛開始閉合
                FatigueDetectionLogger.logEvent(
                    "眼睛開始閉合",
                    eventType = "EyeClosureStart",
                    duration = null,
                )
                isEyeClosed = true
                lastEyeClosureStartTime = currentTime
                null
            }
            eyesClosed && isEyeClosed -> {
                // 眼睛持續閉合，檢查是否超過1.5秒
                val closureDuration = currentTime - lastEyeClosureStartTime
                FatigueDetectionLogger.logEvent(
                    "眼睛持續閉合中",
                    eventType = "EyeClosureOngoing",
                    duration = closureDuration,
                )
                if (closureDuration >= DEFAULT_EAR_CLOSURE_DURATION_THRESHOLD) {
                    // 超過1.5秒，觸發警告事件，但不重置狀態
                    FatigueDetectionLogger.logEvent(
                        "眼睛閉合超時（警告）",
                        eventType = "EyeClosure",
                        duration = closureDuration,
                    )
                    // 重置閉眼開始時間避免重複觸發
                    lastEyeClosureStartTime = currentTime
                    FatigueEvent.EyeClosure(closureDuration)
                } else {
                    null
                }
            }
            !eyesClosed && isEyeClosed -> {
                // 眼睛睜開，檢查閉合時間
                val closureDuration = currentTime - lastEyeClosureStartTime
                isEyeClosed = false
                if (closureDuration >= DEFAULT_EAR_CLOSURE_DURATION_THRESHOLD) {
                    // 超過1.5秒，觸發警告事件
                    FatigueDetectionLogger.logEvent(
                        "眼睛閉合超時（警告）",
                        eventType = "EyeClosure",
                        duration = closureDuration,
                    )
                    FatigueEvent.EyeClosure(closureDuration)
                } else {
                    // 正常眨眼，不觸發事件
                    FatigueDetectionLogger.logEvent(
                        "眨眼檢測",
                        eventType = "Blink",
                        duration = closureDuration,
                    )
                    detectBlink(currentTime)
                    null
                }
            }
            else -> {
                // 眼睛狀態沒有變化，記錄當前狀態
                FatigueDetectionLogger.logEvent(
                    "眼睛狀態穩定: $eyeStatus",
                    eventType = "EyeStatusStable",
                )
                null
            }
        }
    }

    /**
     * 改善的打哈欠檢測邏輯
     * 要求：張嘴 -> 持續一段時間 -> 閉嘴，且總時間要夠長避免說話誤判
     * 根據用戶需求：首次打哈欠觸發提醒，達2次以上觸發警告
     */
    private fun detectYawn(
        landmarks: List<NormalizedLandmark>,
        currentTime: Long,
    ): FatigueEvent? {
        val mar = calculateMAR(landmarks, LandmarkIndices.MOUTH)
        
        // 提高 MAR 閾值，更準確區分打哈欠和說話
        val yawnMarThreshold = currentMarThreshold * 1.4f // 比正常說話更大的張嘴程度

        return when {
            mar > yawnMarThreshold && !isMouthOpen -> {
                // 嘴巴開始張開（需要比說話更大的張嘴程度）
                isMouthOpen = true
                lastMouthOpenStartTime = currentTime
                FatigueDetectionLogger.logEvent(
                    "嘴巴開始張開，MAR=${"%.3f".format(mar)}，閾值=${"%.3f".format(yawnMarThreshold)}",
                    eventType = "MouthOpenStart"
                )
                null
            }
            mar > yawnMarThreshold && isMouthOpen -> {
                // 嘴巴持續張開，記錄進度但不觸發
                val openDuration = currentTime - lastMouthOpenStartTime
                
                // 每0.5秒記錄一次進度，避免日誌過多
                if (openDuration % 500 == 0L && openDuration >= 1000L) {
                    FatigueDetectionLogger.logEvent(
                        "嘴巴持續張開中，MAR=${"%.3f".format(mar)}，持續${openDuration}ms",
                        eventType = "MouthOpenOngoing",
                        duration = openDuration
                    )
                }
                null
            }
            mar <= yawnMarThreshold && isMouthOpen -> {
                // 嘴巴閉合，檢查是否符合打哈欠條件
                isMouthOpen = false
                val totalDuration = currentTime - lastMouthOpenStartTime
                
                FatigueDetectionLogger.logEvent(
                    "嘴巴閉合，總持續時間=${totalDuration}ms，MAR=${"%.3f".format(mar)}",
                    eventType = "MouthClosed",
                    duration = totalDuration
                )
                
                when {
                    totalDuration >= DEFAULT_YAWN_DURATION_THRESHOLD -> {
                        // 長時間張嘴，確認為打哈欠
                        yawnCount++
                        FatigueDetectionLogger.logEvent(
                            "打哈欠檢測成功（長時間）：${totalDuration}ms，計數：$yawnCount",
                            eventType = "YawnDetected",
                            duration = totalDuration
                        )
                        FatigueEvent.Yawn(totalDuration)
                    }
                    totalDuration >= DEFAULT_YAWN_MIN_DURATION -> {
                        // 中等時間張嘴，可能是打哈欠，但需要更高的 MAR 峰值驗證
                        val maxMarDuringOpen = mar // 簡化：用閉合前的 MAR 作為近似
                        if (maxMarDuringOpen > currentMarThreshold * 1.6f) {
                            // MAR 峰值夠高，確認為打哈欠
                            yawnCount++
                            FatigueDetectionLogger.logEvent(
                                "打哈欠檢測成功（高峰值）：${totalDuration}ms，峰值MAR=${"%.3f".format(maxMarDuringOpen)}，計數：$yawnCount",
                                eventType = "YawnDetected",
                                duration = totalDuration
                            )
                            FatigueEvent.Yawn(totalDuration)
                        } else {
                            // 可能是說話，不觸發
                            FatigueDetectionLogger.logEvent(
                                "疑似說話，不觸發打哈欠：${totalDuration}ms，峰值MAR=${"%.3f".format(maxMarDuringOpen)}",
                                eventType = "PossibleSpeech",
                                duration = totalDuration
                            )
                            null
                        }
                    }
                    else -> {
                        // 時間太短，可能是說話或正常張嘴
                        FatigueDetectionLogger.logEvent(
                            "張嘴時間過短，忽略：${totalDuration}ms",
                            eventType = "ShortMouthOpen",
                            duration = totalDuration
                        )
                        null
                    }
                }
            }
            else -> null
        }
    }

    /**
     * 检测眨眼频率
     * 根據用戶需求：眨眼頻率過高第一次觸發提醒，達2次提醒後觸發警告
     */
    private fun detectBlinkFrequency(
        @Suppress("UNUSED_PARAMETER") landmarks: List<NormalizedLandmark>,
        currentTime: Long,
    ): FatigueEvent? {
        // 检查是否超过1分钟，重置计数
        if (currentTime - lastMinuteStartTime >= 60000) {
            if (blinkCount > DEFAULT_BLINK_FREQUENCY_THRESHOLD) {
                blinkFrequencyWarningCount++ // 增加眨眼頻率警告計數
                val event = FatigueEvent.HighBlinkFrequency(blinkCount)

                FatigueDetectionLogger.logEvent(
                    "眨眼頻率異常",
                    eventType = "HighBlinkFrequency",
                    count = blinkCount,
                )

                blinkCount = 0
                lastMinuteStartTime = currentTime
                return event
            }

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

        FatigueDetectionLogger.logEvent(
            "眨眼檢測檢查",
            eventType = "BlinkCheck",
            duration = timeSinceLastBlink,
        )

        if (timeSinceLastBlink > 200) {
            blinkCount++
            lastBlinkTime = currentTime
            blinkTimestamps.add(currentTime) // 新增：記錄眨眼時間
            FatigueDetectionLogger.logEvent(
                "眨眼檢測成功",
                eventType = "Blink",
                count = blinkCount,
            )
            fatigueListener?.onBlink()
        } else {
            FatigueDetectionLogger.logEvent(
                "眨眼間隔太短，跳過計數",
                eventType = "BlinkSkipped",
                duration = timeSinceLastBlink,
            )
        }
    }



    /**
     * 重置疲劳检测器状态
     */
    fun reset() {
        val previousEventCount = fatigueEventCount
        fatigueEventCount = 0
        blinkCount = 0
        yawnCount = 0 // 重置打哈欠計數
        blinkFrequencyWarningCount = 0 // 重置眨眼頻率警告計數
        isEyeClosed = false
        isMouthOpen = false
        lastEyeClosureStartTime = 0
        lastMouthOpenStartTime = 0
        lastBlinkTime = 0
        lastMinuteStartTime = System.currentTimeMillis()
        blinkTimestamps.clear() // 新增：清空
        stopCalibration() // 停止校正
        isFaceDetected = false // 重置臉部偵測狀態
        // 注意：不重置校正狀態，使用持久化的校正狀態管理器

        FatigueDetectionLogger.logReset(
            "疲勞檢測器完全重置",
            resetType = "FullReset",
            previousCount = previousEventCount,
            reason = "用戶確認已清醒",
        )
    }

    /**
     * 重置疲勞事件計數（不影響校正狀態）
     */
    fun resetFatigueEvents() {
        val previousEventCount = fatigueEventCount
        // 重置所有疲勞相關狀態
        fatigueEventCount = 0
        blinkCount = 0
        yawnCount = 0 // 重置打哈欠計數
        blinkFrequencyWarningCount = 0 // 重置眨眼頻率警告計數
        isEyeClosed = false
        isMouthOpen = false
        lastEyeClosureStartTime = 0
        lastMouthOpenStartTime = 0
        lastBlinkTime = 0
        lastMinuteStartTime = System.currentTimeMillis()
        blinkTimestamps.clear()

        FatigueDetectionLogger.logReset(
            "疲勞事件計數重置",
            resetType = "EventReset",
            previousCount = previousEventCount,
            reason = "用戶要求休息",
        )
    }

    /**
     * 校正完成後重置疲勞事件計數
     */
    private fun resetFatigueEventsAfterCalibration() {
        val previousEventCount = fatigueEventCount


        // 重置所有疲勞相關狀態
        fatigueEventCount = 0
        blinkCount = 0
        yawnCount = 0
        blinkFrequencyWarningCount = 0
        isEyeClosed = false
        isMouthOpen = false
        lastEyeClosureStartTime = 0
        lastMouthOpenStartTime = 0
        lastBlinkTime = 0
        lastMinuteStartTime = System.currentTimeMillis()
        blinkTimestamps.clear()

        FatigueDetectionLogger.logReset(
            "校正完成後重置疲勞事件計數",
            resetType = "PostCalibrationReset",
            previousCount = previousEventCount,
            reason = "校正完成",
        )
    }

    /**
     * 设置疲劳检测参数
     */
    fun setDetectionParameters(
        earThreshold: Float = currentEarThreshold,
        marThreshold: Float = currentMarThreshold,
        fatigueEventThreshold: Int = currentFatigueEventThreshold,
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
     * 獲取當前打哈欠次數
     */
    fun getYawnCount(): Int = yawnCount

    /**
     * 獲取當前閉眼時間（毫秒）
     */
    fun getEyeClosureDuration(): Long {
        return if (isEyeClosed && lastEyeClosureStartTime > 0) {
            System.currentTimeMillis() - lastEyeClosureStartTime
        } else {
            0L
        }
    }

    /**
     * 獲取最近一分鐘的打哈欠次數
     */
    fun getRecentYawnCount(windowMs: Long = 60000L): Int {
        // 這裡可以實現更複雜的時間窗口邏輯，目前簡單返回當前計數
        return yawnCount
    }

    /**
     * 開始校正
     */
    fun startCalibration() {
        isCalibrating = true
        calibrationStartTime = System.currentTimeMillis()
        calibrationEarValues.clear()
        FatigueDetectionLogger.logCalibration(
            "開始校正",
            progress = 0,
            sampleCount = 0,
        )
        fatigueListener?.onCalibrationStarted()
    }

    /**
     * 停止校正
     */
    fun stopCalibration() {
        isCalibrating = false
        calibrationEarValues.clear()
        FatigueDetectionLogger.logCalibration("校正已停止")
    }

    /**
     * 處理校正過程
     */
    private fun handleCalibration(
        landmarks: List<NormalizedLandmark>,
        currentTime: Long,
    ) {
        val elapsedTime = currentTime - calibrationStartTime
        if (elapsedTime >= calibrationDuration) {
            FatigueDetectionLogger.logCalibration("校正時間到，完成校正")
            finishCalibration()
            return
        }

        // 使用組合 EAR 值進行校正
        val combinedEar = calculateCombinedEAR(landmarks)
        calibrationEarValues.add(combinedEar)

        // 通知校正進度
        val progress = (elapsedTime * 100 / calibrationDuration).toInt()
        fatigueListener?.onCalibrationProgress(progress, combinedEar)

        // 每 5 秒記錄一次進度，減少 log 噪音
        if (progress % 20 == 0 && progress > 0) {
            FatigueDetectionLogger.logCalibration(
                "校正進度更新",
                progress = progress,
                currentEar = combinedEar,
                sampleCount = calibrationEarValues.size,
            )
        }
    }

    /**
     * 完成校正並計算新閾值
     */
    private fun finishCalibration() {
        if (calibrationEarValues.isEmpty()) {
            FatigueDetectionLogger.logEvent("校正數據為空，使用默認閾值", eventType = "CalibrationError", level = "ERROR")
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

        // 標記校正已完成（持久化保存）
        calibrationStateManager.markCalibrationCompleted()

        // 校正完成後重置疲勞事件計數，確保從乾淨狀態開始
        resetFatigueEventsAfterCalibration()

        FatigueDetectionLogger.logCalibration(
            "校正完成",
            progress = 100,
            minEar = minEar,
            maxEar = maxEar,
            avgEar = avgEar,
            newThreshold = newThreshold,
            sampleCount = calibrationEarValues.size,
        )

        // 通知校正完成
        fatigueListener?.onCalibrationCompleted(newThreshold, minEar, maxEar, avgEar)

        // 停止校正
        stopCalibration()
    }

    /**
     * 檢查是否正在校正
     */
    fun isCalibrating(): Boolean = isCalibrating

    /**
     * 重置校正狀態（僅在程式完全關閉時調用）
     */
    fun resetCalibrationState() {
        calibrationStateManager.resetCalibrationState()
        Log.d(TAG, "校正狀態已重置")
    }



    /**
     * 獲取重置狀態信息（用於調試）
     */
    fun getResetStatusInfo(): String {
        // 重置保護期 - 移除，這應該是 UI 層的職責
        // val currentTime = System.currentTimeMillis()
        // val protectionElapsed = if (isInResetProtection) currentTime - resetProtectionStartTime else 0L
        // 冷卻期 - 移除，這應該是 UI 層的職責
        // val cooldownElapsed = if (isInCooldownPeriod) currentTime - cooldownStartTime else 0L

        return "ResetProtection: false, " + // 重置保護期 - 移除，這應該是 UI 層的職責
            "Cooldown: false, " + // 冷卻期 - 移除，這應該是 UI 層的職責
            "FatigueCount: $fatigueEventCount"
    }

    /**
     * 獲取校正進度
     */
    fun getCalibrationProgress(): Int {
        if (!isCalibrating) return 0
        val elapsedTime = System.currentTimeMillis() - calibrationStartTime
        return (elapsedTime * 100 / calibrationDuration).toInt().coerceIn(0, 100)
    }

    /**
     * 檢查是否偵測到臉部
     */
    fun isFaceDetected(): Boolean = isFaceDetected

    /**
     * 生成靈敏度調試報告
     */
    fun generateSensitivityReport(): String {
        val currentThresholds =
            mapOf(
                "ear" to currentEarThreshold,
                "mar" to currentMarThreshold,
                "fatigueEvent" to currentFatigueEventThreshold.toFloat(),
            )

        // 將 fatigueEvents 轉換為事件計數
        val eventCounts = fatigueEvents.groupBy { it.javaClass.simpleName }.mapValues { it.value.size }
        
        return FatigueDetectionLogger.generateAnalysisReport(
            earValues = earValues.toList(),
            marValues = marValues.toList(),
            eventCounts = eventCounts,
            calibrationData = currentThresholds,
        )
    }

    /**
     * 獲取當前檢測參數
     */
    fun getDetectionParameters(): Map<String, Any> {
        return mapOf(
            "earThreshold" to currentEarThreshold,
            "marThreshold" to currentMarThreshold,
            "fatigueEventThreshold" to currentFatigueEventThreshold,
            "earClosureDurationThreshold" to DEFAULT_EAR_CLOSURE_DURATION_THRESHOLD,
            "yawnDurationThreshold" to DEFAULT_YAWN_DURATION_THRESHOLD,
            "yawnMinDuration" to DEFAULT_YAWN_MIN_DURATION,
            "blinkFrequencyThreshold" to DEFAULT_BLINK_FREQUENCY_THRESHOLD,
            "calibrationDuration" to calibrationDuration,
            "hasCalibrated" to calibrationStateManager.hasCalibrated(),
            "isCalibrating" to isCalibrating,
            "fatigueEventCount" to fatigueEventCount,
            "blinkCount" to blinkCount,
            "yawnCount" to yawnCount, // 新增：添加打哈欠計數
            "blinkFrequencyWarningCount" to blinkFrequencyWarningCount, // 新增：添加眨眼頻率警告計數
        )
    }

    /**
     * 設置日誌開關
     */
    fun setLogEnabled(
        sensitivity: Boolean = true,
        trigger: Boolean = true,
        calibration: Boolean = true,
        event: Boolean = true,
        reset: Boolean = true,
    ) {
        FatigueDetectionLogger.setLogEnabled(sensitivity, trigger, calibration, event, reset)
    }

    /**
     * 计算EAR (Eye Aspect Ratio)
     * EAR = (A + B) / (2 * C)
     * 其中 A = |p2-p6|, B = |p3-p5|, C = |p1-p4|
     */
    private fun calculateEAR(
        landmarks: List<NormalizedLandmark>,
        eyeIndices: List<Int>,
    ): Float {
        if (eyeIndices.size < 6) {
            FatigueDetectionLogger.logEvent(
                "calculateEAR: 特徵點數量不足，需要6個，實際只有${eyeIndices.size}個",
                eventType = "CalculationError",
                level = "ERROR",
            )
            return 0f
        }
        try {
            val points =
                eyeIndices.map { idx ->
                    if (idx < landmarks.size) landmarks[idx] else null
                }
            if (points.any { it == null }) {
                FatigueDetectionLogger.logEvent(
                    "calculateEAR: landmark index超出範圍，eyeIndices=$eyeIndices, landmarks.size=${landmarks.size}",
                    eventType = "IndexError",
                    level = "ERROR",
                )
                return 0f
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

            if (ear < 0 || ear > 1) {
                FatigueDetectionLogger.logEvent(
                    "EAR 值異常: $ear",
                    eventType = "ValueError",
                    level = "WARN",
                )
            }
            return ear
        } catch (e: Exception) {
            FatigueDetectionLogger.logEvent(
                "calculateEAR: 計算過程中發生錯誤 - ${e.message}",
                eventType = "CalculationException",
                level = "ERROR",
            )
            return 0f
        }
    }

    /**
     * 計算兩眼的組合 EAR
     * 使用左眼和右眼的平均 EAR 值
     */
    private fun calculateCombinedEAR(landmarks: List<NormalizedLandmark>): Float {
        try {
            val leftEar = calculateEAR(landmarks, LandmarkIndices.LEFT_EYE)
            val rightEar = calculateEAR(landmarks, LandmarkIndices.RIGHT_EYE)

            // 計算兩眼的平均 EAR
            val combinedEar = (leftEar + rightEar) / 2.0f

            return combinedEar
        } catch (e: Exception) {
            FatigueDetectionLogger.logEvent(
                "calculateCombinedEAR: 計算過程中發生錯誤 - ${e.message}",
                eventType = "CalculationException",
                level = "ERROR",
            )
            return 0f
        }
    }



    /**
     * 计算MAR (Mouth Aspect Ratio)
     */
    private fun calculateMAR(
        landmarks: List<NormalizedLandmark>,
        mouthIndices: List<Int>,
    ): Float {
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
    private fun euclideanDistance(
        p1: NormalizedLandmark,
        p2: NormalizedLandmark,
    ): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        return sqrt(dx * dx + dy * dy)
    }


}
