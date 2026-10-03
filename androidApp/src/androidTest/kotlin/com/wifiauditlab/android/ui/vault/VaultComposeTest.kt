package com.wifiauditlab.android.ui.vault

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
import com.wifiauditlab.android.support.FakeSavedNetworkRepository
import com.wifiauditlab.android.support.FakeSecretVault
import com.wifiauditlab.android.support.vaultViewModel
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.SavedNetworkListSort
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
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

    private val activity get() = composeTestRule.activity

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
            .onNodeWithText(activity.getString(R.string.vault_empty))
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
        composeTestRule
            .onNodeWithText(activity.getString(R.string.vault_search_hint))
            .performTextInput("casa")
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
        composeTestRule.onNodeWithText(activity.getString(R.string.vault_filter_with_secret)).performClick()
        composeTestRule.waitForIdle()
        waitForText("ConSec")
        composeTestRule.onNodeWithText("ConSec").assertIsDisplayed()
        assertTrue(
            composeTestRule.onAllNodesWithText("SinSec").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun sortChip_selectsLastSeen() {
        val repo = FakeSavedNetworkRepository()
        val vault = FakeSecretVault()
        seed(repo, vault, "Zeta", "Z", withSecret = false)
        seed(repo, vault, "Alpha", "A", withSecret = false)

        val vm = setVault(repo, vault)
        waitForText(activity.getString(R.string.vault_sort_alias))
        composeTestRule
            .onNodeWithText(activity.getString(R.string.vault_sort_last_seen))
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(5_000) {
            vm.state.value.sort == SavedNetworkListSort.LastSeenDesc
        }
        composeTestRule.onNodeWithText("Zeta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpha").assertIsDisplayed()
    }

    @Test
    fun createViaFab_addsNetwork() {
        val repo = FakeSavedNetworkRepository()
        setVault(repo)
        composeTestRule
            .onNodeWithContentDescription(activity.getString(R.string.vault_cd_add))
            .performClick()
        waitForText(activity.getString(R.string.vault_new_network))
        composeTestRule.onNodeWithText(activity.getString(R.string.vault_label_alias)).performTextInput("Nueva")
        composeTestRule.onNodeWithText(activity.getString(R.string.vault_label_ssid)).performTextInput("LAB_SSID")
        composeTestRule
            .onNodeWithText(activity.getString(R.string.vault_password_optional))
            .performTextInput(syntheticSecret)
        composeTestRule.onNodeWithText(activity.getString(R.string.vault_save)).performClick()
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
        val passwordSection = activity.getString(R.string.vault_password_section)
        waitForText(passwordSection)

        val passwordHidden = activity.getString(R.string.vault_password_hidden)
        composeTestRule.onNodeWithContentDescription(passwordHidden).assertIsDisplayed()
        composeTestRule.onNodeWithText("••••••••••••").assertIsDisplayed()

        composeTestRule.onNodeWithText(activity.getString(R.string.vault_show)).performScrollTo().performClick()
        val passwordVisible = activity.getString(R.string.vault_password_visible)
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithContentDescription(passwordVisible)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(passwordVisible).assertIsDisplayed()

        composeTestRule.onNodeWithText(activity.getString(R.string.vault_hide)).performScrollTo().performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithContentDescription(passwordHidden)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText(activity.getString(R.string.vault_edit_alias)).performClick()
        val editAlias = activity.getString(R.string.vault_edit_alias)
        waitForText(editAlias)
        val aliasLabel = activity.getString(R.string.vault_label_alias)
        composeTestRule.onNodeWithText(aliasLabel).performTextClearance()
        composeTestRule.onNodeWithText(aliasLabel).performTextInput("Detalle-Edit")
        composeTestRule.onNodeWithText(activity.getString(R.string.vault_save)).performClick()
        composeTestRule.waitUntil(5_000) {
            repo.networks.value.any { it.alias == "Detalle-Edit" }
        }

        composeTestRule
            .onNodeWithText(activity.getString(R.string.vault_delete_network))
            .performScrollTo()
            .performClick()
        val deleteTitle = activity.getString(R.string.vault_delete_title)
        waitForText(deleteTitle)
        val cancel = activity.getString(R.string.vault_cancel)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(cancel, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(cancel, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertTrue(repo.networks.value.isNotEmpty())

        composeTestRule
            .onNodeWithText(activity.getString(R.string.vault_delete_network))
            .performScrollTo()
            .performClick()
        waitForText(deleteTitle)
        val deleteConfirm = activity.getString(R.string.vault_delete_confirm)
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithText(deleteConfirm, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText(deleteConfirm, useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(5_000) { repo.networks.value.isEmpty() }
        waitForText(activity.getString(R.string.vault_empty))
    }

    @Test
    fun detail_showsLabAndAuditCtas_forPersonalWithSecret() {
        val repo = FakeSavedNetworkRepository()
        val vault = FakeSecretVault()
        seed(repo, vault, "LabEntry", "LAB_ENTRY_SSID", withSecret = true)

        var openedLab: SavedWifiNetwork? = null
        var openedAudit: SavedWifiNetwork? = null
        val vm = vaultViewModel(repo, vault)
        composeTestRule.setContent {
            WifiAuditLabTheme {
                VaultScreen(
                    viewModel = vm,
                    onOpenLab = { network, _ -> openedLab = network },
                    onOpenPasswordAudit = { network -> openedAudit = network },
                )
            }
        }
        composeTestRule.waitForIdle()
        waitForText("LabEntry")
        composeTestRule.onNodeWithText("LabEntry").performClick()
        waitForText(activity.getString(R.string.vault_password_section))

        val openLab = activity.getString(R.string.vault_open_lab)
        composeTestRule.onNodeWithText(openLab).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(openLab).performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedLab?.alias == "LabEntry")

        // Re-open detail for audit CTA (sheet dismissed after lab click may keep detail)
        if (vm.detail.value == null) {
            composeTestRule.onNodeWithText("LabEntry").performClick()
            waitForText(activity.getString(R.string.vault_password_section))
        }
        val openAudit = activity.getString(R.string.vault_open_audit)
        composeTestRule.onNodeWithText(openAudit).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(openAudit).performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedAudit?.alias == "LabEntry")
    }
}
