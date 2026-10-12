package com.wifiauditlab.android.ui.realaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditCapability
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOrchestrator
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOutcome
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPhase
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditSessionState
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditTechnicalDetails
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchOutcome
import com.wifiauditlab.assessment.domain.realaudit.displayLabel
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RealWifiAuditUiState(
    val missingTarget: Boolean = false,
    val displayName: String = "",
    val securityFamily: SecurityFamily? = null,
    val strategyLabel: String = "",
    val phase: RealWifiAuditPhase = RealWifiAuditPhase.Idle,
    val capability: RealWifiAuditCapability? = null,
    val technicalDetails: RealWifiAuditTechnicalDetails? = null,
    val candidatesProcessed: Long = 0,
    val elapsedMillis: Long = 0,
    val ratePerSecond: Double? = null,
    val workerCount: Int = 1,
    val searchOutcome: RealWifiSearchOutcome? = null,
    val canStart: Boolean = false,
    val isBusy: Boolean = false,
    val showBackendUnavailableCard: Boolean = false,
)

class RealWifiAuditViewModel(
    private val targetStore: RealWifiAuditTargetStore,
    private val orchestrator: RealWifiAuditOrchestrator,
) : ViewModel() {
    private val missingTarget = MutableStateFlow(false)

    val state: StateFlow<RealWifiAuditUiState> =
        combine(orchestrator.state, missingTarget) { session, missing ->
            session.toUi(missing)
        }.stateIn(
            viewModelScope,
            // Eagerly so capability is stable on screen load without a Compose subscriber.
            SharingStarted.Eagerly,
            RealWifiAuditUiState(),
        )

    init {
        val target = targetStore.target.value
        if (target == null) {
            missingTarget.value = true
        } else {
            missingTarget.value = false
            orchestrator.prepare(target.network, target.securityProfile)
        }
    }

    fun start() {
        val session = orchestrator.state.value
        // Never call orchestrator when backend is unavailable or not Ready.
        if (session.phase !is RealWifiAuditPhase.Ready) return
        if (session.capability is RealWifiAuditCapability.Unavailable) return
        viewModelScope.launch { orchestrator.start() }
    }

    fun stop() {
        orchestrator.stop()
    }

    override fun onCleared() {
        orchestrator.clear()
        super.onCleared()
    }
}

private fun RealWifiAuditSessionState.toUi(missing: Boolean): RealWifiAuditUiState {
    val busy =
        phase is RealWifiAuditPhase.CheckingCapability ||
            phase is RealWifiAuditPhase.Preparing ||
            phase is RealWifiAuditPhase.Searching ||
            phase is RealWifiAuditPhase.Stopping
    val backendUnavailable = phase is RealWifiAuditPhase.BackendUnavailable
    val completed = phase as? RealWifiAuditPhase.Completed
    val completedOutcome = completed?.outcome as? RealWifiAuditOutcome.Completed
    return RealWifiAuditUiState(
        missingTarget = missing,
        displayName = network?.displayName.orEmpty(),
        securityFamily = securityProfile?.family ?: network?.securityFamily,
        strategyLabel = strategyLabel ?: strategy?.displayLabel().orEmpty(),
        phase = phase,
        capability = capability,
        technicalDetails = technicalDetails,
        candidatesProcessed = progress?.candidatesProcessed ?: 0,
        elapsedMillis = progress?.elapsedMillis ?: 0,
        ratePerSecond = progress?.ratePerSecond,
        workerCount = progress?.workerCount ?: 1,
        searchOutcome = completedOutcome?.search,
        canStart =
            !missing &&
                phase is RealWifiAuditPhase.Ready &&
                capability is RealWifiAuditCapability.Available &&
                !busy,
        isBusy = busy,
        showBackendUnavailableCard = backendUnavailable,
    )
}
