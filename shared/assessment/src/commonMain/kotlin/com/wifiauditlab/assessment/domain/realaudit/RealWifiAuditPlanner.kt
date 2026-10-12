package com.wifiauditlab.assessment.domain.realaudit

import com.wifiauditlab.assessment.domain.real.RealNetworkReference
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/**
 * Builds a [RealWifiAuditPlan] from the selected network and security profile.
 *
 * Pure assessment-layer planner: no Android, no known secret, no Lab imports.
 * Search Engine V2 wiring happens in the Android local-search adapter.
 */
fun interface RealWifiAuditPlanner {
    fun createPlan(
        network: RealNetworkReference,
        securityProfile: WifiSecurityProfile,
        strategy: RealWifiAuditStrategy,
    ): RealWifiAuditPlan
}

class DefaultRealWifiAuditPlanner : RealWifiAuditPlanner {
    override fun createPlan(
        network: RealNetworkReference,
        securityProfile: WifiSecurityProfile,
        strategy: RealWifiAuditStrategy,
    ): RealWifiAuditPlan {
        val limits =
            when (strategy) {
                RealWifiAuditStrategy.PersonalPskAudit,
                RealWifiAuditStrategy.PersonalSaeAudit,
                RealWifiAuditStrategy.TransitionPersonalAudit,
                RealWifiAuditStrategy.LegacyPersonalKeyAudit,
                ->
                    RealWifiAuditLimits(
                        maxDurationMillis = 60_000L,
                        maxCandidatesHint = null,
                        workerCountHint = 2,
                    )
                else ->
                    RealWifiAuditLimits(
                        maxDurationMillis = 15_000L,
                        maxCandidatesHint = 0,
                        workerCountHint = 1,
                    )
            }
        return RealWifiAuditPlan(
            network = network,
            securityProfile = securityProfile,
            strategy = strategy,
            limits = limits,
            strategyLabel = strategy.displayLabel(),
        )
    }
}
