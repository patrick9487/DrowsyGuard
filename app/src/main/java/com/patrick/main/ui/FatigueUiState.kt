package com.patrick.main.ui

sealed class FatigueUiState {
    object Calibrating : FatigueUiState()
    object Normal : FatigueUiState()
    object ModerateAlert : FatigueUiState()
    object SevereDialog : FatigueUiState()
    object RestReminder : FatigueUiState()
}

sealed class FatigueAction {
    object Acknowledge : FatigueAction()
    object RequestRest : FatigueAction()
    object Dismiss : FatigueAction()
} 