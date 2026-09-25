package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.security.SecurityRating
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
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.WifiPskProgressiveAuditPolicy
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
import org.junit.Assert.assertFalse
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

    private class CapturingEngine(
        private val delegate: LabSearchEngine = ScriptedEngine(emptyList()),
    ) : LabSearchEngine {
        var lastChallenge: LabChallenge? = null
            private set

        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> {
            lastChallenge = challenge
            return delegate.run(challenge, plan, limits, cancellation)
        }
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

    private val assessNetworkSecurity = AssessNetworkSecurity(SecurityAssessmentRegistry.default())

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
    ) = LabViewModel(
        engine,
        optimizer,
        analyzer,
        estimator,
        assessNetworkSecurity,
        planner = DefaultAutomaticPasswordAuditPlanner(),
        searchDispatcher = dispatcher,
    )

    private fun LabViewModel.preparePrototype(
        preset: PrototypeSecurityPreset = PrototypeSecurityPreset.WPA2_PERSONAL,
        ssid: String = "LabNet",
        password: String = "1234",
    ) {
        updatePrototype(
            LocalNetworkPrototype(
                ssid = ssid,
                securityProfile = preset.toProfile(),
            ),
        )
        onTargetPasswordChanged(password)
    }

    @Test
    fun guided_defaults_applied_on_init() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            assertEquals(LabInteractionMode.Guided, vm.state.value.mode)
            assertEquals(LabSecretMode.LocalPrototype, vm.state.value.secretMode)
            assertEquals(StrategyChoice.LENGTH, vm.state.value.config.strategy)
            assertEquals(com.wifiauditlab.android.R.string.lab_err_ssid_required, vm.state.value.configErrorRes)
            assertNotNull(vm.state.value.prototypeAssessment)
        }

    @Test
    fun prototypeAssessment_updatesWhenSecurityPresetChanges() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.updatePrototype(
                LocalNetworkPrototype(
                    ssid = "LabNet",
                    securityProfile = PrototypeSecurityPreset.WPA2_PERSONAL.toProfile(),
                ),
            )
            advanceUntilIdle()
            assertEquals(SecurityRating.MODERATE, vm.state.value.prototypeAssessment?.rating)

            vm.updatePrototype(
                vm.state.value.prototype.copy(
                    securityProfile = PrototypeSecurityPreset.WPA3_PERSONAL.toProfile(),
                ),
            )
            advanceUntilIdle()
            assertEquals(SecurityRating.HIGH, vm.state.value.prototypeAssessment?.rating)
        }

    @Test
    fun preview_shows_combinations_and_feasibility() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            vm.setSecretMode(LabSecretMode.RandomHidden)
            advanceUntilIdle()
            assertTrue(vm.state.value.estimatedCombinations > CombinationCount.ZERO)
            assertEquals(FeasibilityRating.Reasonable, vm.state.value.feasibility?.rating)
            assertNull(vm.state.value.configErrorRes)
        }

    @Test
    fun missing_limits_are_invalid_and_start_is_blocked() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            vm.setSecretMode(LabSecretMode.RandomHidden)
            vm.updateConfig(vm.state.value.config.copy(maxAttempts = null, maxDurationSeconds = null))
            advanceUntilIdle()
            assertNotNull(vm.state.value.configErrorRes)
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
            vm.setSecretMode(LabSecretMode.RandomHidden)
            advanceUntilIdle()
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
            vm.setSecretMode(LabSecretMode.RandomHidden)
            advanceUntilIdle()
            vm.start()
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
            vm.setSecretMode(LabSecretMode.RandomHidden)
            advanceUntilIdle()
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Failed, vm.state.value.outcome)
            assertEquals("boom", vm.state.value.errorMessage)
        }

    @Test
    fun prototypeMode_requiresPasswordBeforeStart() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.updatePrototype(LocalNetworkPrototype(ssid = "LabNet"))
            advanceUntilIdle()
            assertEquals(com.wifiauditlab.android.R.string.lab_err_password_required, vm.state.value.configErrorRes)
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchState.Idle, vm.state.value.searchState)
        }

    @Test
    fun createAndTest_movesToReady_andBlocksStartBeforeThat() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(password = "1234")
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.Configure, vm.state.value.guidedPhase)
            assertFalse(vm.state.value.canStartSearch)
            vm.createAndTest()
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.Ready, vm.state.value.guidedPhase)
            assertTrue(vm.state.value.canStartSearch)
            assertEquals(AlphabetChoice.DIGITS, vm.state.value.config.alphabet)
        }

    @Test
    fun repeatGuidedRun_restoresPasswordAndReadyPhase() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.Started(SearchSessionId("s"), samplePlan(), CombinationCount.of(10)),
                            LabSearchEvent.LimitReached(LimitReason.Attempts, metrics),
                        ),
                    ),
                )
            advanceUntilIdle()
            vm.preparePrototype(password = "1234")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.PostResult, vm.state.value.guidedPhase)
            assertEquals("", vm.state.value.targetPassword)
            vm.repeatGuidedRun()
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.Ready, vm.state.value.guidedPhase)
            assertEquals("1234", vm.state.value.targetPassword)
            assertEquals(PrototypeSecurityPreset.WPA2_PERSONAL.toProfile(), vm.state.value.prototype.securityProfile)
        }

    @Test
    fun editGuidedPassword_keepsSecurityPreset() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(preset = PrototypeSecurityPreset.WPA3_PERSONAL, password = "5678")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            vm.editGuidedPassword()
            assertEquals(GuidedPrototypePhase.Configure, vm.state.value.guidedPhase)
            assertEquals(GuidedFocusTarget.Password, vm.state.value.guidedFocusTarget)
            assertEquals("5678", vm.state.value.targetPassword)
            assertEquals(SecurityFamily.WPA3_PERSONAL, vm.state.value.prototype.securityFamily)
        }

    @Test
    fun changeGuidedSecurity_keepsPasswordWhenApplicable() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(password = "1234")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            vm.changeGuidedSecurity()
            assertEquals(GuidedPrototypePhase.Configure, vm.state.value.guidedPhase)
            assertEquals(GuidedFocusTarget.Security, vm.state.value.guidedFocusTarget)
            assertEquals("1234", vm.state.value.targetPassword)
        }

    @Test
    fun guidedAlphabetFitter_updatesConfigWithoutChangingBlindPlanSpace() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(password = "abc")
            advanceUntilIdle()
            val blindSpace = vm.state.value.estimatedCombinations
            vm.onTargetPasswordChanged("abc!@")
            advanceUntilIdle()
            assertNotNull(vm.state.value.config.customAlphabet)
            assertEquals(blindSpace, vm.state.value.estimatedCombinations)
        }

    @Test
    fun prototypeWpa2_foundViaEncapsulatedVerifier() =
        runTest(dispatcher) {
            val engine = CapturingEngine()
            val vm = viewModel(engine)
            advanceUntilIdle()
            vm.preparePrototype(password = "1234")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            val challenge = engine.lastChallenge
            assertNotNull(challenge)
            assertTrue(challenge!!.toString().contains("verifier=encapsulated"))
            assertFalse(challenge.toString().contains("1234"))
        }

    @Test
    fun prototypeWpa3_foundViaEncapsulatedVerifier() =
        runTest(dispatcher) {
            val engine = CapturingEngine()
            val vm = viewModel(engine)
            advanceUntilIdle()
            vm.preparePrototype(
                preset = PrototypeSecurityPreset.WPA3_PERSONAL,
                password = "5678",
            )
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertTrue(engine.lastChallenge!!.toString().contains("verifier=encapsulated"))
        }

    @Test
    fun prototypeTransition_foundViaEncapsulatedVerifier() =
        runTest(dispatcher) {
            val engine = CapturingEngine()
            val vm = viewModel(engine)
            advanceUntilIdle()
            vm.preparePrototype(
                preset = PrototypeSecurityPreset.WPA2_WPA3_TRANSITION,
                password = "abcd",
            )
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertTrue(engine.lastChallenge!!.toString().contains("verifier=encapsulated"))
        }

    @Test
    fun prototypeStart_reachesFound() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.Started(SearchSessionId("s"), samplePlan(), CombinationCount.of(10)),
                            LabSearchEvent.CandidateFound("1234", metrics.copy(attempts = CombinationCount.of(2))),
                        ),
                    ),
                )
            advanceUntilIdle()
            vm.preparePrototype(password = "1234")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Found, vm.state.value.outcome)
            assertEquals("1234", vm.state.value.foundCandidate)
            assertEquals("", vm.state.value.targetPassword)
            assertEquals(GuidedPrototypePhase.PostResult, vm.state.value.guidedPhase)
            assertNotNull(vm.state.value.resultNetworkAssessment)
        }

    @Test
    fun prototypeStart_reachesLimitReached() =
        runTest(dispatcher) {
            val vm =
                viewModel(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.Started(SearchSessionId("s"), samplePlan(), CombinationCount.of(10)),
                            LabSearchEvent.LimitReached(LimitReason.Attempts, metrics),
                        ),
                    ),
                )
            advanceUntilIdle()
            vm.preparePrototype(password = "1234")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchOutcome.LimitReached, vm.state.value.outcome)
        }

    @Test
    fun prototypePlanner_targetBlind_planUnchangedWhenPasswordLengthChanges() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(password = "12")
            advanceUntilIdle()
            val spaceShort = vm.state.value.estimatedCombinations
            vm.onTargetPasswordChanged("1234567890123456")
            advanceUntilIdle()
            assertEquals(spaceShort, vm.state.value.estimatedCombinations)
        }

    @Test
    fun prototypePlanner_challengeHasNoSecretLength() =
        runTest(dispatcher) {
            val engine = CapturingEngine()
            val vm = viewModel(engine)
            advanceUntilIdle()
            vm.preparePrototype(password = "longpassword")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertFalse(engine.lastChallenge!!.toString().contains("longpassword"))
            assertTrue(
                engine.lastChallenge!!.lengthPolicy.minLength >=
                    WifiPskProgressiveAuditPolicy.MIN_PASSPHRASE_LENGTH,
            )
            assertEquals(
                WifiPskProgressiveAuditPolicy.MAX_PASSPHRASE_LENGTH,
                engine.lastChallenge!!.lengthPolicy.maxLength,
            )
        }

    @Test
    fun openPrototype_disablesStartWithoutPasswordFieldError() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.updatePrototype(
                LocalNetworkPrototype(
                    ssid = "OpenLab",
                    securityProfile = PrototypeSecurityPreset.OPEN.toProfile(),
                ),
            )
            advanceUntilIdle()
            assertNull(vm.state.value.configErrorRes)
            assertFalse(vm.state.value.canStartSearch)
        }

    @Test
    fun enterprisePrototype_disablesStartWithoutPasswordFieldError() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.updatePrototype(
                LocalNetworkPrototype(
                    ssid = "CorpLab",
                    securityProfile = PrototypeSecurityPreset.ENTERPRISE_WPA2.toProfile(),
                ),
            )
            advanceUntilIdle()
            assertNull(vm.state.value.configErrorRes)
            assertFalse(vm.state.value.canStartSearch)
        }

    private fun samplePlan(): LabSearchPlan =
        DefaultSearchPlanOptimizer().optimize(
            LabChallenge.withKnownSecret(Alphabet.DIGITS, "01"),
            LengthPrioritizedStrategy.ID,
        )

    @Test
    fun randomMode_usesHiddenSecretChallenge() =
        runTest(dispatcher) {
            val engine = CapturingEngine()
            val vm = viewModel(engine)
            advanceUntilIdle()
            vm.setSecretMode(LabSecretMode.RandomHidden)
            vm.updateConfig(
                vm.state.value.config.copy(
                    alphabet = AlphabetChoice.DIGITS,
                    secretLength = 4,
                    maxAttempts = 500_000,
                    maxDurationSeconds = 30,
                    seed = 1,
                ),
            )
            advanceUntilIdle()
            vm.start()
            advanceUntilIdle()
            val challenge = engine.lastChallenge
            assertNotNull(challenge)
            assertTrue(challenge.toString().contains("verifier=internal"))
        }
}
