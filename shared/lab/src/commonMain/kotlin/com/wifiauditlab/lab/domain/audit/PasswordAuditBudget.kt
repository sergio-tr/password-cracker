package com.wifiauditlab.lab.domain.audit

import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Hard budget for a known-password audit. At least one of [maxDuration] /
 * [maxAttempts] must be set — unbounded audits are rejected.
 *
 * Quick-audit UI presets (novice):
 * - [quick]: 30 seconds
 * - [standard]: 1 minute (default)
 * - [deep]: 5 minutes
 */
data class PasswordAuditBudget(
    val maxDuration: Duration? = null,
    val maxAttempts: CombinationCount? = null,
    val preset: PasswordAuditBudgetPreset = PasswordAuditBudgetPreset.Custom,
) {
    init {
        require(maxDuration != null || maxAttempts != null) {
            "PasswordAuditBudget must define maxDuration and/or maxAttempts"
        }
        require(maxDuration == null || maxDuration > Duration.ZERO) {
            "maxDuration must be positive when present"
        }
        require(maxAttempts == null || !maxAttempts.isZero) {
            "maxAttempts must be positive when present"
        }
    }

    companion object {
        fun quick(): PasswordAuditBudget =
            PasswordAuditBudget(maxDuration = 30.seconds, preset = PasswordAuditBudgetPreset.Quick)

        fun standard(): PasswordAuditBudget =
            PasswordAuditBudget(maxDuration = 1.minutes, preset = PasswordAuditBudgetPreset.Standard)

        fun deep(): PasswordAuditBudget =
            PasswordAuditBudget(maxDuration = 5.minutes, preset = PasswordAuditBudgetPreset.Deep)
    }
}

enum class PasswordAuditBudgetPreset {
    Quick,
    Standard,
    Deep,
    Custom,
}
