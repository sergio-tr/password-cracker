package com.wifiauditlab.android.ui.lab

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.audit.toSharedPasswordSearchProfile
import com.wifiauditlab.android.ui.plan.SearchPlanUiSummary
import com.wifiauditlab.android.ui.plan.toSearchPlanUiSummary
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LabSecretPolicy
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.audit.AutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.GenericProgressiveSearchPlanBuilder
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudget
import com.wifiauditlab.lab.domain.audit.PasswordAuditContext
import com.wifiauditlab.lab.domain.audit.PasswordAuditEngineChoice
import com.wifiauditlab.lab.domain.audit.PasswordAuditPerformanceProfile
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlan
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlanResult
import com.wifiauditlab.lab.domain.audit.WepHexProgressiveAuditPolicy
import com.wifiauditlab.lab.domain.audit.WifiPskProgressiveAuditPolicy
import com.wifiauditlab.lab.domain.audit.isWepHexProfile
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
    UPPERCASE(R.string.lab_alphabet_uppercase, Alphabet.UPPERCASE),
    LETTERS(R.string.lab_alphabet_letters, Alphabet.LETTERS),
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
    /** Exact length when [lengthMin] and [lengthMax] are both null (legacy single-size mode). */
    val secretLength: Int = 4,
    /**
     * Inclusive lower bound. Null = auto (protocol minimum when [lengthMax] is set,
     * otherwise falls back to [secretLength] for single-size mode).
     */
    val lengthMin: Int? = null,
    /**
     * Inclusive upper bound. Null = auto ([LabSearchSpaceDefaults.SOFT_MAX_LENGTH] when
     * [lengthMin] is set, otherwise [secretLength] for single-size mode).
     */
    val lengthMax: Int? = null,
    val maxAttempts: Long? = 5_000_000,
    val maxDurationSeconds: Long? = 30,
    /** When true, both attempt and duration caps may be null — run until STOP / Found / space end. */
    val runUntilCancelled: Boolean = false,
    val workers: Int = 1,
    val seed: Long? = 1,
) {
    fun resolvedAlphabet(): Alphabet = customAlphabet ?: alphabet.alphabet

    val hasActiveLimits: Boolean
        get() = runUntilCancelled || maxAttempts != null || maxDurationSeconds != null

    /**
     * Resolves the candidate length range for synthetic / RandomHidden searches.
     * Auth-aware LocalPrototype still uses the progressive planner; this policy
     * clips display and RandomHidden spaces.
     */
    fun resolvedLengthPolicy(
        protocolMin: Int = LabSearchSpaceDefaults.SYNTHETIC_MIN_LENGTH,
        softMax: Int = LabSearchSpaceDefaults.SOFT_MAX_LENGTH,
    ): LengthPolicy {
        val uiMax = softMax.coerceAtMost(LabSearchSpaceDefaults.HARD_UI_MAX_LENGTH)
        val (rawMin, rawMax) =
            when {
                lengthMin == null && lengthMax == null -> secretLength to secretLength
                lengthMin == null && lengthMax != null -> protocolMin to lengthMax
                lengthMin != null && lengthMax == null -> lengthMin to softMax
                else -> lengthMin!! to lengthMax!!
            }
        val min = rawMin.coerceAtLeast(protocolMin).coerceAtMost(uiMax)
        val max = rawMax.coerceAtLeast(min).coerceAtMost(uiMax)
        return LengthPolicy(min, max)
    }
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
    val guidedPhase: GuidedPrototypePhase = GuidedPrototypePhase.Configure,
    val guidedFocusTarget: GuidedFocusTarget? = null,
    /** Network assessment snapshot taken when the last guided run started. */
    val resultNetworkAssessment: SecurityAssessment? = null,
    /** Blind automatic search plan summary (profile + stages); no password material. */
    val searchPlanSummary: SearchPlanUiSummary? = null,
    val searchStagesExpanded: Boolean = false,
    /** True when prototype fields were filled from [networkContext] (Nearby → Lab). */
    val prototypeSeededFromNetwork: Boolean = false,
) {
    val effectiveSecretLength: Int
        get() = config.resolvedLengthPolicy().maxLength

    val isGuidedPrototypeFlow: Boolean
        get() = mode == LabInteractionMode.Guided && secretMode == LabSecretMode.LocalPrototype

    /** PSK prototype audit is only startable when shared-password demo applies. */
    val canStartSearch: Boolean
        get() =
            configErrorRes == null &&
                guidedStartAllowed &&
                !(
                    secretMode == LabSecretMode.LocalPrototype &&
                        !prototype.securityFamily.supportsSharedPasswordDemo()
                )

    private val guidedStartAllowed: Boolean
        get() = !isGuidedPrototypeFlow || guidedPhase == GuidedPrototypePhase.Ready
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
    private val planner: AutomaticPasswordAuditPlanner = DefaultAutomaticPasswordAuditPlanner(),
    private val progressiveSyntheticBuilder: GenericProgressiveSearchPlanBuilder =
        GenericProgressiveSearchPlanBuilder(),
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calibration: SearchCalibrationService? = null,
    private val networkContextStore: LabNetworkContextStore? = null,
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
) : ViewModel() {
    private val _state = MutableStateFlow(LabUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var cancellation: CancellationController? = null
    private var assessmentJob: Job? = null
    private var calibratedThroughput: Double? = null
    private var cachedPrototypePlan: PasswordAuditPlan? = null
    private var lastGuidedPassword: String = ""
    private var appliedNetworkContextKey: String? = null

    init {
        applyGuidedDefaults(calibratedAttemptsPerSecond = null)
        refreshNetworkContext()
        val service = calibration
        if (service != null) {
            viewModelScope.launch(searchDispatcher) {
                calibratedThroughput = service.lastRecord()?.measuredAttemptsPerSecond
                if (_state.value.mode == LabInteractionMode.Guided &&
                    !_state.value.prototypeSeededFromNetwork
                ) {
                    applyGuidedDefaults(calibratedAttemptsPerSecond = calibratedThroughput)
                } else {
                    recomputePreview()
                }
                val guided = GuidedLabDefaults.recommendedConfig()
                val record =
                    service.calibrate(
                        strategyId = guided.strategy.id.value,
                        workerCount = guided.workers,
                    )
                estimator.refine(record.measuredAttemptsPerSecond)
                calibratedThroughput = record.measuredAttemptsPerSecond
                if (_state.value.mode == LabInteractionMode.Guided &&
                    !_state.value.prototypeSeededFromNetwork
                ) {
                    applyGuidedDefaults(calibratedAttemptsPerSecond = record.measuredAttemptsPerSecond)
                } else {
                    // Keep Nearby-seeded prototype; only refresh worker/budget defaults.
                    _state.update {
                        it.copy(
                            config =
                                GuidedLabDefaults.recommendedConfig(
                                    calibratedAttemptsPerSecond = record.measuredAttemptsPerSecond,
                                ),
                        )
                    }
                    recomputePreview()
                }
            }
        }
    }

    /** Re-read the process-scoped store when Lab becomes visible (tab restore). */
    fun refreshNetworkContext() {
        val ctx = networkContextStore?.current
        _state.update { it.copy(networkContext = ctx) }
        if (ctx != null) {
            val key = ctx.seedKey()
            if (key != appliedNetworkContextKey) {
                appliedNetworkContextKey = key
                applyNetworkContextToPrototype(ctx)
            }
        }
    }

    /** Re-apply Nearby identity/security into the local prototype (password stays empty). */
    fun applyNetworkContextToPrototype() {
        val ctx = _state.value.networkContext ?: networkContextStore?.current ?: return
        appliedNetworkContextKey = ctx.seedKey()
        applyNetworkContextToPrototype(ctx)
    }

    private fun applyNetworkContextToPrototype(ctx: LabNetworkContext) {
        val prototype = localNetworkPrototypeFromContext(ctx)
        _state.update {
            it.copy(
                networkContext = ctx,
                secretMode = LabSecretMode.LocalPrototype,
                mode = LabInteractionMode.Guided,
                prototype = prototype,
                prototypeSeededFromNetwork = true,
                targetPassword = "",
                passwordVisible = false,
                guidedPhase = GuidedPrototypePhase.Configure,
                guidedFocusTarget =
                    if (prototype.securityFamily.supportsSharedPasswordDemo()) {
                        GuidedFocusTarget.Password
                    } else {
                        GuidedFocusTarget.Security
                    },
                resultNetworkAssessment = null,
                configErrorRes = null,
            )
        }
        refreshPrototypeAssessment()
        recomputePreview()
    }

    fun setMode(mode: LabInteractionMode) {
        _state.update {
            it.copy(
                mode = mode,
                advancedExpanded = mode == LabInteractionMode.Advanced,
            )
        }
        if (mode == LabInteractionMode.Guided) {
            applyGuidedDefaults(calibratedAttemptsPerSecond = calibratedThroughput)
        } else {
            recomputePreview()
        }
    }

    fun setSecretMode(mode: LabSecretMode) {
        _state.update {
            it.copy(
                secretMode = mode,
                passwordVisible = false,
                configErrorRes = null,
                guidedPhase = if (mode == LabSecretMode.LocalPrototype) GuidedPrototypePhase.Configure else it.guidedPhase,
                guidedFocusTarget = null,
                resultNetworkAssessment = null,
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
        _state.update {
            it.copy(
                prototype = prototype,
                prototypeSeededFromNetwork = false,
                guidedPhase =
                    if (it.isGuidedPrototypeFlow && it.guidedPhase != GuidedPrototypePhase.PostResult) {
                        GuidedPrototypePhase.Configure
                    } else {
                        it.guidedPhase
                    },
                guidedFocusTarget = null,
            )
        }
        refreshPrototypeAssessment()
        recomputePreview()
    }

    fun onTargetPasswordChanged(value: String) {
        _state.update { current ->
            var syncedConfig = current.config
            if (current.secretMode == LabSecretMode.RandomHidden && value.isNotEmpty()) {
                syncedConfig = syncedConfig.copy(secretLength = value.length)
            } else if (current.isGuidedPrototypeFlow) {
                syncedConfig = applyGuidedAlphabetFit(syncedConfig, value)
            }
            current.copy(
                targetPassword = value,
                config = syncedConfig,
                configErrorRes = null,
                guidedPhase =
                    if (current.isGuidedPrototypeFlow && current.guidedPhase == GuidedPrototypePhase.Ready) {
                        GuidedPrototypePhase.Configure
                    } else {
                        current.guidedPhase
                    },
            )
        }
        recomputePreview()
    }

    fun consumeGuidedFocusTarget() {
        _state.update { it.copy(guidedFocusTarget = null) }
    }

    fun setSearchStagesExpanded(expanded: Boolean) {
        _state.update { it.copy(searchStagesExpanded = expanded) }
    }

    fun toggleSearchStagesExpanded() {
        _state.update { it.copy(searchStagesExpanded = !it.searchStagesExpanded) }
    }

    fun createAndTest() {
        val snapshot = _state.value
        if (!snapshot.isGuidedPrototypeFlow) return
        val validationError = validateForStart(snapshot.config, snapshot)
        if (validationError != null) {
            _state.update { it.copy(configErrorRes = validationError) }
            return
        }
        applyGuidedDefaults(calibratedAttemptsPerSecond = calibratedThroughput)
        val fittedConfig = applyGuidedAlphabetFit(_state.value.config, snapshot.targetPassword)
        _state.update {
            it.copy(
                config = fittedConfig,
                configErrorRes = null,
                guidedPhase = GuidedPrototypePhase.Ready,
                guidedFocusTarget = GuidedFocusTarget.Start,
                outcome = null,
                metrics = null,
                foundCandidate = null,
                errorMessage = null,
                resultNetworkAssessment = null,
            )
        }
        recomputePreview()
    }

    fun repeatGuidedRun() {
        val snapshot = _state.value
        if (!snapshot.isGuidedPrototypeFlow) return
        applyGuidedDefaults(calibratedAttemptsPerSecond = calibratedThroughput)
        val password = lastGuidedPassword.ifEmpty { snapshot.targetPassword }
        val fittedConfig = applyGuidedAlphabetFit(_state.value.config, password)
        _state.update {
            it.copy(
                config = fittedConfig,
                targetPassword = password,
                configErrorRes = null,
                guidedPhase = GuidedPrototypePhase.Ready,
                guidedFocusTarget = GuidedFocusTarget.Start,
                outcome = null,
                metrics = null,
                foundCandidate = null,
                errorMessage = null,
                resultNetworkAssessment = null,
                passwordVisible = false,
            )
        }
        recomputePreview()
    }

    fun editGuidedPassword() {
        if (!_state.value.isGuidedPrototypeFlow) return
        val password = lastGuidedPassword.ifEmpty { _state.value.targetPassword }
        _state.update {
            it.copy(
                guidedPhase = GuidedPrototypePhase.Configure,
                guidedFocusTarget = GuidedFocusTarget.Password,
                outcome = null,
                metrics = null,
                foundCandidate = null,
                errorMessage = null,
                resultNetworkAssessment = null,
                targetPassword = password,
                passwordVisible = false,
                configErrorRes = null,
                searchPlanSummary = null,
                searchStagesExpanded = false,
            )
        }
    }

    fun changeGuidedSecurity() {
        if (!_state.value.isGuidedPrototypeFlow) return
        val password = lastGuidedPassword.ifEmpty { _state.value.targetPassword }
        _state.update {
            it.copy(
                guidedPhase = GuidedPrototypePhase.Configure,
                guidedFocusTarget = GuidedFocusTarget.Security,
                outcome = null,
                metrics = null,
                foundCandidate = null,
                errorMessage = null,
                resultNetworkAssessment = null,
                targetPassword = password,
                passwordVisible = false,
                configErrorRes = null,
                searchPlanSummary = null,
                searchStagesExpanded = false,
            )
        }
    }

    fun togglePasswordVisibility() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun setAdvancedExpanded(expanded: Boolean) {
        _state.update { it.copy(advancedExpanded = expanded) }
    }

    fun resetToGuidedDefaults() {
        setMode(LabInteractionMode.Guided)
        applyGuidedDefaults(calibratedAttemptsPerSecond = calibratedThroughput)
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
        when {
            usesPrototypePlanner(snapshot) -> recomputePrototypePreview(snapshot)
            else -> recomputeSyntheticPreview(snapshot)
        }
    }

    private fun recomputeSyntheticPreview(snapshot: LabUiState) {
        cachedPrototypePlan = null
        if (usesSyntheticProgressive(snapshot)) {
            recomputeGuidedSyntheticPreview(snapshot)
            return
        }
        val challenge = buildSyntheticPreviewChallenge(snapshot)
        val plan = optimizer.optimize(challenge, snapshot.config.strategy.id)
        val limits = runCatching { buildLimits(snapshot.config) }.getOrNull()
        _state.update {
            it.copy(
                estimatedCombinations = plan.searchSpace,
                feasibility = limits?.let { l -> analyzer.analyze(plan, l, estimator) },
                searchPlanSummary = null,
                configErrorRes = validateForStart(snapshot.config, snapshot),
            )
        }
    }

    private fun recomputeGuidedSyntheticPreview(snapshot: LabUiState) {
        val progressive =
            runCatching {
                progressiveSyntheticBuilder.build(
                    maxAttempts =
                        snapshot.config.maxAttempts?.let { CombinationCount.of(it) }
                            ?: if (snapshot.config.runUntilCancelled) {
                                CombinationCount.of(LabSearchSpaceDefaults.UNBOUNDED_STAGE_CAPACITY)
                            } else {
                                null
                            },
                    maxDuration = snapshot.config.maxDurationSeconds?.seconds,
                    calibratedThroughput = calibratedThroughput,
                )
            }.getOrNull()
        if (progressive == null) {
            _state.update {
                it.copy(
                    estimatedCombinations = CombinationCount.ZERO,
                    feasibility = null,
                    searchPlanSummary = null,
                    configErrorRes = R.string.lab_err_limits_required,
                )
            }
            return
        }
        val previewLimits =
            if (snapshot.config.runUntilCancelled &&
                snapshot.config.maxAttempts == null &&
                snapshot.config.maxDurationSeconds == null
            ) {
                SearchLimits.untilCancelled()
            } else {
                progressive.searchLimits
            }
        _state.update {
            it.copy(
                estimatedCombinations = progressive.searchPlan.searchSpace,
                feasibility = analyzer.analyze(progressive.searchPlan, previewLimits, estimator),
                searchPlanSummary = null,
                configErrorRes = validateForStart(snapshot.config, snapshot),
            )
        }
    }

    private fun recomputePrototypePreview(snapshot: LabUiState) {
        val profile = snapshot.prototype.securityFamily.toSharedPasswordSearchProfile()
        if (profile == null) {
            cachedPrototypePlan = null
            _state.update {
                it.copy(
                    estimatedCombinations = CombinationCount.ZERO,
                    feasibility = null,
                    searchPlanSummary = null,
                    configErrorRes = validateForStart(snapshot.config, snapshot),
                )
            }
            return
        }
        val budget = runCatching { prototypeBudget(snapshot.config) }.getOrNull()
        if (budget == null) {
            cachedPrototypePlan = null
            _state.update {
                it.copy(
                    estimatedCombinations = CombinationCount.ZERO,
                    feasibility = null,
                    searchPlanSummary = null,
                    configErrorRes = R.string.lab_err_limits_required,
                )
            }
            return
        }
        when (
            val result =
                planner.createPlan(
                    PasswordAuditContext(
                        sharedPasswordApplicable = true,
                        searchProfile = profile,
                    ),
                    PasswordAuditPerformanceProfile(
                        calibratedAttemptsPerSecond = calibratedThroughput,
                        availableProcessors = availableProcessors,
                    ),
                    budget,
                )
        ) {
            is PasswordAuditPlanResult.Ready -> {
                cachedPrototypePlan = result.plan
                _state.update {
                    it.copy(
                        estimatedCombinations = result.plan.totalCandidateSpace,
                        feasibility = result.plan.feasibility,
                        searchPlanSummary = result.plan.toSearchPlanUiSummary(),
                        configErrorRes = validateForStart(snapshot.config, snapshot),
                    )
                }
            }
            is PasswordAuditPlanResult.NotApplicable -> {
                cachedPrototypePlan = null
                _state.update {
                    it.copy(
                        estimatedCombinations = CombinationCount.ZERO,
                        feasibility = null,
                        searchPlanSummary = null,
                        configErrorRes = validateForStart(snapshot.config, snapshot),
                    )
                }
            }
        }
    }

    fun start() {
        val snapshot = _state.value
        val validationError = validateForStart(snapshot.config, snapshot)
        if (validationError != null) {
            _state.update { it.copy(configErrorRes = validationError) }
            return
        }
        when (snapshot.secretMode) {
            LabSecretMode.RandomHidden -> startSyntheticSearch(snapshot)
            LabSecretMode.LocalPrototype -> startPrototypeSearch(snapshot)
        }
    }

    private fun startSyntheticSearch(snapshot: LabUiState) {
        val challenge = buildSyntheticSearchChallenge(snapshot)
        val (plan, limits) =
            if (usesSyntheticProgressive(snapshot)) {
                val progressive =
                    progressiveSyntheticBuilder.build(
                        maxAttempts =
                            snapshot.config.maxAttempts?.let { CombinationCount.of(it) }
                                ?: if (snapshot.config.runUntilCancelled) {
                                    CombinationCount.of(LabSearchSpaceDefaults.UNBOUNDED_STAGE_CAPACITY)
                                } else {
                                    null
                                },
                        maxDuration = snapshot.config.maxDurationSeconds?.seconds,
                        calibratedThroughput = calibratedThroughput,
                    )
                val engineLimits =
                    if (snapshot.config.runUntilCancelled &&
                        snapshot.config.maxAttempts == null &&
                        snapshot.config.maxDurationSeconds == null
                    ) {
                        SearchLimits.untilCancelled()
                    } else {
                        progressive.searchLimits
                    }
                progressive.searchPlan to engineLimits
            } else {
                optimizer.optimize(challenge, snapshot.config.strategy.id) to buildLimits(snapshot.config)
            }
        (engine as? WorkerAwareLabSearchEngine)?.workers = snapshot.config.workers
        launchSearch(challenge, plan, limits, clearPassword = false)
    }

    private fun startPrototypeSearch(snapshot: LabUiState) {
        val password = snapshot.targetPassword
        lastGuidedPassword = password
        if (snapshot.isGuidedPrototypeFlow) {
            _state.update {
                it.copy(resultNetworkAssessment = snapshot.prototypeAssessment)
            }
        }
        val verifier = EncapsulatedPasswordVerifier.encapsulate(password)

        val (challenge, plan, limits) =
            if (usesPrototypePlanner(snapshot)) {
                val auditPlan =
                    cachedPrototypePlan ?: run {
                        _state.update { it.copy(configErrorRes = R.string.lab_err_limits_required) }
                        return
                    }
                val ch =
                    LabChallenge.withEncapsulatedVerifier(
                        policy = auditPlan.blindChallengePolicy,
                        verifier = verifier,
                        seed = auditPlan.searchPlan.seed,
                    )
                applyEngineSelection(auditPlan)
                val engineLimits =
                    if (snapshot.config.runUntilCancelled &&
                        snapshot.config.maxAttempts == null &&
                        snapshot.config.maxDurationSeconds == null
                    ) {
                        SearchLimits.untilCancelled()
                    } else {
                        auditPlan.searchLimits
                    }
                Triple(ch, auditPlan.searchPlan, engineLimits)
            } else {
                // OPEN / Enterprise LocalPrototype: no shared-password search space.
                val blindPolicy = blindPolicyFromConfig(snapshot.config, snapshot)
                val ch =
                    LabChallenge.withEncapsulatedVerifier(
                        policy = blindPolicy,
                        verifier = verifier,
                        seed = snapshot.config.seed,
                    )
                val searchPlan = optimizer.optimize(ch, snapshot.config.strategy.id)
                (engine as? WorkerAwareLabSearchEngine)?.workers = snapshot.config.workers
                Triple(ch, searchPlan, buildLimits(snapshot.config))
            }

        launchSearch(challenge, plan, limits, clearPassword = true)
    }

    private fun launchSearch(
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        clearPassword: Boolean,
    ) {
        val controller = CancellationController()
        cancellation = controller

        _state.update {
            it.copy(
                searchState = SearchState.Preparing,
                metrics = null,
                outcome = null,
                foundCandidate = null,
                errorMessage = null,
                targetPassword = if (clearPassword) "" else it.targetPassword,
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
        _state.update {
            it.copy(targetPassword = "", passwordVisible = false)
        }
    }

    private fun LabUiState.reduce(event: LabSearchEvent): LabUiState {
        fun withGuidedTerminalOutcome(next: LabUiState): LabUiState =
            if (next.isGuidedPrototypeFlow) {
                next.copy(guidedPhase = GuidedPrototypePhase.PostResult)
            } else {
                next
            }

        return when (event) {
            LabSearchEvent.Preparing -> copy(searchState = SearchState.Preparing)
            is LabSearchEvent.Started -> copy(searchState = SearchState.Running)
            is LabSearchEvent.Progress -> copy(searchState = SearchState.Running, metrics = event.metrics)
            is LabSearchEvent.CandidateFound ->
                withGuidedTerminalOutcome(
                    copy(
                        searchState = SearchState.Completed,
                        metrics = event.metrics,
                        outcome = SearchOutcome.Found,
                        foundCandidate = event.candidate,
                    ),
                )
            is LabSearchEvent.LimitReached ->
                withGuidedTerminalOutcome(
                    copy(
                        searchState = SearchState.LimitReached,
                        metrics = event.metrics,
                        outcome = SearchOutcome.LimitReached,
                    ),
                )
            is LabSearchEvent.Cancelled ->
                withGuidedTerminalOutcome(
                    copy(
                        searchState = SearchState.Cancelled,
                        metrics = event.metrics,
                        outcome = SearchOutcome.Cancelled,
                    ),
                )
            is LabSearchEvent.Completed ->
                withGuidedTerminalOutcome(
                    copy(
                        searchState = SearchState.Completed,
                        metrics = event.metrics,
                        outcome = SearchOutcome.NotFound,
                    ),
                )
            is LabSearchEvent.Failed ->
                withGuidedTerminalOutcome(
                    copy(
                        searchState = SearchState.Failed,
                        metrics = event.metrics ?: metrics,
                        outcome = SearchOutcome.Failed,
                        errorMessage = event.message,
                    ),
                )
        }
    }

    private fun buildSyntheticPreviewChallenge(state: LabUiState): LabChallenge =
        LabChallenge.withHiddenSecret(
            alphabet = state.config.resolvedAlphabet(),
            lengthPolicy = state.config.resolvedLengthPolicy(),
            seed = state.config.seed,
        )

    private fun buildSyntheticSearchChallenge(state: LabUiState): LabChallenge =
        buildSyntheticPreviewChallenge(state)

    private fun blindPolicyFromConfig(
        config: LabConfig,
        state: LabUiState,
    ): LabSecretPolicy {
        if (state.secretMode == LabSecretMode.LocalPrototype &&
            state.prototype.securityFamily.supportsSharedPasswordDemo()
        ) {
            // Auth-aware demos should use [usesPrototypePlanner]; this branch is a safety net
            // that still refuses non-protocol alphabets / lengths (PSK ≥ 8, WEP hex).
            val profile = state.prototype.securityFamily.toSharedPasswordSearchProfile()
            if (profile != null) {
                return DefaultAutomaticPasswordAuditPlanner.blindChallengePolicyFor(profile)
            }
        }
        return LabSecretPolicy(
            alphabet = config.resolvedAlphabet(),
            length = config.resolvedLengthPolicy(),
        )
    }

    /**
     * Guided and Advanced LocalPrototype for PSK/WEP use [AutomaticPasswordAuditPlanner]
     * (auth-aware stages). RandomHidden Advanced keeps the strategy optimizer.
     */
    private fun usesPrototypePlanner(state: LabUiState): Boolean =
        state.secretMode == LabSecretMode.LocalPrototype &&
            state.prototype.securityFamily.supportsSharedPasswordDemo()

    /** Guided RandomHidden uses [GenericProgressiveAuditPolicy]; Advanced keeps the strategy optimizer. */
    private fun usesSyntheticProgressive(state: LabUiState): Boolean =
        state.secretMode == LabSecretMode.RandomHidden &&
            state.mode == LabInteractionMode.Guided

    private fun prototypeBudget(config: LabConfig): PasswordAuditBudget {
        if (config.runUntilCancelled && config.maxAttempts == null && config.maxDurationSeconds == null) {
            // Stage weights need a finite capacity; the engine run uses [SearchLimits.untilCancelled].
            return PasswordAuditBudget(
                maxAttempts = CombinationCount.of(LabSearchSpaceDefaults.UNBOUNDED_STAGE_CAPACITY),
            )
        }
        return PasswordAuditBudget(
            maxDuration = config.maxDurationSeconds?.seconds,
            maxAttempts = config.maxAttempts?.let { CombinationCount.of(it) },
        )
    }

    private fun applyEngineSelection(plan: PasswordAuditPlan) {
        val aware = engine as? WorkerAwareLabSearchEngine ?: return
        aware.workers = plan.workerCount
        when (val choice = plan.engine) {
            is PasswordAuditEngineChoice.Parallel -> aware.parallelVersion = choice.version
            PasswordAuditEngineChoice.BaselineSequential -> Unit
        }
    }

    private fun validateForStart(
        config: LabConfig,
        state: LabUiState,
    ): Int? {
        val limitsMissing =
            runCatching {
                if (usesPrototypePlanner(state)) {
                    prototypeBudget(config)
                } else {
                    buildLimits(config)
                }
            }.isFailure
        if (limitsMissing) return R.string.lab_err_limits_required

        if (state.secretMode == LabSecretMode.LocalPrototype) {
            if (state.prototype.ssid.isBlank()) return R.string.lab_err_ssid_required
            if (!state.prototype.securityFamily.supportsSharedPasswordDemo()) return null
            if (state.targetPassword.isBlank()) return R.string.lab_err_password_required
            val profile = state.prototype.securityFamily.toSharedPasswordSearchProfile()
            if (profile?.isWepHexProfile() == true) {
                if (!WepHexProgressiveAuditPolicy.isValidHexKey(state.targetPassword)) {
                    return R.string.lab_err_password_wep_hex
                }
            } else if (state.targetPassword.length < WifiPskProgressiveAuditPolicy.MIN_PASSPHRASE_LENGTH) {
                return R.string.lab_err_password_psk_min_length
            }
            return validatePasswordAlphabet(state)
        }

        return null
    }

    private fun validatePasswordAlphabet(state: LabUiState): Int? {
        val password = state.targetPassword
        val alphabet = guidedValidationAlphabet(state)
        if (!password.all { alphabet.symbols.contains(it) }) {
            return if (state.mode == LabInteractionMode.Guided) {
                R.string.lab_err_password_alphabet_guided
            } else {
                R.string.lab_err_password_alphabet
            }
        }
        return null
    }

    private fun guidedValidationAlphabet(state: LabUiState): Alphabet {
        val profile = state.prototype.securityFamily.toSharedPasswordSearchProfile()
        if (!usesPrototypePlanner(state)) {
            return state.config.resolvedAlphabet()
        }
        if (profile?.isWepHexProfile() == true) {
            return GuidedAlphabetFitter.fitForWepHex(state.targetPassword)?.let { fit ->
                fit.customAlphabet ?: Alphabet.HEX_UPPER
            } ?: Alphabet.of("0123456789ABCDEFabcdef")
        }
        return GuidedAlphabetFitter.fitForWifiPsk(state.targetPassword)?.let { fit ->
            fit.customAlphabet ?: fit.choice.alphabet
        } ?: DefaultAutomaticPasswordAuditPlanner.BLIND_CHALLENGE_POLICY.alphabet
    }

    private fun applyGuidedAlphabetFit(
        config: LabConfig,
        password: String,
        family: SecurityFamily = _state.value.prototype.securityFamily,
    ): LabConfig {
        val profile = family.toSharedPasswordSearchProfile()
        val fit =
            if (profile?.isWepHexProfile() == true) {
                GuidedAlphabetFitter.fitForWepHex(password)
            } else {
                GuidedAlphabetFitter.fitForWifiPsk(password)
            } ?: return config
        return config.copy(
            alphabet = fit.choice,
            customAlphabet = fit.customAlphabet,
            secretLength = password.length.coerceAtLeast(1),
        )
    }

    private fun buildLimits(config: LabConfig): SearchLimits {
        if (config.runUntilCancelled && config.maxAttempts == null && config.maxDurationSeconds == null) {
            return SearchLimits.untilCancelled()
        }
        return SearchLimits.of(
            maxDuration = config.maxDurationSeconds?.seconds,
            maxAttempts = config.maxAttempts?.let { CombinationCount.of(it) },
        )
    }
}
