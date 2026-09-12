package com.wifiauditlab.lab.engine

import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LimitReason
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchSessionId
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.DefaultSearchMetricsCollector
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.asVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Optional worker-pool engine. Ranges are disjoint so candidates are never
 * duplicated. Single-worker [DefaultLabSearchEngine] remains the correctness
 * reference; this class exists to measure whether extra workers help.
 */
class ParallelLabSearchEngine(
    private val pool: WorkerPoolConfig,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : LabSearchEngine {
    override fun run(
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        cancellation: CancellationSignal,
    ): Flow<LabSearchEvent> =
        flow {
            if (plan.buckets.any { it.searchSpaceSize.toLongOrNull() == null }) {
                emitAll(DefaultLabSearchEngine(timeSource).run(challenge, plan, limits, cancellation))
                return@flow
            }
            emit(LabSearchEvent.Preparing)
            val verifier = challenge.asVerifier()
            val space = plan.searchSpace
            val sessionId = SearchSessionId.random()
            val collector = DefaultSearchMetricsCollector(plan.totalBuckets, space)
            val start = timeSource.markNow()
            emit(LabSearchEvent.Started(sessionId, plan, space))

            val maxAttempts = limits.maxAttempts?.toLongOrNull()
            val stop = kotlinx.coroutines.flow.MutableStateFlow(false)
            val mutex = Mutex()
            var processed = 0L
            var found: String? = null
            var limitReason: LimitReason? = null

            try {
                coroutineScope {
                    bucketLoop@ for (bucket in plan.buckets) {
                        if (cancellation.isCancelled || stop.value) break
                        val bucketSize = bucket.searchSpaceSize.toLongOrNull() ?: break
                        val ranges = partitionIndexSpace(bucketSize, pool.workerCount)
                        val results =
                            ranges.map { range ->
                                async {
                                    searchRange(
                                        bucket.candidateSource(plan.seed).candidates(),
                                        range,
                                        limits.batchSize,
                                        {
                                            cancellation.isCancelled || stop.value
                                        },
                                        { candidate -> verifier.verify(candidate) },
                                    )
                                }
                            }.awaitAll()

                        mutex.withLock {
                            processed += results.sumOf { it.attempts }
                            if (found == null) {
                                found = results.firstOrNull { it.found != null }?.found
                            }
                            if (maxAttempts != null && processed >= maxAttempts && found == null) {
                                limitReason = LimitReason.Attempts
                            }
                            collector.record(processed, start.elapsedNow(), bucket.index)
                        }
                        if (found != null) {
                            stop.value = true
                            break
                        }
                        if (limitReason != null) break
                        if (cancellation.isCancelled) break
                        if (limits.maxDuration != null && start.elapsedNow() >= limits.maxDuration) {
                            limitReason = LimitReason.Duration
                            break
                        }
                    }
                }

                val metrics = collector.snapshot()
                when {
                    found != null -> emit(LabSearchEvent.CandidateFound(found!!, metrics))
                    cancellation.isCancelled -> emit(LabSearchEvent.Cancelled(metrics))
                    limitReason != null -> emit(LabSearchEvent.LimitReached(limitReason!!, metrics))
                    else -> emit(LabSearchEvent.Completed(metrics))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                emit(
                    LabSearchEvent.Failed(
                        error.message ?: (error::class.simpleName ?: "unknown error"),
                        collector.snapshot(),
                    ),
                )
            }
        }

    private fun searchRange(
        candidates: Sequence<String>,
        range: IndexRange,
        batchSize: Int,
        shouldStop: () -> Boolean,
        matches: (String) -> Boolean,
    ): WorkerOutcome {
        var attempts = 0L
        var found: String? = null
        val iterator = candidates.drop(range.start.toInt()).take(range.count.toInt()).iterator()
        while (iterator.hasNext()) {
            var inBatch = 0
            while (inBatch < batchSize && iterator.hasNext()) {
                if (shouldStop()) return WorkerOutcome(attempts, found)
                val candidate = iterator.next()
                attempts++
                inBatch++
                if (matches(candidate)) {
                    found = candidate
                    return WorkerOutcome(attempts, found)
                }
            }
            if (shouldStop()) break
        }
        return WorkerOutcome(attempts, found)
    }

    private data class WorkerOutcome(
        val attempts: Long,
        val found: String?,
    )
}

/**
 * Chooses the single-worker baseline or the pool. [workers] is set by the UI
 * before [run] and is clamped by [WorkerPoolConfig].
 */
class WorkerAwareLabSearchEngine(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val availableProcessors: Int = 2,
) : LabSearchEngine {
    @Volatile
    var workers: Int = 1

    override fun run(
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        cancellation: CancellationSignal,
    ): Flow<LabSearchEvent> {
        val pool = WorkerPoolConfig.forDevice(workers, availableProcessors)
        return if (pool.workerCount <= 1) {
            DefaultLabSearchEngine(timeSource).run(challenge, plan, limits, cancellation)
        } else {
            ParallelLabSearchEngine(pool, timeSource).run(challenge, plan, limits, cancellation)
        }
    }
}
