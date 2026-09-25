package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.connection.DefaultNetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatch
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import com.wifiauditlab.core.audit.PasswordAuditInapplicableReason

/**
 * Whether a known-password audit may start for [observation].
 * Does not inspect or require the password value itself.
 */
sealed interface PasswordAuditEligibility {
    data class EligibleConnectedNetwork(
        val match: NetworkConnectionMatch,
    ) : PasswordAuditEligibility

    data object NotCurrentlyConnected : PasswordAuditEligibility

    data class UnsupportedAuthenticationModel(
        val family: SecurityFamily,
        val reason: PasswordAuditInapplicableReason,
    ) : PasswordAuditEligibility

    data object MissingPermissions : PasswordAuditEligibility

    data object InsufficientInformation : PasswordAuditEligibility
}

fun interface ConnectionInspectionPermissionGate {
    fun hasPermission(): Boolean
}

interface PasswordAuditEligibilityChecker {
    suspend fun check(observation: WifiObservation): PasswordAuditEligibility
}

class DefaultPasswordAuditEligibilityChecker(
    private val connectionProvider: CurrentWifiConnectionProvider,
    private val connectionMatcher: NetworkConnectionMatcher = DefaultNetworkConnectionMatcher(),
    private val permissionGate: ConnectionInspectionPermissionGate = ConnectionInspectionPermissionGate { true },
) : PasswordAuditEligibilityChecker {
    override suspend fun check(observation: WifiObservation): PasswordAuditEligibility {
        if (!permissionGate.hasPermission()) {
            return PasswordAuditEligibility.MissingPermissions
        }

        val family = observation.securityProfile.family
        if (!family.supportsSharedPasswordAudit()) {
            return PasswordAuditEligibility.UnsupportedAuthenticationModel(
                family = family,
                reason = family.unsupportedAuditReason(),
            )
        }

        val connection: CurrentWifiConnection? = connectionProvider.currentConnection()
        return when (
            val match =
                connectionMatcher.match(
                    observationSsid = observation.ssid,
                    observationBssid = observation.bssid,
                    connection = connection,
                )
        ) {
            NetworkConnectionMatch.Exact,
            NetworkConnectionMatch.Probable,
            -> PasswordAuditEligibility.EligibleConnectedNetwork(match)
            NetworkConnectionMatch.NotConnected,
            NetworkConnectionMatch.DifferentNetwork,
            -> PasswordAuditEligibility.NotCurrentlyConnected
            NetworkConnectionMatch.InsufficientInformation ->
                PasswordAuditEligibility.InsufficientInformation
        }
    }
}

fun SecurityFamily.supportsSharedPasswordAudit(): Boolean =
    when (this) {
        SecurityFamily.WPA_PERSONAL,
        SecurityFamily.WPA2_PERSONAL,
        SecurityFamily.WPA3_PERSONAL,
        SecurityFamily.WPA2_WPA3_PERSONAL,
        SecurityFamily.WEP,
        -> true
        else -> false
    }

fun SecurityFamily.unsupportedAuditReason(): PasswordAuditInapplicableReason =
    toPasswordAuditInapplicableReason() ?: PasswordAuditInapplicableReason.UnsupportedAuth
