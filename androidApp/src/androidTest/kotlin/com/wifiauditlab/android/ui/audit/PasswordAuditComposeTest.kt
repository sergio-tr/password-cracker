package com.wifiauditlab.android.ui.audit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wifiauditlab.assessment.application.GetSavedNetwork
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.domain.audit.HeuristicSecretStrengthAnalyzer
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
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
    val composeRule = createComposeRule()

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
            PasswordAuditScreen(viewModel = vm, onBack = {})
        }
        composeRule.onNodeWithText("No hay una red seleccionada para auditar.", substring = false).assertIsDisplayed()
    }

    private class EmptyRepo : SavedNetworkRepository {
        override fun observeAll(): Flow<List<SavedWifiNetwork>> = flowOf(emptyList())

        override suspend fun getById(id: SavedNetworkId) = null

        override suspend fun findByIdentity(identity: NetworkIdentity) = emptyList<SavedWifiNetwork>()

        override suspend fun create(network: com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork) =
            error("unused")

        override suspend fun update(network: SavedWifiNetwork) = network

        override suspend fun delete(id: SavedNetworkId) = Unit
    }

    private class EmptyVault : SecretVault {
        override suspend fun create(secret: com.wifiauditlab.assessment.domain.vault.NetworkSecret) =
            com.wifiauditlab.assessment.domain.vault.SecretId("x")

        override suspend fun read(id: com.wifiauditlab.assessment.domain.vault.SecretId) = null

        override suspend fun update(
            id: com.wifiauditlab.assessment.domain.vault.SecretId,
            secret: com.wifiauditlab.assessment.domain.vault.NetworkSecret,
        ) = Unit

        override suspend fun delete(id: com.wifiauditlab.assessment.domain.vault.SecretId) = Unit
    }
}
