package com.wifiauditlab.assessment.domain.real

import com.wifiauditlab.assessment.domain.audit.supportsSharedPasswordAudit
import com.wifiauditlab.assessment.domain.audit.unsupportedAuditReason
import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.connection.DefaultNetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatch
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.core.audit.PasswordAuditInapplicableReason

/**
 * Whether the product may offer / run a **real** credential probe for a scanned
 * or saved network. Distinct from Lab Search Engine eligibility.
 *
 * Product policy: selected SSID must match the current association SSID
 * (security family + permissions also required). BSSID match is optional
 * evidence ([Executable.bssidConfirmed]); it does **not** gate execution.
 *
 * [NetworkConnectionMatch] remains unchanged for other consumers; this checker
 * maps Exact **and** Probable to [Executable].
 */
sealed interface RealNetworkPasswordEligibility {
    /**
     * Selected network matches the current Wi‑Fi association by SSID.
     * [bssidConfirmed] is true when observation and connection BSSID also match.
     */
    data class Executable(
        val connection: CurrentWifiConnection,
        val bssidConfirmed: Boolean = false,
    ) : RealNetworkPasswordEligibility

    /**
     * Retained for UI compatibility. The checker no longer returns this for
     * same-SSID associations (those are [Executable]).
     */
    data class ProbableMatchOnly(
        val connection: CurrentWifiConnection,
    ) : RealNetworkPasswordEligibility

    data object NotCurrentlyConnected : RealNetworkPasswordEligibility

    data class UnsupportedAuthenticationModel(
        val family: SecurityFamily,
        val reason: PasswordAuditInapplicableReason,
    ) : RealNetworkPasswordEligibility

    data object MissingPermissions : RealNetworkPasswordEligibility

    data object InsufficientInformation : RealNetworkPasswordEligibility
}

interface RealNetworkPasswordEligibilityChecker {
    suspend fun check(
        ssid: Ssid,
        bssid: Bssid?,
        securityFamily: SecurityFamily,
    ): RealNetworkPasswordEligibility

    suspend fun check(observation: WifiObservation): RealNetworkPasswordEligibility =
        check(
            ssid = observation.ssid,
            bssid = observation.bssid,
            securityFamily = observation.securityProfile.family,
        )

    suspend fun checkSaved(
        ssid: Ssid,
        knownBssids: Set<Bssid>,
        securityFamily: SecurityFamily,
    ): RealNetworkPasswordEligibility =
        check(ssid, knownBssids.firstOrNull(), securityFamily)
}

class DefaultRealNetworkPasswordEligibilityChecker(
    private val connectionProvider: CurrentWifiConnectionProvider,
    private val connectionMatcher: NetworkConnectionMatcher = DefaultNetworkConnectionMatcher(),
    private val permissionGate: () -> Boolean = { true },
) : RealNetworkPasswordEligibilityChecker {
    override suspend fun check(
        ssid: Ssid,
        bssid: Bssid?,
        securityFamily: SecurityFamily,
    ): RealNetworkPasswordEligibility {
        if (!permissionGate()) {
            return RealNetworkPasswordEligibility.MissingPermissions
        }
        if (!securityFamily.supportsSharedPasswordAudit()) {
            return RealNetworkPasswordEligibility.UnsupportedAuthenticationModel(
                family = securityFamily,
                reason = securityFamily.unsupportedAuditReason(),
            )
        }
        val connection = connectionProvider.currentConnection()
        if (connection == null) {
            return RealNetworkPasswordEligibility.NotCurrentlyConnected
        }
        val connectionSsid = connection.ssid?.normalized?.takeIf { it.isNotEmpty() }
        if (connectionSsid == null) {
            return RealNetworkPasswordEligibility.InsufficientInformation
        }

        // SSID-only path when observation BSSID is unavailable.
        if (bssid == null) {
            return if (connectionSsid == ssid.normalized) {
                RealNetworkPasswordEligibility.Executable(
                    connection = connection,
                    bssidConfirmed = false,
                )
            } else {
                RealNetworkPasswordEligibility.NotCurrentlyConnected
            }
        }

        return when (
            connectionMatcher.match(
                observationSsid = ssid,
                observationBssid = bssid,
                connection = connection,
            )
        ) {
            NetworkConnectionMatch.Exact ->
                RealNetworkPasswordEligibility.Executable(
                    connection = connection,
                    bssidConfirmed = true,
                )
            // Same ESS / same SSID, different or missing BSSID — still executable.
            NetworkConnectionMatch.Probable ->
                RealNetworkPasswordEligibility.Executable(
                    connection = connection,
                    bssidConfirmed = false,
                )
            NetworkConnectionMatch.NotConnected,
            NetworkConnectionMatch.DifferentNetwork,
            -> RealNetworkPasswordEligibility.NotCurrentlyConnected
            NetworkConnectionMatch.InsufficientInformation ->
                RealNetworkPasswordEligibility.InsufficientInformation
        }
    }

    override suspend fun checkSaved(
        ssid: Ssid,
        knownBssids: Set<Bssid>,
        securityFamily: SecurityFamily,
    ): RealNetworkPasswordEligibility {
        for (candidate in knownBssids) {
            val result = check(ssid, candidate, securityFamily)
            if (result is RealNetworkPasswordEligibility.Executable && result.bssidConfirmed) {
                return result
            }
        }
        val any = check(ssid, knownBssids.firstOrNull(), securityFamily)
        if (any is RealNetworkPasswordEligibility.Executable) return any
        return any
    }
}
