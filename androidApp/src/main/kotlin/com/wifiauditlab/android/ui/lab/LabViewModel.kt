package com.wifiauditlab.android.ui.lab

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.android.R
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.CancellationController
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.domain.engine.SearchFeasibility
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import com.wifiauditlab.lab.engine.AdaptiveSyntheticStrategy
import com.wifiauditlab.lab.engine.LengthPrioritizedStrategy
import com.wifiauditlab.lab.engine.SyntheticProbabilityWeightedStrategy
import com.wifiauditlab.lab.engine.TieredAlphabetStrategy
import com.wifiauditlab.lab.engine.UniformBaselineStrategy
import com.wifiauditlab.lab.engine.WorkerAwareLabSearchEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

enum class AlphabetChoice(
    @StringRes val labelRes: Int,
    val alphabet: Alphabet,
) {
    DIGITS(R.string.lab_alphabet_digits, Alphabet.DIGITS),
    LOWERCASE(R.string.lab_alphabet_lowercase, Alphabet.LOWERCASE),
    LOWER_ALPHANUMERIC(R.string.lab_alphabet_lower_alphanumeric, Alphabet.LOWER_ALPHANUMERIC),
    ALPHANUMERIC(R.string.lab_alphabet_alphanumeric, Alphabet.ALPHANUMERIC),
}

enum class StrategyChoice(
    @StringRes val labelRes: Int,
    val id: SearchStrategyId,
) {
    UNIFORM(R.string.lab_strategy_uniform, UniformBaselineStrategy.ID),
    LENGTH(R.string.lab_strategy_length, LengthPrioritizedStrategy.ID),
    TIERED(R.string.lab_strategy_tiered, TieredAlphabetStrategy.ID),
    WEIGHTED(R.string.lab_strategy_weighted, SyntheticProbabilityWeightedStrategy.ID),
    ADAPTIVE(R.string.lab_strategy_adaptive, AdaptiveSyntheticStrategy.ID),
}

data class LabConfig(
    val alphabet: AlphabetChoice = AlphabetChoice.DIGITS,
    val customAlphabet: Alphabet? = null,
    val strategy: StrategyChoice = StrategyChoice.LENGTH,
    val secretLength: Int = 4,
    val maxAttempts: Long? = 5_000_000,
    val maxDurationSeconds: Long? = 30,
    val workers: Int = 1,
    val seed: Long? = 1,
) {
    fun resolvedAlphabet(): Alphabet = customAlphabet ?: alphabet.alphabet

    val hasActiveLimits: Boolean
        get() = maxAttempts != null || maxDurationSeconds != null
}

data class LabUiState(
    val config: LabConfig = GuidedLabDefaults.recommendedConfig(),
    val mode: LabInteractionMode = LabInteractionMode.Guided,
    val secretMode: LabSecretMode = LabSecretMode.LocalPrototype,
    val prototype: LocalNetworkPrototype = LocalNetworkPrototype(),
    val targetPassword: String = "",
    val passwordVisible: Boolean = false,
    val advancedExpanded: Boolean = false,
    val networkContext: LabNetworkContext? = null,
    val prototypeAssessment: SecurityAssessment? = null,
    val prototypeAssessmentLoading: Boolean = false,
    val searchState: SearchState = SearchState.Idle,
    val estimatedCombinations: CombinationCount = CombinationCount.ZERO,
    val feasibility: SearchFeasibility? = null,
    val metrics: SearchMetrics? = null,
    val outcome: SearchOutcome? = null,
    val foundCandidate: String? = null,
    @StringRes val configErrorRes: Int? = null,
    val errorMessage: String? = null,
) {
    val effectiveSecretLength: Int
        get() = config.secretLength
}

/**
 * Drives the Synthetic Security Lab. Holds only presentation state and delegates
 * all search logic to the engine; cancellation is cooperative and near-instant.
 */
class LabViewModel(
    private val engine: LabSearchEngine,
    private val optimizer: SearchPlanOptimizer,
    private val analyzer: SearchFeasibilityAnalyzer,
    private val estimator: SearchPerformanceEstimator,
    private val assessNetworkSecurity: AssessNetworkSecurity,
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calibration: SearchCalibrationService? = null,
    private val networkContextStore: LabNetworkContextStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LabUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var cancellation: CancellationController? = null
    private var assessmentJob: Job? = null

    init {
        refreshNetworkContext()
        applyGuidedDefaults(calibratedAttemptsPerSecond = null)
        refreshPrototypeAssessment()
        val service = calibration
        if (service != null) {
            viewModelScope.launch(searchDispatcher) {
                val guided = GuidedLabDefaults.recommendedConfig()
                val record =
                    service.calibrate(
                        strategyId = guided.strategy.id.value,
                        workerCount = guided.workers,
                    )
                estimator.refine(record.measuredAttemptsPerSecond)
                if (_state.value.mode == LabInteractionMode.Guided) {
                    applyGuidedDefaults(calibratedAttemptsPerSecond = record.measuredAttemptsPerSecond)
                } else {
                    recomputePreview()
                }
            }
        }
    }

    /** Re-read the process-scoped store when Lab becomes visible (tab restore). */
    fun refreshNetworkContext() {
        _state.update { it.copy(networkContext = networkContextStore?.current) }
    }

    fun setMode(mode: LabInteractionMode) {
        _state.update {
            it.copy(
                mode = mode,
                advancedExpanded = mode == LabInteractionMode.Advanced,
            )
        }
        if (mode == LabInteractionMode.Guided) {
            applyGuidedDefaults(calibratedAttemptsPerSecond = null)
        }
    }

    fun setSecretMode(mode: LabSecretMode) {
        _state.update {
            it.copy(
                secretMode = mode,
                passwordVisible = false,
                configErrorRes = null,
            )
        }
        if (mode == LabSecretMode.LocalPrototype) {
            refreshPrototypeAssessment()
        } else {
            _state.update { it.copy(prototypeAssessment = null, prototypeAssessmentLoading = false) }
        }
        recomputePreview()
    }

    fun updatePrototype(prototype: LocalNetworkPrototype) {
        _state.update { it.copy(prototype = prototype) }
        refreshPrototypeAssessment()
        recomputePreview()
    }

    fun onTargetPasswordChanged(value: String) {
        _state.update { current ->
            var syncedConfig = current.config
            if (value.isNotEmpty()) {
                syncedConfig = syncedConfig.copy(secretLength = value.length)
            }
            current.copy(
                targetPassword = value,
                config = syncedConfig,
                configErrorRes = null,
            )
        }
        recomputePreview()
    }

    fun togglePasswordVisibility() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun setAdvancedExpanded(expanded: Boolean) {
        _state.update { it.copy(advancedExpanded = expanded) }
    }

    fun resetToGuidedDefaults() {
        setMode(LabInteractionMode.Guided)
        applyGuidedDefaults(calibratedAttemptsPerSecond = null)
    }

    fun applyGuidedDefaults(calibratedAttemptsPerSecond: Double?) {
        val config = GuidedLabDefaults.recommendedConfig(calibratedAttemptsPerSecond = calibratedAttemptsPerSecond)
        _state.update {
            it.copy(
                config = config,
                mode = LabInteractionMode.Guided,
                secretMode = LabSecretMode.LocalPrototype,
                advancedExpanded = false,
            )
        }
        refreshPrototypeAssessment()
        recomputePreview()
    }

    fun updateConfig(config: LabConfig) {
        _state.update { current ->
            val normalized =
                if (config.alphabet != current.config.alphabet) {
                    config.copy(customAlphabet = null)
                } else {
                    config
                }
            current.copy(config = normalized)
        }
        recomputePreview()
    }

    private fun refreshPrototypeAssessment() {
        val snapshot = _state.value
        if (snapshot.secretMode != LabSecretMode.LocalPrototype) return
        val profile = snapshot.prototype.securityProfile
        assessmentJob?.cancel()
        assessmentJob =
            viewModelScope.launch(searchDispatcher) {
                _state.update { it.copy(prototypeAssessmentLoading = true) }
                val assessment = assessNetworkSecurity(profile)
                _state.update {
                    it.copy(
                        prototypeAssessment = assessment,
                        prototypeAssessmentLoading = false,
                    )
                }
            }
    }

    private fun recomputePreview() {
        val snapshot = _state.value
        val challenge = buildChallenge(snapshot, forPreview = true)
        val plan = optimizer.optimize(challenge, snapshot.config.strategy.id)
        val limits = runCatching { buildLimits(snapshot.config) }.getOrNull()
        _state.update {
            it.copy(
                estimatedCombinations = plan.searchSpace,
                feasibility = limits?.let { l -> analyzer.analyze(plan, l, estimator) },
                configErrorRes = validateForStart(snapshot.config, snapshot),
            )
        }
    }

    fun start() {
        val snapshot = _state.value
        val validationError = validateForStart(snapshot.config, snapshot)
        if (validationError != null) {
            _state.update { it.copy(configErrorRes = validationError) }
            return
        }
        val limits = buildLimits(snapshot.config)
        val challenge = buildChallenge(snapshot)
        val plan = optimizer.optimize(challenge, snapshot.config.strategy.id)
        (engine as? WorkerAwareLabSearchEngine)?.workers = snapshot.config.workers
        val controller = CancellationController()
        cancellation = controller

        _state.update {
            it.copy(
                searchState = SearchState.Preparing,
                metrics = null,
                outcome = null,
                foundCandidate = null,
                errorMessage = null,
                targetPassword = "",
                passwordVisible = false,
            )
        }

        searchJob =
            viewModelScope.launch(searchDispatcher) {
                engine.run(challenge, plan, limits, controller).collect { event ->
                    _state.update { current -> current.reduce(event) }
                }
            }
    }

    fun stop() {
        _state.update { it.copy(searchState = SearchState.Cancelling) }
        cancellation?.cancel()
    }

    override fun onCleared() {
        cancellation?.cancel()
        searchJob?.cancel()
        assessmentJob?.cancel()
    }

    private fun LabUiState.reduce(event: LabSearchEvent): LabUiState =
        when (event) {
            LabSearchEvent.Preparing -> copy(searchState = SearchState.Preparing)
            is LabSearchEvent.Started -> copy(searchState = SearchState.Running)
            is LabSearchEvent.Progress -> copy(searchState = SearchState.Running, metrics = event.metrics)
            is LabSearchEvent.CandidateFound ->
                copy(searchState = SearchState.Completed, metrics = event.metrics, outcome = SearchOutcome.Found, foundCandidate = event.candidate)
            is LabSearchEvent.LimitReached ->
                copy(searchState = SearchState.LimitReached, metrics = event.metrics, outcome = SearchOutcome.LimitReached)
            is LabSearchEvent.Cancelled ->
                copy(searchState = SearchState.Cancelled, metrics = event.metrics, outcome = SearchOutcome.Cancelled)
            is LabSearchEvent.Completed ->
                copy(searchState = SearchState.Completed, metrics = event.metrics, outcome = SearchOutcome.NotFound)
            is LabSearchEvent.Failed ->
                copy(
                    searchState = SearchState.Failed,
                    metrics = event.metrics ?: metrics,
                    outcome = SearchOutcome.Failed,
                    errorMessage = event.message,
                )
        }

    private fun buildChallenge(
        state: LabUiState,
        forPreview: Boolean = false,
    ): LabChallenge {
        val config = state.config
        return when (state.secretMode) {
            LabSecretMode.RandomHidden ->
                LabChallenge.withHiddenSecret(
                    alphabet = config.resolvedAlphabet(),
                    lengthPolicy = LengthPolicy.exactly(config.secretLength),
                    seed = config.seed,
                )
            LabSecretMode.LocalPrototype ->
                LabChallenge.withHiddenSecret(
                    alphabet = config.resolvedAlphabet(),
                    lengthPolicy = LengthPolicy.exactly(state.effectiveSecretLength),
                    seed = config.seed,
                )
        }
    }

    private fun validateForStart(
        config: LabConfig,
        state: LabUiState,
    ): Int? {
        val limitsMissing =
            runCatching { buildLimits(config) }.isFailure
        if (limitsMissing) return R.string.lab_err_limits_required

        if (state.secretMode == LabSecretMode.LocalPrototype) {
            if (state.prototype.ssid.isBlank()) return R.string.lab_err_ssid_required
            return R.string.lab_err_prototype_search_deferred
        }

        return null
    }

    private fun buildLimits(config: LabConfig): SearchLimits =
        SearchLimits.of(
            maxDuration = config.maxDurationSeconds?.seconds,
            maxAttempts = config.maxAttempts?.let { CombinationCount.of(it) },
        )
}
