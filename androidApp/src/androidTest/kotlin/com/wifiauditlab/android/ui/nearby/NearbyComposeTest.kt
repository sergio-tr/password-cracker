package com.wifiauditlab.android.ui.nearby

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.R
import com.wifiauditlab.android.support.FakeSavedNetworkRepository
import com.wifiauditlab.android.support.FakeWifiScanner
import com.wifiauditlab.android.support.nearbyViewModel
import com.wifiauditlab.android.support.observation
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.port.WifiScanState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private fun setNearby(
        scanner: FakeWifiScanner,
        repo: FakeSavedNetworkRepository = FakeSavedNetworkRepository(),
        onOpenSecurityAnalysis: (NearbyItem) -> Unit = {},
        onOpenLab: (NearbyItem, String?) -> Unit = { _, _ -> },
    ): NearbyViewModel {
        val vm = nearbyViewModel(scanner, repo)
        composeTestRule.setContent {
            WifiAuditLabTheme {
                NearbyScreen(
                    viewModel = vm,
                    onOpenSecurityAnalysis = onOpenSecurityAnalysis,
                    onOpenLab = onOpenLab,
                )
            }
        }
        composeTestRule.waitForIdle()
        return vm
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
    fun loading_showsScanningMessage() {
        setNearby(FakeWifiScanner(WifiScanState.Loading))
        composeTestRule.onNodeWithText(activity.getString(R.string.nearby_scanning)).assertIsDisplayed()
    }

    @Test
    fun emptyResults_showsEmptyMessage() {
        setNearby(FakeWifiScanner(WifiScanState.Results(emptyList())))
        val emptyMessage = activity.getString(R.string.nearby_empty)
        waitForText(emptyMessage)
        composeTestRule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    @Test
    fun results_unknownNetworkShowsChip() {
        setNearby(FakeWifiScanner(WifiScanState.Results(listOf(observation()))))
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.nearby_unknown)).assertIsDisplayed()
    }

    @Test
    fun knownNetwork_showsAliasAndSavedChip() {
        val repo = FakeSavedNetworkRepository()
        runBlocking {
            repo.create(
                NewSavedWifiNetwork(
                    alias = "Casa",
                    ssid = "Home",
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                    knownBssids = setOf(Bssid.of("AA:BB:CC:DD:EE:99")),
                ),
            )
        }
        setNearby(
            FakeWifiScanner(WifiScanState.Results(listOf(observation(ssid = "Home")))),
            repo,
        )
        waitForText("Casa")
        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.nearby_saved)).assertIsDisplayed()
    }

    @Test
    fun permissionRequired_showsGrantPrompt() {
        setNearby(FakeWifiScanner(WifiScanState.PermissionRequired))
        composeTestRule
            .onNodeWithText(activity.getString(R.string.nearby_permission_required))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(activity.getString(R.string.nearby_grant_permission)).assertIsDisplayed()
    }

    @Test
    fun locationDisabled_showsLocationPrompt() {
        setNearby(FakeWifiScanner(WifiScanState.LocationServicesDisabled))
        composeTestRule
            .onNodeWithText(activity.getString(R.string.nearby_location_disabled))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(activity.getString(R.string.nearby_open_location_settings))
            .assertIsDisplayed()
    }

    @Test
    fun throttled_showsLimitedScanMessage() {
        setNearby(
            FakeWifiScanner(WifiScanState.Throttled(listOf(observation(ssid = "Cafe")))),
        )
        val throttledMessage = activity.getString(R.string.nearby_throttled)
        waitForText(throttledMessage)
        composeTestRule.onNodeWithText(throttledMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cafe").assertIsDisplayed()
    }

    @Test
    fun scannerError_showsErrorMessage() {
        val errorDetail = "timeout de laboratorio"
        setNearby(FakeWifiScanner(WifiScanState.Error(errorDetail)))
        composeTestRule
            .onNodeWithText(activity.getString(R.string.nearby_scan_error, errorDetail))
            .assertIsDisplayed()
    }

    @Test
    fun refresh_delegatesToScanner() {
        val scanner = FakeWifiScanner(WifiScanState.Idle)
        setNearby(scanner)
        composeTestRule.onNodeWithText(activity.getString(R.string.nearby_refresh)).performClick()
        composeTestRule.waitUntil(5_000) { scanner.refreshCount >= 1 }
        assertEquals(1, scanner.refreshCount)
    }

    @Test
    fun openDetail_showsSecurityAndVaultActions() {
        setNearby(FakeWifiScanner(WifiScanState.Results(listOf(observation()))))
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").performClick()
        val openWifi = activity.getString(R.string.audit_open_wifi_settings)
        waitForText(openWifi)
        composeTestRule.onNodeWithText(openWifi).performScrollTo().assertIsDisplayed()
        val openLab = activity.getString(R.string.nearby_open_lab)
        waitForText(openLab)
        composeTestRule.onNodeWithText(openLab).performScrollTo().assertIsDisplayed()
        val analyzeSecurity = activity.getString(R.string.nearby_analyze_security)
        waitForText(analyzeSecurity)
        composeTestRule.onNodeWithText(analyzeSecurity).performScrollTo().assertIsDisplayed()
        val saveVault = activity.getString(R.string.nearby_save_vault)
        waitForText(saveVault)
        composeTestRule.onNodeWithText(saveVault).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saveToVault_withAliasMarksSaved() {
        val repo = FakeSavedNetworkRepository()
        setNearby(FakeWifiScanner(WifiScanState.Results(listOf(observation()))), repo)
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").performClick()
        // Wait for async eligibility/assessment so the sheet layout is stable before editing.
        waitForText(activity.getString(R.string.audit_open_wifi_settings), timeoutMs = 10_000)
        val aliasLabel = activity.getString(R.string.nearby_label_alias)
        waitForText(aliasLabel)
        composeTestRule
            .onNodeWithText(aliasLabel)
            .performScrollTo()
            .performTextReplacement("Lab-Casa")
        composeTestRule
            .onNodeWithText(activity.getString(R.string.nearby_save_vault))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(15_000) { repo.networks.value.isNotEmpty() }
        assertEquals("Lab-Casa", repo.networks.value.single().alias)
        waitForText(activity.getString(R.string.nearby_vault_saved_confirm), timeoutMs = 10_000)
    }

    @Test
    fun securityAnalysisCallback_isFlagged() {
        var opened: NearbyItem? = null
        setNearby(
            FakeWifiScanner(WifiScanState.Results(listOf(observation()))),
            onOpenSecurityAnalysis = { opened = it },
        )
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").performClick()
        val analyzeSecurity = activity.getString(R.string.nearby_analyze_security)
        waitForText(analyzeSecurity)
        composeTestRule.onNodeWithText(analyzeSecurity).performClick()
        composeTestRule.waitUntil(5_000) { opened != null }
        assertTrue(opened!!.observation.ssid.value == "Home")
    }

    @Test
    fun openLabCallback_isFlagged() {
        var opened: NearbyItem? = null
        setNearby(
            FakeWifiScanner(WifiScanState.Results(listOf(observation()))),
            onOpenLab = { item, _ -> opened = item },
        )
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").performClick()
        val openLab = activity.getString(R.string.nearby_open_lab)
        waitForText(openLab)
        composeTestRule.onNodeWithText(openLab).performClick()
        composeTestRule.waitUntil(5_000) { opened != null }
        assertTrue(opened!!.observation.ssid.value == "Home")
    }
}
