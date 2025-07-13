package com.patrick.main.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.patrick.core.FatigueLevel

class FatigueViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<FatigueUiState>(FatigueUiState.Calibrating)
    val uiState: StateFlow<FatigueUiState> = _uiState

    // 眨眼頻率（五秒更新一次）
    private val _blinkFrequency = MutableStateFlow(0)
    val blinkFrequency: StateFlow<Int> = _blinkFrequency

    // 是否顯示眨眼頻率（可由設定控制）
    private val _showBlinkFrequency = MutableStateFlow(true)
    val showBlinkFrequency: StateFlow<Boolean> = _showBlinkFrequency

    // 狀態資訊文字（AppBar 中間顯示）
    private val _statusText = MutableStateFlow("校正中，請自然眨眼 15 秒…")
    val statusText: StateFlow<String> = _statusText
    
    // 校正進度
    private val _calibrationProgress = MutableStateFlow(0)
    val calibrationProgress: StateFlow<Int> = _calibrationProgress
    
    // 校正完成的 EAR 值
    private val _calibrationEarValue = MutableStateFlow(0f)
    val calibrationEarValue: StateFlow<Float> = _calibrationEarValue

    fun onCalibrating() {
        _uiState.value = FatigueUiState.Calibrating
        _statusText.value = "校正中，請自然眨眼 15 秒…"
        _calibrationProgress.value = 0
        _calibrationEarValue.value = 0f
    }
    
    fun onCalibrationProgress(progress: Int, currentEar: Float) {
        android.util.Log.d("FatigueViewModel", "[FatigueViewModel] onCalibrationProgress: progress=$progress, currentEar=$currentEar")
        _calibrationProgress.value = progress
        _calibrationEarValue.value = currentEar
        _statusText.value = "校正中… ${progress}%"
    }
    
    fun onCalibrationCompleted(newThreshold: Float, minEar: Float, maxEar: Float, avgEar: Float) {
        _calibrationProgress.value = 100
        _calibrationEarValue.value = avgEar
        _statusText.value = "校正完成！EAR: ${String.format("%.3f", avgEar)}, 閾值: ${String.format("%.3f", newThreshold)}"
    }

    fun onFatigueLevelChanged(level: FatigueLevel) {
        _uiState.value = when (level) {
            FatigueLevel.NORMAL -> FatigueUiState.Normal
            FatigueLevel.MODERATE -> FatigueUiState.ModerateAlert
            FatigueLevel.SEVERE -> FatigueUiState.SevereDialog
        }
        // 狀態資訊可根據 level 動態變化
        _statusText.value = when (level) {
            FatigueLevel.NORMAL -> "偵測中…"
            FatigueLevel.MODERATE -> "中度疲勞警示"
            FatigueLevel.SEVERE -> "重度疲勞警示"
        }
    }
    fun onAction(action: FatigueAction) {
        when (action) {
            FatigueAction.Acknowledge -> {
                _uiState.value = FatigueUiState.Normal
                _statusText.value = "偵測中…"
            }
            FatigueAction.RequestRest -> {
                _uiState.value = FatigueUiState.RestReminder
                _statusText.value = "請盡快休息"
            }
            FatigueAction.Dismiss -> {
                _uiState.value = FatigueUiState.Normal
                _statusText.value = "偵測中…"
            }
        }
    }
    // 提供外部更新眨眼頻率的方法
    fun updateBlinkFrequency(freq: Int) {
        _blinkFrequency.value = freq
    }
    // 提供外部切換顯示/隱藏眨眼頻率的方法
    fun setShowBlinkFrequency(show: Boolean) {
        _showBlinkFrequency.value = show
    }
} 