package com.wifiauditlab.assessment.domain.audit

/**
 * Maps [WifiManager] local-only failure reason codes (API 34+) to domain results.
 * Numeric values match the platform constants; kept here for JVM unit tests without Android.
 *
 * @see android.net.wifi.WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_AUTHENTICATION
 */
object LocalOnlyFailureReasonMapper {
    const val UNKNOWN: Int = 0
    const val ASSOCIATION: Int = 1
    const val AUTHENTICATION: Int = 2
    const val IP_PROVISIONING: Int = 3
    const val NOT_FOUND: Int = 4
    const val NO_RESPONSE: Int = 5
    const val USER_REJECT: Int = 6

    fun map(reason: Int): NetworkValidationResult =
        when (reason) {
            AUTHENTICATION -> NetworkValidationResult.AuthenticationRejected
            ASSOCIATION -> NetworkValidationResult.AssociationFailed
            IP_PROVISIONING -> NetworkValidationResult.IpProvisioningFailed
            NOT_FOUND -> NetworkValidationResult.NetworkNotFound
            NO_RESPONSE -> NetworkValidationResult.NoResponse
            USER_REJECT -> NetworkValidationResult.UserRejected
            UNKNOWN -> NetworkValidationResult.Inconclusive("UNKNOWN")
            else -> NetworkValidationResult.Inconclusive("reason=$reason")
        }
}
