package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.connection.NetworkConnectionMatch
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
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultPasswordAuditEligibilityCheckerTest {
    private val home =
        observation(ssid = "Casa", bssid = "AA:BB:CC:DD:EE:01", family = SecurityFamily.WPA2_PERSONAL)

    @Test
    fun exactConnectedPersonal_isEligible() =
        runTest {
            val checker =
                checker(
                    connection =
                        CurrentWifiConnection(
                            ssid = Ssid("Casa"),
                            bssid = Bssid.of("AA:BB:CC:DD:EE:01"),
                            securityFamily = SecurityFamily.WPA2_PERSONAL,
                            rssi = -48,
                        ),
                )
            val result = checker.check(home)
            val eligible = assertIs<PasswordAuditEligibility.EligibleConnectedNetwork>(result)
            assertEquals(NetworkConnectionMatch.Exact, eligible.match)
        }

    @Test
    fun probableSameSsid_isEligible() =
        runTest {
            val checker =
                checker(
                    connection =
                        CurrentWifiConnection(
                            ssid = Ssid("Casa"),
                            bssid = Bssid.of("AA:BB:CC:DD:EE:99"),
                            securityFamily = SecurityFamily.WPA2_WPA3_PERSONAL,
                            rssi = -60,
                        ),
                )
            val result = checker.check(home.copy(securityProfile = personal(SecurityFamily.WPA2_WPA3_PERSONAL)))
            val eligible = assertIs<PasswordAuditEligibility.EligibleConnectedNetwork>(result)
            assertEquals(NetworkConnectionMatch.Probable, eligible.match)
        }

    @Test
    fun differentSsid_isNotCurrentlyConnected() =
        runTest {
            val checker =
                checker(
                    connection =
                        CurrentWifiConnection(
                            ssid = Ssid("Otro"),
                            bssid = Bssid.of("11:22:33:44:55:66"),
                            securityFamily = SecurityFamily.WPA2_PERSONAL,
                            rssi = -50,
                        ),
                )
            assertIs<PasswordAuditEligibility.NotCurrentlyConnected>(checker.check(home))
        }

    @Test
    fun notConnected_mapsCorrectly() =
        runTest {
            val checker = checker(connection = null)
            assertIs<PasswordAuditEligibility.NotCurrentlyConnected>(checker.check(home))
        }

    @Test
    fun missingPermissions_shortCircuits() =
        runTest {
            val checker =
                checker(
                    connection =
                        CurrentWifiConnection(
                            ssid = Ssid("Casa"),
                            bssid = Bssid.of("AA:BB:CC:DD:EE:01"),
                            securityFamily = SecurityFamily.WPA2_PERSONAL,
                            rssi = -48,
                        ),
                    hasPermission = false,
                )
            assertIs<PasswordAuditEligibility.MissingPermissions>(checker.check(home))
        }

    @Test
    fun openNetwork_isUnsupported() =
        runTest {
            val checker =
                checker(
                    connection =
                        CurrentWifiConnection(
                            ssid = Ssid("OpenCafe"),
                            bssid = Bssid.of("AA:BB:CC:DD:EE:01"),
                            securityFamily = SecurityFamily.OPEN,
                            rssi = -40,
                        ),
                )
            val open = observation(ssid = "OpenCafe", family = SecurityFamily.OPEN)
            val result = assertIs<PasswordAuditEligibility.UnsupportedAuthenticationModel>(checker.check(open))
            assertEquals(SecurityFamily.OPEN, result.family)
            assertTrue(result.reason.isNotBlank())
        }

    @Test
    fun enterpriseAndOweAndDpp_areUnsupported() =
        runTest {
            val families =
                listOf(
                    SecurityFamily.WPA2_ENTERPRISE,
                    SecurityFamily.OWE,
                    SecurityFamily.DPP,
                    SecurityFamily.PASSPOINT,
                )
            for (family in families) {
                val checker =
                    checker(
                        connection =
                            CurrentWifiConnection(
                                ssid = Ssid("X"),
                                bssid = Bssid.of("AA:BB:CC:DD:EE:01"),
                                securityFamily = family,
                                rssi = -40,
                            ),
                    )
                val result =
                    checker.check(observation(ssid = "X", family = family))
                assertIs<PasswordAuditEligibility.UnsupportedAuthenticationModel>(result)
            }
        }

    @Test
    fun insufficientConnectionInfo_mapsCorrectly() =
        runTest {
            val checker =
                checker(
                    connection =
                        CurrentWifiConnection(
                            ssid = null,
                            bssid = Bssid.of("02:00:00:00:00:00"),
                            securityFamily = null,
                            rssi = -40,
                        ),
                )
            assertIs<PasswordAuditEligibility.InsufficientInformation>(checker.check(home))
        }

    private fun checker(
        connection: CurrentWifiConnection?,
        hasPermission: Boolean = true,
    ): PasswordAuditEligibilityChecker =
        DefaultPasswordAuditEligibilityChecker(
            connectionProvider =
                object : CurrentWifiConnectionProvider {
                    override suspend fun currentConnection(): CurrentWifiConnection? = connection
                },
            permissionGate = ConnectionInspectionPermissionGate { hasPermission },
        )

    private fun observation(
        ssid: String,
        bssid: String = "AA:BB:CC:DD:EE:01",
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
    ): WifiObservation =
        WifiObservation(
            ssid = Ssid(ssid),
            bssid = Bssid.of(bssid),
            signal = WifiSignal(-50),
            channel = WifiChannel(6, WifiBand.GHZ_2_4, 2437),
            standard = WifiStandard.WIFI_5,
            securityProfile = personal(family),
            observedAtEpochMillis = 0L,
        )

    private fun personal(family: SecurityFamily): WifiSecurityProfile =
        WifiSecurityProfile(
            family = family,
            keyManagements = setOf("PSK"),
            managementFrameProtection = ManagementFrameProtection.UNKNOWN,
            isTransitionMode = family == SecurityFamily.WPA2_WPA3_PERSONAL,
            rawCapabilities = null,
        )
}
