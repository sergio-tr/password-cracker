package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LimitReason
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchSessionId
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.DefaultSearchMetricsCollector
import com.wifiauditlab.lab.domain.engine.IndexedCandidateSpace
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.OdometerIndexedCandidateSpace
import com.wifiauditlab.lab.domain.engine.asVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Parallel engine V2: workers claim dynamic index ranges and resolve candidates
 * via [IndexedCandidateSpace] (base-N jump + odometer advance). No full
 * enumeration of prior candidates; counters stay exact.
 */
class IndexedParallelLabSearchEngine(
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
            emit(LabSearchEvent.Preparing)
            val verifier = challenge.asVerifier()
            val space = plan.searchSpace
            val sessionId = SearchSessionId.random()
            val collector = DefaultSearchMetricsCollector(plan.totalBuckets, space)
            val start = timeSource.markNow()
            emit(LabSearchEvent.Started(sessionId, plan, space))

            val maxAttempts = limits.maxAttempts
            val stop = MutableStateFlow(false)
            val mutex = Mutex()
            var processed = CombinationCount.ZERO
            var found: String? = null
            var limitReason: LimitReason? = null

            try {
                coroutineScope {
                    for (bucket in plan.buckets) {
                        if (cancellation.isCancelled || stop.value) break
                        val indexed: IndexedCandidateSpace =
                            OdometerIndexedCandidateSpace(bucket.alphabet, bucket.length, plan.seed)
                        val scheduler =
                            DynamicRangeScheduler(
                                totalSize = bucket.searchSpaceSize,
                                chunkSize = DynamicRangeScheduler.chunkSizeFor(limits.batchSize),
                            )
                        val outcomes =
                            (0 until pool.workerCount).map {
                                async {
                                    workerLoop(
                                        indexed = indexed,
                                        scheduler = scheduler,
                                        batchSize = limits.batchSize,
                                        shouldStop = {
                                            cancellation.isCancelled || stop.value || scheduler.isCancelled()
                                        },
                                        matches = { candidate -> verifier.verify(candidate) },
                                    )
                                }
                            }.awaitAll()

                        mutex.withLock {
                            for (outcome in outcomes) {
                                processed += outcome.attempts
                                if (found == null && outcome.found != null) {
                                    found = outcome.found
                                }
                            }
                            if (maxAttempts != null && processed >= maxAttempts && found == null) {
                                limitReason = LimitReason.Attempts
                            }
                            val processedLong = processed.toLongOrNull()
                            if (processedLong != null) {
                                collector.record(processedLong, start.elapsedNow(), bucket.index)
                            }
                        }

                        if (found != null) {
                            stop.value = true
                            scheduler.cancel()
                            break
                        }
                        if (limitReason != null) {
                            scheduler.cancel()
                            break
                        }
                        if (cancellation.isCancelled) {
                            scheduler.cancel()
                            break
                        }
                        if (limits.maxDuration != null && start.elapsedNow() >= limits.maxDuration) {
                            limitReason = LimitReason.Duration
                            scheduler.cancel()
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

    private suspend fun workerLoop(
        indexed: IndexedCandidateSpace,
        scheduler: DynamicRangeScheduler,
        batchSize: Int,
        shouldStop: () -> Boolean,
        matches: (String) -> Boolean,
    ): WorkerOutcome {
        var attempts = CombinationCount.ZERO
        var found: String? = null
        while (!shouldStop()) {
            val range = scheduler.claimNext() ?: break
            if (range.isEmpty) continue
            val iterator =
                indexed.candidatesInRange(range.startInclusive, range.endExclusive).iterator()
            while (iterator.hasNext()) {
                var inBatch = 0
                while (inBatch < batchSize && iterator.hasNext()) {
                    if (shouldStop()) {
                        scheduler.cancel()
                        return WorkerOutcome(attempts, found)
                    }
                    val candidate = iterator.next()
                    attempts += CombinationCount.ONE
                    inBatch++
                    if (matches(candidate)) {
                        found = candidate
                        scheduler.cancel()
                        return WorkerOutcome(attempts, found)
                    }
                }
                if (shouldStop()) {
                    scheduler.cancel()
                    break
                }
            }
            if (found != null) break
        }
        return WorkerOutcome(attempts, found)
    }

    private data class WorkerOutcome(
        val attempts: CombinationCount,
        val found: String?,
    )
}

/**
 * Sequential engine that uses [IndexedCandidateSpace] for each bucket. Useful as
 * a fairness/perf comparison against [DefaultLabSearchEngine]; not the default.
 */
class IndexedSequentialLabSearchEngine(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : LabSearchEngine {
    override fun run(
        challenge: LabChallenge,
        plan: LabSearchPlan,
        limits: SearchLimits,
        cancellation: CancellationSignal,
    ): Flow<LabSearchEvent> =
        flow {
            emit(LabSearchEvent.Preparing)
            val verifier = challenge.asVerifier()
            val space = plan.searchSpace
            val sessionId = SearchSessionId.random()
            val collector = DefaultSearchMetricsCollector(plan.totalBuckets, space)
            val start = timeSource.markNow()
            emit(LabSearchEvent.Started(sessionId, plan, space))

            try {
                val maxAttempts = limits.maxAttempts
                var processed = CombinationCount.ZERO
                var found: String? = null
                var limitReason: LimitReason? = null
                var cancelled = false

                run runLoop@{
                    for (bucket in plan.buckets) {
                        val indexed = OdometerIndexedCandidateSpace(bucket.alphabet, bucket.length, plan.seed)
                        val iterator =
                            indexed.candidatesInRange(CombinationCount.ZERO, indexed.size).iterator()
                        while (iterator.hasNext()) {
                            var inBatch = 0
                            batch@ while (inBatch < limits.batchSize && iterator.hasNext()) {
                                if (maxAttempts != null && processed >= maxAttempts) {
                                    limitReason = LimitReason.Attempts
                                    break@batch
                                }
                                val candidate = iterator.next()
                                processed += CombinationCount.ONE
                                inBatch++
                                if (verifier.verify(candidate)) {
                                    found = candidate
                                    break@batch
                                }
                            }
                            val elapsed = start.elapsedNow()
                            processed.toLongOrNull()?.let { collector.record(it, elapsed, bucket.index) }
                            if (found != null) return@runLoop
                            if (limitReason != null) return@runLoop
                            if (cancellation.isCancelled) {
                                cancelled = true
                                return@runLoop
                            }
                            if (limits.maxDuration != null && elapsed >= limits.maxDuration) {
                                limitReason = LimitReason.Duration
                                return@runLoop
                            }
                        }
                    }
                }

                val metrics = collector.snapshot()
                when {
                    found != null -> emit(LabSearchEvent.CandidateFound(found!!, metrics))
                    cancelled -> emit(LabSearchEvent.Cancelled(metrics))
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
}
