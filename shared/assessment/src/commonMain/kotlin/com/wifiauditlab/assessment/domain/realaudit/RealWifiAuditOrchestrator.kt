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
    val capability: RealWifiAuditCapability? = null,
    val technicalDetails: RealWifiAuditTechnicalDetails? = null,
    val progress: RealWifiAuditProgress? = null,
    val plan: RealWifiAuditPlan? = null,
)

/**
 * Real Wi‑Fi audit orchestrator.
 *
 * Capability of [ExternalCandidateVerificationPort] is resolved in [prepare].
 * Password strategies with Unavailable external backend land on
 * [RealWifiAuditPhase.BackendUnavailable] — START must not call [start].
 *
 * Flow: StrategyResolver → Planner → Search Engine V2 → CandidateVerifier
 * → ExternalVerificationBackend.
 */
class RealWifiAuditOrchestrator(
    private val strategyResolver: RealWifiAuditStrategyResolver,
    private val planner: RealWifiAuditPlanner,
    private val search: RealWifiSearchPort,
    private val externalVerifier: ExternalCandidateVerificationPort,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RealWifiAuditSessionState())
    val state: StateFlow<RealWifiAuditSessionState> = _state.asStateFlow()

    @Volatile
    private var stopRequested: Boolean = false

    /** Observability for tests — increments only when [start] begins real work. */
    @Volatile
    var startInvocationCount: Int = 0
        private set

    fun prepare(
        network: RealNetworkReference,
        securityProfile: WifiSecurityProfile,
    ) {
        stopRequested = false
        startInvocationCount = 0
        _state.value =
            RealWifiAuditSessionState(
                network = network,
                securityProfile = securityProfile,
                phase = RealWifiAuditPhase.CheckingCapability,
            )

        val strategy = strategyResolver.resolve(securityProfile)
        val plan = planner.createPlan(network, securityProfile, strategy)
        val externalCapability = externalVerifier.capability()
        val externalLabel =
            when (externalCapability) {
                ExternalVerifierCapability.Available -> "Available"
                ExternalVerifierCapability.Unavailable -> "Unavailable"
            }
        val details =
            RealWifiAuditTechnicalDetails(
                strategyName = strategy::class.simpleName ?: strategy.toString(),
                plannerReady = true,
                searchEngineReady = true,
                externalVerifierLabel = externalLabel,
            )

        when {
            strategy is RealWifiAuditStrategy.Unsupported -> {
                _state.value =
                    RealWifiAuditSessionState(
                        network = network,
                        securityProfile = securityProfile,
                        strategy = strategy,
                        strategyLabel = plan.strategyLabel,
                        phase = RealWifiAuditPhase.Completed(RealWifiAuditOutcome.Unsupported),
                        capability =
                            RealWifiAuditCapability.Unavailable(
                                RealWifiAuditUnavailableReason.CapabilityUnavailable,
                            ),
                        technicalDetails = details,
                        progress =
                            RealWifiAuditProgress(
                                strategyLabel = plan.strategyLabel,
                                statusMessage = "unsupported",
                            ),
                        plan = plan,
                    )
            }
            !strategy.requiresPasswordSearch() -> {
                // Assessment-only: no external verifier required.
                _state.value =
                    RealWifiAuditSessionState(
                        network = network,
                        securityProfile = securityProfile,
                        strategy = strategy,
                        strategyLabel = plan.strategyLabel,
                        phase = RealWifiAuditPhase.Ready,
                        capability = RealWifiAuditCapability.Available,
                        technicalDetails = details.copy(externalVerifierLabel = "NotRequired"),
                        progress =
                            RealWifiAuditProgress(
                                strategyLabel = plan.strategyLabel,
                                workerCount = plan.limits.workerCountHint,
                                statusMessage = "ready",
                            ),
                        plan = plan,
                    )
            }
            externalCapability is ExternalVerifierCapability.Unavailable -> {
                _state.value =
                    RealWifiAuditSessionState(
                        network = network,
                        securityProfile = securityProfile,
                        strategy = strategy,
                        strategyLabel = plan.strategyLabel,
                        phase = RealWifiAuditPhase.BackendUnavailable,
                        capability =
                            RealWifiAuditCapability.Unavailable(
                                RealWifiAuditUnavailableReason.ExternalVerifierUnavailable,
                            ),
                        technicalDetails = details,
                        progress =
                            RealWifiAuditProgress(
                                strategyLabel = plan.strategyLabel,
                                workerCount = plan.limits.workerCountHint,
                                statusMessage = "backend_unavailable",
                            ),
                        plan = plan,
                    )
            }
            else -> {
                _state.value =
                    RealWifiAuditSessionState(
                        network = network,
                        securityProfile = securityProfile,
                        strategy = strategy,
                        strategyLabel = plan.strategyLabel,
                        phase = RealWifiAuditPhase.Ready,
                        capability = RealWifiAuditCapability.Available,
                        technicalDetails = details,
                        progress =
                            RealWifiAuditProgress(
                                strategyLabel = plan.strategyLabel,
                                workerCount = plan.limits.workerCountHint,
                                statusMessage = "ready",
                            ),
                        plan = plan,
                    )
            }
        }
    }

    suspend fun start(consent: RealNetworkConsent = RealNetworkConsent(true)) {
        val plan =
            mutex.withLock {
                val snapshot = _state.value
                val currentPlan = snapshot.plan ?: return
                // Only Ready may start — BackendUnavailable / CheckingCapability never enter execution.
                if (snapshot.phase !is RealWifiAuditPhase.Ready) return
                if (snapshot.capability is RealWifiAuditCapability.Unavailable) return
                check(consent.granted)
                stopRequested = false

                if (currentPlan.strategy is RealWifiAuditStrategy.Unsupported) {
                    startInvocationCount += 1
                    _state.update {
                        it.copy(phase = RealWifiAuditPhase.Completed(RealWifiAuditOutcome.Unsupported))
                    }
                    return
                }

                if (!currentPlan.strategy.requiresPasswordSearch()) {
                    startInvocationCount += 1
                    _state.update {
                        it.copy(
                            phase =
                                RealWifiAuditPhase.Completed(
                                    RealWifiAuditOutcome.AssessmentOnly(currentPlan.strategy),
                                ),
                        )
                    }
                    return
                }

                // Defense in depth — prepare already gated capability; do not count as start.
                if (externalVerifier.capability() is ExternalVerifierCapability.Unavailable) {
                    _state.update {
                        it.copy(
                            phase = RealWifiAuditPhase.BackendUnavailable,
                            capability =
                                RealWifiAuditCapability.Unavailable(
                                    RealWifiAuditUnavailableReason.ExternalVerifierUnavailable,
                                ),
                        )
                    }
                    return
                }

                startInvocationCount += 1
                _state.update {
                    it.copy(
                        phase = RealWifiAuditPhase.Preparing,
                        progress =
                            it.progress?.copy(statusMessage = "preparing")
                                ?: RealWifiAuditProgress(
                                    strategyLabel = currentPlan.strategyLabel,
                                    statusMessage = "preparing",
                                ),
                    )
                }
                currentPlan
            }

        _state.update {
            it.copy(
                phase = RealWifiAuditPhase.Searching,
                progress =
                    RealWifiAuditProgress(
                        strategyLabel = plan.strategyLabel,
                        workerCount = plan.limits.workerCountHint,
                        statusMessage = "searching",
                    ),
            )
        }

        val searchResult =
            search.search(
                plan = plan,
                progress =
                    RealWifiAuditProgressSink { progress ->
                        _state.update { current -> current.copy(progress = progress) }
                    },
            )

        when (searchResult) {
            is RealWifiSearchExecutionResult.Unavailable ->
                setCompleted(RealWifiAuditOutcome.Unavailable(searchResult.reason))
            is RealWifiSearchExecutionResult.Failed ->
                setFailed(searchResult.reason)
            is RealWifiSearchExecutionResult.Cancelled ->
                setCompleted(RealWifiAuditOutcome.Cancelled)
            is RealWifiSearchExecutionResult.Exhausted ->
                setCompleted(
                    RealWifiAuditOutcome.Completed(RealWifiSearchOutcome.Exhausted),
                )
            is RealWifiSearchExecutionResult.LimitReached ->
                setCompleted(
                    RealWifiAuditOutcome.Completed(RealWifiSearchOutcome.LimitReached),
                )
            is RealWifiSearchExecutionResult.Found ->
                setCompleted(
                    RealWifiAuditOutcome.Completed(RealWifiSearchOutcome.Found),
                )
        }
    }

    fun stop() {
        val phase = _state.value.phase
        if (phase !is RealWifiAuditPhase.Searching &&
            phase !is RealWifiAuditPhase.Preparing
        ) {
            return
        }
        stopRequested = true
        _state.update { it.copy(phase = RealWifiAuditPhase.Stopping) }
        search.cancel()
        externalVerifier.cancel()
    }

    fun clear() {
        stopRequested = true
        search.cancel()
        externalVerifier.cancel()
        stopRequested = false
        startInvocationCount = 0
        _state.value = RealWifiAuditSessionState()
    }

    private fun setCompleted(outcome: RealWifiAuditOutcome) {
        _state.update { it.copy(phase = RealWifiAuditPhase.Completed(outcome)) }
    }

    private fun setFailed(reason: RealWifiAuditFailureReason) {
        _state.update { it.copy(phase = RealWifiAuditPhase.Failed(reason)) }
    }
}
