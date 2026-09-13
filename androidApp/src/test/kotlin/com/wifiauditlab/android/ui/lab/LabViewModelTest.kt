package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchSessionId
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.FeasibilityRating
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchFeasibility
import com.wifiauditlab.lab.domain.engine.SearchFeasibilityAnalyzer
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import com.wifiauditlab.lab.engine.DefaultSearchPlanOptimizer
import com.wifiauditlab.lab.engine.FixedThroughputEstimator
import com.wifiauditlab.lab.engine.LengthPrioritizedStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LabViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class ScriptedEngine(private val events: List<LabSearchEvent>) : LabSearchEngine {
        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
            options: com.wifiauditlab.lab.domain.engine.LabSearchRunOptions,
        ): Flow<LabSearchEvent> = flow { events.forEach { emit(it) } }
    }

    private class RecordingCancelEngine : LabSearchEngine {
        var cancelled = false
            private set

        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
            options: com.wifiauditlab.lab.domain.engine.LabSearchRunOptions,
        ): Flow<LabSearchEvent> =
            flow {
                emit(LabSearchEvent.Preparing)
                emit(LabSearchEvent.Started(SearchSessionId("s"), plan, plan.searchSpace))
                cancelled = cancellation.isCancelled
            }
    }

    private val metrics =
        SearchMetrics.initial(totalBuckets = 1, searchSpace = CombinationCount.of(10))

    private fun viewModel(
        engine: LabSearchEngine,
        optimizer: SearchPlanOptimizer = DefaultSearchPlanOptimizer(),
        analyzer: SearchFeasibilityAnalyzer =
            object : SearchFeasibilityAnalyzer {
                override fun analyze(
                    plan: LabSearchPlan,
                    limits: SearchLimits,
                    estimator: SearchPerformanceEstimator,
                ) = SearchFeasibility(FeasibilityRating.Reasonable, 1.seconds, "ok")
            },
        estimator: SearchPerformanceEstimator = FixedThroughputEstimator(),
    ) = LabViewModel(engine, optimizer, analyzer, estimator, dispatcher)

    @Test
    fun preview_shows_combinations_and_feasibility() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            assertTrue(vm.state.value.estimatedCombinations > CombinationCount.ZERO)
            assertEquals(FeasibilityRating.Reasonable, vm.state.value.feasibility?.rating)
            assertNull(vm.state.value.configError)
        }

    @Test
    fun missing_limits_are_invalid_and_start_is_blocked() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            vm.updateConfig(vm.state.value.config.copy(maxAttempts = null, maxDurationSeconds = null))
            advanceUntilIdle()
            assertNotNull(vm.state.value.configError)
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchState.Idle, vm.state.value.searchState)
        }

    @Test
    fun start_reaches_found_and_keeps_metrics() =
        runTest(dispatcher) {
            val plan =
                DefaultSearchPlanOptimizer().optimize(
                    LabChallenge.withKnownSecret(Alphabet.DIGITS, "01"),
                    LengthPrioritizedStrategy.ID,
                )
            val vm =
                viewModel(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.Started(SearchSessionId("s"), plan, CombinationCount.of(10)),
                            LabSearchEvent.CandidateFound("01", metrics.copy(attempts = CombinationCount.of(2))),
                        ),
                    ),
                )
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Found, vm.state.value.outcome)
            assertEquals("01", vm.state.value.foundCandidate)
            assertEquals(CombinationCount.of(2), vm.state.value.metrics?.attempts)
        }

    @Test
    fun stop_marks_cancelling() =
        runTest(dispatcher) {
            val vm = viewModel(RecordingCancelEngine())
            vm.stop()
            assertEquals(SearchState.Cancelling, vm.state.value.searchState)
        }

    @Test
    fun failed_keeps_error_message() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    ScriptedEngine(
                        listOf(LabSearchEvent.Failed("boom", metrics)),
                    ),
                )
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Failed, vm.state.value.outcome)
            assertEquals("boom", vm.state.value.errorMessage)
        }

    @Test
    fun pause_event_marks_paused_not_cancelled() =
        runTest(dispatcher) {
            val plan =
                DefaultSearchPlanOptimizer().optimize(
                    LabChallenge.withKnownSecret(Alphabet.DIGITS, "01", seed = 1L),
                    LengthPrioritizedStrategy.ID,
                )
            val cursor =
                com.wifiauditlab.lab.domain.LabSearchCursor(
                    sessionId = SearchSessionId("s"),
                    currentBucketIndex = 0,
                    nextCandidateIndexInBucket = CombinationCount.of(4),
                    attemptCount = CombinationCount.of(4),
                    elapsedActive = 1.seconds,
                )
            val repo = com.wifiauditlab.lab.domain.InMemoryLabSessionRepository()
            val vm =
                LabViewModel(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.Started(SearchSessionId("s"), plan, CombinationCount.of(10)),
                            LabSearchEvent.Paused(metrics.copy(attempts = CombinationCount.of(4)), cursor),
                        ),
                    ),
                    DefaultSearchPlanOptimizer(),
                    object : SearchFeasibilityAnalyzer {
                        override fun analyze(
                            plan: LabSearchPlan,
                            limits: SearchLimits,
                            estimator: SearchPerformanceEstimator,
                        ) = SearchFeasibility(FeasibilityRating.Reasonable, 1.seconds, "ok")
                    },
                    FixedThroughputEstimator(),
                    dispatcher,
                    sessionRepository = repo,
                )
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchState.Paused, vm.state.value.searchState)
            assertNull(vm.state.value.outcome)
            assertTrue(vm.state.value.hasResumableSession)
            assertNotNull(repo.load())
            vm.stop()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Cancelled, vm.state.value.outcome)
            assertNull(repo.load())
        }
}
