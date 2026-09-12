package com.wifiauditlab.assessment.port

import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import kotlinx.coroutines.flow.Flow

/** Snapshot of the Wi-Fi scanning subsystem. Explicit states avoid impossible flags. */
sealed interface WifiScanState {
    data object Idle : WifiScanState
    data object Loading : WifiScanState
    data class Results(val observations: List<WifiObservation>) : WifiScanState

    /** The OS throttled scans; last known results (if any) are still shown. */
    data class Throttled(val lastObservations: List<WifiObservation>) : WifiScanState
    data object PermissionRequired : WifiScanState
    data object LocationServicesDisabled : WifiScanState
    data object Unavailable : WifiScanState
    data class Error(val message: String) : WifiScanState
}

/** Result of asking for a refresh. A refresh does not guarantee a fresh physical scan. */
enum class WifiScanRequestResult {
    STARTED,
    THROTTLED,
    PERMISSION_REQUIRED,
    LOCATION_SERVICES_DISABLED,
    UNAVAILABLE,
}

/**
 * Platform-independent contract over Wi-Fi scanning. Implemented by an Android
 * adapter; never referenced by the synthetic lab.
 */
interface WifiScanner {
    fun observeState(): Flow<WifiScanState>
    suspend fun refresh(): WifiScanRequestResult
}
