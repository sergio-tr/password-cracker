package com.wifiauditlab.android.ui.lab

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.support.observation
import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LimitReason
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertTrue
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
        networkContextStore: LabNetworkContextStore? = null,
    ) = LabViewModel(
        engine,
        optimizer,
        analyzer,
        estimator,
        // Keep search collection on Main so Compose UI tests observe emissions deterministically.
        searchDispatcher = Dispatchers.Main.immediate,
        networkContextStore = networkContextStore,
    )

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

    private fun scrollToText(text: String) {
        composeTestRule.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun startSearch() {
        // Start lives in the fixed bottom action bar — must not require scroll.
        composeTestRule.onNodeWithContentDescription("Iniciar búsqueda").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun configAndFeasibility_areVisible() {
        setLab(viewModel(ScriptedEngine(emptyList())))
        composeTestRule.onNodeWithText("Laboratorio sintético").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Modo guiado del laboratorio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Experimento guiado").assertIsDisplayed()
        // Technical knobs stay collapsed until the user opens advanced options.
        composeTestRule.onNodeWithContentDescription("Opciones avanzadas").performClick()
        composeTestRule.onNodeWithText("Reto").assertIsDisplayed()
        waitForText("Antes de iniciar")
        scrollToText("Antes de iniciar")
        waitForText("Viabilidad: Razonable")
        scrollToText("Viabilidad: Razonable")
        scrollToText("ok")
        composeTestRule.onNodeWithContentDescription("Iniciar búsqueda").assertIsDisplayed()
        composeTestRule.onNodeWithText("Iniciar prueba").assertIsDisplayed()
    }

    @Test
    fun guidedMode_hidesTechnicalConfigByDefault() {
        setLab(viewModel(ScriptedEngine(emptyList())))
        composeTestRule.onNodeWithContentDescription("Modo guiado del laboratorio").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Reto").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithText("Iniciar prueba").assertIsDisplayed()
    }

    @Test
    fun invalidLimits_showsErrorAndBlocksStart() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Opciones avanzadas").performClick()

        // Clear both limit fields so config becomes invalid.
        composeTestRule.onNodeWithText(vm.state.value.config.maxAttempts!!.toString()).performScrollTo().performTextClearance()
        composeTestRule.onNodeWithText(vm.state.value.config.maxDurationSeconds!!.toString()).performScrollTo().performTextClearance()
        composeTestRule.waitForIdle()

        waitForText("Configura al menos un límite de intentos o de tiempo.")
        scrollToText("Configura al menos un límite de intentos o de tiempo.")
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
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Found }
        waitForText("ENCONTRADO")
        scrollToText("ENCONTRADO")
        scrollToText("Secreto encontrado: lab-01")
    }

    @Test
    fun stop_alwaysVisibleWithoutScroll_whileRunning() {
        val vm = viewModel(HangingEngine(metrics))
        setLab(vm)
        startSearch()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchState == SearchState.Running ||
                vm.state.value.searchState == SearchState.Preparing
        }
        // Regression: DETENER must be in the fixed action bar — never require scroll.
        composeTestRule.onNodeWithContentDescription("Detener búsqueda").assertIsDisplayed()
        composeTestRule.onNodeWithText("DETENER").assertIsDisplayed()
        waitForText("Buscando")
    }

    @Test
    fun stop_runsCancellingThenCancelled_withoutScroll() {
        val vm = viewModel(HangingEngine(metrics))
        setLab(vm)
        startSearch()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchState == SearchState.Running ||
                vm.state.value.searchState == SearchState.Preparing
        }
        composeTestRule.onNodeWithContentDescription("Detener búsqueda").assertIsDisplayed().performClick()

        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchState == SearchState.Cancelling ||
                vm.state.value.searchState == SearchState.Cancelled ||
                vm.state.value.outcome == SearchOutcome.Cancelled
        }
        // Immediate UI feedback after tap (Cancelling and/or terminal Cancelled).
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Deteniendo", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("CANCELADO").fetchSemanticsNodes().isNotEmpty() ||
                vm.state.value.outcome == SearchOutcome.Cancelled
        }
        composeTestRule.waitUntil(5_000) {
            vm.state.value.outcome == SearchOutcome.Cancelled &&
                vm.state.value.searchState == SearchState.Cancelled
        }
        waitForText("CANCELADO")
        // Result card is pinned above config after terminal outcomes.
        composeTestRule.onNodeWithText("CANCELADO").assertIsDisplayed()
        // Start returns to the fixed bar — cancel completed.
        composeTestRule.onNodeWithContentDescription("Iniciar búsqueda").assertIsDisplayed()
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
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.LimitReached }
        waitForText("LÍMITE ALCANZADO")
        scrollToText("LÍMITE ALCANZADO")
    }

    @Test
    fun networkContextBanner_showsSimulationLocalDisclaimer() {
        val store = LabNetworkContextStore()
        store.set(
            labNetworkContextFromNearby(
                NearbyItem(
                    observation = observation(ssid = "MOVISTAR_XXXX"),
                    alias = "Casa",
                    savedNetworkId = null,
                    isKnown = true,
                    ambiguous = false,
                ),
                assessmentSummary = null,
            ),
        )
        setLab(viewModel(ScriptedEngine(emptyList()), networkContextStore = store))
        waitForText("Simulación local")
        composeTestRule.onNodeWithContentDescription("Contexto de red del laboratorio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
        composeTestRule.onNodeWithText("MOVISTAR_XXXX").assertIsDisplayed()
        composeTestRule.onNodeWithText("Simulación local").assertIsDisplayed()
        waitForText("no realiza intentos de conexión")
    }
}
