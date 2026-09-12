package com.wifiauditlab.android.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.DeleteSavedNetwork
import com.wifiauditlab.assessment.application.ObserveSavedNetworks
import com.wifiauditlab.assessment.domain.vault.LocationLabel
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.port.SecretVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    val networks: List<SavedWifiNetwork> = emptyList(),
    val revealedSecretId: SavedNetworkId? = null,
    val revealedSecret: String? = null,
)

/**
 * Presentation state for the Vault. Secrets are hidden by default and only read
 * from the [SecretVault] on an explicit reveal action; the revealed value is kept
 * only transiently and cleared on hide.
 */
class VaultViewModel(
    observeSaved: ObserveSavedNetworks,
    private val createSavedNetwork: CreateSavedNetwork,
    private val deleteSavedNetwork: DeleteSavedNetwork,
    private val secretVault: SecretVault,
) : ViewModel() {
    private val reveal = MutableStateFlow<Pair<SavedNetworkId, String>?>(null)

    val state: StateFlow<VaultUiState> =
        combine(observeSaved(), reveal) { networks, revealed ->
            VaultUiState(
                networks = networks,
                revealedSecretId = revealed?.first,
                revealedSecret = revealed?.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    fun create(
        alias: String,
        ssid: String,
        family: SecurityFamily,
        location: String?,
        secret: String?,
    ) {
        viewModelScope.launch {
            createSavedNetwork(
                NewSavedWifiNetwork(
                    alias = alias,
                    ssid = ssid,
                    securityFamily = family,
                    locationLabel = location?.takeIf { it.isNotBlank() }?.let(::LocationLabel),
                ),
                secret?.takeIf { it.isNotEmpty() }?.let(::NetworkSecret),
            )
        }
    }

    fun delete(id: SavedNetworkId) {
        viewModelScope.launch { deleteSavedNetwork(id) }
    }

    fun revealSecret(network: SavedWifiNetwork) {
        val secretId = network.secretId ?: return
        viewModelScope.launch {
            val secret = secretVault.read(secretId)
            if (secret != null) reveal.value = network.id to secret.value
        }
    }

    fun hideSecret() {
        reveal.value = null
    }
}
