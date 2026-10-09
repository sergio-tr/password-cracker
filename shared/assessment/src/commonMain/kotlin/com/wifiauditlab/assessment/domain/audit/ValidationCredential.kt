package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.wifi.SecurityFamily

/**
 * One-shot passphrase for a single [NetworkValidationAdapter.validateOnce] call.
 * Cleared after [use] / [clear]. Never logged; [toString] is redacted.
 */
class ValidationCredential private constructor(
    private var passphrase: CharArray?,
) {
    fun isPresent(): Boolean = passphrase != null && passphrase!!.isNotEmpty()

    /**
     * Provides the passphrase to [block] then clears the stored value.
     * @return result of [block], or null if credential was already cleared/empty.
     */
    fun <T> use(block: (String) -> T): T? {
        val chars = passphrase ?: return null
        if (chars.isEmpty()) {
            clear()
            return null
        }
        return try {
            block(String(chars))
        } finally {
            clear()
        }
    }

    fun clear() {
        passphrase?.fill('\u0000')
        passphrase = null
    }

    override fun toString(): String = "ValidationCredential(••••••••)"

    companion object {
        fun fromPlaintext(value: String): ValidationCredential =
            ValidationCredential(value.toCharArray())
    }
}

/** Families with a public [WifiNetworkSpecifier] personal-passphrase builder path. */
fun SecurityFamily.supportsLabNetworkValidation(): Boolean =
    when (this) {
        SecurityFamily.WPA2_PERSONAL,
        SecurityFamily.WPA3_PERSONAL,
        SecurityFamily.WPA2_WPA3_PERSONAL,
        -> true
        else -> false
    }

/**
 * Device-level LAB network-validation capability (F2).
 * Distinct from gate admission — describes what the platform can demonstrate.
 */
sealed interface AndroidNetworkValidationCapability {
    data class Available(
        val validationStrength: LabValidationStrength,
        val apiLevel: Int,
        val staConcurrencyForLocalOnly: Boolean,
        val failureReasonsObservable: Boolean,
    ) : AndroidNetworkValidationCapability

    data class Unavailable(
        val reason: AndroidNetworkValidationUnavailableReason,
    ) : AndroidNetworkValidationCapability
}

enum class LabValidationStrength {
    /**
     * API 34+ with [android.net.wifi.WifiManager.LocalOnlyConnectionFailureListener]:
     * authentication / association / etc. reasons are observable.
     */
    AuthenticationResultCapable,

    /**
     * Real local-only request possible but failures may be [NetworkValidationResult.Inconclusive]
     * (API 29–33). Not used as Available for product gate in F2.
     */
    ConnectivityProbeOnly,
}

enum class AndroidNetworkValidationUnavailableReason {
    RequiresApi34,
    RequiresStaConcurrencyForLocalOnly,
    WifiDisabled,
    MissingWifiStatePermission,
    MissingNearbyOrLocationPermission,
    UnsupportedSecurityFamily,
    PlatformApiLimitation,
    DeviceUnsupported,
}

fun AndroidNetworkValidationUnavailableReason.toApAuthReason(): ApAuthUnavailableReason =
    when (this) {
        AndroidNetworkValidationUnavailableReason.RequiresApi34 ->
            ApAuthUnavailableReason.RequiresApi34
        AndroidNetworkValidationUnavailableReason.RequiresStaConcurrencyForLocalOnly ->
            ApAuthUnavailableReason.RequiresStaConcurrency
        AndroidNetworkValidationUnavailableReason.PlatformApiLimitation,
        AndroidNetworkValidationUnavailableReason.MissingWifiStatePermission,
        AndroidNetworkValidationUnavailableReason.MissingNearbyOrLocationPermission,
        -> ApAuthUnavailableReason.PlatformApiLimitation
        AndroidNetworkValidationUnavailableReason.DeviceUnsupported,
        AndroidNetworkValidationUnavailableReason.WifiDisabled,
        -> ApAuthUnavailableReason.DeviceUnsupported
        AndroidNetworkValidationUnavailableReason.UnsupportedSecurityFamily ->
            ApAuthUnavailableReason.NotImplemented
    }
