package com.wifiauditlab.android.ui.realtest

import com.wifiauditlab.android.ui.nearby.NearbyItem
import com.wifiauditlab.assessment.domain.real.RealNetworkPasswordExecutionResult
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Ssid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Navigation payload — no password material. */
data class RealNetworkPasswordTestRequest(
    val network: RealNetworkReference,
    val savedNetworkId: SavedNetworkId? = null,
    /** When true, UI may offer revealing a Vault secret after explicit authorization. */
    val mayUseVaultSecret: Boolean = false,
)

enum class RealNetworkPasswordSecretSource {
    Manual,
    Vault,
}

sealed interface RealNetworkPasswordTestPhase {
    data object Ready : RealNetworkPasswordTestPhase

    data object Running : RealNetworkPasswordTestPhase

    data object Stopping : RealNetworkPasswordTestPhase

    data class Completed(
        val result: RealNetworkPasswordExecutionResult,
    ) : RealNetworkPasswordTestPhase

    data class Error(
        val messageResHint: String,
    ) : RealNetworkPasswordTestPhase
}

data class RealNetworkPasswordTestUiState(
    val network: RealNetworkReference? = null,
    /** True when eligibility checker returns Executable (SSID match; BSSID optional). */
    val networkEligible: Boolean = false,
    /** True when Executable.bssidConfirmed — informational only. */
    val bssidConfirmed: Boolean = false,
    val passwordInput: String = "",
    val passwordVisible: Boolean = false,
    val consentChecked: Boolean = false,
    val secretSource: RealNetworkPasswordSecretSource = RealNetworkPasswordSecretSource.Manual,
    val vaultSecretAuthorized: Boolean = false,
    val mayUseVaultSecret: Boolean = false,
    val phase: RealNetworkPasswordTestPhase = RealNetworkPasswordTestPhase.Ready,
    val missingTarget: Boolean = false,
) {
    val canStart: Boolean
        get() =
            !missingTarget &&
                networkEligible &&
                passwordInput.isNotEmpty() &&
                consentChecked &&
                phase is RealNetworkPasswordTestPhase.Ready

    val isBusy: Boolean
        get() =
            phase is RealNetworkPasswordTestPhase.Running ||
                phase is RealNetworkPasswordTestPhase.Stopping
}

class RealNetworkPasswordTestTargetStore {
    private val _request = MutableStateFlow<RealNetworkPasswordTestRequest?>(null)
    val request: StateFlow<RealNetworkPasswordTestRequest?> = _request.asStateFlow()

    fun set(request: RealNetworkPasswordTestRequest) {
        _request.value = request
    }

    fun clear() {
        _request.value = null
    }
}

fun realNetworkPasswordTestRequestFromNearby(item: NearbyItem): RealNetworkPasswordTestRequest =
    RealNetworkPasswordTestRequest(
        network =
            RealNetworkReference(
                ssid = item.observation.ssid,
                bssid = item.observation.bssid,
                securityFamily = item.observation.securityProfile.family,
                displayName = item.alias ?: item.observation.ssid.toString(),
            ),
        savedNetworkId = item.savedNetworkId,
        mayUseVaultSecret = false,
    )

fun realNetworkPasswordTestRequestFromVault(network: SavedWifiNetwork): RealNetworkPasswordTestRequest =
    RealNetworkPasswordTestRequest(
        network =
            RealNetworkReference(
                ssid = Ssid(network.ssid),
                bssid = network.knownBssids.firstOrNull(),
                securityFamily = network.securityFamily,
                displayName = network.alias ?: network.ssid,
            ),
        savedNetworkId = network.id,
        mayUseVaultSecret = network.hasSecret,
    )
