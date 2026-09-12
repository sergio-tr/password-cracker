package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.SearchMetrics
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Accumulates batch-level counters and produces [SearchMetrics] snapshots. */
interface SearchMetricsCollector {
    fun record(
        totalAttempts: Long,
        elapsed: Duration,
        currentBucketIndex: Int,
    )

    fun snapshot(): SearchMetrics
}

/**
 * Default collector. Keeps processed attempts as a [Long] (bounded runs never
 * approach [Long.MAX_VALUE]) while sizing the space with [CombinationCount].
 */
class DefaultSearchMetricsCollector(
    private val totalBuckets: Int,
    private val searchSpace: CombinationCount,
) : SearchMetricsCollector {
    private var attempts: Long = 0
    private var elapsed: Duration = Duration.ZERO
    private var bucketIndex: Int = 0

    override fun record(
        totalAttempts: Long,
        elapsed: Duration,
        currentBucketIndex: Int,
    ) {
        this.attempts = totalAttempts
        this.elapsed = elapsed
        this.bucketIndex = currentBucketIndex
    }

    override fun snapshot(): SearchMetrics {
        val seconds = elapsed.inWholeMicroseconds / 1_000_000.0
        val rate = if (seconds > 0.0) attempts / seconds else 0.0
        val attemptsCount = CombinationCount.of(attempts)
        val spaceLong = searchSpace.toLongOrNull()
        val remaining: Duration? =
            if (rate > 0.0 && spaceLong != null && spaceLong >= attempts) {
                ((spaceLong - attempts) / rate).seconds
            } else {
                null
            }
        return SearchMetrics(
            attempts = attemptsCount,
            elapsed = elapsed,
            attemptsPerSecond = rate,
            currentBucketIndex = bucketIndex,
            totalBuckets = totalBuckets,
            searchSpace = searchSpace,
            processedPercentage = attemptsCount.percentageOf(searchSpace),
            estimatedRemaining = remaining,
        )
    }
}
