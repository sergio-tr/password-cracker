package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.connection.CurrentWifiConnection
import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthorizedApTestGateTest {
    private val gate = AuthorizedApTestGate()

    private fun connectedRequest(
        labModeEnabled: Boolean = true,
        labAuthorized: Boolean = true,
        consent: Boolean = true,
        capability: ApAuthCapability = ApAuthCapability.Available,
        family: SecurityFamily = SecurityFamily.WPA2_PERSONAL,
        connectionSsid: String = "LabNet",
        requestSsid: String = "LabNet",
        connectionBssid: String = "AA:BB:CC:DD:EE:01",
        requestBssid: String = "AA:BB:CC:DD:EE:01",
    ) = AuthorizedApTestGateRequest(
        mode = VerificationMode.LAB_NETWORK_VALIDATION,
        labModeEnabled = labModeEnabled,
        requestedSsid = Ssid(requestSsid),
        requestedBssid = Bssid.of(requestBssid),
        securityFamily = family,
        connection =
            CurrentWifiConnection(
                ssid = Ssid(connectionSsid),
                bssid = Bssid.of(connectionBssid),
                securityFamily = family,
                rssi = -40,
            ),
        labAuthorized = labAuthorized,
        userConsentGranted = consent,
        capability = capability,
    )

    @Test
    fun admitsWhenAllConditionsHold() {
        assertIs<AuthorizedApTestGateResult.Admitted>(gate.evaluate(connectedRequest()))
    }

    @Test
    fun deniesLocalMode() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(connectedRequest().copy(mode = VerificationMode.LOCAL_AUDIT)),
            )
        assertEquals(listOf(AuthorizedApTestDenial.ModeNotLabValidation), denied.reasons)
    }

    @Test
    fun deniesWhenLabModeDisabled() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(connectedRequest(labModeEnabled = false)),
            )
        assertTrue(AuthorizedApTestDenial.LabModeDisabled in denied.reasons)
    }

    @Test
    fun deniesWhenNotCurrentlyConnected() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(connectedRequest().copy(connection = null)),
            )
        assertTrue(AuthorizedApTestDenial.NotCurrentlyConnected in denied.reasons)
    }

    @Test
    fun deniesWhenNetworkNotRegistered() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(connectedRequest(labAuthorized = false)),
            )
        assertTrue(AuthorizedApTestDenial.NotLabAuthorized in denied.reasons)
    }

    @Test
    fun deniesWhenConsentMissing() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(connectedRequest(consent = false)),
            )
        assertTrue(AuthorizedApTestDenial.ConsentRequired in denied.reasons)
    }

    @Test
    fun deniesWhenCapabilityUnavailable() {
        val denied =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(
                    connectedRequest(
                        capability =
                            ApAuthCapability.Unavailable(ApAuthUnavailableReason.PlatformApiLimitation),
                    ),
                ),
            )
        assertTrue(AuthorizedApTestDenial.PlatformCapabilityUnavailable in denied.reasons)
    }

    @Test
    fun deniesTargetMismatchAndUnsupportedFamily() {
        val mismatch =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(connectedRequest(requestSsid = "Other", requestBssid = "11:22:33:44:55:66")),
            )
        assertTrue(AuthorizedApTestDenial.TargetMismatch in mismatch.reasons)

        val family =
            assertIs<AuthorizedApTestGateResult.Denied>(
                gate.evaluate(connectedRequest(family = SecurityFamily.OPEN)),
            )
        assertTrue(AuthorizedApTestDenial.UnsupportedFamily in family.reasons)
    }

    @Test
    fun inMemoryRegistry_roundTrip() =
        runTest {
            val store = InMemoryAuthorizedLabNetworkStore()
            val key = AuthorizedLabNetworkKey.of("LabNet", SecurityFamily.WPA2_PERSONAL)
            assertTrue(!store.isAuthorized(key))
            store.setAuthorized(key, true)
            assertTrue(store.isAuthorized(key))
            store.setAuthorized(key, false)
            assertTrue(!store.isAuthorized(key))
        }

    @Test
    fun androidValidationAdapter_failClosed() =
        runTest {
            val adapter = AndroidValidationAdapter()
            assertEquals(
                ApAuthCapability.Unavailable(ApAuthUnavailableReason.PlatformApiLimitation),
                adapter.capability(),
            )
            assertIs<NetworkValidationResult.Unavailable>(
                adapter.validateOnce(
                    AuthorizedValidationContext(
                        sessionId = "test",
                        networkSnapshot =
                            LabNetworkSnapshot(
                                ssid = "LabNet",
                                securityFamily = SecurityFamily.WPA2_PERSONAL,
                            ),
                        budget = LabSessionBudget.standard(),
                    ),
                    ValidationCredential.fromPlaintext("password1"),
                ),
            )
        }

    @Test
    fun probeRequest_toString_redactsPassphrase() {
        val request =
            ApAuthProbeRequest(
                ssid = "LabNet",
                bssidHint = null,
                family = SecurityFamily.WPA2_PERSONAL,
                passphrase = "super-secret",
            )
        assertTrue(!request.toString().contains("super-secret"))
        assertTrue(request.toString().contains("••••••••"))
    }
}
