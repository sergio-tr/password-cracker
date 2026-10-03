package com.wifiauditlab.assessment.port

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import kotlinx.coroutines.flow.Flow

/**
 * Observes changes to the active Wi‑Fi association.
 * Domain sessions use this to fail-closed on NETWORK_CHANGED / NETWORK_LOST.
 */
fun interface WifiConnectionMonitor {
    fun observe(): Flow<CurrentWifiConnection?>
}
