package com.wifiauditlab.assessment.port

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection

/**
 * Platform port for reading the active Wi-Fi association.
 * Implemented by Android adapters; never referenced by `:shared:lab`.
 */
interface CurrentWifiConnectionProvider {
    suspend fun currentConnection(): CurrentWifiConnection?
}
