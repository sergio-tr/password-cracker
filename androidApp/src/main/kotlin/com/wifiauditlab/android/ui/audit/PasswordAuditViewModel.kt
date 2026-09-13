package com.wifiauditlab.android.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibility
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibilityChecker
import com.wifiauditlab.assessment.domain.audit.PasswordAuditResultComposer
import com.wifiauditlab.assessment.domain.audit.SecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.supportsSharedPasswordAudit
import com.wifiauditlab.assessment.domain.audit.unsupportedAuditReason
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Quick Audit: password source, automatic plan, and **local** search execution.
 * Never authenticates candidates against a router/AP.
 * Vault plaintext is revealed only at start (not held in the UI field by default).
 * Strength analysis never feeds the planner.
 */
class PasswordAuditViewModel(
    private val targetStore: PasswordAuditTargetStore,
    private val planner: AutomaticPasswordAuditPlanner,
    private val getSavedNetwork: GetSavedNetwork,
    private val revealSecret: RevealSavedNetworkSecret,
    private val updateSecret: UpdateSavedNetworkSecret,
    private val createSavedNetwork: CreateSavedNetwork,
    private val eligibilityChecker: PasswordAuditEligibilityChecker? = null,
    private val engine: LabSearchEngine,
    private val calibration: SearchCalibrationService? = null,
    private val strengthAnalyzer: SecretStrengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordAuditUiState())
    val state: StateFlow<PasswordAuditUiState> = _state.asStateFlow()

    private var calibratedThroughput: Double? = null
    private var request: PasswordAuditRequest? = null
    private var searchJob: Job? = null
    private var cancellation: CancellationController? = null

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
        _state.value =
            PasswordAuditUiState(
                displayName = current.network.displayName,
                ssidLabel = current.network.ssid.toString(),
                familyLabel = current.network.familyDisplayLabel(),
                metaLine = current.network.metaLine(),
                savedNetworkId = current.savedNetworkId,
                loadingPlan = true,
                saveToVault = false,
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
                )
            }
            calibratedThroughput = calibration?.lastRecord()?.measuredAttemptsPerSecond
            rebuildPlan()
        }
    }

    fun selectSecretSource(source: PasswordAuditSecretSource) {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                secretSource = source,
                passwordError = null,
                infoMessage = null,
                // Never leave vault plaintext lingering when switching away.
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

    /**
     * Starts a **local** search via [EncapsulatedPasswordVerifier]. Never talks to the AP.
     * Vault secrets are revealed only here, then discarded from UI state.
     */
    fun onStartAuditClicked() {
        if (_state.value.isActive) return
        val snapshot = _state.value
        if (!snapshot.hasSecretReady) {
            _state.update {
                it.copy(passwordError = "Introduce o selecciona la contraseña conocida.")
            }
            return
        }
        val plan = snapshot.plan
        if (plan == null) {
            _state.update {
                it.copy(startBlockedReason = "No hay un plan automático válido para esta red.")
            }
            return
        }
        if (snapshot.feasibilityRating == FeasibilityRating.Invalid) {
            _state.update {
                it.copy(startBlockedReason = "La configuración actual no es válida para iniciar.")
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
                                    connectionLostMessage =
                                        "No estás conectado a esta red. " +
                                            "Conéctate primero para realizar una auditoría local.",
                                    startBlockedReason = "Se ha perdido la conexión a esta red.",
                                )
                            }
                            return@launch
                        }
                        else -> {
                            _state.update {
                                it.copy(
                                    startBlockedReason =
                                        "Esta red ya no es elegible para una auditoría de contraseña.",
                                )
                            }
                            return@launch
                        }
                    }
                }

                val password =
                    resolvePasswordForStart() ?: run {
                        _state.update {
                            it.copy(passwordError = "No se pudo obtener la contraseña conocida.")
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
        _state.update { it.copy(searchState = SearchState.Cancelling) }
        cancellation?.cancel()
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
                it.copy(infoMessage = "La auditoría continúa; no se pudo guardar en el Vault.")
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
                ).withResultReport(cancelled = false, failed = false)
            is LabSearchEvent.Failed ->
                copy(
                    searchState = SearchState.Failed,
                    metrics = event.metrics ?: metrics,
                    outcome = SearchOutcome.Failed,
                    errorMessage = "La auditoría se detuvo por un error.",
                ).withResultReport(cancelled = false, failed = true)
        }

    private fun PasswordAuditUiState.withResultReport(
        cancelled: Boolean,
        failed: Boolean,
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
                ),
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
                            planNotApplicableReason =
                                "Define al menos una duración o un límite de intentos.",
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
                    !current.hasSecretReady -> "Falta la contraseña conocida."
                    current.plan == null -> current.planNotApplicableReason ?: "Sin plan automático."
                    current.feasibilityRating == FeasibilityRating.Invalid ->
                        "La configuración actual no es válida para iniciar."
                    current.connectionLostMessage != null ->
                        "Se ha perdido la conexión a esta red."
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
