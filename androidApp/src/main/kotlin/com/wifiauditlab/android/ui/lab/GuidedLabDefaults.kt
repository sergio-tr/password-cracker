package com.wifiauditlab.android.ui.lab

import androidx.annotation.StringRes
import com.wifiauditlab.android.R
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

@StringRes
fun SecurityFamily.guidedLabExplanationRes(): Int =
    when {
        supportsSharedPasswordDemo() -> R.string.lab_guided_shared_password
        this == SecurityFamily.OPEN || this == SecurityFamily.OWE -> R.string.lab_guided_open_owe
        this == SecurityFamily.WPA2_ENTERPRISE ||
            this == SecurityFamily.WPA3_ENTERPRISE ||
            this == SecurityFamily.PASSPOINT -> R.string.lab_guided_enterprise
        this == SecurityFamily.DPP -> R.string.lab_guided_dpp
        this == SecurityFamily.WEP -> R.string.lab_guided_wep
        else -> R.string.lab_guided_unknown
    }

@StringRes
fun LabNetworkContext?.guidedTitleRes(): Int =
    when {
        this == null -> R.string.lab_guided_experiment
        securityFamily.supportsSharedPasswordDemo() -> R.string.lab_password_demo
        else -> R.string.lab_generic
    }
