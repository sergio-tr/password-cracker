package com.wifiauditlab.assessment.application

import com.wifiauditlab.assessment.domain.match.KnownNetworkMatcher
import com.wifiauditlab.assessment.domain.match.NetworkMatchResult
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.WifiScanRequestResult
import com.wifiauditlab.assessment.port.WifiScanState
import com.wifiauditlab.assessment.port.WifiScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * An observed network already classified against the Vault. The presentation
 * layer maps this straight to UI without running any matching logic itself.
 */
data class NearbyNetwork(
    val observation: WifiObservation,
    val match: NetworkMatchResult,
) {
    val isKnown: Boolean
        get() =
            match is NetworkMatchResult.Exact ||
                match is NetworkMatchResult.Probable ||
                match is NetworkMatchResult.Ambiguous

    val isAmbiguous: Boolean get() = match is NetworkMatchResult.Ambiguous

    /** Alias of the single matched network, or null when unknown/ambiguous. */
    val knownAlias: String?
        get() =
            when (val m = match) {
                is NetworkMatchResult.Exact -> m.network.alias
                is NetworkMatchResult.Probable -> m.network.alias
                else -> null
            }
}

/** Scan state plus the classified networks derived from it. */
data class NearbyNetworksSnapshot(
    val scanState: WifiScanState,
    val networks: List<NearbyNetwork>,
)

/**
 * Streams nearby networks, matching each observation against the current Vault
 * snapshot. Keeps the matching (business) logic out of the ViewModel.
 */
class ObserveNearbyNetworks(
    private val scanner: WifiScanner,
    private val repository: SavedNetworkRepository,
    private val matcher: KnownNetworkMatcher,
) {
    operator fun invoke(): Flow<NearbyNetworksSnapshot> =
        combine(scanner.observeState(), repository.observeAll()) { scan, saved ->
            val observations =
                when (scan) {
                    is WifiScanState.Results -> scan.observations
                    is WifiScanState.Throttled -> scan.lastObservations
                    else -> emptyList()
                }
            NearbyNetworksSnapshot(
                scanState = scan,
                networks = observations.map { NearbyNetwork(it, matcher.match(it, saved)) },
            )
        }
}

/** Requests a scan refresh. Does not guarantee a fresh physical scan. */
class RefreshNearbyNetworks(private val scanner: WifiScanner) {
    suspend operator fun invoke(): WifiScanRequestResult = scanner.refresh()
}
