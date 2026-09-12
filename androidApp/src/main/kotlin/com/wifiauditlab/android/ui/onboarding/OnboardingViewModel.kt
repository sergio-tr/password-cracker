package com.wifiauditlab.android.ui.onboarding

import androidx.lifecycle.ViewModel
import com.wifiauditlab.assessment.port.OnboardingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OnboardingViewModel(
    private val preferences: OnboardingPreferences,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            OnboardingUiState(completed = preferences.isCompleted()),
        )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next() {
        _state.update { current ->
            if (current.isLast) {
                preferences.markCompleted()
                current.copy(completed = true)
            } else {
                current.copy(pageIndex = current.pageIndex + 1)
            }
        }
    }

    fun skip() {
        preferences.markCompleted()
        _state.update { it.copy(completed = true) }
    }

    fun reopenFromSettings() {
        _state.update { OnboardingUiState(pageIndex = 0, completed = false) }
    }
}
