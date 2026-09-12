package com.wifiauditlab.android.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifiauditlab.assessment.application.ObserveSavedNetworks
import com.wifiauditlab.assessment.domain.match.KnownNetworkMatcher
import com.wifiauditlab.assessment.domain.match.NetworkMatchResult
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.port.WifiScanRequestResult
import com.wifiauditlab.assessment.port.WifiScanState
import com.wifiauditlab.assessment.port.WifiScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NearbyItem(
    val observation: WifiObservation,
    val alias: String?,
    val isKnown: Boolean,
    val ambiguous: Boolean,
)

data class NearbyUiState(
    val scanState: WifiScanState = WifiScanState.Idle,
    val items: List<NearbyItem> = emptyList(),
    val lastRequest: WifiScanRequestResult? = null,
)

class NearbyViewModel(
    private val scanner: WifiScanner,
    private val matcher: KnownNetworkMatcher,
    observeSaved: ObserveSavedNetworks,
) : ViewModel() {
    val state: StateFlow<NearbyUiState> =
        combine(scanner.observeState(), observeSaved()) { scan, saved ->
            val items =
                (scan as? WifiScanState.Results)?.observations?.map { observation ->
                    when (val match = matcher.match(observation, saved)) {
                        is NetworkMatchResult.Exact ->
                            NearbyItem(observation, match.network.alias, isKnown = true, ambiguous = false)
                        is NetworkMatchResult.Probable ->
                            NearbyItem(observation, match.network.alias, isKnown = true, ambiguous = false)
                        is NetworkMatchResult.Ambiguous ->
                            NearbyItem(observation, null, isKnown = true, ambiguous = true)
                        NetworkMatchResult.Unknown ->
                            NearbyItem(observation, null, isKnown = false, ambiguous = false)
                    }
                }.orEmpty()
            NearbyUiState(scanState = scan, items = items)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUiState())

    fun refresh() {
        viewModelScope.launch { scanner.refresh() }
    }
}
