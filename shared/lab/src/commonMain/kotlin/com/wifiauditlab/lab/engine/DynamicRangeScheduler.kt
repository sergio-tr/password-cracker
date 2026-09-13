package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Half-open index range `[startInclusive, endExclusive)` over a candidate space.
 */
data class CombinationIndexRange(
    val startInclusive: CombinationCount,
    val endExclusive: CombinationCount,
) {
    init {
        require(startInclusive >= CombinationCount.ZERO) { "startInclusive must be non-negative" }
        require(startInclusive <= endExclusive) { "startInclusive must be <= endExclusive" }
    }

    val count: CombinationCount get() = endExclusive - startInclusive

    val isEmpty: Boolean get() = startInclusive == endExclusive
}

/**
 * Thread-safe, bounded, cancelable cursor that hands out disjoint chunks covering
 * `[0, totalSize)` exactly once — no gaps, no duplicates.
 */
class DynamicRangeScheduler(
    private val totalSize: CombinationCount,
    chunkSize: CombinationCount,
    initialNextStart: CombinationCount = CombinationCount.ZERO,
) {
    init {
        require(totalSize >= CombinationCount.ZERO) { "totalSize must be non-negative" }
        require(chunkSize > CombinationCount.ZERO) { "chunkSize must be positive" }
        require(initialNextStart >= CombinationCount.ZERO) { "initialNextStart must be non-negative" }
        require(initialNextStart <= totalSize) { "initialNextStart must be <= totalSize" }
    }

    private val effectiveChunk: CombinationCount =
        if (totalSize.isZero) {
            CombinationCount.ONE
        } else {
            chunkSize.coerceAtMost(totalSize)
        }

    private val mutex = Mutex()
    private var nextStart: CombinationCount = initialNextStart
    private var issued: CombinationCount = initialNextStart

    @Volatile
    private var cancelled: Boolean = false

    /** Exact number of candidate indices claimed so far (sum of issued range sizes). */
    suspend fun issuedCount(): CombinationCount = mutex.withLock { issued }

    /** Next unclaimed index — used as the resume cursor after a cooperative pause. */
    suspend fun nextStart(): CombinationCount = mutex.withLock { nextStart }

    fun isCancelled(): Boolean = cancelled

    suspend fun isExhausted(): Boolean = mutex.withLock { nextStart >= totalSize }

    fun cancel() {
        cancelled = true
    }

    /**
     * Claims the next disjoint chunk, or `null` when cancelled or the space is
     * fully covered.
     */
    suspend fun claimNext(): CombinationIndexRange? =
        mutex.withLock {
            if (cancelled || nextStart >= totalSize) return@withLock null
            val end = (nextStart + effectiveChunk).coerceAtMost(totalSize)
            val range = CombinationIndexRange(nextStart, end)
            nextStart = end
            issued += range.count
            range
        }

    companion object {
        fun chunkSizeFor(
            batchSize: Int,
            batchesPerChunk: Int = DEFAULT_BATCHES_PER_CHUNK,
        ): CombinationCount {
            require(batchSize >= 1) { "batchSize must be >= 1" }
            require(batchesPerChunk >= 1) { "batchesPerChunk must be >= 1" }
            return CombinationCount.of(batchSize.toLong() * batchesPerChunk)
        }

        const val DEFAULT_BATCHES_PER_CHUNK: Int = 8
    }
}
