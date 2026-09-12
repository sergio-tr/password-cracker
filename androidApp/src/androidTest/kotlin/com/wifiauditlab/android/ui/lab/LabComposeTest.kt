package com.wifiauditlab.android.ui.lab

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LimitReason
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchSessionId
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class LabComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private class ScriptedEngine(private val events: List<LabSearchEvent>) : LabSearchEngine {
        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> = flow { events.forEach { emit(it) } }
    }

    private class HangingEngine(
        private val metrics: SearchMetrics,
    ) : LabSearchEngine {
        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> =
            flow {
                emit(LabSearchEvent.Preparing)
                emit(LabSearchEvent.Started(SearchSessionId("hang"), plan, plan.searchSpace))
                emit(LabSearchEvent.Progress(metrics))
                while (!cancellation.isCancelled) {
                    delay(50)
                }
                emit(LabSearchEvent.Cancelled(metrics))
            }
    }

    private val metrics =
        SearchMetrics.initial(totalBuckets = 1, searchSpace = CombinationCount.of(10))

    private val samplePlan: LabSearchPlan =
        DefaultSearchPlanOptimizer().optimize(
            LabChallenge.withKnownSecret(Alphabet.DIGITS, "01"),
            LengthPrioritizedStrategy.ID,
        )

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
    ) = LabViewModel(engine, optimizer, analyzer, estimator)

    private fun setLab(vm: LabViewModel) {
        composeTestRule.setContent {
            WifiAuditLabTheme {
                LabScreen(viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun waitForText(
        text: String,
        timeoutMs: Long = 5_000,
    ) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun configAndFeasibility_areVisible() {
        setLab(viewModel(ScriptedEngine(emptyList())))
        composeTestRule.onNodeWithText("Laboratorio sintético").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Antes de iniciar").assertIsDisplayed()
        waitForText("Viabilidad: Razonable")
        composeTestRule.onNodeWithText("Viabilidad: Razonable").assertIsDisplayed()
        composeTestRule.onNodeWithText("ok").assertIsDisplayed()
        composeTestRule.onNodeWithText("Iniciar búsqueda").assertIsDisplayed()
    }

    @Test
    fun invalidLimits_showsErrorAndBlocksStart() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        composeTestRule.waitForIdle()

        // Clear both limit fields so config becomes invalid.
        composeTestRule.onNodeWithText(vm.state.value.config.maxAttempts!!.toString()).performTextClearance()
        composeTestRule.onNodeWithText(vm.state.value.config.maxDurationSeconds!!.toString()).performTextClearance()
        composeTestRule.waitForIdle()

        waitForText("Configura al menos un límite de intentos o de tiempo.")
        composeTestRule
            .onNodeWithText("Configura al menos un límite de intentos o de tiempo.")
            .assertIsDisplayed()
    }

    @Test
    fun start_reachesFound() {
        val vm =
            viewModel(
                ScriptedEngine(
                    listOf(
                        LabSearchEvent.Preparing,
                        LabSearchEvent.Started(SearchSessionId("s"), samplePlan, CombinationCount.of(10)),
                        LabSearchEvent.CandidateFound("lab-01", metrics.copy(attempts = CombinationCount.of(2))),
                    ),
                ),
            )
        setLab(vm)
        composeTestRule.onNodeWithText("Iniciar búsqueda").performClick()
        waitForText("ENCONTRADO")
        composeTestRule.onNodeWithText("ENCONTRADO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Secreto encontrado: lab-01").assertIsDisplayed()
    }

    @Test
    fun stop_visibleWhileRunning_andCancelShowsCancellingOrCancelled() {
        val vm = viewModel(HangingEngine(metrics))
        setLab(vm)
        composeTestRule.onNodeWithText("Iniciar búsqueda").performClick()
        waitForText("STOP")
        composeTestRule.onNodeWithContentDescription("Detener búsqueda").assertIsDisplayed()
        composeTestRule.onNodeWithText("STOP").assertIsDisplayed()

        composeTestRule.onNodeWithText("STOP").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("CANCELLING").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("CANCELADO").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun limitReached_showsOutcome() {
        val vm =
            viewModel(
                ScriptedEngine(
                    listOf(
                        LabSearchEvent.Preparing,
                        LabSearchEvent.Started(SearchSessionId("s"), samplePlan, CombinationCount.of(10)),
                        LabSearchEvent.LimitReached(LimitReason.Attempts, metrics),
                    ),
                ),
            )
        setLab(vm)
        composeTestRule.onNodeWithText("Iniciar búsqueda").performClick()
        waitForText("LÍMITE ALCANZADO")
        composeTestRule.onNodeWithText("LÍMITE ALCANZADO").assertIsDisplayed()
    }
}
