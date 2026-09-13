package com.wifiauditlab.android.ui.audit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.android.ui.theme.WifiAuditLabTheme
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import com.wifiauditlab.lab.domain.audit.DefaultAutomaticPasswordAuditPlanner
import com.wifiauditlab.lab.engine.DefaultLabSearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordAuditComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun missingTargetShowsMessage() {
        val store = PasswordAuditTargetStore()
        val vm =
            PasswordAuditViewModel(
                targetStore = store,
                planner = DefaultAutomaticPasswordAuditPlanner(),
                getSavedNetwork = GetSavedNetwork(EmptyRepo()),
                revealSecret = RevealSavedNetworkSecret(EmptyRepo(), EmptyVault()),
                engine = DefaultLabSearchEngine(),
                strengthAnalyzer = HeuristicSecretStrengthAnalyzer(),
                availableProcessors = 2,
            )
        composeRule.setContent {
            WifiAuditLabTheme {
                PasswordAuditScreen(viewModel = vm, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audit_missing_target").assertIsDisplayed()
        composeRule.onNodeWithText("Auditoría rápida").assertIsDisplayed()
    }

    private class EmptyRepo : SavedNetworkRepository {
        override fun observeAll(): Flow<List<SavedWifiNetwork>> = flowOf(emptyList())

        override suspend fun getById(id: SavedNetworkId) = null

        override suspend fun findByIdentity(identity: NetworkIdentity) = emptyList<SavedWifiNetwork>()

        override suspend fun create(network: NewSavedWifiNetwork) = error("unused")

        override suspend fun update(network: SavedWifiNetwork) = network

        override suspend fun delete(id: SavedNetworkId) = Unit
    }

    private class EmptyVault : SecretVault {
        override suspend fun create(secret: NetworkSecret) = SecretId("x")

        override suspend fun read(id: SecretId) = null

        override suspend fun update(
            id: SecretId,
            secret: NetworkSecret,
        ) = Unit

        override suspend fun delete(id: SecretId) = Unit
    }
}
