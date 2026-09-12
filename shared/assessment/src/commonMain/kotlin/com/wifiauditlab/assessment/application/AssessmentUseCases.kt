package com.wifiauditlab.assessment.application

import com.wifiauditlab.assessment.domain.match.KnownNetworkMatcher
import com.wifiauditlab.assessment.domain.match.NetworkMatchResult
import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.SecurityAssessmentRegistry
import com.wifiauditlab.assessment.domain.wifi.WifiObservation
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.port.SavedNetworkRepository
import kotlinx.coroutines.flow.first

/** Matches an observation against the current Vault snapshot. */
class MatchKnownNetwork(
    private val repository: SavedNetworkRepository,
    private val matcher: KnownNetworkMatcher,
) {
    suspend operator fun invoke(observation: WifiObservation): NetworkMatchResult {
        val candidates = repository.observeAll().first()
        return matcher.match(observation, candidates)
    }
}

/** Produces a user-facing security assessment for a normalized profile. */
class AssessNetworkSecurity(private val registry: SecurityAssessmentRegistry) {
    suspend operator fun invoke(profile: WifiSecurityProfile): SecurityAssessment = registry.assess(profile)
    suspend operator fun invoke(observation: WifiObservation): SecurityAssessment =
        registry.assess(observation.securityProfile)
}
