package com.wifiauditlab.android.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.wifiauditlab.assessment.domain.audit.AndroidNetworkValidationCapability
import com.wifiauditlab.assessment.domain.audit.AndroidNetworkValidationUnavailableReason
import com.wifiauditlab.assessment.domain.audit.LabValidationStrength
import com.wifiauditlab.assessment.domain.audit.supportsLabNetworkValidation
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily

/**
 * Evaluates whether this device can demonstrate a **single** authorized local-only
 * Wi‑Fi validation with observable authentication failure reasons (F2 product policy).
 *
 * Decision (documented):
 * - Available only on **API 34+** with STA concurrency for local-only and required permissions.
 * - API 29–33 can issue WifiNetworkSpecifier requests but lack
 *   [WifiManager.LocalOnlyConnectionFailureListener] → product treats as Unavailable(RequiresApi34)
 *   rather than claiming AuthenticationResultCapable.
 * - Without STA concurrency, a local-only request may displace the primary association and
 *   violate F1 NETWORK_CHANGED → STOP; therefore Unavailable(RequiresStaConcurrencyForLocalOnly).
 */
class AndroidNetworkValidationCapabilityEvaluator(
    context: Context,
    private val permissions: AndroidWifiPermissionManager,
) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun evaluate(securityFamily: SecurityFamily? = null): AndroidNetworkValidationCapability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return AndroidNetworkValidationCapability.Unavailable(
                AndroidNetworkValidationUnavailableReason.RequiresApi34,
            )
        }
        if (wifiManager == null) {
            return AndroidNetworkValidationCapability.Unavailable(
                AndroidNetworkValidationUnavailableReason.DeviceUnsupported,
            )
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_WIFI_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return AndroidNetworkValidationCapability.Unavailable(
                AndroidNetworkValidationUnavailableReason.MissingWifiStatePermission,
            )
        }
        if (!permissions.hasScanPermission()) {
            return AndroidNetworkValidationCapability.Unavailable(
                AndroidNetworkValidationUnavailableReason.MissingNearbyOrLocationPermission,
            )
        }
        @Suppress("DEPRECATION")
        if (!wifiManager.isWifiEnabled) {
            return AndroidNetworkValidationCapability.Unavailable(
                AndroidNetworkValidationUnavailableReason.WifiDisabled,
            )
        }
        val concurrency =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported
            } else {
                false
            }
        if (!concurrency) {
            return AndroidNetworkValidationCapability.Unavailable(
                AndroidNetworkValidationUnavailableReason.RequiresStaConcurrencyForLocalOnly,
            )
        }
        if (securityFamily != null && !securityFamily.supportsLabNetworkValidation()) {
            return AndroidNetworkValidationCapability.Unavailable(
                AndroidNetworkValidationUnavailableReason.UnsupportedSecurityFamily,
            )
        }
        return AndroidNetworkValidationCapability.Available(
            validationStrength = LabValidationStrength.AuthenticationResultCapable,
            apiLevel = Build.VERSION.SDK_INT,
            staConcurrencyForLocalOnly = true,
            failureReasonsObservable = true,
        )
    }
}
