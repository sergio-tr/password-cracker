package com.wifiauditlab.android.ui.lab

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.support.observation
import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
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
import com.wifiauditlab.lab.domain.audit.SharedPasswordSearchProfile
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class LabComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

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
        AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
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
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_start_search))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun createAndTest() {
        scrollToText(activity.getString(R.string.lab_create_and_test))
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_create_and_test))
            .performClick()
        composeTestRule.waitForIdle()
    }

    /** Guided defaults to LocalPrototype; engine-scripted tests need RandomHidden. */
    private fun useRandomHidden(vm: LabViewModel) {
        vm.setSecretMode(LabSecretMode.RandomHidden)
        composeTestRule.waitForIdle()
    }

    @Test
    fun configAndFeasibility_areVisible() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        useRandomHidden(vm)
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_title)).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_guided_mode))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_guided_experiment))
            .performScrollTo()
            .assertIsDisplayed()
        // Technical knobs stay collapsed until the user opens advanced options.
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_advanced_options))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_challenge))
            .performScrollTo()
            .assertIsDisplayed()
        val beforeStart = activity.getString(R.string.lab_before_start)
        waitForText(beforeStart)
        scrollToText(beforeStart)
        val feasibilityReasonable =
            activity.getString(
                R.string.lab_feasibility,
                activity.getString(R.string.lab_feasibility_reasonable),
            )
        waitForText(feasibilityReasonable)
        scrollToText(feasibilityReasonable)
        scrollToText("ok")
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_start_search))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_start_test)).assertIsDisplayed()
    }

    @Test
    fun guidedMode_hidesTechnicalConfigByDefault() {
        setLab(viewModel(ScriptedEngine(emptyList())))
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_guided_prototype))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.lab_challenge))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        assertTrue(
            composeTestRule
                .onAllNodesWithContentDescription(activity.getString(R.string.lab_cd_start_search))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun guidedPrototype_createAndTestThenStart_reachesFound() {
        val vm =
            viewModel(
                ScriptedEngine(
                    listOf(
                        LabSearchEvent.Preparing,
                        LabSearchEvent.Started(SearchSessionId("s"), samplePlan, CombinationCount.of(10)),
                        LabSearchEvent.CandidateFound("12345678", metrics.copy(attempts = CombinationCount.of(2))),
                    ),
                ),
            )
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("GuidedLab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("12345678")
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Found }
        waitForText(activity.getString(R.string.lab_result_network_config_heading))
        waitForText(activity.getString(R.string.lab_result_password_resistance_heading))
        waitForText(activity.getString(R.string.lab_post_repeat))
    }

    @Test
    fun guidedPrototype_afterResult_editPasswordKeepsSecurity() {
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
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("EditLab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("12345678")
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.LimitReached }
        scrollToText(activity.getString(R.string.lab_post_edit_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_post_edit_password)).performClick()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.guidedPhase == GuidedPrototypePhase.Configure &&
                vm.state.value.prototype.securityFamily == SecurityFamily.WPA2_PERSONAL
        }
        assertEquals("12345678", vm.state.value.targetPassword)
    }

    @Test
    fun openPreset_showsAssessmentWithoutPasswordField() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_preset_open))
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.prototype.securityFamily == SecurityFamily.OPEN &&
                vm.state.value.prototypeAssessment != null &&
                !vm.state.value.prototypeAssessmentLoading
        }
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("OpenLab")
        waitForText(activity.getString(R.string.lab_prototype_assessment_heading))
        val noPsk = activity.getString(R.string.lab_prototype_no_password_audit)
        waitForText(noPsk)
        scrollToText(noPsk)
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.lab_prototype_password))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun invalidLimits_showsErrorAndBlocksStart() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        useRandomHidden(vm)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_advanced_options))
            .performScrollTo()
            .performClick()

        // Clear both limit fields so config becomes invalid.
        composeTestRule.onNodeWithText(vm.state.value.config.maxAttempts!!.toString()).performScrollTo().performTextClearance()
        composeTestRule.onNodeWithText(vm.state.value.config.maxDurationSeconds!!.toString()).performScrollTo().performTextClearance()
        composeTestRule.waitForIdle()

        val limitsRequired = activity.getString(R.string.lab_err_limits_required)
        waitForText(limitsRequired)
        scrollToText(limitsRequired)
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
        useRandomHidden(vm)
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Found }
        val outcomeFound = activity.getString(R.string.lab_outcome_found)
        waitForText(outcomeFound)
        scrollToText(outcomeFound)
        scrollToText(activity.getString(R.string.lab_found_secret, "lab-01"))
    }

    @Test
    fun stop_alwaysVisibleWithoutScroll_whileRunning() {
        val vm = viewModel(HangingEngine(metrics))
        setLab(vm)
        useRandomHidden(vm)
        startSearch()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchState == SearchState.Running ||
                vm.state.value.searchState == SearchState.Preparing
        }
        // Regression: DETENER must be in the fixed action bar — never require scroll.
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_stop_search))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_stop)).assertIsDisplayed()
        waitForText(activity.getString(R.string.lab_state_running))
    }

    @Test
    fun stop_runsCancellingThenCancelled_withoutScroll() {
        val vm = viewModel(HangingEngine(metrics))
        setLab(vm)
        useRandomHidden(vm)
        startSearch()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchState == SearchState.Running ||
                vm.state.value.searchState == SearchState.Preparing
        }
        val stopping = activity.getString(R.string.lab_stopping)
        val cancelled = activity.getString(R.string.lab_outcome_cancelled)
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_stop_search))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchState == SearchState.Cancelling ||
                vm.state.value.searchState == SearchState.Cancelled ||
                vm.state.value.outcome == SearchOutcome.Cancelled
        }
        // Immediate UI feedback after tap (Cancelling and/or terminal Cancelled).
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(stopping, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText(cancelled).fetchSemanticsNodes().isNotEmpty() ||
                vm.state.value.outcome == SearchOutcome.Cancelled
        }
        composeTestRule.waitUntil(5_000) {
            vm.state.value.outcome == SearchOutcome.Cancelled &&
                vm.state.value.searchState == SearchState.Cancelled
        }
        waitForText(cancelled)
        // Result card is pinned above config after terminal outcomes.
        composeTestRule.onNodeWithText(cancelled).assertIsDisplayed()
        // Start returns to the fixed bar — cancel completed.
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_start_search))
            .assertIsDisplayed()
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
        useRandomHidden(vm)
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.LimitReached }
        val outcomeLimit = activity.getString(R.string.lab_outcome_limit)
        waitForText(outcomeLimit)
        scrollToText(outcomeLimit)
    }

    @Test
    fun prototypeMode_showsLocalOnlyBannerAssessmentAndPasswordField() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        composeTestRule.waitForIdle()
        val localOnly = activity.getString(R.string.lab_prototype_local_only)
        waitForText(localOnly)
        composeTestRule.onNodeWithText(localOnly).performScrollTo().assertIsDisplayed()

        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextClearance()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("TestSSID")
        composeTestRule.waitUntil(5_000) { vm.state.value.prototypeAssessment != null }
        waitForText(activity.getString(R.string.lab_prototype_assessment_heading))
        scrollToText(activity.getString(R.string.lab_prototype_password))
        scrollToText(activity.getString(R.string.lab_create_and_test))
    }

    @Test
    fun prototypeWpa2_startReachesFound() {
        val vm =
            viewModel(
                ScriptedEngine(
                    listOf(
                        LabSearchEvent.Preparing,
                        LabSearchEvent.Started(SearchSessionId("s"), samplePlan, CombinationCount.of(10)),
                        LabSearchEvent.CandidateFound("12345678", metrics.copy(attempts = CombinationCount.of(2))),
                    ),
                ),
            )
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("Wpa2Lab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("12345678")
        composeTestRule.waitForIdle()
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Found }
        waitForText(activity.getString(R.string.lab_outcome_found))
    }

    @Test
    fun guidedPrototype_ready_showsSearchPlanExplainability() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("PlanLab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("12345678")
        createAndTest()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.guidedPhase == GuidedPrototypePhase.Ready &&
                vm.state.value.searchPlanSummary != null
        }
        val wpa2Profile = activity.getString(R.string.search_profile_wpa2_personal_psk)
        waitForText(wpa2Profile)
        scrollToText(wpa2Profile)
        scrollToText(activity.getString(R.string.search_plan_how_search))
        composeTestRule.onNodeWithText(activity.getString(R.string.search_plan_how_search)).performClick()
        waitForText(activity.getString(R.string.search_plan_stage_digits_8))
        assertEquals(SharedPasswordSearchProfile.WPA2_PERSONAL_PSK, vm.state.value.searchPlanSummary!!.profile)
        assertEquals(6, vm.state.value.searchPlanSummary!!.stages.size)
    }

    @Test
    fun guidedPrototype_wpa3Ready_showsWpa3SearchProfile() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_preset_wpa3))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("Wpa3Plan")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("87654321")
        createAndTest()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchPlanSummary?.profile == SharedPasswordSearchProfile.WPA3_PERSONAL_PSK
        }
        waitForText(activity.getString(R.string.search_profile_wpa3_personal_psk))
    }

    @Test
    fun prototypeWpa3_startReachesFound() {
        val vm =
            viewModel(
                ScriptedEngine(
                    listOf(
                        LabSearchEvent.Preparing,
                        LabSearchEvent.Started(SearchSessionId("s"), samplePlan, CombinationCount.of(10)),
                        LabSearchEvent.CandidateFound("87654321", metrics.copy(attempts = CombinationCount.of(2))),
                    ),
                ),
            )
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_preset_wpa3))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("Wpa3Lab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("87654321")
        composeTestRule.waitForIdle()
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Found }
    }

    @Test
    fun prototypeTransition_startReachesFound() {
        val vm =
            viewModel(
                ScriptedEngine(
                    listOf(
                        LabSearchEvent.Preparing,
                        LabSearchEvent.Started(SearchSessionId("s"), samplePlan, CombinationCount.of(10)),
                        LabSearchEvent.CandidateFound("abcdefgh", metrics.copy(attempts = CombinationCount.of(2))),
                    ),
                ),
            )
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_preset_transition))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("TransLab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("abcdefgh")
        composeTestRule.waitForIdle()
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Found }
    }

    @Test
    fun prototypeStop_whileRunning_reachesCancelled() {
        val vm = viewModel(HangingEngine(metrics))
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("StopLab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("12345678")
        composeTestRule.waitForIdle()
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.searchState == SearchState.Running ||
                vm.state.value.searchState == SearchState.Preparing
        }
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_stop_search))
            .performClick()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Cancelled }
    }

    @Test
    fun prototypeLimitReached_showsOutcome() {
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
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("LimitLab")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("12345678")
        composeTestRule.waitForIdle()
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.LimitReached }
        waitForText(activity.getString(R.string.lab_outcome_limit))
    }

    @Test
    fun enterprisePreset_showsAssessmentWithoutPasswordAuditCta() {
        val vm = viewModel(ScriptedEngine(emptyList()))
        setLab(vm)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_preset_enterprise_wpa2))
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.prototype.securityFamily == SecurityFamily.WPA2_ENTERPRISE &&
                vm.state.value.prototypeAssessment != null &&
                !vm.state.value.prototypeAssessmentLoading
        }
        val noPsk = activity.getString(R.string.lab_prototype_no_password_audit)
        waitForText(noPsk)
        scrollToText(noPsk)
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.lab_prototype_password))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
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
        val simulationLocal = activity.getString(R.string.lab_simulation_local)
        waitForText(simulationLocal)
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_network_context))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
        composeTestRule.onNodeWithText("MOVISTAR_XXXX").assertIsDisplayed()
        composeTestRule.onNodeWithText(simulationLocal).assertIsDisplayed()
        waitForText(activity.getString(R.string.lab_simulation_local_body))
    }
}
