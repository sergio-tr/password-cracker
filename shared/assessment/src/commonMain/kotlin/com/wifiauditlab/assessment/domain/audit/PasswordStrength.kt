package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.wifi.Bssid
import com.wifiauditlab.assessment.domain.wifi.Ssid
import com.wifiauditlab.assessment.domain.wifi.WifiBand
import com.wifiauditlab.assessment.domain.wifi.WifiSecurityProfile
import com.wifiauditlab.assessment.domain.wifi.WifiStandard

/**
 * Network snapshot for a known-password audit. Carries configuration context only —
 * never the password itself.
 */
data class PasswordAuditNetworkContext(
    val displayName: String,
    val ssid: Ssid,
    val bssid: Bssid?,
    val securityProfile: WifiSecurityProfile,
    val wifiStandard: WifiStandard?,
    val band: WifiBand?,
)

/** Observed resistance of a known password after local search and/or structure analysis. */
enum class PasswordResistanceRating {
    VERY_LOW,
    LOW,
    MODERATE,
    HIGH,
    UNKNOWN,
}

/**
 * Structure-only assessment of a known secret.
 * Must never feed into SearchPlanner / CandidateSource ordering for that same secret.
 */
data class PasswordStrengthAssessment(
    val rating: PasswordResistanceRating,
    val length: Int,
    val charsetDiversity: Int,
    val summary: String,
    val measuredSearch: Boolean = false,
)

fun interface SecretStrengthAnalyzer {
    fun analyze(plaintext: String): PasswordStrengthAssessment
}

/**
 * Heuristic structure analyzer. Deliberately shallow: length + character-class diversity.
 * Does not claim attacker time; does not adjust search ordering.
 */
class HeuristicSecretStrengthAnalyzer : SecretStrengthAnalyzer {
    override fun analyze(plaintext: String): PasswordStrengthAssessment {
        val length = plaintext.length
        val classes =
            listOf(
                plaintext.any { it.isDigit() },
                plaintext.any { it.isLowerCase() },
                plaintext.any { it.isUpperCase() },
                plaintext.any { !it.isLetterOrDigit() },
            ).count { it }
        val rating =
            when {
                length < 8 -> PasswordResistanceRating.VERY_LOW
                length < 10 && classes <= 2 -> PasswordResistanceRating.LOW
                length < 12 && classes <= 2 -> PasswordResistanceRating.MODERATE
                length >= 12 && classes >= 3 -> PasswordResistanceRating.HIGH
                length >= 10 -> PasswordResistanceRating.MODERATE
                else -> PasswordResistanceRating.LOW
            }
        val summary =
            "Longitud $length · $classes clases de caracteres. " +
                "Este análisis estructural no prioriza la búsqueda que intenta descubrir el secreto."
        return PasswordStrengthAssessment(
            rating = rating,
            length = length,
            charsetDiversity = classes,
            summary = summary,
            measuredSearch = false,
        )
    }
}
