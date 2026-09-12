package com.wifiauditlab.android.support

import com.wifiauditlab.android.ui.nearby.NearbyViewModel
import com.wifiauditlab.android.ui.vault.VaultViewModel
import com.wifiauditlab.assessment.application.AssessNetworkSecurity
import com.wifiauditlab.assessment.application.CreateSavedNetwork
import com.wifiauditlab.assessment.application.DeleteSavedNetwork
import com.wifiauditlab.assessment.application.ObserveNearbyNetworks
import com.wifiauditlab.assessment.application.ObserveSavedNetworks
import com.wifiauditlab.assessment.application.QuerySavedNetworks
import com.wifiauditlab.assessment.application.RecordNearbySightings
import com.wifiauditlab.assessment.application.RecordSavedNetworkSighting
import com.wifiauditlab.assessment.application.RefreshNearbyNetworks
import com.wifiauditlab.assessment.application.RemoveSavedNetworkSecret
import com.wifiauditlab.assessment.application.RevealSavedNetworkSecret
import com.wifiauditlab.assessment.application.SaveNearbyNetwork
import com.wifiauditlab.assessment.application.UpdateSavedNetworkAlias
import com.wifiauditlab.assessment.application.UpdateSavedNetworkLocation
import com.wifiauditlab.assessment.application.UpdateSavedNetworkNotes
import com.wifiauditlab.assessment.application.UpdateSavedNetworkSecret
import com.wifiauditlab.assessment.domain.match.DefaultKnownNetworkMatcher
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SavedNetworkId
import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.NetworkIdentity
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import com.wifiauditlab.assessment.port.SecretVault
import com.wifiauditlab.assessment.port.WifiScanRequestResult
import com.wifiauditlab.assessment.port.WifiScanState
import com.wifiauditlab.assessment.port.WifiScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeWifiScanner(initial: WifiScanState = WifiScanState.Idle) : WifiScanner {
    val state = MutableStateFlow(initial)
    var refreshCount = 0
        private set

    override fun observeState(): Flow<WifiScanState> = state

    override suspend fun refresh(): WifiScanRequestResult {
        refreshCount++
        return WifiScanRequestResult.STARTED
    }
}

class FakeSavedNetworkRepository : SavedNetworkRepository {
    val networks = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())
    private var seq = 0

    override fun observeAll(): Flow<List<SavedWifiNetwork>> = networks

    override suspend fun getById(id: SavedNetworkId): SavedWifiNetwork? =
        networks.value.firstOrNull { it.id == id }

    override suspend fun findByIdentity(identity: NetworkIdentity): List<SavedWifiNetwork> =
        networks.value.filter { it.identity == identity }

    override suspend fun create(network: NewSavedWifiNetwork): SavedWifiNetwork {
        val created =
            SavedWifiNetwork(
                id = SavedNetworkId("net-${seq++}"),
                alias = network.alias,
                ssid = network.ssid,
                securityFamily = network.securityFamily,
                knownBssids = network.knownBssids,
                locationLabel = network.locationLabel,
                geoLocation = network.geoLocation,
                secretId = null,
                notes = network.notes,
                createdAtEpochMillis = 0L,
                lastSeenAtEpochMillis = null,
            )
        networks.update { it + created }
        return created
    }

    override suspend fun update(network: SavedWifiNetwork): SavedWifiNetwork {
        networks.update { list -> list.map { if (it.id == network.id) network else it } }
        return network
    }

    override suspend fun delete(id: SavedNetworkId) {
        networks.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeSecretVault : SecretVault {
    private val secrets = mutableMapOf<SecretId, NetworkSecret>()
    private var seq = 0

    override suspend fun create(secret: NetworkSecret): SecretId {
        val id = SecretId("sec-${seq++}")
        secrets[id] = secret
        return id
    }

    override suspend fun read(id: SecretId): NetworkSecret? = secrets[id]

    override suspend fun update(
        id: SecretId,
        secret: NetworkSecret,
    ) {
        secrets[id] = secret
    }

    override suspend fun delete(id: SecretId) {
        secrets.remove(id)
    }
}

fun observation(
    ssid: String = "Home",
    bssid: String = "AA:BB:CC:DD:EE:01",
    family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
): WifiObservation =
    WifiObservation(
        ssid = Ssid(ssid),
        bssid = Bssid.of(bssid),
        signal = WifiSignal(-50),
        channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
        standard = WifiStandard.WIFI_5,
        securityProfile =
            WifiSecurityProfile(
                family = family,
                keyManagements = setOf("PSK"),
                managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                isTransitionMode = false,
                rawCapabilities = "[WPA2-PSK-CCMP][ESS]",
            ),
        observedAtEpochMillis = 0L,
    )

fun nearbyViewModel(
    scanner: FakeWifiScanner,
    repo: FakeSavedNetworkRepository = FakeSavedNetworkRepository(),
): NearbyViewModel =
    NearbyViewModel(
        ObserveNearbyNetworks(scanner, repo, DefaultKnownNetworkMatcher()),
        RefreshNearbyNetworks(scanner),
        AssessNetworkSecurity(SecurityAssessmentRegistry.default()),
        SaveNearbyNetwork(
            CreateSavedNetwork(repo, FakeSecretVault()),
            UpdateSavedNetworkAlias(repo),
            RecordSavedNetworkSighting(repo),
        ),
        RecordNearbySightings(RecordSavedNetworkSighting(repo)),
    )

fun vaultViewModel(
    repo: FakeSavedNetworkRepository = FakeSavedNetworkRepository(),
    vault: FakeSecretVault = FakeSecretVault(),
): VaultViewModel =
    VaultViewModel(
        ObserveSavedNetworks(repo),
        QuerySavedNetworks(),
        CreateSavedNetwork(repo, vault),
        DeleteSavedNetwork(repo, vault),
        UpdateSavedNetworkAlias(repo),
        UpdateSavedNetworkLocation(repo),
        UpdateSavedNetworkNotes(repo),
        UpdateSavedNetworkSecret(repo, vault),
        RemoveSavedNetworkSecret(repo, vault),
        RevealSavedNetworkSecret(repo, vault),
    )
