package com.wifiauditlab.assessment.domain.real

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.port.CurrentWifiConnectionProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealNetworkPasswordExecutionTest {
    private val ssid = Ssid("Home")
    private val bssid = Bssid.of("AA:BB:CC:DD:EE:01")
    private val family = SecurityFamily.WPA2_PERSONAL

    @Test
    fun sensitiveCredential_toString_isRedacted() {
        val credential = SensitiveCredential.of("super-secret")
        assertFalse(credential.toString().contains("super-secret"))
        assertTrue(credential.toString().contains("SensitiveCredential"))
    }

    @Test
    fun request_toString_doesNotLeakPassword() {
        val request =
            RealNetworkPasswordExecutionRequest(
                network =
                    RealNetworkReference(
                        ssid = ssid,
                        bssid = bssid,
                        securityFamily = family,
                        displayName = "Home",
                    ),
                credential = SensitiveCredential.of("leaked-password"),
                consent = RealNetworkConsent(true),
            )
        assertFalse(request.toString().contains("leaked-password"))
    }

    @Test
    fun sameSsid_bssidEqual_isExecutable_confirmed() =
        runTest {
            val executable =
                assertIs<RealNetworkPasswordEligibility.Executable>(
                    checker(connection(ssid, bssid)).check(ssid, bssid, family),
                )
            assertTrue(executable.bssidConfirmed)
        }

    @Test
    fun sameSsid_bssidUnavailable_isExecutable_unconfirmed() =
        runTest {
            val executable =
                assertIs<RealNetworkPasswordEligibility.Executable>(
                    checker(connection(ssid, bssid)).check(ssid, bssid = null, family),
                )
            assertFalse(executable.bssidConfirmed)
        }

    @Test
    fun sameSsid_differentBssid_isExecutable_unconfirmed() =
        runTest {
            val executable =
                assertIs<RealNetworkPasswordEligibility.Executable>(
                    checker(connection(ssid, Bssid.of("11:22:33:44:55:66")))
                        .check(ssid, bssid, family),
                )
            assertFalse(executable.bssidConfirmed)
        }

    @Test
    fun differentSsid_isBlocked() =
        runTest {
            assertIs<RealNetworkPasswordEligibility.NotCurrentlyConnected>(
                checker(connection(Ssid("Other"), Bssid.of("FF:EE:DD:CC:BB:AA")))
                    .check(ssid, bssid, family),
            )
        }

    @Test
    fun notConnected_isBlocked() =
        runTest {
            assertIs<RealNetworkPasswordEligibility.NotCurrentlyConnected>(
                checker(null).check(ssid, bssid, family),
            )
        }

    @Test
    fun missingPermissions_isBlocked() =
        runTest {
            assertIs<RealNetworkPasswordEligibility.MissingPermissions>(
                DefaultRealNetworkPasswordEligibilityChecker(
                    connectionProvider = FixedConnection(connection(ssid, bssid)),
                    permissionGate = { false },
                ).check(ssid, bssid, family),
            )
        }

    private fun checker(connection: CurrentWifiConnection?) =
        DefaultRealNetworkPasswordEligibilityChecker(FixedConnection(connection))

    private fun connection(
        ssid: Ssid,
        bssid: Bssid?,
    ) = CurrentWifiConnection(
        ssid = ssid,
        bssid = bssid,
        securityFamily = family,
        rssi = -40,
    )

    private class FixedConnection(
        private val connection: CurrentWifiConnection?,
    ) : CurrentWifiConnectionProvider {
        override suspend fun currentConnection(): CurrentWifiConnection? = connection
    }
}
