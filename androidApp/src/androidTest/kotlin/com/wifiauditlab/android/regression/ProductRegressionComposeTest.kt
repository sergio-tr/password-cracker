package com.wifiauditlab.android.regression

import android.content.Context
import android.content.res.Configuration
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.support.FakeSavedNetworkRepository
import com.wifiauditlab.android.support.FakeWifiScanner
import com.wifiauditlab.android.support.nearbyViewModel
import com.wifiauditlab.android.support.vaultViewModel
import com.wifiauditlab.android.ui.audit.PasswordAuditRequest
import com.wifiauditlab.android.ui.audit.PasswordAuditScreen
import com.wifiauditlab.android.ui.audit.PasswordAuditTargetStore
import com.wifiauditlab.android.ui.audit.PasswordAuditViewModel
import com.wifiauditlab.android.ui.lab.GuidedPrototypePhase
import com.wifiauditlab.android.ui.lab.LabScreen
import com.wifiauditlab.android.ui.lab.LabViewModel
import com.wifiauditlab.android.ui.nearby.NearbyScreen
import com.wifiauditlab.android.ui.permissions.PermissionAction
import com.wifiauditlab.android.ui.permissions.PermissionCenterContent
import com.wifiauditlab.android.ui.permissions.PermissionCenterViewModel
import com.wifiauditlab.android.ui.permissions.PermissionInventory
import com.wifiauditlab.android.ui.permissions.PermissionItem
import com.wifiauditlab.android.ui.permissions.PermissionKind
import com.wifiauditlab.android.ui.permissions.PermissionStatus
import com.wifiauditlab.android.ui.security.SecurityAnalysisRequest
import com.wifiauditlab.android.ui.security.SecurityAnalysisScreen
import com.wifiauditlab.android.ui.security.SecurityAnalysisTargetStore
import com.wifiauditlab.android.ui.security.SecurityAnalysisViewModel
import com.wifiauditlab.android.ui.settings.SettingsBenchmarkViewModel
import com.wifiauditlab.android.ui.settings.SettingsCalibrationViewModel
import com.wifiauditlab.android.ui.settings.SettingsScreen
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.android.ui.vault.VaultScreen
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.audit.PasswordAuditNetworkContext
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.port.WifiScanState
import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.BenchmarkComparison
import com.wifiauditlab.lab.domain.BenchmarkRecord
import com.wifiauditlab.lab.domain.BenchmarkScenario
import com.wifiauditlab.lab.domain.CalibrationEnvironment
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchMetrics
import com.wifiauditlab.lab.domain.SearchOutcome
import com.wifiauditlab.lab.domain.SearchSessionId
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.LabBenchmarkService
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.engine.DefaultSearchFeasibilityAnalyzer
import com.wifiauditlab.lab.engine.DefaultSearchPlanOptimizer
import com.wifiauditlab.lab.engine.FixedThroughputEstimator
import com.wifiauditlab.lab.engine.LengthPrioritizedStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * FIX-05 product regression guard: localization packs, Material navigation chrome,
 * and guided Lab local-prototype flows discovered in the manual UX gap audit.
 */
@RunWith(AndroidJUnit4::class)
class ProductRegressionComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private class NoOpEngine : LabSearchEngine {
        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> = emptyFlow()
    }

    /** First run hangs until STOP; second run completes with Found (guided edit/rerun flow). */
    private class CancelThenFindEngine(
        private val metrics: SearchMetrics,
        private val plan: LabSearchPlan,
    ) : LabSearchEngine {
        private var invocations = 0

        override fun run(
            challenge: LabChallenge,
            plan: LabSearchPlan,
            limits: SearchLimits,
            cancellation: CancellationSignal,
        ): Flow<LabSearchEvent> =
            flow {
                invocations++
                if (invocations == 1) {
                    emit(LabSearchEvent.Preparing)
                    emit(LabSearchEvent.Started(SearchSessionId("hang"), plan, plan.searchSpace))
                    emit(LabSearchEvent.Progress(metrics))
                    while (!cancellation.isCancelled) {
                        delay(50)
                    }
                    emit(LabSearchEvent.Cancelled(metrics))
                } else {
                    emit(LabSearchEvent.Preparing)
                    emit(LabSearchEvent.Started(SearchSessionId("rerun"), this@CancelThenFindEngine.plan, plan.searchSpace))
                    emit(LabSearchEvent.CandidateFound("87654321", metrics.copy(attempts = CombinationCount.of(2))))
                }
            }
    }

    private val metrics =
        SearchMetrics.initial(totalBuckets = 1, searchSpace = CombinationCount.of(10))

    private val samplePlan: LabSearchPlan =
        DefaultSearchPlanOptimizer().optimize(
            LabChallenge.withKnownSecret(Alphabet.DIGITS, "01"),
            LengthPrioritizedStrategy.ID,
        )

    private fun labViewModel(engine: LabSearchEngine = NoOpEngine()): LabViewModel =
        LabViewModel(
            engine,
            DefaultSearchPlanOptimizer(),
            DefaultSearchFeasibilityAnalyzer(),
            FixedThroughputEstimator(),
            AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
            searchDispatcher = Dispatchers.Main.immediate,
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

    private fun createAndTest() {
        scrollToText(activity.getString(R.string.lab_create_and_test))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_create_and_test)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun startSearch() {
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_start_search))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun assertNoNavigateBack() {
        assertTrue(
            composeTestRule
                .onAllNodesWithContentDescription(activity.getString(R.string.navigate_back))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    // ── Localization packs ────────────────────────────────────────────────────

    @Test
    fun spanishAndEnglishResourcePacks_differForNavAndLabTitles() {
        val es = localizedContext(Locale("es"))
        val en = localizedContext(Locale.ENGLISH)
        assertEquals("Cercanas", es.getString(R.string.nav_nearby))
        assertEquals("Nearby", en.getString(R.string.nav_nearby))
        assertNotEquals(es.getString(R.string.lab_title), en.getString(R.string.lab_title))
    }

    // ── Navigation chrome (FIX-02) ────────────────────────────────────────────

    @Test
    fun topLevel_nearby_hasNoNavigateBack() {
        composeTestRule.setContent {
            WifiAuditLabTheme {
                NearbyScreen(
                    viewModel = nearbyViewModel(FakeWifiScanner(WifiScanState.Idle)),
                    onOpenSecurityAnalysis = {},
                    onOpenLab = { _, _ -> },
                )
            }
        }
        composeTestRule.waitForIdle()
        assertNoNavigateBack()
    }

    @Test
    fun topLevel_vault_hasNoNavigateBack() {
        composeTestRule.setContent {
            WifiAuditLabTheme {
                VaultScreen(viewModel = vaultViewModel())
            }
        }
        composeTestRule.waitForIdle()
        assertNoNavigateBack()
    }

    @Test
    fun topLevel_lab_hasNoNavigateBack() {
        setLab(labViewModel())
        assertNoNavigateBack()
    }

    @Test
    fun topLevel_settings_hasNoNavigateBack() {
        val calibrationEnv =
            CalibrationEnvironment(
                engineVersion = "1.0",
                deviceClass = "phone",
                abi = "arm64-v8a",
                appVersion = "0.1.0",
            )
        val calibrationVm =
            SettingsCalibrationViewModel(
                object : SearchCalibrationService {
                    override suspend fun calibrate(
                        strategyId: String,
                        workerCount: Int,
                        force: Boolean,
                    ) = error("not used")

                    override suspend fun lastRecord() = null

                    override suspend fun loadUsable(
                        strategyId: String,
                        workerCount: Int,
                    ) = null

                    override suspend fun clear() = Unit
                },
                { calibrationEnv },
            )
        val benchmarkVm =
            SettingsBenchmarkViewModel(
                object : LabBenchmarkService {
                    override suspend fun runScenario(scenario: BenchmarkScenario): BenchmarkRecord =
                        error("not used")

                    override suspend fun runSuite(scenarios: List<BenchmarkScenario>): List<BenchmarkRecord> =
                        emptyList()

                    override suspend fun history(): List<BenchmarkRecord> = emptyList()

                    override suspend fun clear() = Unit

                    override suspend fun compareWorkers(challengeProfile: String): BenchmarkComparison =
                        error("not used")
                },
            )
        composeTestRule.setContent {
            WifiAuditLabTheme {
                SettingsScreen(
                    calibrationViewModel = calibrationVm,
                    benchmarkViewModel = benchmarkVm,
                )
            }
        }
        composeTestRule.waitForIdle()
        assertNoNavigateBack()
    }

    @Test
    fun childScreen_permissionCenter_hasNavigateBack() {
        var backPressed = false
        val inventory =
            object : PermissionInventory {
                override fun refresh(): List<PermissionItem> =
                    listOf(
                        PermissionItem(
                            id = "wifi_discovery",
                            nameRes = R.string.permissions_wifi_discovery,
                            kind = PermissionKind.RequiredPermission,
                            status = PermissionStatus.Granted,
                            rationaleRes = R.string.permissions_wifi_rationale_legacy,
                            action = PermissionAction.None,
                        ),
                    )
            }
        val vm = PermissionCenterViewModel { _ -> inventory }
        composeTestRule.setContent {
            WifiAuditLabTheme {
                PermissionCenterContent(
                    viewModel = vm,
                    onBack = { backPressed = true },
                    onRequestPermission = {},
                    onOpenAppSettings = {},
                    onOpenLocationSettings = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.navigate_back))
            .assertIsDisplayed()
            .performClick()
        assertTrue(backPressed)
    }

    @Test
    fun childScreen_securityAnalysis_hasNavigateBack() {
        var backPressed = false
        val store = SecurityAnalysisTargetStore()
        store.set(
            SecurityAnalysisRequest(
                displayName = "Test",
                ssidLabel = "SSID-Test",
                profile =
                    WifiSecurityProfile(
                        family = SecurityFamily.WPA2_PERSONAL,
                        keyManagements = setOf("PSK"),
                        managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                        isTransitionMode = false,
                        rawCapabilities = "[WPA2-PSK-CCMP][ESS]",
                    ),
            ),
        )
        val vm =
            SecurityAnalysisViewModel(
                store,
                AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
            )
        composeTestRule.setContent {
            WifiAuditLabTheme {
                SecurityAnalysisScreen(
                    viewModel = vm,
                    onBack = { backPressed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.navigate_back))
            .assertIsDisplayed()
            .performClick()
        assertTrue(backPressed)
    }

    @Test
    fun childScreen_passwordAudit_hasNavigateBack() {
        var backPressed = false
        val store = PasswordAuditTargetStore()
        store.set(
            PasswordAuditRequest(
                network =
                    PasswordAuditNetworkContext(
                        displayName = "Audit",
                        ssid = Ssid("AuditSSID"),
                        bssid = Bssid.of("11:22:33:44:55:66"),
                        securityProfile =
                            WifiSecurityProfile(
                                family = SecurityFamily.WPA2_PERSONAL,
                                keyManagements = setOf("WPA_PSK"),
                                managementFrameProtection = ManagementFrameProtection.CAPABLE,
                                isTransitionMode = false,
                                rawCapabilities = null,
                            ),
                        wifiStandard = null,
                        band = null,
                    ),
                savedNetworkId = null,
            ),
        )
        val vm =
            PasswordAuditViewModel(
                targetStore = store,
                planner = DefaultAutomaticPasswordAuditPlanner(),
                getSavedNetwork = GetSavedNetwork(FakeSavedNetworkRepository()),
                revealSecret = RevealSavedNetworkSecret(FakeSavedNetworkRepository(), com.wifiauditlab.android.support.FakeSecretVault()),
                updateSecret = UpdateSavedNetworkSecret(FakeSavedNetworkRepository(), com.wifiauditlab.android.support.FakeSecretVault()),
                createSavedNetwork = CreateSavedNetwork(FakeSavedNetworkRepository(), com.wifiauditlab.android.support.FakeSecretVault()),
                eligibilityChecker = null,
                assessNetworkSecurity = AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
                engine = NoOpEngine(),
                calibration = null,
                strengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
                availableProcessors = 4,
                ioDispatcher = Dispatchers.Main.immediate,
            )
        composeTestRule.setContent {
            WifiAuditLabTheme {
                PasswordAuditScreen(
                    viewModel = vm,
                    onBack = { backPressed = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.navigate_back))
            .assertIsDisplayed()
            .performClick()
        assertTrue(backPressed)
    }

    // ── Guided Lab local prototype (FIX-03/04) ──────────────────────────────

    @Test
    fun guidedLab_showsPrototypeSsidWithoutOpeningAdvanced() {
        val vm = labViewModel()
        setLab(vm)

        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.lab_cd_guided_prototype))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_assessment_heading))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_password))
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.lab_challenge))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_create_and_test))
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeTestRule
                .onAllNodesWithText(activity.getString(R.string.lab_start_test))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun guidedPrototype_wpa2CustomPassword_stopThenEditAndRerun() {
        val vm = labViewModel(CancelThenFindEngine(metrics, samplePlan))
        setLab(vm)

        composeTestRule
            .onNodeWithText(activity.getString(R.string.lab_prototype_ssid))
            .performScrollTo()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_ssid)).performTextInput("Wpa2Stop")
        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("12345678")
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
        waitForText(activity.getString(R.string.lab_outcome_cancelled))

        scrollToText(activity.getString(R.string.lab_post_edit_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_post_edit_password)).performClick()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.guidedPhase == GuidedPrototypePhase.Configure &&
                vm.state.value.prototype.securityFamily == SecurityFamily.WPA2_PERSONAL
        }

        scrollToText(activity.getString(R.string.lab_prototype_password))
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextClearance()
        composeTestRule.onNodeWithText(activity.getString(R.string.lab_prototype_password)).performTextInput("87654321")
        createAndTest()
        startSearch()
        composeTestRule.waitUntil(5_000) { vm.state.value.outcome == SearchOutcome.Found }
        waitForText(activity.getString(R.string.lab_result_password_resistance_heading))
        scrollToText(activity.getString(R.string.lab_post_repeat))
    }

    @Test
    fun openPrototype_showsNoPasswordFieldAndExplanation() {
        val vm = labViewModel()
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
    fun enterprisePrototype_showsNoMisleadingPskAudit() {
        val vm = labViewModel()
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

    private fun localizedContext(locale: Locale): Context {
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(locale)
        return appContext.createConfigurationContext(config)
    }
}
