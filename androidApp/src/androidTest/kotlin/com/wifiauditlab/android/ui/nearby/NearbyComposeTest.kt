package com.wifiauditlab.android.ui.nearby

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        composeTestRule.onNodeWithText("Escaneando…").assertIsDisplayed()
    }

    @Test
    fun emptyResults_showsEmptyMessage() {
        setNearby(FakeWifiScanner(WifiScanState.Results(emptyList())))
        waitForText("No se han encontrado redes todavía.")
        composeTestRule.onNodeWithText("No se han encontrado redes todavía.").assertIsDisplayed()
    }

    @Test
    fun results_unknownNetworkShowsChip() {
        setNearby(FakeWifiScanner(WifiScanState.Results(listOf(observation()))))
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Desconocida").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Guardada").assertIsDisplayed()
    }

    @Test
    fun permissionRequired_showsGrantPrompt() {
        setNearby(FakeWifiScanner(WifiScanState.PermissionRequired))
        composeTestRule
            .onNodeWithText("Necesitamos permiso para descubrir redes. Pulsa Actualizar para concederlo.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Conceder permiso").assertIsDisplayed()
    }

    @Test
    fun locationDisabled_showsLocationPrompt() {
        setNearby(FakeWifiScanner(WifiScanState.LocationServicesDisabled))
        composeTestRule
            .onNodeWithText("Activa la ubicación del dispositivo para poder escanear redes Wi-Fi.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Abrir ajustes de ubicación").assertIsDisplayed()
    }

    @Test
    fun throttled_showsLimitedScanMessage() {
        setNearby(
            FakeWifiScanner(WifiScanState.Throttled(listOf(observation(ssid = "Cafe")))),
        )
        waitForText("Escaneo limitado temporalmente. Se muestran los últimos resultados.")
        composeTestRule
            .onNodeWithText("Escaneo limitado temporalmente. Se muestran los últimos resultados.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Cafe").assertIsDisplayed()
    }

    @Test
    fun scannerError_showsErrorMessage() {
        setNearby(FakeWifiScanner(WifiScanState.Error("timeout de laboratorio")))
        composeTestRule.onNodeWithText("Error al escanear: timeout de laboratorio").assertIsDisplayed()
    }

    @Test
    fun refresh_delegatesToScanner() {
        val scanner = FakeWifiScanner(WifiScanState.Idle)
        setNearby(scanner)
        composeTestRule.onNodeWithText("Actualizar").performClick()
        composeTestRule.waitUntil(5_000) { scanner.refreshCount >= 1 }
        assertEquals(1, scanner.refreshCount)
    }

    @Test
    fun openDetail_showsSecurityAndVaultActions() {
        setNearby(FakeWifiScanner(WifiScanState.Results(listOf(observation()))))
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").performClick()
        waitForText("Probar en laboratorio")
        composeTestRule.onNodeWithText("Probar en laboratorio").performScrollTo().assertIsDisplayed()
        waitForText("Analizar seguridad")
        composeTestRule.onNodeWithText("Analizar seguridad").performScrollTo().assertIsDisplayed()
        waitForText("Guardar en el Vault")
        composeTestRule.onNodeWithText("Guardar en el Vault").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saveToVault_withAliasMarksSaved() {
        val repo = FakeSavedNetworkRepository()
        setNearby(FakeWifiScanner(WifiScanState.Results(listOf(observation()))), repo)
        waitForText("Home")
        composeTestRule.onNodeWithText("Home").performClick()
        waitForText("Alias")
        composeTestRule.onNodeWithText("Alias").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithText("Alias").performTextInput("Lab-Casa")
        composeTestRule.onNodeWithText("Guardar en el Vault").performScrollTo().performClick()
        composeTestRule.waitUntil(5_000) { repo.networks.value.isNotEmpty() }
        assertEquals("Lab-Casa", repo.networks.value.single().alias)
        waitForText("Guardada en el Vault.")
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
        waitForText("Analizar seguridad")
        composeTestRule.onNodeWithText("Analizar seguridad").performClick()
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
        waitForText("Probar en laboratorio")
        composeTestRule.onNodeWithText("Probar en laboratorio").performClick()
        composeTestRule.waitUntil(5_000) { opened != null }
        assertTrue(opened!!.observation.ssid.value == "Home")
    }
}
