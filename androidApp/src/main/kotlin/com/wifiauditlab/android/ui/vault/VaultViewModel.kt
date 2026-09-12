package com.wifiauditlab.android.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.DeleteSavedNetwork
import com.wifiauditlab.assessment.application.ObserveSavedNetworks
import com.wifiauditlab.assessment.application.QuerySavedNetworks
import com.wifiauditlab.assessment.application.RemoveSavedNetworkSecret
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.SavedNetworkListSort
import com.wifiauditlab.assessment.application.SavedNetworkSecretFilter
import com.wifiauditlab.assessment.application.UpdateSavedNetworkAlias
import com.wifiauditlab.assessment.application.UpdateSavedNetworkLocation
import com.wifiauditlab.assessment.application.UpdateSavedNetworkNotes
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.vault.LocationLabel
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    val query: String = "",
    val sort: SavedNetworkListSort = SavedNetworkListSort.AliasAsc,
    val filter: SavedNetworkSecretFilter = SavedNetworkSecretFilter.All,
    val networks: List<SavedWifiNetwork> = emptyList(),
    val isEmpty: Boolean = true,
)

/**
 * Detail of a single saved network. The plaintext secret is only present here
 * after an explicit reveal; it is never loaded automatically and is cleared on
 * hide or when the detail is dismissed.
 */
data class VaultDetailState(
    val network: SavedWifiNetwork,
    val revealedSecret: String? = null,
)

/**
 * Presentation state for the Vault. Search, sort and filter run through
 * [QuerySavedNetworks]; secrets stay hidden by default and are read only on
 * an explicit reveal, kept transiently and cleared on hide.
 */
class VaultViewModel(
    observeSaved: ObserveSavedNetworks,
    private val querySaved: QuerySavedNetworks,
    private val createSavedNetwork: CreateSavedNetwork,
    private val deleteSavedNetwork: DeleteSavedNetwork,
    private val updateAlias: UpdateSavedNetworkAlias,
    private val updateLocation: UpdateSavedNetworkLocation,
    private val updateNotes: UpdateSavedNetworkNotes,
    private val updateSecret: UpdateSavedNetworkSecret,
    private val removeSecret: RemoveSavedNetworkSecret,
    private val revealSecretUseCase: RevealSavedNetworkSecret,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SavedNetworkListSort.AliasAsc)
    private val filter = MutableStateFlow(SavedNetworkSecretFilter.All)
    private val selectedId = MutableStateFlow<SavedNetworkId?>(null)
    private val revealed = MutableStateFlow<Pair<SavedNetworkId, String>?>(null)

    private val networks = observeSaved()

    val state: StateFlow<VaultUiState> =
        combine(networks, query, sort, filter) { all, q, s, f ->
            VaultUiState(
                query = q,
                sort = s,
                filter = f,
                networks = querySaved(all, q, f, s),
                isEmpty = all.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    val detail: StateFlow<VaultDetailState?> =
        combine(networks, selectedId, revealed) { all, id, reveal ->
            val network = id?.let { sel -> all.firstOrNull { it.id == sel } } ?: return@combine null
            VaultDetailState(
                network = network,
                revealedSecret = reveal?.takeIf { it.first == network.id }?.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: SavedNetworkListSort) {
        sort.value = value
    }

    fun setFilter(value: SavedNetworkSecretFilter) {
        filter.value = value
    }

    fun select(network: SavedWifiNetwork) {
        revealed.value = null
        selectedId.value = network.id
    }

    fun dismissDetail() {
        revealed.value = null
        selectedId.value = null
    }

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

    fun editAlias(
        id: SavedNetworkId,
        alias: String,
    ) {
        if (alias.isBlank()) return
        viewModelScope.launch { updateAlias(id, alias.trim()) }
    }

    fun editLocation(
        id: SavedNetworkId,
        location: String?,
    ) {
        viewModelScope.launch {
            updateLocation(id, location?.takeIf { it.isNotBlank() }?.let(::LocationLabel))
        }
    }

    fun editNotes(
        id: SavedNetworkId,
        notes: String?,
    ) {
        viewModelScope.launch { updateNotes(id, notes) }
    }

    /** Sets the secret on first use or replaces the existing ciphertext in place. */
    fun setOrReplaceSecret(
        id: SavedNetworkId,
        secret: String,
    ) {
        if (secret.isEmpty()) return
        viewModelScope.launch {
            updateSecret(id, NetworkSecret(secret))
            if (revealed.value?.first == id) revealed.value = id to secret
        }
    }

    fun removeSecret(id: SavedNetworkId) {
        viewModelScope.launch {
            removeSecret.invoke(id)
            if (revealed.value?.first == id) revealed.value = null
        }
    }

    fun delete(id: SavedNetworkId) {
        viewModelScope.launch {
            deleteSavedNetwork(id)
            if (selectedId.value == id) dismissDetail()
        }
    }

    fun revealSecret(network: SavedWifiNetwork) {
        if (!network.hasSecret) return
        viewModelScope.launch {
            val secret = revealSecretUseCase(network.id) ?: return@launch
            revealed.value = network.id to secret
        }
    }

    fun hideSecret() {
        revealed.value = null
    }
}
