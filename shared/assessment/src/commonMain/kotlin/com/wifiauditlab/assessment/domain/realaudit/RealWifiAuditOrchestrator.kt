package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.real.RealNetworkConsent
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RealWifiAuditSessionState(
    val network: RealNetworkReference? = null,
    val securityProfile: WifiSecurityProfile? = null,
    val strategy: RealWifiAuditStrategy? = null,
    val strategyLabel: String? = null,
    val phase: RealWifiAuditPhase = RealWifiAuditPhase.Idle,
    val progress: RealWifiAuditProgress? = null,
    val plan: RealWifiAuditPlan? = null,
)

/**
 * Application orchestrator for real Wi‑Fi audits.
 *
 * Flow: network → resolve strategy → plan → backend.execute → outcome.
 * Does not generate credentials or call Lab Search Engine against the AP.
 */
class RealWifiAuditOrchestrator(
    private val strategyResolver: RealWifiAuditStrategyResolver,
    private val planner: RealWifiAuditPlanner,
    private val backend: RealWifiAuditBackend,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RealWifiAuditSessionState())
    val state: StateFlow<RealWifiAuditSessionState> = _state.asStateFlow()

    fun prepare(
        network: RealNetworkReference,
        securityProfile: WifiSecurityProfile,
    ) {
        val strategy = strategyResolver.resolve(securityProfile)
        val plan = planner.createPlan(network, securityProfile, strategy)
        _state.value =
            RealWifiAuditSessionState(
                network = network,
                securityProfile = securityProfile,
                strategy = strategy,
                strategyLabel = plan.strategyLabel,
                phase =
                    if (strategy is RealWifiAuditStrategy.Unsupported) {
                        RealWifiAuditPhase.Completed(RealWifiAuditOutcome.Unsupported)
                    } else {
                        RealWifiAuditPhase.Ready
                    },
                progress =
                    RealWifiAuditProgress(
                        strategyLabel = plan.strategyLabel,
                        workerCount = plan.limits.workerCountHint,
                    ),
                plan = plan,
            )
    }

    suspend fun start(consent: RealNetworkConsent = RealNetworkConsent(true)) {
        val plan =
            mutex.withLock {
                val snapshot = _state.value
                val currentPlan = snapshot.plan ?: return
                if (snapshot.phase !is RealWifiAuditPhase.Ready) return
                check(consent.granted)
                if (currentPlan.strategy is RealWifiAuditStrategy.Unsupported) {
                    _state.update {
                        it.copy(phase = RealWifiAuditPhase.Completed(RealWifiAuditOutcome.Unsupported))
                    }
                    return
                }
                _state.update { it.copy(phase = RealWifiAuditPhase.Preparing) }
                _state.update { it.copy(phase = RealWifiAuditPhase.Running) }
                currentPlan
            }

        val sink =
            RealWifiAuditProgressSink { progress ->
                _state.update { current -> current.copy(progress = progress) }
            }
        val result = backend.execute(plan, sink)
        mutex.withLock {
            val phase = _state.value.phase
            if (phase is RealWifiAuditPhase.Running || phase is RealWifiAuditPhase.Stopping) {
                _state.update { it.copy(phase = mapBackendResult(result)) }
            }
        }
    }

    fun stop() {
        val phase = _state.value.phase
        if (phase !is RealWifiAuditPhase.Running && phase !is RealWifiAuditPhase.Preparing) return
        _state.update { it.copy(phase = RealWifiAuditPhase.Stopping) }
        backend.cancel()
    }

    fun clear() {
        backend.cancel()
        _state.value = RealWifiAuditSessionState()
    }

    private fun mapBackendResult(result: RealWifiAuditBackendResult): RealWifiAuditPhase =
        when (result) {
            RealWifiAuditBackendResult.Completed ->
                RealWifiAuditPhase.Completed(RealWifiAuditOutcome.Completed)
            RealWifiAuditBackendResult.Cancelled ->
                RealWifiAuditPhase.Completed(RealWifiAuditOutcome.Cancelled)
            is RealWifiAuditBackendResult.Unavailable ->
                RealWifiAuditPhase.Completed(RealWifiAuditOutcome.Unavailable(result.reason))
            is RealWifiAuditBackendResult.Failed ->
                RealWifiAuditPhase.Failed(result.reason)
        }
}
