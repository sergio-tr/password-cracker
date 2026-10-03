package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.LabModePreferences
import com.wifiauditlab.assessment.port.WifiConnectionMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class LabValidationStartRequest(
    val requestedSsid: Ssid,
    val requestedBssid: Bssid,
    val securityFamily: SecurityFamily,
    val userConsentGranted: Boolean,
    val budget: LabSessionBudget = LabSessionBudget.standard(),
)

sealed interface LabValidationStartOutcome {
    data class Started(
        val session: LabValidationSession,
    ) : LabValidationStartOutcome

    data class Denied(
        val reasons: List<AuthorizedApTestDenial>,
        val session: LabValidationSession?,
    ) : LabValidationStartOutcome

    data class RejectedConcurrent(
        val activeSessionId: String,
    ) : LabValidationStartOutcome
}

/**
 * Central orchestrator for [VerificationMode.LAB_NETWORK_VALIDATION] sessions.
 *
 * UI / ViewModel must not duplicate gate, budget, monitor, or concurrency rules.
 * Maximum one active LAB session at a time.
 */
class LabValidationSessionOrchestrator(
    private val gate: AuthorizedApTestGate = AuthorizedApTestGate(),
    private val connectionProvider: CurrentWifiConnectionProvider,
    private val connectionMonitor: WifiConnectionMonitor,
    private val labModePreferences: LabModePreferences,
    private val registry: AuthorizedLabNetworkStore,
    private val adapter: NetworkValidationAdapter,
    private val evidenceLog: LabSessionEvidenceLog = InMemoryLabSessionEvidenceLog(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val idFactory: () -> String = {
        "lab-${clockMs()}-${(0..9999).random()}"
    },
) {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<LabValidationSession?>(null)
    val session: StateFlow<LabValidationSession?> = _session.asStateFlow()

    private var sessionJob: Job? = null
    private val stopLock = Any()

    @Volatile
    private var stopReason: LabSessionTerminationReason? = null

    fun evidence(): LabSessionEvidenceLog = evidenceLog

    private fun peekStopReason(): LabSessionTerminationReason? = stopReason

    private fun trySetStopReason(reason: LabSessionTerminationReason): Boolean =
        synchronized(stopLock) {
            if (stopReason != null) return false
            stopReason = reason
            true
        }

    /**
     * Evaluates the gate, creates at most one session, runs the adapter under budget/timeout,
     * and monitors network / lab-mode / registry invariants until a terminal state.
     */
    suspend fun requestStart(
        scope: CoroutineScope,
        request: LabValidationStartRequest,
    ): LabValidationStartOutcome =
        mutex.withLock {
            val current = _session.value
            if (current != null && !current.state.isTerminal) {
                return@withLock LabValidationStartOutcome.RejectedConcurrent(current.sessionId)
            }

            stopReason = null
            sessionJob?.cancel()
            sessionJob = null

            val connection = connectionProvider.currentConnection()
            val capability = adapter.capability()
            val labModeEnabled = labModePreferences.isLabModeEnabled()
            val key = AuthorizedLabNetworkKey.of(request.requestedSsid.value, request.securityFamily)
            val labAuthorized = registry.isAuthorized(key)

            val gateResult =
                gate.evaluate(
                    AuthorizedApTestGateRequest(
                        mode = VerificationMode.LAB_NETWORK_VALIDATION,
                        labModeEnabled = labModeEnabled,
                        requestedSsid = request.requestedSsid,
                        requestedBssid = request.requestedBssid,
                        securityFamily = request.securityFamily,
                        connection = connection,
                        labAuthorized = labAuthorized,
                        userConsentGranted = request.userConsentGranted,
                        capability = capability,
                    ),
                )

            val sessionId = idFactory()
            val now = clockMs()

            when (gateResult) {
                is AuthorizedApTestGateResult.Denied -> {
                    val deniedSession =
                        LabValidationSession(
                            sessionId = sessionId,
                            networkSnapshot =
                                snapshotOrFallback(connection, request.securityFamily)
                                    ?: LabNetworkSnapshot(
                                        ssid = request.requestedSsid.value.trim(),
                                        securityFamily = request.securityFamily,
                                    ),
                            createdAtEpochMs = now,
                            finishedAtEpochMs = now,
                            state = LabValidationSessionState.Denied,
                            consentGranted = request.userConsentGranted,
                            capabilitySnapshot = capability,
                            admissionDenials = gateResult.reasons,
                            budget = request.budget,
                            terminationReason = LabSessionTerminationReason.GateDenied,
                            resultSummary = "Denied:${gateResult.reasons.joinToString(",")}",
                        )
                    _session.value = deniedSession
                    appendEvidence(
                        deniedSession,
                        event = "gate_denied",
                        adapterCategory = null,
                        detail = gateResult.reasons.joinToString(","),
                    )
                    return@withLock LabValidationStartOutcome.Denied(gateResult.reasons, deniedSession)
                }
                AuthorizedApTestGateResult.Admitted -> Unit
            }

            val snapshot =
                LabNetworkSnapshot.fromConnection(connection!!, request.securityFamily)
                    ?: run {
                        val reasons = listOf(AuthorizedApTestDenial.InsufficientConnectionInfo)
                        val deniedSession =
                            LabValidationSession(
                                sessionId = sessionId,
                                networkSnapshot =
                                    LabNetworkSnapshot(
                                        ssid = request.requestedSsid.value.trim(),
                                        securityFamily = request.securityFamily,
                                    ),
                                createdAtEpochMs = now,
                                finishedAtEpochMs = now,
                                state = LabValidationSessionState.Denied,
                                consentGranted = request.userConsentGranted,
                                capabilitySnapshot = capability,
                                admissionDenials = reasons,
                                budget = request.budget,
                                terminationReason = LabSessionTerminationReason.GateDenied,
                                resultSummary = "Denied:InsufficientConnectionInfo",
                            )
                        _session.value = deniedSession
                        appendEvidence(deniedSession, "snapshot_failed", null, "InsufficientConnectionInfo")
                        return@withLock LabValidationStartOutcome.Denied(reasons, deniedSession)
                    }

            // Target must be the current network (gate already matched requested vs connection).
            if (snapshot.ssid != request.requestedSsid.value.trim()) {
                val reasons = listOf(AuthorizedApTestDenial.TargetMismatch)
                val deniedSession =
                    LabValidationSession(
                        sessionId = sessionId,
                        networkSnapshot = snapshot,
                        createdAtEpochMs = now,
                        finishedAtEpochMs = now,
                        state = LabValidationSessionState.Denied,
                        consentGranted = request.userConsentGranted,
                        capabilitySnapshot = capability,
                        admissionDenials = reasons,
                        budget = request.budget,
                        terminationReason = LabSessionTerminationReason.GateDenied,
                        resultSummary = "Denied:TargetMismatch",
                    )
                _session.value = deniedSession
                appendEvidence(deniedSession, "target_mismatch", null, null)
                return@withLock LabValidationStartOutcome.Denied(reasons, deniedSession)
            }

            var session =
                LabValidationSession(
                    sessionId = sessionId,
                    networkSnapshot = snapshot,
                    createdAtEpochMs = now,
                    startedAtEpochMs = now,
                    state = LabValidationSessionState.Admitted,
                    consentGranted = request.userConsentGranted,
                    capabilitySnapshot = capability,
                    budget = request.budget,
                )
            _session.value = session
            appendEvidence(session, "admitted", null, null)

            sessionJob =
                scope.launch {
                    runSession(session)
                }

            LabValidationStartOutcome.Started(session)
        }

    /** Cooperative user cancel. Hiding the screen is not a cancel. */
    fun userCancel() {
        requestStop(LabSessionTerminationReason.UserCancelled)
        sessionJob?.cancel()
    }

    private fun requestStop(reason: LabSessionTerminationReason) {
        trySetStopReason(reason)
        val current = _session.value ?: return
        if (!current.state.isTerminal && current.state != LabValidationSessionState.Stopping) {
            publish(current.copy(state = LabValidationSessionState.Stopping))
        }
    }

    private suspend fun runSession(initial: LabValidationSession) {
        var session = initial
        try {
            session = session.copy(state = LabValidationSessionState.Running)
            publish(session)
            appendEvidence(session, "running", null, null)

            coroutineScope {
                val monitorJob =
                    launch {
                        monitorInvariants(session.networkSnapshot, session.budget)
                    }
                try {
                    val finishedInBudget =
                        withTimeoutOrNull(session.budget.maxDurationMs) {
                            executeAdapter(session)
                        }
                    if (finishedInBudget == null) {
                        trySetStopReason(LabSessionTerminationReason.BudgetExhausted)
                    }
                } catch (e: CancellationException) {
                    trySetStopReason(LabSessionTerminationReason.UserCancelled)
                    throw e
                } finally {
                    monitorJob.cancel()
                }
            }
        } catch (_: CancellationException) {
            // stopReason already set by userCancel / monitor / timeout path
        } finally {
            finalizeSession()
        }
    }

    private suspend fun executeAdapter(session: LabValidationSession) {
        if (session.operationsConsumed >= session.budget.maxOperations) {
            trySetStopReason(LabSessionTerminationReason.BudgetExhausted)
            return
        }
        val context =
            AuthorizedValidationContext(
                sessionId = session.sessionId,
                networkSnapshot = session.networkSnapshot,
                budget = session.budget,
            )
        val updated = session.copy(operationsConsumed = session.operationsConsumed + 1)
        publish(updated)

        val result =
            try {
                withTimeoutOrNull(session.budget.operationTimeoutMs) {
                    adapter.validateOnce(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val errorCategory = t::class.simpleName ?: "Error"
                trySetStopReason(LabSessionTerminationReason.AdapterError)
                NetworkValidationResult.PlatformError(errorCategory)
            }

        if (peekStopReason() != null) return

        if (result == null) {
            trySetStopReason(LabSessionTerminationReason.Timeout)
            return
        }

        when (result) {
            NetworkValidationResult.Succeeded ->
                completeSuccess(updated, LabSessionTerminationReason.Completed, result.category)
            NetworkValidationResult.Rejected ->
                completeSuccess(updated, LabSessionTerminationReason.AdapterRejected, result.category)
            is NetworkValidationResult.Unavailable ->
                completeSuccess(updated, LabSessionTerminationReason.AdapterUnavailable, result.category)
            NetworkValidationResult.Cancelled ->
                trySetStopReason(LabSessionTerminationReason.UserCancelled)
            is NetworkValidationResult.PlatformError ->
                completeSuccess(updated, LabSessionTerminationReason.AdapterError, result.category)
        }
    }

    private fun completeSuccess(
        session: LabValidationSession,
        reason: LabSessionTerminationReason,
        resultCategory: String,
    ) {
        if (!trySetStopReason(reason)) return
        val finished =
            session.copy(
                state =
                    when (reason) {
                        LabSessionTerminationReason.Completed,
                        LabSessionTerminationReason.AdapterRejected,
                        LabSessionTerminationReason.AdapterUnavailable,
                        -> LabValidationSessionState.Completed
                        else -> LabValidationSessionState.Failed
                    },
                finishedAtEpochMs = clockMs(),
                terminationReason = reason,
                resultSummary = resultCategory,
                consentGranted = false,
            )
        publish(finished)
        appendEvidence(finished, "terminal", resultCategory, null)
    }

    private fun finalizeSession() {
        val current = _session.value ?: return
        if (current.state.isTerminal) {
            if (current.consentGranted) {
                publish(current.copy(consentGranted = false))
            }
            return
        }
        val reason = peekStopReason() ?: LabSessionTerminationReason.UserCancelled
        val terminalState =
            when (reason) {
                LabSessionTerminationReason.UserCancelled -> LabValidationSessionState.Cancelled
                LabSessionTerminationReason.Completed,
                LabSessionTerminationReason.AdapterRejected,
                LabSessionTerminationReason.AdapterUnavailable,
                -> LabValidationSessionState.Completed
                LabSessionTerminationReason.GateDenied,
                LabSessionTerminationReason.ConcurrentSessionRejected,
                -> LabValidationSessionState.Denied
                else -> LabValidationSessionState.Failed
            }
        val finished =
            current.copy(
                state = terminalState,
                finishedAtEpochMs = clockMs(),
                terminationReason = reason,
                resultSummary = current.resultSummary ?: reason.name,
                consentGranted = false,
            )
        publish(finished)
        appendEvidence(finished, "terminal", finished.resultSummary, reason.name)
    }

    private suspend fun monitorInvariants(
        snapshot: LabNetworkSnapshot,
        budget: LabSessionBudget,
    ) {
        coroutineScope {
            launch {
                connectionMonitor.observe().collect { connection ->
                    evaluateConnectionInvariant(snapshot, connection)
                }
            }
            launch {
                while (peekStopReason() == null) {
                    evaluateSoftInvariants(snapshot, budget)
                    delay(INVARIANT_POLL_MS)
                }
            }
        }
    }

    private suspend fun evaluateConnectionInvariant(
        snapshot: LabNetworkSnapshot,
        connection: CurrentWifiConnection?,
    ) {
        if (peekStopReason() != null) return
        if (connection == null) {
            requestStop(LabSessionTerminationReason.NetworkLost)
            sessionJob?.cancel()
            return
        }
        if (!snapshot.matches(connection)) {
            requestStop(LabSessionTerminationReason.NetworkChanged)
            sessionJob?.cancel()
        }
    }

    private suspend fun evaluateSoftInvariants(
        snapshot: LabNetworkSnapshot,
        budget: LabSessionBudget,
    ) {
        if (peekStopReason() != null) return
        if (!labModePreferences.isLabModeEnabled()) {
            requestStop(LabSessionTerminationReason.LabModeDisabled)
            sessionJob?.cancel()
            return
        }
        if (!registry.isAuthorized(snapshot.registryKey())) {
            requestStop(LabSessionTerminationReason.NetworkAuthorizationRevoked)
            sessionJob?.cancel()
            return
        }
        val current = _session.value
        if (current?.startedAtEpochMs != null &&
            clockMs() - current.startedAtEpochMs > budget.maxDurationMs
        ) {
            requestStop(LabSessionTerminationReason.BudgetExhausted)
            sessionJob?.cancel()
        }
    }

    companion object {
        /** Soft poll for lab-mode / registry / duration — not aggressive Wi‑Fi scanning. */
        const val INVARIANT_POLL_MS: Long = 200
    }

    private fun publish(session: LabValidationSession) {
        _session.value = session
    }

    private fun appendEvidence(
        session: LabValidationSession,
        event: String,
        adapterCategory: String?,
        detail: String?,
    ) {
        evidenceLog.append(
            LabSessionEvidenceRecord(
                sessionId = session.sessionId,
                timestampEpochMs = clockMs(),
                event = event,
                verificationMode = VerificationMode.LAB_NETWORK_VALIDATION,
                networkSsid = session.networkSnapshot.ssid,
                networkFamily = session.networkSnapshot.securityFamily.name,
                capability = capabilityLabel(session.capabilitySnapshot),
                state = session.state,
                terminationReason = session.terminationReason,
                adapterResultCategory = adapterCategory,
                operationsConsumed = session.operationsConsumed,
                detail = detail,
            ),
        )
    }

    private fun capabilityLabel(capability: ApAuthCapability): String =
        when (capability) {
            ApAuthCapability.Available -> "Available"
            is ApAuthCapability.Unavailable -> "Unavailable:${capability.reason.name}"
        }

    private fun snapshotOrFallback(
        connection: CurrentWifiConnection?,
        fallbackFamily: SecurityFamily,
    ): LabNetworkSnapshot? =
        connection?.let { LabNetworkSnapshot.fromConnection(it, fallbackFamily) }
}

/** ADR / plan alias. */
typealias AuthorizedApTestSessionOrchestrator = LabValidationSessionOrchestrator
