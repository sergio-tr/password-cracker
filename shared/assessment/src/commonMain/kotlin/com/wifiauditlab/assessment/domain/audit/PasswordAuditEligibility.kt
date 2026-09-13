package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.connection.DefaultNetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatch
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider

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
        val reason: String,
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
        -> true
        else -> false
    }

fun SecurityFamily.unsupportedAuditReason(): String =
    when (this) {
        SecurityFamily.OPEN ->
            "Esta red está abierta: no hay una contraseña Wi-Fi compartida que auditar."
        SecurityFamily.OWE ->
            "OWE (Enhanced Open) no usa una contraseña Wi-Fi compartida del mismo modo."
        SecurityFamily.WPA2_ENTERPRISE,
        SecurityFamily.WPA3_ENTERPRISE,
        ->
            "La autenticación enterprise (802.1X) no se modela como una contraseña Wi-Fi compartida."
        SecurityFamily.PASSPOINT ->
            "Passpoint no utiliza una contraseña Wi-Fi compartida del mismo modo."
        SecurityFamily.DPP ->
            "DPP (Easy Connect) no se representa como una contraseña compartida."
        SecurityFamily.WEP ->
            "WEP está obsoleto; esta auditoría se centra en contraseñas personales modernas (WPA/WPA2/WPA3)."
        SecurityFamily.UNKNOWN ->
            "No se puede determinar si esta red usa una contraseña Wi-Fi compartida."
        else ->
            "Este mecanismo de autenticación no admite una auditoría de contraseña compartida."
    }
