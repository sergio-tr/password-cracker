package com.wifiauditlab.android.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.ApAuthCapability
import com.wifiauditlab.assessment.domain.audit.ApAuthUnavailableReason
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGate
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGateRequest
import com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGateResult
import com.wifiauditlab.assessment.domain.audit.AuthorizedLabNetworkKey
import com.wifiauditlab.assessment.domain.audit.AuthorizedLabNetworkStore
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.LabSessionBudget
import com.wifiauditlab.assessment.domain.audit.LabSessionTerminationReason
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionOrchestrator
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionState
import com.wifiauditlab.assessment.domain.audit.LabValidationStartOutcome
import com.wifiauditlab.assessment.domain.audit.LabValidationStartRequest
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibility
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibilityChecker
import com.wifiauditlab.assessment.domain.audit.PasswordAuditResultComposer
import com.wifiauditlab.assessment.domain.audit.PlatformApAuthCapabilityProvider
import com.wifiauditlab.assessment.domain.audit.SecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.ValidationCredential
import com.wifiauditlab.assessment.domain.audit.VerificationMode
import com.wifiauditlab.assessment.domain.audit.supportsSharedPasswordAudit
import com.wifiauditlab.assessment.domain.audit.unsupportedAuditReason
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.LabModePreferences
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.audit.AutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudget
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudgetPreset
import com.wifiauditlab.lab.domain.audit.PasswordAuditContext
import com.wifiauditlab.lab.domain.audit.PasswordAuditEngineChoice
import com.wifiauditlab.lab.domain.audit.PasswordAuditPerformanceProfile
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlan
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlanResult
import com.wifiauditlab.lab.domain.engine.CancellationController
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.engine.WorkerAwareLabSearchEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Quick Audit: password source, automatic plan, and search execution.
 *
 * Default path is [VerificationMode.LOCAL_AUDIT] (encapsulated verifier; no AP).
 * [VerificationMode.LAB_NETWORK_VALIDATION] goes through [LabValidationSessionOrchestrator]
 * (ADR-004 / F1): gate, snapshot, budget, monitors, typed result. Stock
 * [com.wifiauditlab.assessment.domain.audit.AndroidValidationAdapter] stays Unavailable.
 */
class PasswordAuditViewModel(
    private val targetStore: PasswordAuditTargetStore,
    private val planner: AutomaticPasswordAuditPlanner,
    private val getSavedNetwork: GetSavedNetwork,
    private val revealSecret: RevealSavedNetworkSecret,
    private val updateSecret: UpdateSavedNetworkSecret,
    private val createSavedNetwork: CreateSavedNetwork,
    private val eligibilityChecker: PasswordAuditEligibilityChecker? = null,
    private val assessNetworkSecurity: AssessNetworkSecurity? = null,
    private val engine: LabSearchEngine,
    private val calibration: SearchCalibrationService? = null,
    private val strengthAnalyzer: SecretStrengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
    private val authorizedLabStore: AuthorizedLabNetworkStore? = null,
    private val apCapabilityProvider: PlatformApAuthCapabilityProvider? = null,
    private val connectionProvider: CurrentWifiConnectionProvider? = null,
    private val labModePreferences: LabModePreferences? = null,
    private val apTestGate: AuthorizedApTestGate = AuthorizedApTestGate(),
    private val labSessionOrchestrator: LabValidationSessionOrchestrator? = null,
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordAuditUiState())
    val state: StateFlow<PasswordAuditUiState> = _state.asStateFlow()

    private var calibratedThroughput: Double? = null
    private var request: PasswordAuditRequest? = null
    private var searchJob: Job? = null
    private var cancellation: CancellationController? = null
    private var networkAssessmentCache: SecurityAssessment? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        val current = targetStore.current
        if (current == null) {
            _state.value = PasswordAuditUiState(missingTarget = true, loadingPlan = false)
            return
        }
        request = current
        val network = current.network
        _state.value =
            PasswordAuditUiState(
                displayName = network.displayName,
                ssidLabel = network.ssid.toString(),
                securityFamily = network.securityProfile.family,
                wifiStandard = network.wifiStandard,
                band = network.band,
                savedNetworkId = current.savedNetworkId,
                loadingPlan = true,
                saveToVault = false,
                allowsAuthorizedApTest = current.allowsAuthorizedApTest,
                labModeEnabled = labModePreferences?.isLabModeEnabled() == true,
                mode = PasswordAuditInteractionMode.Automatic,
                preset = PasswordAuditBudgetPreset.Standard,
            )
        viewModelScope.launch(ioDispatcher) {
            val vaultAvailable =
                current.savedNetworkId?.let { id ->
                    getSavedNetwork(id)?.hasSecret == true
                } == true
            _state.update {
                it.copy(
                    vaultSecretAvailable = vaultAvailable,
                    secretSource =
                        if (vaultAvailable) {
                            PasswordAuditSecretSource.Vault
                        } else {
                            PasswordAuditSecretSource.Manual
                        },
                    labModeEnabled = labModePreferences?.isLabModeEnabled() == true,
                )
            }
            calibratedThroughput = calibration?.lastRecord()?.measuredAttemptsPerSecond
            networkAssessmentCache =
                assessNetworkSecurity?.let { assess ->
                    runCatching { assess(current.network.securityProfile) }.getOrNull()
                }
            _state.update { it.copy(networkAssessment = networkAssessmentCache) }
            refreshApTestAdmission()
            rebuildPlan()
        }
    }

    fun selectVerificationMode(mode: VerificationMode) {
        if (_state.value.isActive) return
        val req = request
        val labModeOn = labModePreferences?.isLabModeEnabled() == true
        if (mode == VerificationMode.LAB_NETWORK_VALIDATION &&
            (req?.allowsAuthorizedApTest != true || !labModeOn)
        ) {
            return
        }
        _state.update {
            it.copy(
                verificationMode = mode,
                startBlockedReason = null,
                labModeEnabled = labModeOn,
                apTestConsentGranted =
                    if (mode == VerificationMode.LOCAL_AUDIT) false else it.apTestConsentGranted,
            )
        }
        viewModelScope.launch(ioDispatcher) { refreshApTestAdmission() }
    }

    /** Re-read Settings preference (e.g. user toggled lab mode then returned). */
    fun refreshLabModePreference() {
        val enabled = labModePreferences?.isLabModeEnabled() == true
        _state.update {
            it.copy(
                labModeEnabled = enabled,
                verificationMode =
                    if (!enabled && it.verificationMode == VerificationMode.LAB_NETWORK_VALIDATION) {
                        VerificationMode.LOCAL_AUDIT
                    } else {
                        it.verificationMode
                    },
                apTestConsentGranted =
                    if (!enabled) false else it.apTestConsentGranted,
            )
        }
        viewModelScope.launch(ioDispatcher) { refreshApTestAdmission() }
    }

    fun setApTestConsent(granted: Boolean) {
        if (_state.value.isActive) return
        _state.update { it.copy(apTestConsentGranted = granted, startBlockedReason = null) }
        viewModelScope.launch(ioDispatcher) { refreshApTestAdmission() }
    }

    fun setLabAuthorizedForTarget(authorized: Boolean) {
        if (_state.value.isActive) return
        val req = request ?: return
        val store = authorizedLabStore ?: return
        val key =
            AuthorizedLabNetworkKey.of(
                req.network.ssid.value,
                req.network.securityProfile.family,
            )
        viewModelScope.launch(ioDispatcher) {
            store.setAuthorized(key, authorized)
            refreshApTestAdmission()
        }
    }

    private suspend fun refreshApTestAdmission() {
        val req = request ?: return
        val capability =
            apCapabilityProvider?.capability()
                ?: ApAuthCapability.Unavailable(ApAuthUnavailableReason.NotImplemented)
        val labModeEnabled = labModePreferences?.isLabModeEnabled() == true
        val key =
            AuthorizedLabNetworkKey.of(
                req.network.ssid.value,
                req.network.securityProfile.family,
            )
        val labAuthorized = authorizedLabStore?.isAuthorized(key) == true
        val denials =
            if (_state.value.verificationMode != VerificationMode.LAB_NETWORK_VALIDATION) {
                emptyList()
            } else {
                val bssid =
                    req.network.bssid
                        ?: req.observation?.bssid
                        ?: Bssid.of("02:00:00:00:00:00")
                when (
                    val gateResult =
                        apTestGate.evaluate(
                            AuthorizedApTestGateRequest(
                                mode = VerificationMode.LAB_NETWORK_VALIDATION,
                                labModeEnabled = labModeEnabled,
                                requestedSsid = req.network.ssid,
                                requestedBssid = bssid,
                                securityFamily = req.network.securityProfile.family,
                                connection = connectionProvider?.currentConnection(),
                                labAuthorized = labAuthorized,
                                userConsentGranted = _state.value.apTestConsentGranted,
                                capability = capability,
                            ),
                        )
                ) {
                    AuthorizedApTestGateResult.Admitted -> emptyList()
                    is AuthorizedApTestGateResult.Denied -> gateResult.reasons
                }
            }
        _state.update {
            it.copy(
                labModeEnabled = labModeEnabled,
                apAuthCapability = capability,
                labAuthorizedForTarget = labAuthorized,
                apTestDenials = denials,
            )
        }
    }

    fun selectSecretSource(source: PasswordAuditSecretSource) {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                secretSource = source,
                passwordError = null,
                infoMessage = null,
                passwordInput = if (source == PasswordAuditSecretSource.Vault) "" else it.passwordInput,
                passwordVisible = false,
                saveToVault = if (source == PasswordAuditSecretSource.Vault) false else it.saveToVault,
            )
        }
        refreshStartGate()
    }

    fun onPasswordChanged(value: String) {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                passwordInput = value,
                secretSource = PasswordAuditSecretSource.Manual,
                passwordError = null,
                infoMessage = null,
                strength = if (value.isNotEmpty()) strengthAnalyzer.analyze(value) else null,
            )
        }
        refreshStartGate()
    }

    fun togglePasswordVisibility() {
        if (_state.value.secretSource != PasswordAuditSecretSource.Manual) return
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun clearPassword() {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                passwordInput = "",
                passwordVisible = false,
                passwordError = null,
                strength = null,
                infoMessage = null,
            )
        }
        refreshStartGate()
    }

    fun setSaveToVault(checked: Boolean) {
        if (_state.value.isActive) return
        if (_state.value.secretSource != PasswordAuditSecretSource.Manual) return
        _state.update { it.copy(saveToVault = checked) }
    }

    fun selectMode(mode: PasswordAuditInteractionMode) {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                mode = mode,
                advancedExpanded = mode == PasswordAuditInteractionMode.Advanced,
                infoMessage = null,
            )
        }
        if (mode == PasswordAuditInteractionMode.Automatic) {
            resetToAutomaticDefaults()
        }
    }

    fun resetToAutomaticDefaults() {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                mode = PasswordAuditInteractionMode.Automatic,
                preset = PasswordAuditBudgetPreset.Standard,
                advancedExpanded = false,
                customDurationSeconds = "60",
                customMaxAttempts = "",
                planDetailsExpanded = false,
                searchStagesExpanded = false,
                infoMessage = null,
            )
        }
        rebuildPlan()
    }

    fun setAdvancedExpanded(expanded: Boolean) {
        _state.update {
            it.copy(
                advancedExpanded = expanded,
                mode =
                    if (expanded) {
                        PasswordAuditInteractionMode.Advanced
                    } else {
                        PasswordAuditInteractionMode.Automatic
                    },
            )
        }
    }

    fun setPlanDetailsExpanded(expanded: Boolean) {
        _state.update { it.copy(planDetailsExpanded = expanded) }
    }

    fun setSearchStagesExpanded(expanded: Boolean) {
        _state.update { it.copy(searchStagesExpanded = expanded) }
    }

    fun toggleSearchStagesExpanded() {
        _state.update { it.copy(searchStagesExpanded = !it.searchStagesExpanded) }
    }

    fun selectPreset(preset: PasswordAuditBudgetPreset) {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                preset = preset,
                advancedExpanded =
                    preset == PasswordAuditBudgetPreset.Custom ||
                        it.mode == PasswordAuditInteractionMode.Advanced,
                infoMessage = null,
            )
        }
        rebuildPlan()
    }

    fun onCustomDurationChanged(value: String) {
        if (_state.value.isActive) return
        _state.update { it.copy(customDurationSeconds = value.filter { ch -> ch.isDigit() }) }
    }

    fun onCustomAttemptsChanged(value: String) {
        if (_state.value.isActive) return
        _state.update { it.copy(customMaxAttempts = value.filter { ch -> ch.isDigit() }) }
    }

    fun applyCustomBudget() {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                preset = PasswordAuditBudgetPreset.Custom,
                mode = PasswordAuditInteractionMode.Advanced,
                advancedExpanded = true,
            )
        }
        rebuildPlan()
    }

    fun onStartAuditClicked() {
        if (_state.value.isActive) return
        val snapshot = _state.value
        if (snapshot.verificationMode == VerificationMode.LAB_NETWORK_VALIDATION) {
            startLabNetworkValidation(snapshot)
            return
        }
        if (!snapshot.hasSecretReady) {
            _state.update {
                it.copy(passwordError = PasswordAuditUiError.MissingPassword)
            }
            return
        }
        val plan = snapshot.plan
        if (plan == null) {
            _state.update {
                it.copy(startBlockedReason = PasswordAuditUiError.NoValidPlan)
            }
            return
        }
        if (snapshot.feasibilityRating == FeasibilityRating.Invalid) {
            _state.update {
                it.copy(startBlockedReason = PasswordAuditUiError.InvalidConfig)
            }
            return
        }

        searchJob =
            viewModelScope.launch(ioDispatcher) {
                val req = request
                if (req != null && eligibilityChecker != null && req.observation != null) {
                    when (eligibilityChecker.check(req.observation)) {
                        is PasswordAuditEligibility.EligibleConnectedNetwork -> Unit
                        PasswordAuditEligibility.NotCurrentlyConnected -> {
                            _state.update {
                                it.copy(
                                    connectionLostMessage = PasswordAuditUiError.NotConnected,
                                    startBlockedReason = PasswordAuditUiError.ConnectionLost,
                                )
                            }
                            return@launch
                        }
                        else -> {
                            _state.update {
                                it.copy(
                                    startBlockedReason = PasswordAuditUiError.NoLongerEligible,
                                )
                            }
                            return@launch
                        }
                    }
                }

                val password =
                    resolvePasswordForStart() ?: run {
                        _state.update {
                            it.copy(passwordError = PasswordAuditUiError.PasswordUnavailable)
                        }
                        return@launch
                    }

                if (snapshot.saveToVault && snapshot.secretSource == PasswordAuditSecretSource.Manual) {
                    persistSecretBestEffort(password)
                }

                val strength = strengthAnalyzer.analyze(password)
                val verifier = EncapsulatedPasswordVerifier.encapsulate(password)
                val challenge =
                    LabChallenge.withEncapsulatedVerifier(
                        policy = plan.blindChallengePolicy,
                        verifier = verifier,
                        seed = plan.searchPlan.seed,
                    )
                applyEngineSelection(plan)

                val controller = CancellationController()
                cancellation = controller

                _state.update {
                    it.copy(
                        passwordInput = "",
                        passwordVisible = false,
                        passwordError = null,
                        strength = strength,
                        infoMessage = null,
                        connectionLostMessage = null,
                        searchState = SearchState.Preparing,
                        metrics = null,
                        outcome = null,
                        discoveredWithinBudget = false,
                        errorMessage = null,
                        errorDetails = null,
                        showErrorDetails = false,
                        resultReport = null,
                    )
                }

                engine.run(challenge, plan.searchPlan, plan.searchLimits, controller).collect { event ->
                    _state.update { current -> current.reduce(event) }
                }
            }
    }

    fun stop() {
        if (!_state.value.isActive) return
        if (_state.value.verificationMode == VerificationMode.LAB_NETWORK_VALIDATION &&
            labSessionOrchestrator != null
        ) {
            _state.update {
                it.copy(
                    labValidationPhase = LabValidationUiPhase.Running,
                    searchState = SearchState.Cancelling,
                )
            }
            labSessionOrchestrator.userCancel()
            return
        }
        _state.update { it.copy(searchState = SearchState.Cancelling) }
        cancellation?.cancel()
    }

    private fun startLabNetworkValidation(snapshot: PasswordAuditUiState) {
        val orchestrator = labSessionOrchestrator
        val req = request
        if (orchestrator == null || req == null) {
            _state.update {
                it.copy(startBlockedReason = PasswordAuditUiError.ApTestNotAdmitted)
            }
            viewModelScope.launch(ioDispatcher) { refreshApTestAdmission() }
            return
        }
        if (!snapshot.hasSecretReady) {
            _state.update {
                it.copy(passwordError = PasswordAuditUiError.MissingPassword)
            }
            return
        }
        val bssid =
            req.network.bssid
                ?: req.observation?.bssid
                ?: Bssid.of("02:00:00:00:00:00")
        searchJob =
            viewModelScope.launch(ioDispatcher) {
                _state.update {
                    it.copy(
                        labValidationPhase = LabValidationUiPhase.Checking,
                        labSessionId = null,
                        labTerminationReason = null,
                        labResultSummary = null,
                        apTestDenials = emptyList(),
                        startBlockedReason = null,
                        searchState = SearchState.Preparing,
                        outcome = null,
                        errorMessage = null,
                        errorDetails = null,
                        resultReport = null,
                    )
                }
                val password =
                    resolvePasswordForStart() ?: run {
                        _state.update {
                            it.copy(passwordError = PasswordAuditUiError.PasswordUnavailable)
                        }
                        return@launch
                    }
                val credential = ValidationCredential.fromPlaintext(password)
                val outcome =
                    orchestrator.requestStart(
                        scope = viewModelScope,
                        request =
                            LabValidationStartRequest(
                                requestedSsid = req.network.ssid,
                                requestedBssid = bssid,
                                securityFamily = req.network.securityProfile.family,
                                userConsentGranted = snapshot.apTestConsentGranted,
                                budget = LabSessionBudget.standard(),
                                credential = credential,
                            ),
                    )
                when (outcome) {
                    is LabValidationStartOutcome.Denied -> {
                        _state.update {
                            it.copy(
                                labValidationPhase = LabValidationUiPhase.Denied,
                                labSessionId = outcome.session?.sessionId,
                                labTerminationReason = LabSessionTerminationReason.GateDenied,
                                apTestDenials = outcome.reasons,
                                apTestConsentGranted = false,
                                startBlockedReason = PasswordAuditUiError.ApTestNotAdmitted,
                                searchState = SearchState.Idle,
                            )
                        }
                        refreshApTestAdmission()
                    }
                    is LabValidationStartOutcome.RejectedConcurrent -> {
                        _state.update {
                            it.copy(
                                labValidationPhase = LabValidationUiPhase.Failed,
                                startBlockedReason = PasswordAuditUiError.ApTestNotAdmitted,
                                searchState = SearchState.Idle,
                                apTestConsentGranted = false,
                            )
                        }
                    }
                    is LabValidationStartOutcome.Started -> {
                        _state.update {
                            it.copy(
                                labValidationPhase = LabValidationUiPhase.Admitted,
                                labSessionId = outcome.session.sessionId,
                                searchState = SearchState.Preparing,
                            )
                        }
                        orchestrator.session
                            .mapNotNull { it }
                            .onEach { session ->
                                _state.update {
                                    it.copy(
                                        labValidationPhase = session.state.toUiPhase(),
                                        labSessionId = session.sessionId,
                                        labTerminationReason = session.terminationReason,
                                        labResultSummary = session.resultSummary,
                                        searchState = session.state.toSearchState(),
                                        apTestConsentGranted =
                                            if (session.state.isTerminal) {
                                                false
                                            } else {
                                                it.apTestConsentGranted
                                            },
                                        startBlockedReason =
                                            if (session.state == LabValidationSessionState.Denied) {
                                                PasswordAuditUiError.ApTestNotAdmitted
                                            } else {
                                                it.startBlockedReason
                                            },
                                        apTestDenials =
                                            session.admissionDenials.ifEmpty { it.apTestDenials },
                                    )
                                }
                            }.first { it.state.isTerminal }
                    }
                }
            }
    }

    private fun LabValidationSessionState.toUiPhase(): LabValidationUiPhase =
        when (this) {
            LabValidationSessionState.Created -> LabValidationUiPhase.Checking
            LabValidationSessionState.Admitted -> LabValidationUiPhase.Admitted
            LabValidationSessionState.Running,
            LabValidationSessionState.Stopping,
            -> LabValidationUiPhase.Running
            LabValidationSessionState.Completed -> LabValidationUiPhase.Completed
            LabValidationSessionState.Cancelled -> LabValidationUiPhase.Cancelled
            LabValidationSessionState.Denied -> LabValidationUiPhase.Denied
            LabValidationSessionState.Failed -> LabValidationUiPhase.Failed
        }

    private fun LabValidationSessionState.toSearchState(): SearchState =
        when (this) {
            LabValidationSessionState.Created,
            LabValidationSessionState.Admitted,
            -> SearchState.Preparing
            LabValidationSessionState.Running -> SearchState.Running
            LabValidationSessionState.Stopping -> SearchState.Cancelling
            LabValidationSessionState.Completed -> SearchState.Completed
            LabValidationSessionState.Cancelled -> SearchState.Cancelled
            LabValidationSessionState.Denied,
            LabValidationSessionState.Failed,
            -> SearchState.Failed
        }

    fun toggleErrorDetails() {
        _state.update { it.copy(showErrorDetails = !it.showErrorDetails) }
    }

    fun toggleImproveGuide() {
        _state.update { it.copy(improveGuideExpanded = !it.improveGuideExpanded) }
    }

    fun toggleResultDetails() {
        _state.update { it.copy(resultDetailsExpanded = !it.resultDetailsExpanded) }
    }

    private fun sanitizeError(raw: String): String {
        val trimmed = raw.trim().take(240)
        return trimmed.ifBlank { "error-local" }
    }

    private suspend fun resolvePasswordForStart(): String? {
        val ui = _state.value
        return when (ui.secretSource) {
            PasswordAuditSecretSource.Vault -> {
                val id = ui.savedNetworkId ?: return null
                revealSecret(id)?.takeIf { it.isNotEmpty() }
            }
            PasswordAuditSecretSource.Manual -> ui.passwordInput.takeIf { it.isNotEmpty() }
        }
    }

    private suspend fun persistSecretBestEffort(password: String) {
        val req = request ?: return
        try {
            val existingId = _state.value.savedNetworkId
            if (existingId != null) {
                updateSecret(existingId, NetworkSecret(password))
            } else {
                val created =
                    createSavedNetwork(
                        NewSavedWifiNetwork(
                            alias = req.network.displayName,
                            ssid = req.network.ssid.value,
                            securityFamily = req.network.securityProfile.family,
                            knownBssids = setOfNotNull(req.network.bssid),
                        ),
                        NetworkSecret(password),
                    )
                _state.update {
                    it.copy(
                        savedNetworkId = created.id,
                        vaultSecretAvailable = true,
                        saveToVault = false,
                    )
                }
                request = req.copy(savedNetworkId = created.id)
            }
        } catch (_: Throwable) {
            _state.update {
                it.copy(infoMessage = PasswordAuditUiError.VaultSaveFailed)
            }
        }
    }

    private fun applyEngineSelection(plan: PasswordAuditPlan) {
        val aware = engine as? WorkerAwareLabSearchEngine ?: return
        aware.workers = plan.workerCount
        when (val choice = plan.engine) {
            is PasswordAuditEngineChoice.Parallel -> aware.parallelVersion = choice.version
            PasswordAuditEngineChoice.BaselineSequential -> Unit
        }
    }

    private fun PasswordAuditUiState.reduce(event: LabSearchEvent): PasswordAuditUiState =
        when (event) {
            LabSearchEvent.Preparing -> copy(searchState = SearchState.Preparing)
            is LabSearchEvent.Started -> copy(searchState = SearchState.Running)
            is LabSearchEvent.Progress -> copy(searchState = SearchState.Running, metrics = event.metrics)
            is LabSearchEvent.CandidateFound ->
                copy(
                    searchState = SearchState.Completed,
                    metrics = event.metrics,
                    outcome = SearchOutcome.Found,
                    discoveredWithinBudget = true,
                ).withResultReport(cancelled = false, failed = false)
            is LabSearchEvent.LimitReached ->
                copy(
                    searchState = SearchState.LimitReached,
                    metrics = event.metrics,
                    outcome = SearchOutcome.LimitReached,
                    discoveredWithinBudget = false,
                ).withResultReport(cancelled = false, failed = false)
            is LabSearchEvent.Cancelled ->
                copy(
                    searchState = SearchState.Cancelled,
                    metrics = event.metrics,
                    outcome = SearchOutcome.Cancelled,
                ).withResultReport(cancelled = true, failed = false)
            is LabSearchEvent.Completed ->
                copy(
                    searchState = SearchState.Completed,
                    metrics = event.metrics,
                    outcome = SearchOutcome.NotFound,
                    discoveredWithinBudget = false,
                ).withResultReport(cancelled = false, failed = false, exhausted = true)
            is LabSearchEvent.Failed ->
                copy(
                    searchState = SearchState.Failed,
                    metrics = event.metrics ?: metrics,
                    outcome = SearchOutcome.Failed,
                    errorMessage = PasswordAuditUiError.AuditFailed,
                    errorDetails = sanitizeError(event.message),
                    showErrorDetails = false,
                ).withResultReport(cancelled = false, failed = true)
        }

    private fun PasswordAuditUiState.withResultReport(
        cancelled: Boolean,
        failed: Boolean,
        exhausted: Boolean = false,
    ): PasswordAuditUiState =
        copy(
            resultReport =
                PasswordAuditResultComposer.compose(
                    discoveredWithinBudget = discoveredWithinBudget,
                    cancelled = cancelled,
                    failed = failed,
                    structural = strength,
                    measuredAttempts = metrics?.attempts,
                    measuredDuration = metrics?.elapsed,
                    budgetedAttemptCapacity = plan?.budgetedAttemptCapacity,
                    attemptsPerSecond = metrics?.attemptsPerSecond,
                    exhausted = exhausted,
                    networkAssessment = networkAssessment ?: networkAssessmentCache,
                    failureMessage = errorDetails,
                ),
            improveGuideExpanded = false,
            resultDetailsExpanded = false,
        )

    private fun rebuildPlan() {
        val req = request ?: return
        _state.update { it.copy(loadingPlan = true, planNotApplicableReason = null) }
        viewModelScope.launch(ioDispatcher) {
            val budget =
                budgetFromState(_state.value) ?: run {
                    _state.update {
                        it.copy(
                            loadingPlan = false,
                            plan = null,
                            explanation = null,
                        )
                    }
                    refreshStartGate()
                    return@launch
                }
            val family = req.network.securityProfile.family
            val context =
                PasswordAuditContext(
                    sharedPasswordApplicable = family.supportsSharedPasswordAudit(),
                    inapplicableReason =
                        if (family.supportsSharedPasswordAudit()) {
                            null
                        } else {
                            family.unsupportedAuditReason()
                        },
                    searchProfile = family.toSharedPasswordSearchProfile(),
                )
            val performance =
                PasswordAuditPerformanceProfile(
                    calibratedAttemptsPerSecond = calibratedThroughput,
                    availableProcessors = availableProcessors,
                )
            when (val result = planner.createPlan(context, performance, budget)) {
                is PasswordAuditPlanResult.Ready -> {
                    _state.update {
                        it.copy(
                            loadingPlan = false,
                            plan = result.plan,
                            explanation = result.plan.explanation,
                            feasibilityRating = result.plan.feasibility.rating,
                            feasibilityReason = result.plan.feasibility.reason,
                            planNotApplicableReason = null,
                        )
                    }
                }
                is PasswordAuditPlanResult.NotApplicable -> {
                    _state.update {
                        it.copy(
                            loadingPlan = false,
                            plan = null,
                            explanation = null,
                            feasibilityRating = null,
                            feasibilityReason = null,
                            planNotApplicableReason = result.reason,
                        )
                    }
                }
            }
            refreshStartGate()
        }
    }

    private fun budgetFromState(ui: PasswordAuditUiState): PasswordAuditBudget? =
        when (ui.preset) {
            PasswordAuditBudgetPreset.Quick -> PasswordAuditBudget.quick()
            PasswordAuditBudgetPreset.Standard -> PasswordAuditBudget.standard()
            PasswordAuditBudgetPreset.Deep -> PasswordAuditBudget.deep()
            PasswordAuditBudgetPreset.Custom -> {
                val duration =
                    ui.customDurationSeconds.toLongOrNull()?.takeIf { it > 0 }?.seconds
                val attempts =
                    ui.customMaxAttempts.toLongOrNull()?.takeIf { it > 0 }?.let { CombinationCount.of(it) }
                if (duration == null && attempts == null) {
                    null
                } else {
                    PasswordAuditBudget(
                        maxDuration = duration,
                        maxAttempts = attempts,
                        preset = PasswordAuditBudgetPreset.Custom,
                    )
                }
            }
        }

    private fun refreshStartGate() {
        _state.update { current ->
            val reason =
                when {
                    current.isActive -> null
                    !current.hasSecretReady -> PasswordAuditUiError.PasswordRequired
                    current.plan == null ->
                        when {
                            current.planNotApplicableReason != null ->
                                current.planNotApplicableReason.toUiError()
                            current.preset == PasswordAuditBudgetPreset.Custom &&
                                budgetFromState(current) == null ->
                                PasswordAuditUiError.BudgetRequired
                            else -> PasswordAuditUiError.NoPlan
                        }
                    current.feasibilityRating == FeasibilityRating.Invalid ->
                        PasswordAuditUiError.InvalidConfig
                    current.connectionLostMessage != null ->
                        PasswordAuditUiError.ConnectionLost
                    else -> null
                }
            current.copy(startBlockedReason = reason)
        }
    }

    override fun onCleared() {
        cancellation?.cancel()
        searchJob?.cancel()
        _state.update {
            it.copy(passwordInput = "", passwordVisible = false)
        }
        super.onCleared()
    }
}
