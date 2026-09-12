package com.wifiauditlab.android.ui.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

enum class AlphabetChoice(val label: String, val alphabet: Alphabet) {
    DIGITS("Dígitos (0-9)", Alphabet.DIGITS),
    LOWERCASE("Minúsculas (a-z)", Alphabet.LOWERCASE),
    LOWER_ALPHANUMERIC("Alfanumérico (a-z, 0-9)", Alphabet.LOWER_ALPHANUMERIC),
    ALPHANUMERIC("Alfanumérico (a-z, A-Z, 0-9)", Alphabet.ALPHANUMERIC),
}

enum class StrategyChoice(val label: String, val id: SearchStrategyId) {
    UNIFORM("Uniforme", UniformBaselineStrategy.ID),
    LENGTH("Longitud primero", LengthPrioritizedStrategy.ID),
    TIERED("Alfabeto por capas", TieredAlphabetStrategy.ID),
    WEIGHTED("Probabilidad sintética", SyntheticProbabilityWeightedStrategy.ID),
    ADAPTIVE("Adaptativa", AdaptiveSyntheticStrategy.ID),
}

data class LabConfig(
    val alphabet: AlphabetChoice = AlphabetChoice.DIGITS,
    val strategy: StrategyChoice = StrategyChoice.LENGTH,
    val secretLength: Int = 4,
    val maxAttempts: Long? = 5_000_000,
    val maxDurationSeconds: Long? = 30,
    val seed: Long? = 1,
) {
    fun activeLimitsDescription(): String =
        buildList {
            maxAttempts?.let { add("$it intentos") }
            maxDurationSeconds?.let { add("$it s") }
        }.joinToString(" · ").ifEmpty { "Sin límite (inválido)" }
}

data class LabUiState(
    val config: LabConfig = LabConfig(),
    val searchState: SearchState = SearchState.Idle,
    val estimatedCombinations: CombinationCount = CombinationCount.ZERO,
    val feasibility: SearchFeasibility? = null,
    val metrics: SearchMetrics? = null,
    val outcome: SearchOutcome? = null,
    val foundCandidate: String? = null,
    val configError: String? = null,
    val errorMessage: String? = null,
)

/**
 * Drives the Synthetic Security Lab. Holds only presentation state and delegates
 * all search logic to the engine; cancellation is cooperative and near-instant.
 */
class LabViewModel(
    private val engine: LabSearchEngine,
    private val optimizer: SearchPlanOptimizer,
    private val analyzer: SearchFeasibilityAnalyzer,
    private val estimator: SearchPerformanceEstimator,
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calibration: SearchCalibrationService? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LabUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var cancellation: CancellationController? = null

    init {
        recomputePreview(_state.value.config)
        val service = calibration
        if (service != null) {
            viewModelScope.launch(searchDispatcher) {
                val record = service.calibrate(_state.value.config.strategy.id.value)
                estimator.refine(record.measuredAttemptsPerSecond)
                recomputePreview(_state.value.config)
            }
        }
    }

    fun updateConfig(config: LabConfig) {
        _state.update { it.copy(config = config) }
        recomputePreview(config)
    }

    private fun recomputePreview(config: LabConfig) {
        val challenge = buildChallenge(config)
        val plan = optimizer.optimize(challenge, config.strategy.id)
        val limits = runCatching { buildLimits(config) }.getOrNull()
        _state.update {
            it.copy(
                estimatedCombinations = plan.searchSpace,
                feasibility = limits?.let { l -> analyzer.analyze(plan, l, estimator) },
                configError =
                    if (limits == null) {
                        "Configura al menos un límite de intentos o de tiempo."
                    } else {
                        null
                    },
            )
        }
    }

    fun start() {
        val config = _state.value.config
        val limits =
            runCatching { buildLimits(config) }.getOrNull() ?: run {
                _state.update { it.copy(configError = "Configura al menos un límite de intentos o de tiempo.") }
                return
            }
        val challenge = buildChallenge(config)
        val plan = optimizer.optimize(challenge, config.strategy.id)
        val controller = CancellationController()
        cancellation = controller

        _state.update {
            it.copy(
                searchState = SearchState.Preparing,
                metrics = null,
                outcome = null,
                foundCandidate = null,
                errorMessage = null,
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

    private fun buildChallenge(config: LabConfig): LabChallenge =
        LabChallenge.withHiddenSecret(
            alphabet = config.alphabet.alphabet,
            lengthPolicy = LengthPolicy.exactly(config.secretLength),
            seed = config.seed,
        )

    private fun buildLimits(config: LabConfig): SearchLimits =
        SearchLimits.of(
            maxDuration = config.maxDurationSeconds?.seconds,
            maxAttempts = config.maxAttempts?.let { CombinationCount.of(it) },
        )
}
