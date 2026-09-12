package com.wifiauditlab.android.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SecurityAnalysisViewModel(
    private val targetStore: SecurityAnalysisTargetStore,
    private val assessSecurity: AssessNetworkSecurity,
) : ViewModel() {
    private val _state = MutableStateFlow(SecurityAnalysisUiState())
    val state: StateFlow<SecurityAnalysisUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val request = targetStore.current
        if (request == null) {
            _state.value = SecurityAnalysisUiState(loading = false, missingTarget = true)
            return
        }
        _state.value =
            SecurityAnalysisUiState(
                displayName = request.displayName,
                ssidLabel = request.ssidLabel,
                loading = true,
            )
        viewModelScope.launch {
            val assessment = assessSecurity(request.profile)
            _state.update {
                it.copy(
                    loading = false,
                    assessment = assessment,
                    authentication = buildAuthenticationSummary(request.profile),
                    recommendations = defensiveRecommendations(request.profile, assessment),
                )
            }
        }
    }

    fun toggleTechnicalDetails() {
        _state.update { it.copy(technicalExpanded = !it.technicalExpanded) }
    }
}
