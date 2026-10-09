package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.connection.DefaultNetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatch
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatcher
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid

/** Platform ability to validate a passphrase against an AP (not the search engine). */
sealed interface ApAuthCapability {
    data object Available : ApAuthCapability

    data class Unavailable(
        val reason: ApAuthUnavailableReason,
    ) : ApAuthCapability
}

enum class ApAuthUnavailableReason {
    /** Stock app cannot perform silent / batch AP auth probes. */
    PlatformApiLimitation,

    /** Device lacks concurrent local-only Wi‑Fi request support. */
    DeviceUnsupported,

    /** Privileged / system build required for this probe path. */
    RequiresPrivilegedBuild,

    /** Capability not wired yet (foundation / F0). */
    NotImplemented,

    /** Definitive auth failure reasons require API 34+ (F2 product policy). */
    RequiresApi34,

    /** Local-only probe would displace the primary STA — conflicts with F1 stop-on-change. */
    RequiresStaConcurrency,
}

fun interface PlatformApAuthCapabilityProvider {
    suspend fun capability(): ApAuthCapability
}

/**
 * Inputs for the fail-closed gate. [requestedSsid]/[requestedBssid] must describe
 * the **current** connection target — never an arbitrary scanned network chosen
 * for attack.
 *
 * Product policy (F0):
 * `labModeEnabled AND currentNetwork AND registeredLabNetwork AND consent AND capability`.
 */
data class AuthorizedApTestGateRequest(
    val mode: VerificationMode,
    val labModeEnabled: Boolean,
    val requestedSsid: Ssid,
    val requestedBssid: Bssid,
    val securityFamily: SecurityFamily,
    val connection: CurrentWifiConnection?,
    val labAuthorized: Boolean,
    val userConsentGranted: Boolean,
    val capability: ApAuthCapability,
)

sealed interface AuthorizedApTestGateResult {
    data object Admitted : AuthorizedApTestGateResult

    data class Denied(
        val reasons: List<AuthorizedApTestDenial>,
    ) : AuthorizedApTestGateResult {
        init {
            require(reasons.isNotEmpty())
        }
    }
}

enum class AuthorizedApTestDenial {
    ModeNotLabValidation,
    LabModeDisabled,
    NotCurrentlyConnected,
    TargetMismatch,
    InsufficientConnectionInfo,
    NotLabAuthorized,
    ConsentRequired,
    UnsupportedFamily,
    PlatformCapabilityUnavailable,
}

/**
 * Central fail-closed policy for [VerificationMode.LAB_NETWORK_VALIDATION].
 * [VerificationMode.LOCAL_AUDIT] is out of scope (always use local path).
 *
 * Plan name: LabValidationPolicy. ADR-004 name: AuthorizedApTestGate.
 */
class AuthorizedApTestGate(
    private val connectionMatcher: NetworkConnectionMatcher = DefaultNetworkConnectionMatcher(),
) {
    fun evaluate(request: AuthorizedApTestGateRequest): AuthorizedApTestGateResult {
        if (request.mode != VerificationMode.LAB_NETWORK_VALIDATION) {
            return AuthorizedApTestGateResult.Denied(listOf(AuthorizedApTestDenial.ModeNotLabValidation))
        }

        val denials = mutableListOf<AuthorizedApTestDenial>()

        if (!request.labModeEnabled) {
            denials += AuthorizedApTestDenial.LabModeDisabled
        }
        if (!request.securityFamily.supportsLabNetworkValidation()) {
            denials += AuthorizedApTestDenial.UnsupportedFamily
        }
        if (!request.labAuthorized) {
            denials += AuthorizedApTestDenial.NotLabAuthorized
        }
        if (!request.userConsentGranted) {
            denials += AuthorizedApTestDenial.ConsentRequired
        }
        when (request.capability) {
            ApAuthCapability.Available -> Unit
            is ApAuthCapability.Unavailable ->
                denials += AuthorizedApTestDenial.PlatformCapabilityUnavailable
        }

        val connection = request.connection
        if (connection == null) {
            denials += AuthorizedApTestDenial.NotCurrentlyConnected
        } else {
            when (
                val match =
                    connectionMatcher.match(
                        observationSsid = request.requestedSsid,
                        observationBssid = request.requestedBssid,
                        connection = connection,
                    )
            ) {
                NetworkConnectionMatch.Exact,
                NetworkConnectionMatch.Probable,
                -> Unit
                NetworkConnectionMatch.NotConnected ->
                    denials += AuthorizedApTestDenial.NotCurrentlyConnected
                NetworkConnectionMatch.DifferentNetwork ->
                    denials += AuthorizedApTestDenial.TargetMismatch
                NetworkConnectionMatch.InsufficientInformation ->
                    denials += AuthorizedApTestDenial.InsufficientConnectionInfo
            }
        }

        return if (denials.isEmpty()) {
            AuthorizedApTestGateResult.Admitted
        } else {
            AuthorizedApTestGateResult.Denied(denials.distinct())
        }
    }
}

/** Plan name retained as alias of [AuthorizedApTestGate]. */
typealias LabValidationPolicy = AuthorizedApTestGate
