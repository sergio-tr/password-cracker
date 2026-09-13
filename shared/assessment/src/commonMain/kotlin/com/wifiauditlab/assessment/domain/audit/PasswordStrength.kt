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
    VERY_HIGH,
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
 * Heuristic structure analyzer. Considers length, character-class diversity,
 * repetition and simple sequences. Does not claim attacker time; does not adjust search ordering.
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
        val hasHeavyRepetition = hasHeavyRepetition(plaintext)
        val hasSimpleSequence = hasSimpleSequence(plaintext)
        val rating =
            when {
                length < 8 || hasHeavyRepetition || (hasSimpleSequence && length < 12) ->
                    PasswordResistanceRating.VERY_LOW
                length < 10 && classes <= 2 -> PasswordResistanceRating.LOW
                length < 12 && classes <= 2 -> PasswordResistanceRating.MODERATE
                length >= 16 && classes >= 3 && !hasHeavyRepetition && !hasSimpleSequence ->
                    PasswordResistanceRating.VERY_HIGH
                length >= 12 && classes >= 3 && !hasHeavyRepetition -> PasswordResistanceRating.HIGH
                length >= 10 -> PasswordResistanceRating.MODERATE
                else -> PasswordResistanceRating.LOW
            }
        val flags =
            buildList {
                if (hasHeavyRepetition) add("repetición")
                if (hasSimpleSequence) add("secuencia simple")
            }
        val flagText = if (flags.isEmpty()) "" else " · Señales: ${flags.joinToString(", ")}"
        val summary =
            "Longitud $length · $classes clases de caracteres$flagText. " +
                "Este análisis estructural no prioriza la búsqueda que intenta descubrir el secreto."
        return PasswordStrengthAssessment(
            rating = rating,
            length = length,
            charsetDiversity = classes,
            summary = summary,
            measuredSearch = false,
        )
    }

    private fun hasHeavyRepetition(value: String): Boolean {
        if (value.length < 3) return false
        if (value.toSet().size == 1) return true
        var maxRun = 1
        var cur = 1
        for (i in 1 until value.length) {
            if (value[i] == value[i - 1]) {
                cur++
                maxRun = maxOf(maxRun, cur)
            } else {
                cur = 1
            }
        }
        return maxRun >= 4 || (value.length >= 6 && value.toSet().size <= 2)
    }

    private fun hasSimpleSequence(value: String): Boolean {
        val lower = value.lowercase()
        val sequences =
            listOf(
                "0123456789",
                "9876543210",
                "abcdefghijklmnopqrstuvwxyz",
                "zyxwvutsrqponmlkjihgfedcba",
                "qwertyuiop",
                "asdfghjkl",
            )
        return sequences.any { seq ->
            seq.windowed(4).any { lower.contains(it) } ||
                lower.windowed(4).any { window ->
                    seq.contains(window)
                }
        }
    }
}
