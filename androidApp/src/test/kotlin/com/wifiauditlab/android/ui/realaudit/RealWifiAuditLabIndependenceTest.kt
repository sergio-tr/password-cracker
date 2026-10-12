package com.wifiauditlab.android.ui.realaudit

import com.wifiauditlab.android.lab.LabModeSurfaces
import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditPlanner
import com.wifiauditlab.assessment.domain.realaudit.DefaultRealWifiAuditStrategyResolver
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditBackend
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditBackendResult
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOrchestrator
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditOutcome
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPhase
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditPlan
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditProgressSink
import com.wifiauditlab.assessment.domain.realaudit.RealWifiAuditStrategy
import com.wifiauditlab.assessment.domain.wifi.ManagementFrameProtection
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real Wi‑Fi audit must remain available regardless of Lab Mode.
 * Lab surfaces hide/show independently.
 */
class RealWifiAuditLabIndependenceTest {
    @Test
    fun labOff_hidesLab_but_realAuditStillRuns() =
        runTest {
            assertFalse(LabModeSurfaces.showLabTab(false))
            val outcome = runAuditOnce()
            assertEquals(RealWifiAuditOutcome.Completed, outcome)
        }

    @Test
    fun labOn_showsLab_and_realAuditStillRuns() =
        runTest {
            assertTrue(LabModeSurfaces.showLabTab(true))
            val outcome = runAuditOnce()
            assertEquals(RealWifiAuditOutcome.Completed, outcome)
        }

    private suspend fun runAuditOnce(): RealWifiAuditOutcome {
        val orchestrator =
            RealWifiAuditOrchestrator(
                strategyResolver = DefaultRealWifiAuditStrategyResolver(),
                planner = DefaultRealWifiAuditPlanner(),
                backend =
                    object : RealWifiAuditBackend {
                        override suspend fun execute(
                            plan: RealWifiAuditPlan,
                            progress: RealWifiAuditProgressSink,
                        ): RealWifiAuditBackendResult {
                            assertEquals(RealWifiAuditStrategy.PersonalPskAudit, plan.strategy)
                            return RealWifiAuditBackendResult.Completed
                        }

                        override fun cancel() = Unit
                    },
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
        orchestrator.start()
        return (orchestrator.state.value.phase as RealWifiAuditPhase.Completed).outcome
    }
}
