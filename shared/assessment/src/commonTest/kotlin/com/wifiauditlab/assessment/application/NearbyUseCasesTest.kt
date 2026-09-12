package com.wifiauditlab.assessment.application

import com.wifiauditlab.assessment.domain.match.DefaultKnownNetworkMatcher
import com.wifiauditlab.assessment.domain.match.NetworkMatchResult
import com.wifiauditlab.assessment.domain.vault.NewSavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiChannel
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiSignal
import com.wifiauditlab.assessment.domain.wifi.WifiStandard
import com.wifiauditlab.assessment.port.WifiScanRequestResult
import com.wifiauditlab.assessment.port.WifiScanState
import com.wifiauditlab.assessment.testing.FakeWifiScanner
import com.wifiauditlab.assessment.testing.InMemorySavedNetworkRepository
import com.wifiauditlab.assessment.testing.InMemorySecretVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NearbyUseCasesTest {
    private fun observation(
        ssid: String,
        bssid: String,
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
    ) = WifiObservation(
        ssid = Ssid(ssid),
        bssid = Bssid.of(bssid),
        signal = WifiSignal(-50),
        channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
        standard = WifiStandard.WIFI_5,
        securityProfile =
            WifiSecurityProfile(
                family = family,
                keyManagements = emptySet(),
                managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                isTransitionMode = false,
                rawCapabilities = null,
            ),
        observedAtEpochMillis = 0L,
    )

    @Test
    fun observe_classifies_results_against_empty_vault_as_unknown() =
        runTest {
            val scanner = FakeWifiScanner(WifiScanState.Results(listOf(observation("Home", "AA:BB:CC:DD:EE:01"))))
            val useCase = ObserveNearbyNetworks(scanner, InMemorySavedNetworkRepository(), DefaultKnownNetworkMatcher())

            val snapshot = useCase().first()

            assertEquals(1, snapshot.networks.size)
            val nearby = snapshot.networks.single()
            assertIs<NetworkMatchResult.Unknown>(nearby.match)
            assertFalse(nearby.isKnown)
            assertEquals(null, nearby.knownAlias)
        }

    @Test
    fun observe_marks_saved_network_as_known_exact() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            repo.create(
                NewSavedWifiNetwork(
                    alias = "Casa",
                    ssid = "Home",
                    securityFamily = SecurityFamily.WPA2_PERSONAL,
                    knownBssids = setOf(Bssid.of("AA:BB:CC:DD:EE:01")),
                ),
            )
            val scanner = FakeWifiScanner(WifiScanState.Results(listOf(observation("Home", "AA:BB:CC:DD:EE:01"))))
            val useCase = ObserveNearbyNetworks(scanner, repo, DefaultKnownNetworkMatcher())

            val nearby = useCase().first().networks.single()

            assertIs<NetworkMatchResult.Exact>(nearby.match)
            assertTrue(nearby.isKnown)
            assertEquals("Casa", nearby.knownAlias)
        }

    @Test
    fun observe_classifies_throttled_last_observations() =
        runTest {
            val scanner = FakeWifiScanner(WifiScanState.Throttled(listOf(observation("Cafe", "AA:BB:CC:DD:EE:02"))))
            val useCase = ObserveNearbyNetworks(scanner, InMemorySavedNetworkRepository(), DefaultKnownNetworkMatcher())

            val snapshot = useCase().first()

            assertEquals(1, snapshot.networks.size)
            assertIs<WifiScanState.Throttled>(snapshot.scanState)
        }

    @Test
    fun observe_yields_no_networks_for_non_result_states() =
        runTest {
            val scanner = FakeWifiScanner(WifiScanState.PermissionRequired)
            val useCase = ObserveNearbyNetworks(scanner, InMemorySavedNetworkRepository(), DefaultKnownNetworkMatcher())

            val snapshot = useCase().first()

            assertTrue(snapshot.networks.isEmpty())
            assertEquals(WifiScanState.PermissionRequired, snapshot.scanState)
        }

    @Test
    fun refresh_delegates_to_scanner_and_returns_result() =
        runTest {
            val scanner = FakeWifiScanner()
            scanner.nextRefreshResult = WifiScanRequestResult.THROTTLED
            val useCase = RefreshNearbyNetworks(scanner)

            val result = useCase()

            assertEquals(WifiScanRequestResult.THROTTLED, result)
            assertEquals(1, scanner.refreshCount)
        }

    @Test
    fun save_nearby_creates_unknown_and_updates_known() =
        runTest {
            val repo = InMemorySavedNetworkRepository()
            val vault = InMemorySecretVault()
            val save =
                SaveNearbyNetwork(
                    CreateSavedNetwork(repo, vault),
                    UpdateSavedNetworkAlias(repo),
                    RecordSavedNetworkSighting(repo),
                )
            val created = save(observation("MOVISTAR_1234", "AA:BB:CC:DD:EE:01"), "Casa")
            assertEquals("Casa", created.alias)
            assertEquals(1, created.knownBssids.size)

            val updated =
                save(
                    observation("MOVISTAR_1234", "AA:BB:CC:DD:EE:02"),
                    "Hogar",
                    existingId = created.id,
                )
            assertEquals("Hogar", updated.alias)
            assertEquals(2, updated.knownBssids.size)
            assertEquals(1, ObserveSavedNetworks(repo)().first().size)
        }
}
