package com.wifiauditlab.android.ui.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabChallengeDefinition
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LabSessionCheckpoint
import com.wifiauditlab.lab.domain.LabSessionRepository
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchLimitsSnapshot
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.CancellationController
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.LabSearchRunOptions
import com.wifiauditlab.lab.domain.engine.PauseController
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.domain.engine.SearchFeasibility
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import com.wifiauditlab.lab.domain.toRawDecimal
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
    val workers: Int = 1,
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
    val hasResumableSession: Boolean = false,
)

/**
 * Drives the Synthetic Security Lab. Holds only presentation state and delegates
 * all search logic to the engine; cancellation and pause are cooperative.
 */
class LabViewModel(
    private val engine: LabSearchEngine,
    private val optimizer: SearchPlanOptimizer,
    private val analyzer: SearchFeasibilityAnalyzer,
    private val estimator: SearchPerformanceEstimator,
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calibration: SearchCalibrationService? = null,
    private val sessionRepository: LabSessionRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LabUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var cancellation: CancellationController? = null
    private var pauseController: PauseController? = null
    private var activeChallenge: LabChallenge? = null
    private var activePlan: LabSearchPlan? = null
    private var activeLimits: SearchLimits? = null

    init {
        recomputePreview(_state.value.config)
        val service = calibration
        if (service != null) {
            viewModelScope.launch(searchDispatcher) {
                val config = _state.value.config
                val record =
                    service.calibrate(
                        strategyId = config.strategy.id.value,
                        workerCount = config.workers,
                    )
                estimator.refine(record.measuredAttemptsPerSecond)
                recomputePreview(_state.value.config)
            }
        }
        val sessions = sessionRepository
        if (sessions != null) {
            viewModelScope.launch(searchDispatcher) {
                val checkpoint = sessions.load()
                if (checkpoint != null && checkpoint.state == SearchState.Paused) {
                    _state.update {
                        it.copy(
                            searchState = SearchState.Paused,
                            metrics = checkpoint.metrics,
                            hasResumableSession = true,
                            config = configFromCheckpoint(checkpoint),
                        )
                    }
                    recomputePreview(_state.value.config)
                }
            }
        }
    }

    fun updateConfig(config: LabConfig) {
        if (_state.value.searchState == SearchState.Paused) return
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
        if (_state.value.searchState == SearchState.Paused) return
        val config = _state.value.config
        val limits =
            runCatching { buildLimits(config) }.getOrNull() ?: run {
                _state.update { it.copy(configError = "Configura al menos un límite de intentos o de tiempo.") }
                return
            }
        if (config.seed == null) {
            _state.update {
                it.copy(configError = "Se requiere una seed para poder pausar/reanudar sin guardar el secreto.")
            }
            return
        }
        val challenge = buildChallenge(config)
        val plan = optimizer.optimize(challenge, config.strategy.id)
        (engine as? WorkerAwareLabSearchEngine)?.workers = config.workers
        viewModelScope.launch(searchDispatcher) {
            sessionRepository?.clear()
        }
        beginRun(challenge, plan, limits, resumeFrom = null, config = config)
    }

    fun pause() {
        val current = _state.value.searchState
        if (current != SearchState.Running && current != SearchState.Preparing) return
        _state.update { it.copy(searchState = SearchState.Pausing) }
        pauseController?.pause()
    }

    fun resume() {
        if (_state.value.searchState != SearchState.Paused) return
        viewModelScope.launch(searchDispatcher) {
            val checkpoint =
                sessionRepository?.load() ?: run {
                    _state.update {
                        it.copy(
                            searchState = SearchState.Failed,
                            outcome = SearchOutcome.Failed,
                            errorMessage = "No hay sesión pausada para reanudar.",
                            hasResumableSession = false,
                        )
                    }
                    return@launch
                }
            if (checkpoint.strategyId != _state.value.config.strategy.id) {
                _state.update {
                    it.copy(
                        errorMessage = "La estrategia no puede cambiarse al reanudar.",
                    )
                }
                return@launch
            }
            if (checkpoint.planSchemaVersion != LabSessionCheckpoint.PLAN_SCHEMA_VERSION) {
                _state.update {
                    it.copy(
                        searchState = SearchState.Failed,
                        outcome = SearchOutcome.Failed,
                        errorMessage = "Checkpoint incompatible (versión de plan).",
                        hasResumableSession = false,
                    )
                }
                return@launch
            }
            val challenge = checkpoint.challenge.toChallenge()
            val plan = optimizer.optimize(challenge, checkpoint.strategyId)
            if (!checkpoint.matchesPlan(plan)) {
                _state.update {
                    it.copy(
                        searchState = SearchState.Failed,
                        outcome = SearchOutcome.Failed,
                        errorMessage = "El plan reanudado no coincide con el checkpoint.",
                        hasResumableSession = false,
                    )
                }
                return@launch
            }
            val limits = checkpoint.limits.toLimits()
            val config = configFromCheckpoint(checkpoint)
            (engine as? WorkerAwareLabSearchEngine)?.workers = checkpoint.workerCount
            _state.update {
                it.copy(
                    config = config,
                    hasResumableSession = true,
                    outcome = null,
                    foundCandidate = null,
                    errorMessage = null,
                )
            }
            beginRun(challenge, plan, limits, resumeFrom = checkpoint.cursor, config = config)
        }
    }

    fun stop() {
        when (_state.value.searchState) {
            SearchState.Paused -> {
                viewModelScope.launch(searchDispatcher) {
                    sessionRepository?.clear()
                }
                _state.update {
                    it.copy(
                        searchState = SearchState.Cancelled,
                        outcome = SearchOutcome.Cancelled,
                        hasResumableSession = false,
                    )
                }
            }
            SearchState.Running,
            SearchState.Preparing,
            SearchState.Pausing,
            -> {
                _state.update { it.copy(searchState = SearchState.Cancelling) }
                cancellation?.cancel()
            }
            else -> {
                _state.update { it.copy(searchState = SearchState.Cancelling) }
                cancellation?.cancel()
            }
        }
    }

    override fun onCleared() {
        cancellation?.cancel()
        searchJob?.cancel()
    }

    private fun beginRun(
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        resumeFrom: com.wifiauditlab.lab.domain.LabSearchCursor?,
        config: LabConfig,
    ) {
        val controller = CancellationController()
        val pause = PauseController()
        cancellation = controller
        pauseController = pause
        activeChallenge = challenge
        activePlan = plan
        activeLimits = limits

        _state.update {
            it.copy(
                searchState = SearchState.Preparing,
                metrics = if (resumeFrom != null) it.metrics else null,
                outcome = null,
                foundCandidate = null,
                errorMessage = null,
                hasResumableSession = resumeFrom != null,
            )
        }

        searchJob =
            viewModelScope.launch(searchDispatcher) {
                engine
                    .run(
                        challenge,
                        plan,
                        limits,
                        controller,
                        LabSearchRunOptions(pause = pause, resumeFrom = resumeFrom),
                    ).collect { event ->
                        if (event is LabSearchEvent.Paused) {
                            persistCheckpoint(event, challenge, plan, limits, config)
                        }
                        if (event is LabSearchEvent.Cancelled ||
                            event is LabSearchEvent.CandidateFound ||
                            event is LabSearchEvent.Completed ||
                            event is LabSearchEvent.LimitReached ||
                            event is LabSearchEvent.Failed
                        ) {
                            sessionRepository?.clear()
                        }
                        _state.update { current -> current.reduce(event) }
                    }
            }
    }

    private suspend fun persistCheckpoint(
        event: LabSearchEvent.Paused,
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        config: LabConfig,
    ) {
        val repository = sessionRepository ?: return
        val definition = LabChallengeDefinition.from(challenge)
        val checkpoint =
            LabSessionCheckpoint(
                sessionId = event.cursor.sessionId,
                challenge = definition,
                strategyId = plan.strategyId,
                planSchemaVersion = LabSessionCheckpoint.PLAN_SCHEMA_VERSION,
                searchSpaceExact = plan.searchSpace.toRawDecimal(),
                totalBuckets = plan.totalBuckets,
                limits = SearchLimitsSnapshot.from(limits),
                cursor = event.cursor,
                metrics = event.metrics,
                state = SearchState.Paused,
                workerCount = config.workers,
                engineVersion = LabEngineVersion.CURRENT,
            )
        repository.save(checkpoint)
    }

    private fun LabUiState.reduce(event: LabSearchEvent): LabUiState =
        when (event) {
            LabSearchEvent.Preparing -> copy(searchState = SearchState.Preparing)
            is LabSearchEvent.Started -> copy(searchState = SearchState.Running)
            is LabSearchEvent.Progress -> copy(searchState = SearchState.Running, metrics = event.metrics)
            is LabSearchEvent.CandidateFound ->
                copy(
                    searchState = SearchState.Completed,
                    metrics = event.metrics,
                    outcome = SearchOutcome.Found,
                    foundCandidate = event.candidate,
                    hasResumableSession = false,
                )
            is LabSearchEvent.LimitReached ->
                copy(
                    searchState = SearchState.LimitReached,
                    metrics = event.metrics,
                    outcome = SearchOutcome.LimitReached,
                    hasResumableSession = false,
                )
            is LabSearchEvent.Cancelled ->
                copy(
                    searchState = SearchState.Cancelled,
                    metrics = event.metrics,
                    outcome = SearchOutcome.Cancelled,
                    hasResumableSession = false,
                )
            is LabSearchEvent.Paused ->
                copy(
                    searchState = SearchState.Paused,
                    metrics = event.metrics,
                    outcome = null,
                    hasResumableSession = true,
                )
            is LabSearchEvent.Completed ->
                copy(
                    searchState = SearchState.Completed,
                    metrics = event.metrics,
                    outcome = SearchOutcome.NotFound,
                    hasResumableSession = false,
                )
            is LabSearchEvent.Failed ->
                copy(
                    searchState = SearchState.Failed,
                    metrics = event.metrics ?: metrics,
                    outcome = SearchOutcome.Failed,
                    errorMessage = event.message,
                    hasResumableSession = false,
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

    private fun configFromCheckpoint(checkpoint: LabSessionCheckpoint): LabConfig {
        val alphabet =
            AlphabetChoice.entries.firstOrNull {
                it.alphabet.symbols == checkpoint.challenge.alphabetSymbols
            } ?: AlphabetChoice.DIGITS
        val strategy =
            StrategyChoice.entries.firstOrNull { it.id == checkpoint.strategyId }
                ?: StrategyChoice.LENGTH
        return LabConfig(
            alphabet = alphabet,
            strategy = strategy,
            secretLength = checkpoint.challenge.minLength,
            maxAttempts = checkpoint.limits.maxAttemptsExact?.toLongOrNull(),
            maxDurationSeconds = checkpoint.limits.maxDurationMillis?.div(1000),
            workers = checkpoint.workerCount,
            seed = checkpoint.challenge.seed,
        )
    }
}
