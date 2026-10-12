package com.wifiauditlab.android.ui.realwifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.domain.audit.ApAuthCapability
import com.wifiauditlab.assessment.domain.audit.ApAuthUnavailableReason
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestDenial
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGate
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGateRequest
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGateResult
import com.wifiauditlab.assessment.domain.audit.FixedLabAccessPoint
import com.wifiauditlab.assessment.domain.audit.FixedLabAccessPointProvider
import com.wifiauditlab.assessment.domain.audit.FixedLabConnectionMatch
import com.wifiauditlab.assessment.domain.audit.LabSessionBudget
import com.wifiauditlab.assessment.domain.audit.LabSessionTerminationReason
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionOrchestrator
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionState
import com.wifiauditlab.assessment.domain.audit.LabValidationStartOutcome
import com.wifiauditlab.assessment.domain.audit.LabValidationStartRequest
import com.wifiauditlab.assessment.domain.audit.PlatformApAuthCapabilityProvider
import com.wifiauditlab.assessment.domain.audit.ValidationCredential
import com.wifiauditlab.assessment.domain.audit.VerificationMode
import com.wifiauditlab.assessment.domain.audit.supportsLabNetworkValidation
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.LabModePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RealWifiProbePhase {
    Idle,
    Checking,
    Denied,
    Running,
    Stopping,
    Completed,
    Cancelled,
    Failed,
}

enum class RealWifiProbeUiOutcome {
    Validated,
    AuthenticationRejected,
    Inconclusive,
}

data class RealWifiProbeUiState(
    val featureCompiledIn: Boolean = false,
    val fixedLab: FixedLabAccessPoint? = null,
    val labModeEnabled: Boolean = false,
    val connectedToFixedLab: Boolean = false,
    val connectionStatusKnown: Boolean = false,
    val capacityAvailable: Boolean = false,
    val capabilityMessage: String? = null,
    val passwordInput: String = "",
    val passwordVisible: Boolean = false,
    val passwordError: String? = null,
    val showConsentDialog: Boolean = false,
    val consentChecked: Boolean = false,
    val phase: RealWifiProbePhase = RealWifiProbePhase.Idle,
    val denials: List<AuthorizedApTestDenial> = emptyList(),
    val outcome: RealWifiProbeUiOutcome? = null,
    val resultDetail: String? = null,
    val terminationReason: LabSessionTerminationReason? = null,
    val learningExpanded: Boolean = false,
    val cooldownRemainingMs: Long = 0,
    val infoMessage: String? = null,
) {
    val isActive: Boolean
        get() =
            phase == RealWifiProbePhase.Checking ||
                phase == RealWifiProbePhase.Running ||
                phase == RealWifiProbePhase.Stopping

    val canRequestStart: Boolean
        get() =
            featureCompiledIn &&
                fixedLab != null &&
                labModeEnabled &&
                connectedToFixedLab &&
                passwordInput.length >= RealWifiProbeViewModel.MIN_PSK_LENGTH &&
                passwordError == null &&
                !isActive &&
                cooldownRemainingMs <= 0
}

/**
 * Didactic one-shot real Wi‑Fi credential probe (lab flavor).
 * Always goes through [LabValidationSessionOrchestrator] — never the Search Engine.
 */
class RealWifiProbeViewModel(
    private val featureCompiledIn: Boolean,
    private val fixedLabProvider: FixedLabAccessPointProvider,
    private val labModePreferences: LabModePreferences,
    private val connectionProvider: CurrentWifiConnectionProvider,
    private val capabilityProvider: PlatformApAuthCapabilityProvider,
    private val gate: AuthorizedApTestGate,
    private val orchestrator: LabValidationSessionOrchestrator,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            RealWifiProbeUiState(featureCompiledIn = featureCompiledIn),
        )
    val state: StateFlow<RealWifiProbeUiState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var cooldownUntilMs: Long = 0L

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val fixed = if (featureCompiledIn) fixedLabProvider.configuredOrNull() else null
            val labMode = labModePreferences.isLabModeEnabled()
            val connection = runCatching { connectionProvider.currentConnection() }.getOrNull()
            val match = fixed?.matchesConnection(connection)
            val capability = runCatching { capabilityProvider.capability() }.getOrNull()
            _state.update {
                it.copy(
                    featureCompiledIn = featureCompiledIn,
                    fixedLab = fixed,
                    labModeEnabled = labMode,
                    connectedToFixedLab = match == FixedLabConnectionMatch.Matched,
                    connectionStatusKnown =
                        match != null && match != FixedLabConnectionMatch.InsufficientInformation,
                    capacityAvailable = capability is ApAuthCapability.Available,
                    capabilityMessage = capabilityLabel(capability),
                    cooldownRemainingMs = (cooldownUntilMs - clockMs()).coerceAtLeast(0),
                )
            }
        }
    }

    fun onPasswordChanged(value: String) {
        _state.update {
            it.copy(
                passwordInput = value,
                passwordError = validatePassword(value, it.fixedLab),
            )
        }
    }

    fun togglePasswordVisibility() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun requestStart() {
        if (!_state.value.canRequestStart) return
        _state.update {
            it.copy(showConsentDialog = true, consentChecked = false)
        }
    }

    fun dismissConsentDialog() {
        _state.update {
            it.copy(showConsentDialog = false, consentChecked = false)
        }
    }

    fun setConsentChecked(checked: Boolean) {
        _state.update { it.copy(consentChecked = checked) }
    }

    fun confirmConsentAndStart() {
        val snapshot = _state.value
        if (!snapshot.showConsentDialog || !snapshot.consentChecked) return
        _state.update { it.copy(showConsentDialog = false) }
        startProbe(userConsentGranted = true)
    }

    fun toggleLearning() {
        _state.update { it.copy(learningExpanded = !it.learningExpanded) }
    }

    fun stop() {
        if (!_state.value.isActive) return
        _state.update { it.copy(phase = RealWifiProbePhase.Stopping) }
        orchestrator.userCancel()
    }

    private fun startProbe(userConsentGranted: Boolean) {
        val snapshot = _state.value
        if (snapshot.isActive || snapshot.cooldownRemainingMs > 0) return
        val fixed = snapshot.fixedLab ?: return
        if (!userConsentGranted) return

        sessionJob?.cancel()
        sessionJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        phase = RealWifiProbePhase.Checking,
                        denials = emptyList(),
                        outcome = null,
                        resultDetail = null,
                        terminationReason = null,
                        infoMessage = null,
                        consentChecked = false,
                    )
                }

                val connection = connectionProvider.currentConnection()
                val capability = capabilityProvider.capability()
                val preGate =
                    gate.evaluate(
                        AuthorizedApTestGateRequest(
                            mode = VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION,
                            labModeEnabled = labModePreferences.isLabModeEnabled(),
                            requestedSsid = Ssid(fixed.ssidNormalized),
                            requestedBssid = fixed.bssidNormalized,
                            securityFamily = fixed.securityFamily,
                            connection = connection,
                            labAuthorized = true,
                            userConsentGranted = true,
                            capability = capability,
                            fixedLabAccessPoint = fixed,
                        ),
                    )
                if (preGate is AuthorizedApTestGateResult.Denied) {
                    _state.update {
                        it.copy(
                            phase = RealWifiProbePhase.Denied,
                            denials = preGate.reasons,
                        )
                    }
                    return@launch
                }

                val password = _state.value.passwordInput
                val formatError = validatePassword(password, fixed)
                if (formatError != null) {
                    _state.update {
                        it.copy(phase = RealWifiProbePhase.Idle, passwordError = formatError)
                    }
                    return@launch
                }

                val credential = ValidationCredential.fromPlaintext(password)
                val outcome =
                    orchestrator.requestStart(
                        scope = viewModelScope,
                        request =
                            LabValidationStartRequest(
                                requestedSsid = Ssid(fixed.ssidNormalized),
                                requestedBssid = fixed.bssidNormalized,
                                securityFamily = fixed.securityFamily,
                                userConsentGranted = true,
                                budget = LabSessionBudget.standard(),
                                credential = credential,
                                verificationMode = VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION,
                                fixedLabAccessPoint = fixed,
                            ),
                    )
                when (outcome) {
                    is LabValidationStartOutcome.Denied -> {
                        _state.update {
                            it.copy(
                                phase = RealWifiProbePhase.Denied,
                                denials = outcome.reasons,
                            )
                        }
                    }
                    is LabValidationStartOutcome.RejectedConcurrent -> {
                        _state.update {
                            it.copy(
                                phase = RealWifiProbePhase.Failed,
                                infoMessage = "concurrent",
                            )
                        }
                    }
                    is LabValidationStartOutcome.Started -> {
                        _state.update { it.copy(phase = RealWifiProbePhase.Running) }
                        val terminal =
                            orchestrator.session
                                .mapNotNull { it }
                                .first { it.state.isTerminal }
                        _state.update { ui ->
                            ui.copy(
                                phase = terminal.state.toUiPhase(),
                                terminationReason = terminal.terminationReason,
                                resultDetail = terminal.resultSummary,
                                denials = terminal.admissionDenials.ifEmpty { ui.denials },
                                outcome =
                                    if (terminal.state == LabValidationSessionState.Completed ||
                                        terminal.state == LabValidationSessionState.Failed
                                    ) {
                                        mapOutcome(terminal.resultSummary)
                                    } else {
                                        null
                                    },
                            )
                        }
                        beginCooldown()
                    }
                }
            }
    }

    private fun beginCooldown() {
        cooldownUntilMs = clockMs() + COOLDOWN_MS
        viewModelScope.launch {
            while (true) {
                val remaining = (cooldownUntilMs - clockMs()).coerceAtLeast(0)
                _state.update { it.copy(cooldownRemainingMs = remaining) }
                if (remaining <= 0) break
                delay(200)
            }
        }
    }

    private fun validatePassword(
        value: String,
        fixed: FixedLabAccessPoint?,
    ): String? {
        if (fixed == null) return null
        if (!fixed.securityFamily.supportsLabNetworkValidation()) return "unsupported_family"
        if (value.isEmpty()) return null
        if (value.length < MIN_PSK_LENGTH) return "psk_too_short"
        if (value.length > MAX_PSK_LENGTH) return "psk_too_long"
        return null
    }

    private fun capabilityLabel(capability: ApAuthCapability?): String? =
        when (capability) {
            null -> null
            ApAuthCapability.Available -> "available"
            is ApAuthCapability.Unavailable ->
                when (capability.reason) {
                    ApAuthUnavailableReason.RequiresApi34 -> "requires_api34"
                    ApAuthUnavailableReason.RequiresStaConcurrency -> "requires_sta"
                    ApAuthUnavailableReason.PermissionMissing -> "permission"
                    ApAuthUnavailableReason.UnsupportedSecurityFamily -> "family"
                    else -> "unavailable"
                }
        }

    private fun mapOutcome(summary: String?): RealWifiProbeUiOutcome? {
        val text = summary.orEmpty()
        return when {
            text == "Validated" || text.startsWith("Validated") -> RealWifiProbeUiOutcome.Validated
            text.contains("AuthenticationRejected") -> RealWifiProbeUiOutcome.AuthenticationRejected
            text.isBlank() -> null
            else -> RealWifiProbeUiOutcome.Inconclusive
        }
    }

    private fun LabValidationSessionState.toUiPhase(): RealWifiProbePhase =
        when (this) {
            LabValidationSessionState.Created -> RealWifiProbePhase.Checking
            LabValidationSessionState.Admitted,
            LabValidationSessionState.Running,
            -> RealWifiProbePhase.Running
            LabValidationSessionState.Stopping -> RealWifiProbePhase.Stopping
            LabValidationSessionState.Completed -> RealWifiProbePhase.Completed
            LabValidationSessionState.Cancelled -> RealWifiProbePhase.Cancelled
            LabValidationSessionState.Denied -> RealWifiProbePhase.Denied
            LabValidationSessionState.Failed -> RealWifiProbePhase.Failed
        }

    override fun onCleared() {
        if (_state.value.isActive) {
            orchestrator.userCancel()
        }
        super.onCleared()
    }

    companion object {
        const val MIN_PSK_LENGTH: Int = 8
        const val MAX_PSK_LENGTH: Int = 63
        const val COOLDOWN_MS: Long = 2_500
    }
}
