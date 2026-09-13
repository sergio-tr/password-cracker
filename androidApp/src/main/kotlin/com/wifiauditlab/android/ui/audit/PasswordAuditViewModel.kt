package com.wifiauditlab.android.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.PasswordAuditResultComposer
import com.wifiauditlab.assessment.domain.audit.SecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.supportsSharedPasswordAudit
import com.wifiauditlab.assessment.domain.audit.unsupportedAuditReason
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
 * Quick Audit: password entry, automatic plan, and **local** search execution.
 * Never authenticates candidates against a router/AP.
 * Strength analysis never feeds the planner.
 */
class PasswordAuditViewModel(
    private val targetStore: PasswordAuditTargetStore,
    private val planner: AutomaticPasswordAuditPlanner,
    private val getSavedNetwork: GetSavedNetwork,
    private val revealSecret: RevealSavedNetworkSecret,
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
            )
        viewModelScope.launch(ioDispatcher) {
            val vaultAvailable =
                current.savedNetworkId?.let { id ->
                    getSavedNetwork(id)?.hasSecret == true
                } == true
            _state.update { it.copy(vaultSecretAvailable = vaultAvailable) }
            calibratedThroughput = calibration?.lastRecord()?.measuredAttemptsPerSecond
            rebuildPlan()
        }
    }

    fun onPasswordChanged(value: String) {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                passwordInput = value,
                passwordFromVault = false,
                passwordError = null,
                infoMessage = null,
                strength = if (value.isNotEmpty()) strengthAnalyzer.analyze(value) else null,
            )
        }
        refreshStartGate()
    }

    fun togglePasswordVisibility() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun clearPassword() {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                passwordInput = "",
                passwordFromVault = false,
                passwordVisible = false,
                passwordError = null,
                strength = null,
                infoMessage = null,
            )
        }
        refreshStartGate()
    }

    fun useVaultPassword() {
        if (_state.value.isActive) return
        val id = _state.value.savedNetworkId ?: return
        viewModelScope.launch(ioDispatcher) {
            val plaintext = revealSecret(id)
            if (plaintext.isNullOrEmpty()) {
                _state.update {
                    it.copy(passwordError = "No se pudo leer la contraseña del Vault.")
                }
                return@launch
            }
            _state.update {
                it.copy(
                    passwordInput = plaintext,
                    passwordFromVault = true,
                    passwordError = null,
                    strength = strengthAnalyzer.analyze(plaintext),
                    infoMessage = null,
                )
            }
            refreshStartGate()
        }
    }

    fun selectPreset(preset: PasswordAuditBudgetPreset) {
        if (_state.value.isActive) return
        _state.update {
            it.copy(
                preset = preset,
                advancedExpanded = preset == PasswordAuditBudgetPreset.Custom || it.advancedExpanded,
                infoMessage = null,
            )
        }
        rebuildPlan()
    }

    fun setAdvancedExpanded(expanded: Boolean) {
        _state.update { it.copy(advancedExpanded = expanded) }
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
        _state.update { it.copy(preset = PasswordAuditBudgetPreset.Custom) }
        rebuildPlan()
    }

    /**
     * Starts a **local** search via [EncapsulatedPasswordVerifier]. Never talks to the AP.
     */
    fun onStartAuditClicked() {
        if (_state.value.isActive) return
        val password = _state.value.passwordInput
        if (password.isEmpty()) {
            _state.update {
                it.copy(passwordError = "Introduce o recupera la contraseña conocida.")
            }
            return
        }
        val plan = _state.value.plan
        if (plan == null) {
            _state.update {
                it.copy(startBlockedReason = "No hay un plan automático válido para esta red.")
            }
            return
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
                passwordFromVault = false,
                passwordError = null,
                strength = strength,
                infoMessage = null,
                searchState = SearchState.Preparing,
                metrics = null,
                outcome = null,
                discoveredWithinBudget = false,
                errorMessage = null,
                resultReport = null,
            )
        }

        searchJob =
            viewModelScope.launch(ioDispatcher) {
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
                    errorMessage = event.message,
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
                    current.passwordInput.isEmpty() -> "Falta la contraseña conocida."
                    current.plan == null -> current.planNotApplicableReason ?: "Sin plan automático."
                    else -> null
                }
            current.copy(startBlockedReason = reason)
        }
    }

    override fun onCleared() {
        cancellation?.cancel()
        searchJob?.cancel()
        clearPassword()
        super.onCleared()
    }
}
