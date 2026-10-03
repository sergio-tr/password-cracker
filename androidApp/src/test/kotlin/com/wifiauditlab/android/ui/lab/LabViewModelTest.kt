package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.security.SecurityRating
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
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
import com.wifiauditlab.lab.domain.audit.AutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.GenericProgressiveSearchPlanBuilder
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudget
import com.wifiauditlab.lab.domain.audit.PasswordAuditContext
import com.wifiauditlab.lab.domain.audit.PasswordAuditPerformanceProfile
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlan
import com.wifiauditlab.lab.domain.audit.PasswordAuditPlanResult
import com.wifiauditlab.lab.domain.audit.SharedPasswordSearchProfile
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
import org.junit.Assert.assertNotEquals
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
        var lastPlan: LabSearchPlan? = null
            private set

        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> {
            lastChallenge = challenge
            lastPlan = plan
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

    private class ProfileCapturingPlanner(
        private val delegate: AutomaticPasswordAuditPlanner = DefaultAutomaticPasswordAuditPlanner(),
    ) : AutomaticPasswordAuditPlanner {
        var lastProfile: SharedPasswordSearchProfile? = null
            private set
        var lastPlan: PasswordAuditPlan? = null
            private set

        override fun createPlan(
            context: PasswordAuditContext,
            performance: PasswordAuditPerformanceProfile,
            budget: PasswordAuditBudget,
        ): PasswordAuditPlanResult {
            lastProfile = context.searchProfile
            return when (val result = delegate.createPlan(context, performance, budget)) {
                is PasswordAuditPlanResult.Ready -> {
                    lastPlan = result.plan
                    result
                }
                else -> result
            }
        }
    }

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
        planner: AutomaticPasswordAuditPlanner = DefaultAutomaticPasswordAuditPlanner(),
        networkContextStore: LabNetworkContextStore? = null,
        passwordSeedStore: LabPasswordSeedStore? = null,
    ) = LabViewModel(
        engine,
        optimizer,
        analyzer,
        estimator,
        assessNetworkSecurity,
        planner = planner,
        searchDispatcher = dispatcher,
        networkContextStore = networkContextStore,
        passwordSeedStore = passwordSeedStore,
    )

    private fun LabViewModel.preparePrototype(
        preset: PrototypeSecurityPreset = PrototypeSecurityPreset.WPA2_PERSONAL,
        ssid: String = "LabNet",
        password: String = PSK_DEMO_PASSWORD,
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
    fun refreshNetworkContext_seedsPrototypeFromNearbyStore() =
        runTest(dispatcher) {
            val store = LabNetworkContextStore()
            store.set(
                labNetworkContextFromNearby(
                    NearbyItem(
                        observation =
                            WifiObservation(
                                ssid = Ssid("NearbyNet"),
                                bssid = Bssid.of("AA:BB:CC:DD:EE:01"),
                                signal = WifiSignal(-50),
                                channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
                                standard = WifiStandard.WIFI_5,
                                securityProfile = PrototypeSecurityPreset.WPA2_PERSONAL.toProfile(),
                                observedAtEpochMillis = 0L,
                            ),
                        alias = "Casa",
                        savedNetworkId = null,
                        isKnown = false,
                        ambiguous = false,
                    ),
                    assessmentSummary = "ok",
                ),
            )
            val vm = viewModel(ScriptedEngine(emptyList()), networkContextStore = store)
            advanceUntilIdle()
            assertEquals("Casa", vm.state.value.prototype.displayName)
            assertEquals("NearbyNet", vm.state.value.prototype.ssid)
            assertEquals(SecurityFamily.WPA2_PERSONAL, vm.state.value.prototype.securityFamily)
            assertTrue(vm.state.value.prototypeSeededFromNetwork)
            assertEquals(LabSecretMode.LocalPrototype, vm.state.value.secretMode)
            assertEquals("", vm.state.value.targetPassword)
            assertEquals(GuidedFocusTarget.Password, vm.state.value.guidedFocusTarget)
        }

    @Test
    fun refreshNetworkContext_appliesOneShotPasswordSeedFromVault() =
        runTest(dispatcher) {
            val store = LabNetworkContextStore()
            store.set(
                labNetworkContextFromVault(
                    SavedWifiNetwork(
                        id = SavedNetworkId("v1"),
                        alias = "VaultCasa",
                        ssid = "VAULT_SSID",
                        securityFamily = SecurityFamily.WPA2_PERSONAL,
                        knownBssids = emptySet(),
                        locationLabel = null,
                        geoLocation = null,
                        secretId = null,
                        notes = null,
                        createdAtEpochMillis = 0L,
                        lastSeenAtEpochMillis = null,
                    ),
                ),
            )
            val seedStore = LabPasswordSeedStore()
            seedStore.set(PSK_DEMO_PASSWORD)
            val vm =
                viewModel(
                    ScriptedEngine(emptyList()),
                    networkContextStore = store,
                    passwordSeedStore = seedStore,
                )
            advanceUntilIdle()
            assertEquals("VaultCasa", vm.state.value.prototype.displayName)
            assertEquals("VAULT_SSID", vm.state.value.prototype.ssid)
            assertEquals(PSK_DEMO_PASSWORD, vm.state.value.targetPassword)
            assertNull(seedStore.consume())
            assertTrue(vm.state.value.prototypeSeededFromNetwork)
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
            assertNull(vm.state.value.searchPlanSummary)
        }

    @Test
    fun guided_randomHidden_uses_progressive_multiAlphabet_plan() =
        runTest(dispatcher) {
            val engine =
                CapturingEngine(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.LimitReached(LimitReason.Attempts, metrics),
                        ),
                    ),
                )
            val vm = viewModel(engine)
            advanceUntilIdle()
            assertEquals(LabInteractionMode.Guided, vm.state.value.mode)
            vm.setSecretMode(LabSecretMode.RandomHidden)
            advanceUntilIdle()
            assertNull(vm.state.value.searchPlanSummary)
            assertTrue(vm.state.value.estimatedCombinations > CombinationCount.of(10_000))

            vm.start()
            advanceUntilIdle()

            val plan = engine.lastPlan
            assertNotNull(plan)
            assertEquals(GenericProgressiveSearchPlanBuilder.STRATEGY_ID, plan!!.strategyId)
            val alphabets = plan.buckets.map { it.alphabet }.toSet()
            val lengths = plan.buckets.map { it.length }.toSet()
            assertTrue("guided synthetic should span alphabets, got $alphabets", alphabets.size > 1)
            assertTrue("guided synthetic should span lengths, got $lengths", lengths.size > 1)
            assertTrue(Alphabet.DIGITS in alphabets)
            assertTrue(Alphabet.LOWERCASE in alphabets)
            assertNull(vm.state.value.searchPlanSummary)
        }

    @Test
    fun advanced_randomHidden_keeps_strategy_optimizer_path() =
        runTest(dispatcher) {
            val engine =
                CapturingEngine(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.LimitReached(LimitReason.Attempts, metrics),
                        ),
                    ),
                )
            val vm = viewModel(engine)
            advanceUntilIdle()
            vm.setMode(LabInteractionMode.Advanced)
            vm.setSecretMode(LabSecretMode.RandomHidden)
            vm.updateConfig(
                vm.state.value.config.copy(
                    alphabet = AlphabetChoice.DIGITS,
                    secretLength = 4,
                    strategy = StrategyChoice.LENGTH,
                ),
            )
            advanceUntilIdle()
            vm.start()
            advanceUntilIdle()

            val plan = engine.lastPlan
            assertNotNull(plan)
            assertEquals(LengthPrioritizedStrategy.ID, plan!!.strategyId)
            assertEquals(setOf(Alphabet.DIGITS), plan.buckets.map { it.alphabet }.toSet())
            assertEquals(setOf(4), plan.buckets.map { it.length }.toSet())
        }

    @Test
    fun advanced_localPrototype_uses_authAware_progressive_plan() =
        runTest(dispatcher) {
            val engine =
                CapturingEngine(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.LimitReached(LimitReason.Attempts, metrics),
                        ),
                    ),
                )
            val planner = ProfileCapturingPlanner()
            val vm = viewModel(engine, planner = planner)
            advanceUntilIdle()
            vm.setMode(LabInteractionMode.Advanced)
            vm.setSecretMode(LabSecretMode.LocalPrototype)
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
            advanceUntilIdle()

            assertNotNull(vm.state.value.searchPlanSummary)
            assertEquals(
                SharedPasswordSearchProfile.WPA2_PERSONAL_PSK,
                vm.state.value.searchPlanSummary!!.profile,
            )
            assertEquals(SharedPasswordSearchProfile.WPA2_PERSONAL_PSK, planner.lastProfile)

            // Alphabet override must not shrink the auth-aware search space / strategy.
            vm.updateConfig(
                vm.state.value.config.copy(
                    alphabet = AlphabetChoice.DIGITS,
                    secretLength = 4,
                    strategy = StrategyChoice.LENGTH,
                ),
            )
            advanceUntilIdle()
            assertEquals(
                SharedPasswordSearchProfile.WPA2_PERSONAL_PSK,
                vm.state.value.searchPlanSummary!!.profile,
            )

            vm.start()
            advanceUntilIdle()

            val plan = engine.lastPlan
            assertNotNull(plan)
            assertEquals(DefaultAutomaticPasswordAuditPlanner.AUTOMATIC_STRATEGY_ID, plan!!.strategyId)
            val alphabets = plan.buckets.map { it.alphabet }.toSet()
            assertTrue("PSK progressive should span alphabets, got $alphabets", alphabets.size > 1)
            assertTrue(Alphabet.DIGITS in alphabets)
            assertTrue(Alphabet.PRINTABLE_ASCII in alphabets || Alphabet.ALPHANUMERIC in alphabets)
            assertTrue(engine.lastChallenge!!.toString().contains("verifier=encapsulated"))
        }

    @Test
    fun advanced_localPrototype_wep_uses_hex_progressive_plan() =
        runTest(dispatcher) {
            val engine =
                CapturingEngine(
                    ScriptedEngine(
                        listOf(
                            LabSearchEvent.Preparing,
                            LabSearchEvent.LimitReached(LimitReason.Attempts, metrics),
                        ),
                    ),
                )
            val planner = ProfileCapturingPlanner()
            val vm = viewModel(engine, planner = planner)
            advanceUntilIdle()
            vm.setMode(LabInteractionMode.Advanced)
            vm.setSecretMode(LabSecretMode.LocalPrototype)
            vm.preparePrototype(
                preset = PrototypeSecurityPreset.WEP_LEGACY,
                password = "0123456789",
            )
            advanceUntilIdle()

            assertEquals(SharedPasswordSearchProfile.WEP_HEX, planner.lastProfile)
            assertNotNull(vm.state.value.searchPlanSummary)
            assertEquals(SharedPasswordSearchProfile.WEP_HEX, vm.state.value.searchPlanSummary!!.profile)

            vm.start()
            advanceUntilIdle()

            val plan = engine.lastPlan
            assertNotNull(plan)
            assertEquals(DefaultAutomaticPasswordAuditPlanner.AUTOMATIC_STRATEGY_ID, plan!!.strategyId)
            val alphabets = plan.buckets.map { it.alphabet }.toSet()
            assertTrue(alphabets.all { it == Alphabet.HEX_UPPER || it == Alphabet.HEX_LOWER })
            assertEquals(setOf(10, 26), plan.buckets.map { it.length }.toSet())
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
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.Configure, vm.state.value.guidedPhase)
            assertFalse(vm.state.value.canStartSearch)
            vm.createAndTest()
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.Ready, vm.state.value.guidedPhase)
            assertTrue(vm.state.value.canStartSearch)
            assertEquals(AlphabetChoice.DIGITS, vm.state.value.config.alphabet)
            assertNotNull(vm.state.value.searchPlanSummary)
            assertEquals(SharedPasswordSearchProfile.WPA2_PERSONAL_PSK, vm.state.value.searchPlanSummary!!.profile)
            assertEquals(6, vm.state.value.searchPlanSummary!!.stages.size)
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
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.PostResult, vm.state.value.guidedPhase)
            assertEquals("", vm.state.value.targetPassword)
            vm.repeatGuidedRun()
            advanceUntilIdle()
            assertEquals(GuidedPrototypePhase.Ready, vm.state.value.guidedPhase)
            assertEquals(PSK_DEMO_PASSWORD, vm.state.value.targetPassword)
            assertEquals(PrototypeSecurityPreset.WPA2_PERSONAL.toProfile(), vm.state.value.prototype.securityProfile)
        }

    @Test
    fun editGuidedPassword_keepsSecurityPreset() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(preset = PrototypeSecurityPreset.WPA3_PERSONAL, password = "56781234")
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            vm.editGuidedPassword()
            assertEquals(GuidedPrototypePhase.Configure, vm.state.value.guidedPhase)
            assertEquals(GuidedFocusTarget.Password, vm.state.value.guidedFocusTarget)
            assertEquals("56781234", vm.state.value.targetPassword)
            assertEquals(SecurityFamily.WPA3_PERSONAL, vm.state.value.prototype.securityFamily)
        }

    @Test
    fun changeGuidedSecurity_keepsPasswordWhenApplicable() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            vm.changeGuidedSecurity()
            assertEquals(GuidedPrototypePhase.Configure, vm.state.value.guidedPhase)
            assertEquals(GuidedFocusTarget.Security, vm.state.value.guidedFocusTarget)
            assertEquals(PSK_DEMO_PASSWORD, vm.state.value.targetPassword)
        }

    @Test
    fun guidedAlphabetFitter_updatesConfigWithoutChangingBlindPlanSpace() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(password = "abcdefgh")
            advanceUntilIdle()
            val blindSpace = vm.state.value.estimatedCombinations
            vm.onTargetPasswordChanged("abcdefgh!@")
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
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            val challenge = engine.lastChallenge
            assertNotNull(challenge)
            assertTrue(challenge!!.toString().contains("verifier=encapsulated"))
            assertFalse(challenge.toString().contains(PSK_DEMO_PASSWORD))
        }

    @Test
    fun prototypeWpa3_foundViaEncapsulatedVerifier() =
        runTest(dispatcher) {
            val engine = CapturingEngine()
            val vm = viewModel(engine)
            advanceUntilIdle()
            vm.preparePrototype(
                preset = PrototypeSecurityPreset.WPA3_PERSONAL,
                password = "56781234",
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
                password = "abcd1234",
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
                            LabSearchEvent.CandidateFound(PSK_DEMO_PASSWORD, metrics.copy(attempts = CombinationCount.of(2))),
                        ),
                    ),
                )
            advanceUntilIdle()
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
            advanceUntilIdle()
            vm.createAndTest()
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchOutcome.Found, vm.state.value.outcome)
            assertEquals(PSK_DEMO_PASSWORD, vm.state.value.foundCandidate)
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
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
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
            vm.preparePrototype(password = PSK_DEMO_PASSWORD)
            advanceUntilIdle()
            val spaceShort = vm.state.value.estimatedCombinations
            vm.onTargetPasswordChanged("1234567890123456")
            advanceUntilIdle()
            assertEquals(spaceShort, vm.state.value.estimatedCombinations)
        }

    @Test
    fun prototypePlanner_usesSecurityFamilyProfile() =
        runTest(dispatcher) {
            val planner = ProfileCapturingPlanner()
            val vm = viewModel(ScriptedEngine(emptyList()), planner = planner)
            advanceUntilIdle()
            vm.preparePrototype(
                preset = PrototypeSecurityPreset.WPA2_PERSONAL,
                password = PSK_DEMO_PASSWORD,
            )
            advanceUntilIdle()
            val wpa2StageIds = planner.lastPlan!!.stages.map { it.id.value }
            assertEquals(SharedPasswordSearchProfile.WPA2_PERSONAL_PSK, planner.lastProfile)
            assertTrue(wpa2StageIds.all { it.contains("wpa2") })

            vm.updatePrototype(
                vm.state.value.prototype.copy(
                    securityProfile = PrototypeSecurityPreset.WPA3_PERSONAL.toProfile(),
                ),
            )
            advanceUntilIdle()
            val wpa3StageIds = planner.lastPlan!!.stages.map { it.id.value }
            assertEquals(SharedPasswordSearchProfile.WPA3_PERSONAL_PSK, planner.lastProfile)
            assertTrue(wpa3StageIds.all { it.contains("wpa3") })
            assertNotEquals(wpa2StageIds, wpa3StageIds)
        }

    @Test
    fun prototypePskShortPassword_blocksStartWithMinLengthError() =
        runTest(dispatcher) {
            val vm = viewModel(ScriptedEngine(emptyList()))
            advanceUntilIdle()
            vm.preparePrototype(password = "1234")
            advanceUntilIdle()
            assertEquals(
                com.wifiauditlab.android.R.string.lab_err_password_psk_min_length,
                vm.state.value.configErrorRes,
            )
            assertFalse(vm.state.value.canStartSearch)
            vm.createAndTest()
            advanceUntilIdle()
            assertEquals(
                com.wifiauditlab.android.R.string.lab_err_password_psk_min_length,
                vm.state.value.configErrorRes,
            )
            vm.start()
            advanceUntilIdle()
            assertEquals(SearchState.Idle, vm.state.value.searchState)
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

    private companion object {
        /** Meets Wi‑Fi PSK min length for guided prototype start flows. */
        const val PSK_DEMO_PASSWORD = "12345678"
    }

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
