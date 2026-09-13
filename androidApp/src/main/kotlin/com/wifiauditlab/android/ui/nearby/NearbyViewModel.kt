package com.wifiauditlab.android.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.NearbyNetwork
import com.wifiauditlab.assessment.application.ObserveNearbyNetworks
import com.wifiauditlab.assessment.application.RecordNearbySightings
import com.wifiauditlab.assessment.application.RefreshNearbyNetworks
import com.wifiauditlab.assessment.application.SaveNearbyNetwork
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibility
import com.wifiauditlab.assessment.domain.audit.PasswordAuditEligibilityChecker
import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.connection.DefaultNetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatch
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.assessment.port.WifiScanRequestResult
import com.wifiauditlab.assessment.port.WifiScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NearbyItem(
    val observation: WifiObservation,
    val alias: String?,
    val savedNetworkId: SavedNetworkId?,
    val isKnown: Boolean,
    val ambiguous: Boolean,
    val connectionMatch: NetworkConnectionMatch? = null,
) {
    val isCurrentlyConnected: Boolean
        get() =
            connectionMatch is NetworkConnectionMatch.Exact ||
                connectionMatch is NetworkConnectionMatch.Probable
}

data class NearbyUiState(
    val scanState: WifiScanState = WifiScanState.Idle,
    val items: List<NearbyItem> = emptyList(),
)

/** Detail of a selected network, with its (async) security assessment and audit eligibility. */
data class NearbyDetailState(
    val item: NearbyItem,
    val assessment: SecurityAssessment? = null,
    val saved: Boolean = false,
    val auditEligibility: PasswordAuditEligibility? = null,
)

private fun NearbyNetwork.toItem(connectionMatch: NetworkConnectionMatch?): NearbyItem =
    NearbyItem(
        observation = observation,
        alias = knownAlias,
        savedNetworkId = knownNetworkId,
        isKnown = isKnown,
        ambiguous = isAmbiguous,
        connectionMatch = connectionMatch,
    )

/**
 * Presentation for the nearby-networks screen. Contains no matching or
 * assessment logic itself: it only orchestrates use cases and maps their
 * results to UI state.
 */
class NearbyViewModel(
    observeNearby: ObserveNearbyNetworks,
    private val refreshNearby: RefreshNearbyNetworks,
    private val assessSecurity: AssessNetworkSecurity,
    private val saveNearbyNetwork: SaveNearbyNetwork,
    private val recordNearbySightings: RecordNearbySightings,
    private val connectionProvider: CurrentWifiConnectionProvider,
    private val eligibilityChecker: PasswordAuditEligibilityChecker,
    private val connectionMatcher: NetworkConnectionMatcher = DefaultNetworkConnectionMatcher(),
) : ViewModel() {
    private val latestConnection = MutableStateFlow<CurrentWifiConnection?>(null)

    val state: StateFlow<NearbyUiState> =
        observeNearby()
            .onEach { snapshot ->
                recordNearbySightings(snapshot.networks)
                latestConnection.value = runCatching { connectionProvider.currentConnection() }.getOrNull()
            }
            .map { snapshot ->
                val connection = latestConnection.value
                NearbyUiState(
                    scanState = snapshot.scanState,
                    items =
                        snapshot.networks.map { network ->
                            val match =
                                connectionMatcher.match(
                                    observationSsid = network.observation.ssid,
                                    observationBssid = network.observation.bssid,
                                    connection = connection,
                                )
                            network.toItem(match)
                        },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUiState())

    private val _detail = MutableStateFlow<NearbyDetailState?>(null)
    val detail: StateFlow<NearbyDetailState?> = _detail.asStateFlow()

    fun refresh() {
        viewModelScope.launch { refreshNearby() }
    }

    fun select(item: NearbyItem) {
        _detail.value = NearbyDetailState(item)
        viewModelScope.launch {
            val assessment = assessSecurity(item.observation)
            val eligibility = eligibilityChecker.check(item.observation)
            _detail.value =
                _detail.value?.takeIf { it.item == item }?.copy(
                    assessment = assessment,
                    auditEligibility = eligibility,
                )
        }
    }

    fun dismissDetail() {
        _detail.value = null
    }

    fun saveSelectedToVault(alias: String) {
        val current = _detail.value ?: return
        viewModelScope.launch {
            saveNearbyNetwork(
                observation = current.item.observation,
                alias = alias,
                existingId = current.item.savedNetworkId,
            )
            _detail.value = _detail.value?.takeIf { it.item == current.item }?.copy(saved = true)
        }
    }

    /** Exposed for callers that want to react to a manual refresh result. */
    suspend fun requestRefresh(): WifiScanRequestResult = refreshNearby()
}
