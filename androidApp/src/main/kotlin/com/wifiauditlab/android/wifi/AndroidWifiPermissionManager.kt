package com.wifiauditlab.android.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Resolves which runtime permissions a scan needs and whether they are granted.
 * On Android 13+ nearby-Wi-Fi discovery uses [Manifest.permission.NEARBY_WIFI_DEVICES];
 * older versions require location access.
 */
class AndroidWifiPermissionManager(private val context: Context) {

    val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasScanPermission(): Boolean = requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
