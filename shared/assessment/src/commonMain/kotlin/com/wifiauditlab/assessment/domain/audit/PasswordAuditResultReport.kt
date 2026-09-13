package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

/**
 * Qualifies how a figure was obtained so the UI never presents estimates as
 * exact attacker wall-clock time.
 */
enum class EvidenceQuality {
    Measured,
    Estimated,
    Modelled,
}

/**
 * Combined known-password audit report: local search outcome + structural analysis.
 * Must never be fed back into the Search Engine planner.
 */
data class PasswordAuditResultReport(
    val headline: String,
    val combinedResistance: PasswordResistanceRating,
    val resistanceLabel: String,
    val structural: PasswordStrengthAssessment?,
    val discoveredWithinBudget: Boolean,
    val measuredAttempts: CombinationCount?,
    val measuredDuration: Duration?,
    val details: List<String>,
    val evidenceNotes: List<Pair<EvidenceQuality, String>>,
)

/**
 * Builds a novice-friendly report. Search discovery within budget lowers resistance;
 * surviving the budget raises confidence that the secret is harder for this local model.
 */
object PasswordAuditResultComposer {
    fun compose(
        discoveredWithinBudget: Boolean,
        cancelled: Boolean,
        failed: Boolean,
        structural: PasswordStrengthAssessment?,
        measuredAttempts: CombinationCount?,
        measuredDuration: Duration?,
        budgetedAttemptCapacity: CombinationCount?,
    ): PasswordAuditResultReport {
        val combined =
            when {
                failed -> PasswordResistanceRating.UNKNOWN
                cancelled -> PasswordResistanceRating.UNKNOWN
                discoveredWithinBudget ->
                    weakerOf(
                        structural?.rating ?: PasswordResistanceRating.VERY_LOW,
                        PasswordResistanceRating.LOW,
                    )
                else ->
                    strongerOf(
                        structural?.rating ?: PasswordResistanceRating.MODERATE,
                        PasswordResistanceRating.MODERATE,
                    )
            }

        val headline =
            when {
                failed -> "La auditoría no pudo completarse."
                cancelled -> "Auditoría detenida. El resultado es parcial."
                discoveredWithinBudget ->
                    "La contraseña conocida se descubrió dentro del presupuesto local."
                else ->
                    "La contraseña conocida no se descubrió dentro del presupuesto local."
            }

        val details =
            buildList {
                add(
                    if (discoveredWithinBudget) {
                        "En este dispositivo, con el plan automático y el límite elegido, " +
                            "el motor local encontró el secreto."
                    } else if (cancelled) {
                        "Detuviste la búsqueda antes de agotar el presupuesto."
                    } else {
                        "Dentro del límite configurado, el motor local no alcanzó el secreto."
                    },
                )
                structural?.let { add("Análisis estructural: ${it.summary}") }
                add(
                    "Esto no es un ataque al router: solo se compararon candidatos en el teléfono.",
                )
                add(
                    "No interpreta el tiempo de un atacante real; califica Measured / Estimated / Modelled abajo.",
                )
            }

        val evidence =
            buildList {
                if (measuredAttempts != null || measuredDuration != null) {
                    val parts = mutableListOf<String>()
                    measuredAttempts?.let { parts += "${it.toAbbreviatedString()} intentos" }
                    measuredDuration?.let { parts += it.toString() }
                    add(EvidenceQuality.Measured to "Búsqueda local: ${parts.joinToString(" · ")}")
                }
                budgetedAttemptCapacity?.let {
                    add(
                        EvidenceQuality.Estimated to
                            "Capacidad estimada del presupuesto: ${it.toAbbreviatedString()} intentos",
                    )
                }
                structural?.let {
                    add(
                        EvidenceQuality.Modelled to
                            "Estructura (longitud ${it.length}, ${it.charsetDiversity} clases)",
                    )
                }
            }

        return PasswordAuditResultReport(
            headline = headline,
            combinedResistance = combined,
            resistanceLabel = resistanceLabel(combined),
            structural = structural?.copy(measuredSearch = measuredAttempts != null),
            discoveredWithinBudget = discoveredWithinBudget,
            measuredAttempts = measuredAttempts,
            measuredDuration = measuredDuration,
            details = details,
            evidenceNotes = evidence,
        )
    }

    fun resistanceLabel(rating: PasswordResistanceRating): String =
        when (rating) {
            PasswordResistanceRating.VERY_LOW -> "Resistencia muy baja (en este presupuesto)"
            PasswordResistanceRating.LOW -> "Resistencia baja (en este presupuesto)"
            PasswordResistanceRating.MODERATE -> "Resistencia moderada (en este presupuesto)"
            PasswordResistanceRating.HIGH -> "Resistencia alta (en este presupuesto)"
            PasswordResistanceRating.UNKNOWN -> "Resistencia no determinada"
        }

    private fun weakerOf(
        a: PasswordResistanceRating,
        b: PasswordResistanceRating,
    ): PasswordResistanceRating = if (a.ordinal <= b.ordinal) a else b

    private fun strongerOf(
        a: PasswordResistanceRating,
        b: PasswordResistanceRating,
    ): PasswordResistanceRating = if (a.ordinal >= b.ordinal) a else b
}
