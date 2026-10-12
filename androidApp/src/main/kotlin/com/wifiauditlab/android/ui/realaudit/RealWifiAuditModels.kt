package com.wifiauditlab.android.ui.realaudit

import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Navigation payload — selected network + security profile; no secrets. */
data class RealWifiAuditTarget(
    val network: RealNetworkReference,
    val securityProfile: WifiSecurityProfile,
)

class RealWifiAuditTargetStore {
    private val _target = MutableStateFlow<RealWifiAuditTarget?>(null)
    val target: StateFlow<RealWifiAuditTarget?> = _target.asStateFlow()

    fun set(target: RealWifiAuditTarget) {
        _target.value = target
    }

    fun clear() {
        _target.value = null
    }
}

fun realWifiAuditTargetFromNearby(item: NearbyItem): RealWifiAuditTarget =
    RealWifiAuditTarget(
        network =
            RealNetworkReference(
                ssid = item.observation.ssid,
                bssid = item.observation.bssid,
                securityFamily = item.observation.securityProfile.family,
                displayName = item.alias ?: item.observation.ssid.toString(),
            ),
        securityProfile = item.observation.securityProfile,
    )

fun realWifiAuditTargetFromVault(
    network: SavedWifiNetwork,
    profile: WifiSecurityProfile,
): RealWifiAuditTarget =
    RealWifiAuditTarget(
        network =
            RealNetworkReference(
                ssid = Ssid(network.ssid),
                bssid = network.knownBssids.firstOrNull(),
                securityFamily = network.securityFamily,
                displayName = network.alias ?: network.ssid,
            ),
        securityProfile = profile,
    )
