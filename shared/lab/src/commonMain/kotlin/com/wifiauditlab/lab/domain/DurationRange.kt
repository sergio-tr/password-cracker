package com.wifiauditlab.lab.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Inclusive wall-clock range for a search estimate. The UI must show this as an
 * approximation ("approximately 4–7 minutes"), never as an exact clock time.
 */
data class DurationRange(
    val min: Duration,
    val max: Duration,
) {
    init {
        require(min >= Duration.ZERO) { "min duration must be non-negative" }
        require(max >= min) { "max duration must be >= min" }
    }

    fun toApproximateString(): String = "approximately ${format(min)}–${format(max)}"

    companion object {
        fun fromPoint(
            point: Duration,
            lowFactor: Double = 0.75,
            highFactor: Double = 1.40,
        ): DurationRange {
            val min = (point.inWholeMilliseconds * lowFactor).toLong().coerceAtLeast(1).milliseconds
            val max = (point.inWholeMilliseconds * highFactor).toLong().coerceAtLeast(min.inWholeMilliseconds).milliseconds
            return DurationRange(min, max)
        }

        internal fun format(duration: Duration): String {
            val seconds = duration.inWholeSeconds
            return when {
                seconds < 60 -> "${seconds.coerceAtLeast(1)}s"
                seconds < 3600 -> "${seconds / 60} min"
                else -> "${seconds / 3600} h"
            }
        }
    }
}
