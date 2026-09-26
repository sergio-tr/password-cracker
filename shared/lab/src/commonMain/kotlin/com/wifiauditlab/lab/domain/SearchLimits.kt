package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import kotlin.time.Duration

/**
 * Hard bounds that every search must respect.
 *
 * By default a search with neither a duration nor an attempt cap is rejected so
 * unbounded runs cannot start by accident. Explicit [untilCancelled] opts into
 * running until Found, candidate-space exhaustion, or cooperative STOP.
 *
 * The primary constructor enforces this invariant; [validate] lets the UI show a
 * reason before attempting construction.
 */
data class SearchLimits(
    val maxDuration: Duration?,
    val maxAttempts: CombinationCount?,
    val progressInterval: Duration,
    val batchSize: Int,
    val runUntilCancelled: Boolean = false,
) {
    init {
        val violations =
            validate(maxDuration, maxAttempts, progressInterval, batchSize, runUntilCancelled)
        require(violations.isEmpty()) { "invalid SearchLimits: " + violations.joinToString { it.message } }
    }

    enum class Violation(val message: String) {
        UNBOUNDED("a search must define at least a duration limit or an attempt limit"),
        NON_POSITIVE_DURATION("maxDuration must be positive when present"),
        NON_POSITIVE_ATTEMPTS("maxAttempts must be positive when present"),
        NON_POSITIVE_PROGRESS_INTERVAL("progressInterval must be positive"),
        NON_POSITIVE_BATCH_SIZE("batchSize must be positive"),
    }

    companion object {
        val DEFAULT_PROGRESS_INTERVAL: Duration = kotlin.time.Duration.parse("250ms")
        const val DEFAULT_BATCH_SIZE: Int = 4096

        fun validate(
            maxDuration: Duration? = null,
            maxAttempts: CombinationCount? = null,
            progressInterval: Duration = DEFAULT_PROGRESS_INTERVAL,
            batchSize: Int = DEFAULT_BATCH_SIZE,
            runUntilCancelled: Boolean = false,
        ): List<Violation> =
            buildList {
                if (!runUntilCancelled && maxDuration == null && maxAttempts == null) {
                    add(Violation.UNBOUNDED)
                }
                if (maxDuration != null && maxDuration <= Duration.ZERO) add(Violation.NON_POSITIVE_DURATION)
                if (maxAttempts != null && maxAttempts.isZero) add(Violation.NON_POSITIVE_ATTEMPTS)
                if (progressInterval <= Duration.ZERO) add(Violation.NON_POSITIVE_PROGRESS_INTERVAL)
                if (batchSize <= 0) add(Violation.NON_POSITIVE_BATCH_SIZE)
            }

        /** Convenience factory that keeps the sensible progress/batch defaults. */
        fun of(
            maxDuration: Duration? = null,
            maxAttempts: CombinationCount? = null,
            progressInterval: Duration = DEFAULT_PROGRESS_INTERVAL,
            batchSize: Int = DEFAULT_BATCH_SIZE,
        ): SearchLimits = SearchLimits(maxDuration, maxAttempts, progressInterval, batchSize)

        /**
         * Explicit unbounded run: no attempt/duration cap. Stops on Found, space
         * exhaustion, or cooperative cancellation only.
         */
        fun untilCancelled(
            progressInterval: Duration = DEFAULT_PROGRESS_INTERVAL,
            batchSize: Int = DEFAULT_BATCH_SIZE,
        ): SearchLimits =
            SearchLimits(
                maxDuration = null,
                maxAttempts = null,
                progressInterval = progressInterval,
                batchSize = batchSize,
                runUntilCancelled = true,
            )
    }
}
