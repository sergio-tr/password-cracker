package com.wifiauditlab.android.ui.audit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.PasswordAuditNetworkContext
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchSessionId
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.audit.PasswordAuditBudgetPreset
import com.wifiauditlab.lab.domain.audit.SharedPasswordSearchProfile
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Compose regression for Quick Password Audit — STOP visibility and cancellation.
 * Avoids asserting off-screen chips on small emulator viewports (API 29).
 */
@RunWith(AndroidJUnit4::class)
class PasswordAuditComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val repo = FakeRepo()
    private val vault = FakeVault()

    private val progressMetrics =
        SearchMetrics(
            attempts = CombinationCount.of(1_822_301),
            elapsed = 18.seconds,
            attemptsPerSecond = 100_000.0,
            currentBucketIndex = 0,
            totalBuckets = 1,
            searchSpace = CombinationCount.of(10),
            processedPercentage = 0.1,
            estimatedRemaining = null,
        )

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
                emit(LabSearchEvent.Started(SearchSessionId("audit-hang"), plan, plan.searchSpace))
                emit(LabSearchEvent.Progress(metrics))
                while (!cancellation.isCancelled) {
                    delay(50)
                }
                emit(LabSearchEvent.Cancelled(metrics))
            }
    }

    private class FoundEngine(
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
                emit(LabSearchEvent.Started(SearchSessionId("audit-found"), plan, plan.searchSpace))
                emit(LabSearchEvent.Progress(metrics))
                emit(LabSearchEvent.CandidateFound("x", metrics))
            }
    }

    private fun viewModel(
        engine: LabSearchEngine,
        request: PasswordAuditRequest = eligibleRequest(),
    ): PasswordAuditViewModel {
        val store = PasswordAuditTargetStore().also { it.set(request) }
        return PasswordAuditViewModel(
            targetStore = store,
            planner = DefaultAutomaticPasswordAuditPlanner(),
            getSavedNetwork = GetSavedNetwork(repo),
            revealSecret = RevealSavedNetworkSecret(repo, vault),
            updateSecret = UpdateSavedNetworkSecret(repo, vault),
            createSavedNetwork = CreateSavedNetwork(repo, vault),
            eligibilityChecker = null,
            assessNetworkSecurity = AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
            engine = engine,
            calibration = null,
            strengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
            availableProcessors = 4,
            ioDispatcher = Dispatchers.Main.immediate,
        )
    }

    private fun setAudit(vm: PasswordAuditViewModel) {
        composeTestRule.setContent {
            WifiAuditLabTheme {
                PasswordAuditScreen(viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun str(id: Int): String = composeTestRule.activity.getString(id)

    private fun waitForText(
        text: String,
        timeoutMs: Long = 8_000,
    ) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilReady(vm: PasswordAuditViewModel) {
        composeTestRule.waitUntil(5_000) {
            !vm.state.value.loadingPlan && vm.state.value.plan != null
        }
    }

    private fun scrollToText(text: String) {
        composeTestRule.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun automaticDefaults_readyWithoutOpeningAdvanced() {
        val vm = viewModel(HangingEngine(progressMetrics))
        setAudit(vm)
        waitUntilReady(vm)
        assertEquals(PasswordAuditInteractionMode.Automatic, vm.state.value.mode)
        assertEquals(PasswordAuditBudgetPreset.Standard, vm.state.value.preset)
        assertFalse(vm.state.value.advancedExpanded)
        // Chrome always visible without scroll (small emulator viewports).
        composeTestRule.onNodeWithContentDescription(str(R.string.navigate_back)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_start").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.audit_mode_automatic)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun advanced_restoreAutomatic_collapsesOptions() {
        val vm = viewModel(HangingEngine(progressMetrics))
        setAudit(vm)
        waitUntilReady(vm)
        // Drive mode via ViewModel (same contract as chips) — avoids flaky off-screen taps.
        vm.selectMode(PasswordAuditInteractionMode.Advanced)
        composeTestRule.waitForIdle()
        assertTrue(vm.state.value.advancedExpanded)
        composeTestRule.onNodeWithContentDescription(str(R.string.audit_cd_reset_auto))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(PasswordAuditInteractionMode.Automatic, vm.state.value.mode)
        assertEquals(PasswordAuditBudgetPreset.Standard, vm.state.value.preset)
        assertFalse(vm.state.value.advancedExpanded)
    }

    @Test
    fun stop_alwaysVisible_thenCancellingThenCancelled_attemptsStable() {
        val vm = viewModel(HangingEngine(progressMetrics))
        setAudit(vm)
        waitUntilReady(vm)
        vm.onPasswordChanged("zzzzzzzz")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audit_start").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        val stopCd = str(R.string.audit_stop)
        composeTestRule.onNodeWithContentDescription(stopCd).assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_stop").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(8_000) {
            vm.state.value.outcome == SearchOutcome.Cancelled ||
                vm.state.value.searchState == SearchState.Cancelled
        }
        waitForText(str(R.string.audit_result_cancelled))
        val attempts = vm.state.value.metrics?.attempts
        composeTestRule.waitForIdle()
        assertEquals(attempts, vm.state.value.metrics?.attempts)
    }

    @Test
    fun found_showsResultAndImproveGuide() {
        val foundMetrics = progressMetrics.copy(attempts = CombinationCount.of(284_193), elapsed = 4.seconds)
        val vm = viewModel(FoundEngine(foundMetrics))
        setAudit(vm)
        waitUntilReady(vm)
        vm.onPasswordChanged("42")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audit_start").performClick()
        composeTestRule.waitUntil(8_000) { vm.state.value.outcome == SearchOutcome.Found }
        assertNotNull(vm.state.value.resultReport)
        waitForText(str(R.string.audit_outcome_found))
        composeTestRule.onNodeWithText(str(R.string.audit_how_to_improve))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun ready_showsSearchProfileAndExpandableStages() {
        val vm = viewModel(HangingEngine(progressMetrics))
        setAudit(vm)
        waitUntilReady(vm)
        val profileLabel =
            composeTestRule.activity.getString(
                R.string.search_plan_profile_label,
                str(R.string.search_profile_wpa2_personal_psk),
            )
        waitForText(profileLabel)
        waitForText(str(R.string.search_plan_psk_summary))
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTag("search_plan_how_search").fetchSemanticsNodes().isNotEmpty()
        }
        // Expand via VM — off-screen TextButton clicks are unreliable on small emulator viewports.
        vm.setSearchStagesExpanded(true)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(8_000) {
            composeTestRule.onAllNodesWithTag("search_plan_stage").fetchSemanticsNodes().isNotEmpty()
        }
        waitForText(str(R.string.search_plan_stage_digits_8))
        waitForText(
            composeTestRule.activity.getString(
                R.string.search_plan_stage_weight,
                str(R.string.search_plan_stage_digits_8),
                20,
            ),
        )
    }

    @Test
    fun wpa3Ready_showsWpa3SearchProfile() {
        val vm =
            viewModel(
                HangingEngine(progressMetrics),
                eligibleRequest(
                    family = SecurityFamily.WPA3_PERSONAL,
                    keyManagements = setOf("SAE"),
                ),
            )
        setAudit(vm)
        waitUntilReady(vm)
        val wpa3Profile = str(R.string.search_profile_wpa3_personal_psk)
        waitForText(wpa3Profile)
        assertEquals(
            SharedPasswordSearchProfile.WPA3_PERSONAL_PSK,
            vm.state.value.plan!!.explanation.details
                .filterIsInstance<com.wifiauditlab.lab.domain.audit.PlanExplanationDetail.WifiPskMechanism>()
                .first()
                .profile,
        )
    }

    @Test
    fun vaultSource_completeAuditFlow_showsFoundWithoutRevealingField() {
        val created =
            runBlocking {
                CreateSavedNetwork(repo, vault)(
                    NewSavedWifiNetwork(
                        alias = "Casa",
                        ssid = "HOME_WIFI",
                        securityFamily = SecurityFamily.WPA2_PERSONAL,
                        knownBssids = setOf(Bssid.of("11:22:33:44:55:66")),
                    ),
                    NetworkSecret("42"),
                )
            }
        val foundMetrics = progressMetrics.copy(attempts = CombinationCount.of(100), elapsed = 1.seconds)
        val vm =
            viewModel(
                FoundEngine(foundMetrics),
                eligibleRequest().copy(savedNetworkId = created.id),
            )
        setAudit(vm)
        waitUntilReady(vm)
        assertTrue(vm.state.value.passwordInput.isEmpty())
        composeTestRule.onNodeWithTag("audit_start").performClick()
        composeTestRule.waitUntil(8_000) { vm.state.value.outcome == SearchOutcome.Found }
        waitForText(str(R.string.audit_outcome_found))
        assertTrue(vm.state.value.passwordInput.isEmpty())
        assertTrue(vm.state.value.resultReport!!.recommendations.isNotEmpty())
        composeTestRule.onNodeWithText(str(R.string.audit_recommendations))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun eligibleRequest(
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
        keyManagements: Set<String> = setOf("WPA_PSK"),
    ): PasswordAuditRequest =
        PasswordAuditRequest(
            network =
                PasswordAuditNetworkContext(
                    displayName = "Casa",
                    ssid = Ssid("HOME_WIFI"),
                    bssid = Bssid.of("11:22:33:44:55:66"),
                    securityProfile =
                        WifiSecurityProfile(
                            family = family,
                            keyManagements = keyManagements,
                            managementFrameProtection = ManagementFrameProtection.CAPABLE,
                            isTransitionMode = false,
                            rawCapabilities = null,
                        ),
                    wifiStandard = null,
                    band = null,
                ),
            savedNetworkId = null,
            observation =
                WifiObservation(
                    ssid = Ssid("HOME_WIFI"),
                    bssid = Bssid.of("11:22:33:44:55:66"),
                    signal = WifiSignal(-50),
                    channel = WifiChannel(36, WifiBand.GHZ_5, 5180),
                    standard = WifiStandard.WIFI_5,
                    securityProfile =
                        WifiSecurityProfile(
                            family = family,
                            keyManagements = keyManagements,
                            managementFrameProtection = ManagementFrameProtection.CAPABLE,
                            isTransitionMode = false,
                            rawCapabilities = null,
                        ),
                    observedAtEpochMillis = 0L,
                ),
        )

    private class FakeRepo : SavedNetworkRepository {
        val networks = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())
        private var seq = 0

        override fun observeAll(): Flow<List<SavedWifiNetwork>> = networks

        override suspend fun getById(id: SavedNetworkId): SavedWifiNetwork? =
            networks.value.firstOrNull { it.id == id }

        override suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork> =
            networks.value.filter { it.identity == identity }

        override suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork {
            val created =
                SavedWifiNetwork(
                    id = SavedNetworkId("net-${seq++}"),
                    alias = network.alias,
                    ssid = network.ssid,
                    securityFamily = network.securityFamily,
                    knownBssids = network.knownBssids,
                    locationLabel = network.locationLabel,
                    geoLocation = network.geoLocation,
                    secretId = null,
                    notes = network.notes,
                    createdAtEpochMillis = 0L,
                    lastSeenAtEpochMillis = null,
                )
            networks.update { it + created }
            return created
        }

        override suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork {
            networks.update { list -> list.map { if (it.id == network.id) network else it } }
            return network
        }

        override suspend fun delete(id: SavedNetworkId) {
            networks.update { list -> list.filterNot { it.id == id } }
        }
    }

    private class FakeVault : SecretVault {
        private val secrets = mutableMapOf<SecretId, NetworkSecret>()
        private var seq = 0

        override suspend fun create(secret: NetworkSecret): SecretId {
            val id = SecretId("secret-${seq++}")
            secrets[id] = secret
            return id
        }

        override suspend fun read(id: SecretId): NetworkSecret? = secrets[id]

        override suspend fun update(
            id: SecretId,
            secret: NetworkSecret,
        ) {
            secrets[id] = secret
        }

        override suspend fun delete(id: SecretId) {
            secrets.remove(id)
        }
    }
}
