package com.wifiauditlab.android.ui.lab

import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import com.wifiauditlab.lab.engine.WorkerPoolConfig

enum class LabInteractionMode {
    /** Defaults + minimal UI; technical knobs stay under advanced options. */
    Guided,

    /** Full configuration surface for comparison experiments. */
    Advanced,
}

/**
 * Picks a reasonable synthetic LabConfig without inventing real-world password
 * distributions from WPA2/WPA3. Security family only informs UI relevance copy.
 */
object GuidedLabDefaults {
    fun recommendedConfig(
        availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
        calibratedAttemptsPerSecond: Double? = null,
    ): LabConfig {
        val workers =
            WorkerPoolConfig.recommendedWorkerCount(
                availableProcessors = availableProcessors,
                calibratedAttemptsPerSecond = calibratedAttemptsPerSecond,
            ).coerceIn(1, 4)
        return LabConfig(
            alphabet = AlphabetChoice.DIGITS,
            strategy = StrategyChoice.LENGTH,
            secretLength = 4,
            maxAttempts = 2_000_000,
            maxDurationSeconds = 45,
            workers = workers,
            seed = 1,
        )
    }
}

/** Whether a shared Wi-Fi password demo is conceptually relevant for this family. */
fun SecurityFamily.supportsSharedPasswordDemo(): Boolean =
    when (this) {
        SecurityFamily.WPA_PERSONAL,
        SecurityFamily.WPA2_PERSONAL,
        SecurityFamily.WPA3_PERSONAL,
        SecurityFamily.WPA2_WPA3_PERSONAL,
        -> true
        else -> false
    }

fun SecurityFamily.guidedLabExplanation(): String =
    when {
        supportsSharedPasswordDemo() ->
            "Demostración de contraseña: el laboratorio busca un secreto sintético local " +
                "usando esta red solo como contexto didáctico."
        this == SecurityFamily.OPEN || this == SecurityFamily.OWE ->
            "Esta red no utiliza una contraseña Wi-Fi compartida del mismo modo, " +
                "por lo que este experimento no es representativo de su autenticación."
        this == SecurityFamily.WPA2_ENTERPRISE ||
            this == SecurityFamily.WPA3_ENTERPRISE ||
            this == SecurityFamily.PASSPOINT ->
            "Esta red usa autenticación enterprise/Passpoint, no una contraseña Wi-Fi compartida. " +
                "Puedes abrir un laboratorio genérico, pero no equivale a su autenticación."
        this == SecurityFamily.DPP ->
            "DPP (Easy Connect) no se modela como una contraseña compartida. " +
                "El laboratorio genérico sigue siendo una simulación local."
        this == SecurityFamily.WEP ->
            "WEP está obsoleto; la demo de laboratorio es sintética y no autentica contra la red."
        else ->
            "No está claro si esta red usa una contraseña compartida. " +
                "El laboratorio permanece como simulación local."
    }

fun LabNetworkContext?.guidedTitle(): String =
    when {
        this == null -> "Modo guiado"
        securityFamily.supportsSharedPasswordDemo() -> "Demostración de contraseña"
        else -> "Laboratorio genérico"
    }
