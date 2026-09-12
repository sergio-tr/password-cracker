package com.wifiauditlab.assessment.domain.security

import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile

/** Overall, user-facing security verdict. */
enum class SecurityRating { HIGH, MODERATE, LOW, INSECURE }

/** Severity of an individual finding. */
enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

/** A single observation about a network's security, in plain language. */
data class SecurityFinding(
    val severity: Severity,
    val title: String,
    val explanation: String,
)

/**
 * Result of assessing a network. Deliberately separates plain-language content
 * (for everyone) from technical detail (progressive disclosure in the UI).
 */
data class SecurityAssessment(
    val rating: SecurityRating,
    val headline: String,
    val plainExplanation: String,
    val technicalSummary: String,
    val findings: List<SecurityFinding>,
)

/**
 * A strategy that assesses one class of security profile. New mechanisms are
 * added as new strategies rather than by extending a central `when`.
 */
interface SecurityAssessmentStrategy {
    fun supports(profile: WifiSecurityProfile): Boolean

    suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment
}

/**
 * Resolves the appropriate [SecurityAssessmentStrategy] for a profile. The first
 * supporting strategy wins; a catch-all keeps the result total.
 */
class SecurityAssessmentRegistry(
    private val strategies: List<SecurityAssessmentStrategy>,
    private val fallback: SecurityAssessmentStrategy = UnsupportedAssessmentStrategy(),
) {
    suspend fun assess(profile: WifiSecurityProfile): SecurityAssessment =
        (strategies.firstOrNull { it.supports(profile) } ?: fallback).assess(profile)

    companion object {
        /** Registry wired with every built-in strategy, catch-all last. */
        fun default(): SecurityAssessmentRegistry =
            SecurityAssessmentRegistry(
                strategies =
                    listOf(
                        OpenNetworkAssessmentStrategy(),
                        LegacyNetworkAssessmentStrategy(),
                        PersonalNetworkAssessmentStrategy(),
                        EnterpriseNetworkAssessmentStrategy(),
                        OweAssessmentStrategy(),
                        PasspointAssessmentStrategy(),
                        DppAssessmentStrategy(),
                    ),
            )
    }
}
