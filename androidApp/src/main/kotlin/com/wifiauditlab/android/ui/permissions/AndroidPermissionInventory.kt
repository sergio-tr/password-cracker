package com.wifiauditlab.android.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
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
                name = "Wi‑Fi discovery",
                kind = PermissionKind.RequiredPermission,
                status = scanStatus,
                rationale =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "Necesario para descubrir redes cercanas sin usar la ubicación."
                    } else {
                        "En esta versión de Android el descubrimiento Wi‑Fi requiere ubicación."
                    },
                action = scanAction,
            ),
            PermissionItem(
                id = "location_services",
                name = "Location services",
                kind = PermissionKind.SystemService,
                status = if (locationEnabled) PermissionStatus.Enabled else PermissionStatus.Disabled,
                rationale =
                    "Algunos dispositivos exigen los servicios de ubicación activos para " +
                        "devolver resultados de escaneo Wi‑Fi.",
                action =
                    if (locationEnabled) {
                        PermissionAction.None
                    } else {
                        PermissionAction.OpenLocationSettings
                    },
            ),
            PermissionItem(
                id = "secure_vault",
                name = "Secure secret storage",
                kind = PermissionKind.OptionalCapability,
                status =
                    if (capabilities.secureSecretStorage) {
                        PermissionStatus.Available
                    } else {
                        PermissionStatus.Unavailable
                    },
                rationale = "El Vault cifra credenciales con el almacén seguro de la plataforma.",
                action = PermissionAction.None,
            ),
            PermissionItem(
                id = "geolocation",
                name = "Geolocation (optional)",
                kind = PermissionKind.OptionalCapability,
                status =
                    if (capabilities.geolocation) {
                        PermissionStatus.Available
                    } else {
                        PermissionStatus.Unavailable
                    },
                rationale = "Solo se usa si pides guardar coordenadas reales; no es obligatorio.",
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
