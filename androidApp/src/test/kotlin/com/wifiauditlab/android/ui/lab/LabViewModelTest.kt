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
        ): Flow<LabSearchEvent> = flow { events.forEach { emit(it) } }
    }

    private class RecordingCancelEngine(
        private val terminalMetrics: SearchMetrics,
    ) : LabSearchEngine {
        var cancelled = false
            private set
        var sawCancelSignal = false
            private set

        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> =
            flow {
                emit(LabSearchEvent.Preparing)
                emit(LabSearchEvent.Started(SearchSessionId("s"), plan, plan.searchSpace))
                cancelled = cancellation.isCancelled
                while (!cancellation.isCancelled) {
                    kotlinx.coroutines.delay(10)
                }
                sawCancelSignal = true
                emit(LabSearchEvent.Cancelled(terminalMetrics))
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
    fun guided_defaults_applied_on_init() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            assertEquals(LabInteractionMode.Guided, vm.state.value.mode)
            assertEquals(StrategyChoice.LENGTH, vm.state.value.config.strategy)
            assertNull(vm.state.value.configError)
        }

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
            val vm = viewModel(RecordingCancelEngine(metrics))
            vm.stop()
            assertEquals(SearchState.Cancelling, vm.state.value.searchState)
        }

    @Test
    fun stop_whileRunning_signalsCancellation_andEndsCancelled() =
        runTest(dispatcher) {
            val engine = RecordingCancelEngine(metrics)
            val vm = viewModel(engine)
            vm.start()
            // Reach Running without draining the hang loop (delay until cancel).
            dispatcher.scheduler.runCurrent()
            assertEquals(SearchState.Running, vm.state.value.searchState)

            vm.stop()
            assertEquals(SearchState.Cancelling, vm.state.value.searchState)

            advanceUntilIdle()
            assertTrue(engine.sawCancelSignal)
            assertEquals(SearchState.Cancelled, vm.state.value.searchState)
            assertEquals(SearchOutcome.Cancelled, vm.state.value.outcome)
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
}
