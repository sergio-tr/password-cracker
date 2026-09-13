package com.wifiauditlab.android.ui.vault

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.support.FakeSavedNetworkRepository
import com.wifiauditlab.android.support.FakeSecretVault
import com.wifiauditlab.android.support.vaultViewModel
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultComposeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val syntheticSecret = "ui-test-secret-aa"

    private fun setVault(
        repo: FakeSavedNetworkRepository = FakeSavedNetworkRepository(),
        vault: FakeSecretVault = FakeSecretVault(),
    ): VaultViewModel {
        val vm = vaultViewModel(repo, vault)
        composeTestRule.setContent {
            WifiAuditLabTheme {
                VaultScreen(viewModel = vm)
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

    private fun seed(
        repo: FakeSavedNetworkRepository,
        vault: FakeSecretVault,
        alias: String,
        ssid: String,
        withSecret: Boolean,
    ) {
        runBlocking {
            CreateSavedNetwork(repo, vault)(
                NewSavedWifiNetwork(
                    alias = alias,
                    ssid = ssid,
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                ),
                if (withSecret) NetworkSecret(syntheticSecret) else null,
            )
        }
    }

    @Test
    fun empty_showsEmptyState() {
        setVault()
        composeTestRule
            .onNodeWithText("Aún no has guardado ninguna red. Usa el botón + para añadir una.")
            .assertIsDisplayed()
    }

    @Test
    fun list_showsSeededNetworks() {
        val repo = FakeSavedNetworkRepository()
        val vault = FakeSecretVault()
        seed(repo, vault, "Alpha", "SSID_A", withSecret = true)
        seed(repo, vault, "Beta", "SSID_B", withSecret = false)

        setVault(repo, vault)
        waitForText("Alpha")
        composeTestRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beta").assertIsDisplayed()
        composeTestRule.onNodeWithText("SSID_A").assertIsDisplayed()
    }

    @Test
    fun search_filtersList() {
        val repo = FakeSavedNetworkRepository()
        val vault = FakeSecretVault()
        seed(repo, vault, "Casa", "HOME_SSID", withSecret = false)
        seed(repo, vault, "Oficina", "WORK_SSID", withSecret = false)

        setVault(repo, vault)
        waitForText("Casa")
        composeTestRule.onNodeWithText("Buscar por alias, SSID o ubicación").performTextInput("casa")
        composeTestRule.waitForIdle()
        waitForText("Casa")
        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithText("Oficina").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun filter_withSecretChip() {
        val repo = FakeSavedNetworkRepository()
        val vault = FakeSecretVault()
        seed(repo, vault, "ConSec", "SSID_SEC", withSecret = true)
        seed(repo, vault, "SinSec", "SSID_PLAIN", withSecret = false)

        setVault(repo, vault)
        waitForText("ConSec")
        composeTestRule.onNodeWithText("Con contraseña").performClick()
        composeTestRule.waitForIdle()
        waitForText("ConSec")
        composeTestRule.onNodeWithText("ConSec").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithText("SinSec").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun sortChip_opensMenu() {
        val repo = FakeSavedNetworkRepository()
        val vault = FakeSecretVault()
        seed(repo, vault, "Zeta", "Z", withSecret = false)
        seed(repo, vault, "Alpha", "A", withSecret = false)

        setVault(repo, vault)
        waitForText("Orden: Alias A-Z")
        composeTestRule.onNodeWithText("Orden: Alias A-Z").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Vistas recientemente").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Vistas recientemente").performClick()
        composeTestRule.waitForIdle()
        waitForText("Orden: Vistas recientemente")
        composeTestRule.onNodeWithText("Orden: Vistas recientemente").assertIsDisplayed()
    }

    @Test
    fun createViaFab_addsNetwork() {
        val repo = FakeSavedNetworkRepository()
        setVault(repo)
        composeTestRule.onNodeWithContentDescription("Añadir red").performClick()
        waitForText("Nueva red guardada")
        composeTestRule.onNodeWithText("Alias").performTextInput("Nueva")
        composeTestRule.onNodeWithText("SSID").performTextInput("LAB_SSID")
        composeTestRule.onNodeWithText("Contraseña (opcional)").performTextInput(syntheticSecret)
        composeTestRule.onNodeWithText("Guardar").performClick()
        composeTestRule.waitUntil(5_000) { repo.networks.value.any { it.alias == "Nueva" } }
        waitForText("Nueva")
        composeTestRule.onNodeWithText("Nueva").assertIsDisplayed()
    }

    @Test
    fun openDetail_editAlias_secretHiddenRevealHide_deleteConfirmCancelConfirm() {
        val repo = FakeSavedNetworkRepository()
        val vault = FakeSecretVault()
        seed(repo, vault, "Detalle", "DET_SSID", withSecret = true)

        setVault(repo, vault)
        waitForText("Detalle")
        composeTestRule.onNodeWithText("Detalle").performClick()
        waitForText("Contraseña")

        composeTestRule.onNodeWithContentDescription("Contraseña oculta").assertIsDisplayed()
        composeTestRule.onNodeWithText("••••••••••••").assertIsDisplayed()

        composeTestRule.onNodeWithText("Mostrar").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Contraseña visible")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Contraseña visible").assertIsDisplayed()

        composeTestRule.onNodeWithText("Ocultar").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithContentDescription("Contraseña oculta")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("Editar alias").performClick()
        waitForText("Editar alias")
        composeTestRule.onNodeWithText("Alias").performTextClearance()
        composeTestRule.onNodeWithText("Alias").performTextInput("Detalle-Edit")
        composeTestRule.onNodeWithText("Guardar").performClick()
        composeTestRule.waitUntil(5_000) {
            repo.networks.value.any { it.alias == "Detalle-Edit" }
        }

        composeTestRule.onNodeWithText("Eliminar red y contraseña").performClick()
        waitForText("Eliminar red")
        composeTestRule.onNodeWithText("Cancelar").performClick()
        composeTestRule.waitForIdle()
        assertTrue(repo.networks.value.isNotEmpty())

        composeTestRule.onNodeWithText("Eliminar red y contraseña").performClick()
        waitForText("Eliminar red")
        composeTestRule.onNodeWithText("Eliminar").performClick()
        composeTestRule.waitUntil(5_000) { repo.networks.value.isEmpty() }
        waitForText("Aún no has guardado ninguna red. Usa el botón + para añadir una.")
    }
}
