package com.wifiauditlab.android.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.wifiauditlab.android.R
import com.wifiauditlab.android.platform.AndroidPlatformCapabilities
import com.wifiauditlab.android.wifi.AndroidWifiPermissionManager

/**
 * Builds the Permission Center rows from live platform state.
 * [permanentlyDenied] is supplied by the UI after a denied request without rationale.
 */
class AndroidPermissionInventory(
    private val context: Context,
    private val permissionManager: AndroidWifiPermissionManager,
    private val capabilities: AndroidPlatformCapabilities = AndroidPlatformCapabilities(),
    private val permanentlyDenied: () -> Set<String> = { emptySet() },
) : PermissionInventory {
    override fun refresh(): List<PermissionItem> {
        val denied = permanentlyDenied()
        val scanPermission = permissionManager.requiredPermissions.first()
        val scanGranted = permissionManager.hasScanPermission()
        val scanStatus =
            when {
                scanGranted -> PermissionStatus.Granted
                scanPermission in denied -> PermissionStatus.PermanentlyDenied
                else -> PermissionStatus.Missing
            }
        val scanAction =
            when (scanStatus) {
                PermissionStatus.Granted -> PermissionAction.None
                PermissionStatus.PermanentlyDenied -> PermissionAction.OpenAppSettings
                else -> PermissionAction.Request
            }

        val locationEnabled = isLocationEnabled()
        return listOf(
            PermissionItem(
                id = "wifi_discovery",
                nameRes = R.string.permissions_wifi_discovery,
                kind = PermissionKind.RequiredPermission,
                status = scanStatus,
                rationaleRes =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        R.string.permissions_wifi_rationale_tiramisu
                    } else {
                        R.string.permissions_wifi_rationale_legacy
                    },
                action = scanAction,
            ),
            PermissionItem(
                id = "location_services",
                nameRes = R.string.permissions_location_services,
                kind = PermissionKind.SystemService,
                status = if (locationEnabled) PermissionStatus.Enabled else PermissionStatus.Disabled,
                rationaleRes = R.string.permissions_location_rationale,
                action =
                    if (locationEnabled) {
                        PermissionAction.None
                    } else {
                        PermissionAction.OpenLocationSettings
                    },
            ),
            PermissionItem(
                id = "secure_vault",
                nameRes = R.string.permissions_secure_vault,
                kind = PermissionKind.OptionalCapability,
                status =
                    if (capabilities.secureSecretStorage) {
                        PermissionStatus.Available
                    } else {
                        PermissionStatus.Unavailable
                    },
                rationaleRes = R.string.permissions_secure_vault_rationale,
                action = PermissionAction.None,
            ),
            PermissionItem(
                id = "geolocation",
                nameRes = R.string.permissions_geolocation,
                kind = PermissionKind.OptionalCapability,
                status =
                    if (capabilities.geolocation) {
                        PermissionStatus.Available
                    } else {
                        PermissionStatus.Unavailable
                    },
                rationaleRes = R.string.permissions_geolocation_rationale,
                action = PermissionAction.None,
            ),
        )
    }

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun requiredScanPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
}
