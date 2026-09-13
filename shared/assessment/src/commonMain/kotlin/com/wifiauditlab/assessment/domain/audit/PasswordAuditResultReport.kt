package com.wifiauditlab.assessment.domain.audit

import com.wifiauditlab.assessment.domain.security.SecurityAssessment
import com.wifiauditlab.assessment.domain.security.SecurityRating
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

/** Terminal shape of the local known-password search (never claims absolute crackability). */
sealed interface PasswordSearchOutcomeKind {
    data class Found(
        val attempts: CombinationCount?,
        val duration: Duration?,
        val attemptsPerSecond: Double?,
    ) : PasswordSearchOutcomeKind

    data class LimitReached(
        val attempts: CombinationCount?,
        val duration: Duration?,
        val budgetSummary: String,
    ) : PasswordSearchOutcomeKind

    /** Plan candidate space was fully walked without a hit (still not "password does not exist"). */
    data class Exhausted(
        val attempts: CombinationCount?,
        val duration: Duration?,
    ) : PasswordSearchOutcomeKind

    data class Cancelled(
        val attempts: CombinationCount?,
        val duration: Duration?,
    ) : PasswordSearchOutcomeKind

    data class Failed(
        val sanitizedMessage: String?,
    ) : PasswordSearchOutcomeKind
}

data class PerformanceMetric(
    val label: String,
    val value: String,
    val quality: EvidenceQuality,
)

data class AuditRecommendation(
    val id: String,
    val text: String,
)

/**
 * Full known-password audit deliverable for UI.
 * Keeps Wi-Fi configuration assessment separate from password resistance.
 * Must never be fed back into the Search Engine planner.
 */
data class WifiPasswordAuditResult(
    val headline: String,
    val searchOutcome: PasswordSearchOutcomeKind,
    val passwordResistance: PasswordResistanceRating,
    val passwordResistanceLabel: String,
    val passwordStrength: PasswordStrengthAssessment?,
    val networkAssessment: SecurityAssessment?,
    val networkConfigLabel: String?,
    val performanceMetrics: List<PerformanceMetric>,
    val recommendations: List<AuditRecommendation>,
    val classificationNotes: List<String>,
    val detailsExpandedDefault: Boolean = false,
)

/** @deprecated Prefer [WifiPasswordAuditResult]; kept as type alias for gradual migration. */
typealias PasswordAuditResultReport = WifiPasswordAuditResult

/**
 * Builds a novice-friendly report.
 *
 * Classification criteria (transparent, not absolute):
 * - Found within budget → observed resistance is at most Low (local model).
 * - Survived budget / exhausted plan space → at least Moderate, may rise with structure.
 * - Cancelled / failed → Unknown (incomplete evidence).
 * - Structure (length, classes, repetition, sequences) informs but never alone claims High.
 * - Network [SecurityAssessment] is reported separately and never upgrades password rating.
 */
object PasswordAuditResultComposer {
    fun compose(
        searchOutcome: PasswordSearchOutcomeKind,
        structural: PasswordStrengthAssessment?,
        networkAssessment: SecurityAssessment? = null,
        budgetedAttemptCapacity: CombinationCount? = null,
        familyDisplayLabel: String? = null,
    ): WifiPasswordAuditResult {
        val passwordResistance = classifyPasswordResistance(searchOutcome, structural)
        val headline = headlineFor(searchOutcome, passwordResistance)
        val metrics = performanceMetrics(searchOutcome, budgetedAttemptCapacity, structural)
        val recommendations =
            recommendationsFor(
                searchOutcome = searchOutcome,
                passwordResistance = passwordResistance,
                structural = structural,
                network = networkAssessment,
            )
        val notes =
            classificationNotes(
                searchOutcome = searchOutcome,
                passwordResistance = passwordResistance,
                structural = structural,
            )

        return WifiPasswordAuditResult(
            headline = headline,
            searchOutcome = searchOutcome,
            passwordResistance = passwordResistance,
            passwordResistanceLabel = resistanceLabel(passwordResistance),
            passwordStrength = structural?.copy(measuredSearch = searchOutcome is PasswordSearchOutcomeKind.Found),
            networkAssessment = networkAssessment,
            networkConfigLabel = networkConfigLabel(networkAssessment, familyDisplayLabel),
            performanceMetrics = metrics,
            recommendations = recommendations,
            classificationNotes = notes,
        )
    }

    /**
     * Convenience bridge used by the Android ViewModel while migrating call sites.
     */
    fun compose(
        discoveredWithinBudget: Boolean,
        cancelled: Boolean,
        failed: Boolean,
        structural: PasswordStrengthAssessment?,
        measuredAttempts: CombinationCount?,
        measuredDuration: Duration?,
        budgetedAttemptCapacity: CombinationCount?,
        attemptsPerSecond: Double? = null,
        exhausted: Boolean = false,
        networkAssessment: SecurityAssessment? = null,
        familyDisplayLabel: String? = null,
        failureMessage: String? = null,
    ): WifiPasswordAuditResult {
        val outcome =
            when {
                failed ->
                    PasswordSearchOutcomeKind.Failed(failureMessage?.take(240))
                cancelled ->
                    PasswordSearchOutcomeKind.Cancelled(measuredAttempts, measuredDuration)
                discoveredWithinBudget ->
                    PasswordSearchOutcomeKind.Found(measuredAttempts, measuredDuration, attemptsPerSecond)
                exhausted ->
                    PasswordSearchOutcomeKind.Exhausted(measuredAttempts, measuredDuration)
                else ->
                    PasswordSearchOutcomeKind.LimitReached(
                        attempts = measuredAttempts,
                        duration = measuredDuration,
                        budgetSummary =
                            listOfNotNull(
                                measuredAttempts?.toAbbreviatedString()?.let { "$it intentos" },
                                measuredDuration?.let { it.toString() },
                                budgetedAttemptCapacity?.toAbbreviatedString()?.let { "presupuesto ~$it" },
                            ).joinToString(" · ").ifBlank { "límite configurado" },
                    )
            }
        return compose(
            searchOutcome = outcome,
            structural = structural,
            networkAssessment = networkAssessment,
            budgetedAttemptCapacity = budgetedAttemptCapacity,
            familyDisplayLabel = familyDisplayLabel,
        )
    }

    fun resistanceLabel(rating: PasswordResistanceRating): String =
        when (rating) {
            PasswordResistanceRating.VERY_LOW -> "Muy baja"
            PasswordResistanceRating.LOW -> "Baja"
            PasswordResistanceRating.MODERATE -> "Moderada"
            PasswordResistanceRating.HIGH -> "Alta"
            PasswordResistanceRating.VERY_HIGH -> "Muy alta"
            PasswordResistanceRating.UNKNOWN -> "No determinada"
        }

    fun classifyPasswordResistance(
        searchOutcome: PasswordSearchOutcomeKind,
        structural: PasswordStrengthAssessment?,
    ): PasswordResistanceRating {
        val structure = structural?.rating ?: PasswordResistanceRating.MODERATE
        return when (searchOutcome) {
            is PasswordSearchOutcomeKind.Failed,
            is PasswordSearchOutcomeKind.Cancelled,
            -> PasswordResistanceRating.UNKNOWN
            is PasswordSearchOutcomeKind.Found ->
                weakerOf(structure, PasswordResistanceRating.LOW).let { base ->
                    // Very quick finds with weak structure stay VeryLow.
                    val attempts = searchOutcome.attempts
                    if (attempts != null && !attempts.isZero && attempts < CombinationCount.of(10_000)) {
                        weakerOf(base, PasswordResistanceRating.VERY_LOW)
                    } else {
                        base
                    }
                }
            is PasswordSearchOutcomeKind.LimitReached ->
                strongerOf(structure, PasswordResistanceRating.MODERATE).let { base ->
                    if (structure == PasswordResistanceRating.HIGH ||
                        structure == PasswordResistanceRating.VERY_HIGH
                    ) {
                        // Survived budget with strong structure → High (not absolute).
                        strongerOf(base, PasswordResistanceRating.HIGH)
                    } else {
                        base
                    }
                }
            is PasswordSearchOutcomeKind.Exhausted ->
                // Covered the *planned* model, not the universe of passwords.
                strongerOf(structure, PasswordResistanceRating.HIGH)
        }
    }

    private fun headlineFor(
        outcome: PasswordSearchOutcomeKind,
        resistance: PasswordResistanceRating,
    ): String =
        when (outcome) {
            is PasswordSearchOutcomeKind.Found -> "Contraseña encontrada"
            is PasswordSearchOutcomeKind.LimitReached ->
                if (resistance == PasswordResistanceRating.HIGH ||
                    resistance == PasswordResistanceRating.VERY_HIGH
                ) {
                    "Alta resistencia dentro del modelo probado"
                } else {
                    "No se encontró dentro del límite"
                }
            is PasswordSearchOutcomeKind.Exhausted ->
                "No encontrada dentro del modelo probado"
            is PasswordSearchOutcomeKind.Cancelled -> "Auditoría detenida"
            is PasswordSearchOutcomeKind.Failed -> "La auditoría se detuvo por un error"
        }

    private fun performanceMetrics(
        outcome: PasswordSearchOutcomeKind,
        budgeted: CombinationCount?,
        structural: PasswordStrengthAssessment?,
    ): List<PerformanceMetric> {
        val list = mutableListOf<PerformanceMetric>()

        fun addMeasured(
            label: String,
            value: String,
        ) {
            list += PerformanceMetric(label, value, EvidenceQuality.Measured)
        }

        when (outcome) {
            is PasswordSearchOutcomeKind.Found -> {
                outcome.attempts?.let { addMeasured("Combinaciones probadas", it.toExactString()) }
                outcome.duration?.let { addMeasured("Tiempo medido", formatDuration(it)) }
                outcome.attemptsPerSecond?.let {
                    addMeasured("Velocidad", "${it.toLong()} intentos/s")
                }
            }
            is PasswordSearchOutcomeKind.LimitReached -> {
                outcome.attempts?.let { addMeasured("Intentos", it.toExactString()) }
                outcome.duration?.let { addMeasured("Tiempo", formatDuration(it)) }
            }
            is PasswordSearchOutcomeKind.Exhausted -> {
                outcome.attempts?.let { addMeasured("Intentos", it.toExactString()) }
                outcome.duration?.let { addMeasured("Tiempo", formatDuration(it)) }
            }
            is PasswordSearchOutcomeKind.Cancelled -> {
                outcome.attempts?.let { addMeasured("Intentos realizados", it.toExactString()) }
                outcome.duration?.let { addMeasured("Tiempo", formatDuration(it)) }
            }
            is PasswordSearchOutcomeKind.Failed -> Unit
        }
        budgeted?.let {
            list +=
                PerformanceMetric(
                    label = "Capacidad del presupuesto",
                    value = "~${it.toAbbreviatedString()} intentos",
                    quality = EvidenceQuality.Estimated,
                )
        }
        structural?.let {
            list +=
                PerformanceMetric(
                    label = "Estructura modelada",
                    value = "longitud ${it.length} · ${it.charsetDiversity} clases",
                    quality = EvidenceQuality.Modelled,
                )
        }
        return list
    }

    private fun recommendationsFor(
        searchOutcome: PasswordSearchOutcomeKind,
        passwordResistance: PasswordResistanceRating,
        structural: PasswordStrengthAssessment?,
        network: SecurityAssessment?,
    ): List<AuditRecommendation> {
        val out = mutableListOf<AuditRecommendation>()
        val weakPassword =
            passwordResistance == PasswordResistanceRating.VERY_LOW ||
                passwordResistance == PasswordResistanceRating.LOW ||
                searchOutcome is PasswordSearchOutcomeKind.Found
        if (weakPassword) {
            out +=
                AuditRecommendation(
                    id = "longer",
                    text = "Utiliza una contraseña más larga.",
                )
            out +=
                AuditRecommendation(
                    id = "structure",
                    text = "Evita estructuras sencillas o repetitivas.",
                )
        }
        if (structural != null && structural.charsetDiversity < 3 && structural.length < 14) {
            out +=
                AuditRecommendation(
                    id = "charset",
                    text = "Combina mayúsculas, minúsculas, números y símbolos si tus dispositivos lo permiten.",
                )
        }
        when (network?.rating) {
            SecurityRating.INSECURE, SecurityRating.LOW ->
                out +=
                    AuditRecommendation(
                        id = "upgrade-wifi",
                        text = "Mantén WPA2/WPA3 en lugar de protocolos heredados.",
                    )
            SecurityRating.MODERATE ->
                out +=
                    AuditRecommendation(
                        id = "prefer-wpa3",
                        text = "Si todos tus dispositivos lo soportan, prefiere WPA3 Personal.",
                    )
            SecurityRating.HIGH ->
                if (network.findings.any {
                        it.title.contains("transición", ignoreCase = true) ||
                            it.explanation.contains("transición", ignoreCase = true)
                    }
                ) {
                    out +=
                        AuditRecommendation(
                            id = "avoid-transition",
                            text = "Evita modos de transición si todos tus dispositivos soportan WPA3.",
                        )
                }
            null -> Unit
        }
        return out.distinctBy { it.id }
    }

    private fun classificationNotes(
        searchOutcome: PasswordSearchOutcomeKind,
        passwordResistance: PasswordResistanceRating,
        structural: PasswordStrengthAssessment?,
    ): List<String> =
        buildList {
            add("La resistencia observada combina el resultado de la búsqueda local y el análisis estructural.")
            add("No afirma el tiempo que necesitaría un atacante real ni seguridad absoluta.")
            when (searchOutcome) {
                is PasswordSearchOutcomeKind.Found ->
                    add("Encontrada dentro del presupuesto → la resistencia observada no supera Baja en este modelo.")
                is PasswordSearchOutcomeKind.LimitReached ->
                    add("La contraseña resistió el presupuesto de búsqueda seleccionado.")
                is PasswordSearchOutcomeKind.Exhausted ->
                    add("Se agotó el espacio del plan automático; eso no significa que la contraseña «no exista».")
                is PasswordSearchOutcomeKind.Cancelled ->
                    add("No se ha emitido una conclusión completa porque la prueba fue cancelada.")
                is PasswordSearchOutcomeKind.Failed ->
                    add("El motor local falló antes de producir una conclusión útil.")
            }
            structural?.let { add("Estructura: ${it.summary}") }
            add("Veredicto de contraseña: ${resistanceLabel(passwordResistance)} (modelo local).")
        }

    private fun networkConfigLabel(
        assessment: SecurityAssessment?,
        familyDisplayLabel: String?,
    ): String? {
        if (assessment == null && familyDisplayLabel == null) return null
        val rating =
            when (assessment?.rating) {
                SecurityRating.HIGH -> "Alta"
                SecurityRating.MODERATE -> "Moderada"
                SecurityRating.LOW -> "Baja"
                SecurityRating.INSECURE -> "Insegura"
                null -> null
            }
        return listOfNotNull(rating, familyDisplayLabel ?: assessment?.headline).joinToString(" · ")
    }

    private fun formatDuration(duration: Duration): String {
        val ms = duration.inWholeMilliseconds
        return when {
            ms < 10_000 -> {
                val tenths = (ms + 50) / 100
                val whole = tenths / 10
                val frac = tenths % 10
                "$whole,$frac s"
            }
            else -> {
                val total = duration.inWholeSeconds
                val m = total / 60
                val s = total % 60
                if (m > 0) "$m min $s s" else "$s s"
            }
        }
    }

    private fun weakerOf(
        a: PasswordResistanceRating,
        b: PasswordResistanceRating,
    ): PasswordResistanceRating = if (rank(a) <= rank(b)) a else b

    private fun strongerOf(
        a: PasswordResistanceRating,
        b: PasswordResistanceRating,
    ): PasswordResistanceRating = if (rank(a) >= rank(b)) a else b

    private fun rank(r: PasswordResistanceRating): Int =
        when (r) {
            PasswordResistanceRating.VERY_LOW -> 0
            PasswordResistanceRating.LOW -> 1
            PasswordResistanceRating.MODERATE -> 2
            PasswordResistanceRating.HIGH -> 3
            PasswordResistanceRating.VERY_HIGH -> 4
            PasswordResistanceRating.UNKNOWN -> -1
        }
}
