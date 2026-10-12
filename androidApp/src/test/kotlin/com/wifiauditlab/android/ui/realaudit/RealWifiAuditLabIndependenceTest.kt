package com.wifiauditlab.android.ui.realaudit

import com.wifiauditlab.android.lab.LabModeSurfaces
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditPlanner
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditStrategyResolver
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOrchestrator
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPhase
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPlan
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgressSink
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchExecutionResult
import com.wifiauditlab.assessment.domain.realaudit.RealWifiSearchPort
import com.wifiauditlab.assessment.domain.realaudit.UnsupportedExternalCandidateVerificationPort
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.BlackBoxChallengeVerifier
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSecretPolicy
import com.wifiauditlab.lab.domain.LengthPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real audit capability gate is independent of Lab Mode.
 * Lab black-box remains usable regardless of unsupported external verifier.
 */
class RealWifiAuditLabIndependenceTest {
    @Test
    fun labOff_realAudit_backend_unavailable_stable() =
        runTest {
            assertFalse(LabModeSurfaces.showLabTab(false))
            val phase = preparePasswordAudit()
            assertTrue(phase is RealWifiAuditPhase.BackendUnavailable)
        }

    @Test
    fun labOn_realAudit_backend_unavailable_stable() =
        runTest {
            assertTrue(LabModeSurfaces.showLabTab(true))
            val phase = preparePasswordAudit()
            assertTrue(phase is RealWifiAuditPhase.BackendUnavailable)
        }

    @Test
    fun lab_black_box_unaffected_by_unsupported_external_port() {
        val secret = "zz"
        val verifier = BlackBoxChallengeVerifier.encapsulate(secret)
        LabChallenge.withBlackBoxVerifier(
            policy = LabSecretPolicy(Alphabet.LOWERCASE, LengthPolicy.exactly(2)),
            verifier = verifier,
            seed = 1L,
        )
        assertTrue(verifier.verify(secret))
        assertFalse(verifier.verify("aa"))
        assertFalse(verifier.toString().contains(secret))
        // Unsupported external port is Real-only wiring — Lab does not consult it.
        val port = UnsupportedExternalCandidateVerificationPort()
        assertTrue(
            port.capability() is
                com.wifiauditlab.assessment.domain.realaudit.ExternalVerifierCapability.Unavailable,
        )
    }

    private fun preparePasswordAudit(): RealWifiAuditPhase {
        val orchestrator =
            RealWifiAuditOrchestrator(
                strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                planner = DefaultRealWifiAuditPlanner(),
                search =
                    object : RealWifiSearchPort {
                        override suspend fun search(
                            plan: RealWifiAuditPlan,
                            progress: RealWifiAuditProgressSink,
                        ): RealWifiSearchExecutionResult =
                            error("search must not run when backend unavailable")

                        override fun cancel() = Unit
                    },
                externalVerifier = UnsupportedExternalCandidateVerificationPort(),
            )
        orchestrator.prepare(
            RealNetworkReference(
                ssid = Ssid("Casa"),
                bssid = null,
                securityFamily = SecurityFamily.WPA2_PERSONAL,
                displayName = "Casa",
            ),
            WifiSecurityProfile(
                family = SecurityFamily.WPA2_PERSONAL,
                keyManagements = emptySet(),
                managementFrameProtection = ManagementFrameProtection.UNKNOWN,
                isTransitionMode = false,
                rawCapabilities = null,
            ),
        )
        return orchestrator.state.value.phase
    }
}
