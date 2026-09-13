package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchCursor
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSearchPlan
import com.wifiauditlab.lab.domain.LimitReason
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchSessionId
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.domain.engine.DefaultSearchMetricsCollector
import com.wifiauditlab.lab.domain.engine.IndexedCandidateSpace
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.LabSearchRunOptions
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
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Parallel engine V2: workers claim dynamic index ranges and resolve candidates
 * via [IndexedCandidateSpace] (base-N jump + odometer advance). No full
 * enumeration of prior candidates; counters stay exact.
 *
 * Pause cancels further claims but lets in-flight ranges finish so resume does
 * not skip or duplicate significant work.
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
        options: LabSearchRunOptions,
    ): Flow<LabSearchEvent> =
        flow {
            emit(LabSearchEvent.Preparing)
            val verifier = challenge.asVerifier()
            val space = plan.searchSpace
            val resume = options.resumeFrom
            val sessionId = resume?.sessionId ?: SearchSessionId.random()
            val priorAttempts = resume?.attemptCount ?: CombinationCount.ZERO
            val priorElapsed = resume?.elapsedActive ?: Duration.ZERO
            val startBucket = resume?.currentBucketIndex ?: 0
            val startIndexInBucket = resume?.nextCandidateIndexInBucket ?: CombinationCount.ZERO

            val collector =
                DefaultSearchMetricsCollector(
                    totalBuckets = plan.totalBuckets,
                    searchSpace = space,
                    initialAttempts = priorAttempts.toLongOrNull() ?: 0L,
                    initialElapsed = priorElapsed,
                    initialBucketIndex = startBucket.coerceAtMost(plan.totalBuckets - 1),
                )
            val wallStart = timeSource.markNow()
            emit(LabSearchEvent.Started(sessionId, plan, space))

            val maxAttempts = limits.maxAttempts
            val stop = MutableStateFlow(false)
            val pauseRequested = MutableStateFlow(false)
            val mutex = Mutex()
            var processed = priorAttempts
            var found: String? = null
            var limitReason: LimitReason? = null
            var paused = false
            var pauseCursor: LabSearchCursor? = null

            fun activeElapsed(): Duration = priorElapsed + wallStart.elapsedNow()

            try {
                coroutineScope {
                    for (bucket in plan.buckets) {
                        if (bucket.index < startBucket) continue
                        if (cancellation.isCancelled || stop.value) break
                        if (options.pause.isPauseRequested) {
                            pauseRequested.value = true
                            paused = true
                            pauseCursor =
                                LabSearchCursor(
                                    sessionId = sessionId,
                                    currentBucketIndex = bucket.index,
                                    nextCandidateIndexInBucket =
                                        if (bucket.index == startBucket) startIndexInBucket else CombinationCount.ZERO,
                                    attemptCount = processed,
                                    elapsedActive = activeElapsed(),
                                )
                            break
                        }
                        val indexed: IndexedCandidateSpace =
                            OdometerIndexedCandidateSpace(bucket.alphabet, bucket.length, plan.seed)
                        val initialStart =
                            if (bucket.index == startBucket) startIndexInBucket else CombinationCount.ZERO
                        val scheduler =
                            DynamicRangeScheduler(
                                totalSize = bucket.searchSpaceSize,
                                chunkSize = DynamicRangeScheduler.chunkSizeFor(limits.batchSize),
                                initialNextStart = initialStart,
                            )
                        val outcomes =
                            (0 until pool.workerCount).map {
                                async {
                                    workerLoop(
                                        indexed = indexed,
                                        scheduler = scheduler,
                                        batchSize = limits.batchSize,
                                        shouldStopClaiming = {
                                            cancellation.isCancelled ||
                                                stop.value ||
                                                pauseRequested.value ||
                                                options.pause.isPauseRequested ||
                                                scheduler.isCancelled()
                                        },
                                        shouldAbortCurrent = { cancellation.isCancelled || stop.value },
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
                                collector.record(processedLong, activeElapsed(), bucket.index)
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
                        if (options.pause.isPauseRequested || pauseRequested.value) {
                            scheduler.cancel()
                            paused = true
                            pauseCursor =
                                LabSearchCursor(
                                    sessionId = sessionId,
                                    currentBucketIndex = bucket.index,
                                    nextCandidateIndexInBucket = scheduler.nextStart(),
                                    attemptCount = processed,
                                    elapsedActive = activeElapsed(),
                                )
                            break
                        }
                        if (limits.maxDuration != null && activeElapsed() >= limits.maxDuration) {
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
                    paused -> emit(LabSearchEvent.Paused(metrics, pauseCursor!!))
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
        shouldStopClaiming: () -> Boolean,
        shouldAbortCurrent: () -> Boolean,
        matches: (String) -> Boolean,
    ): WorkerOutcome {
        var attempts = CombinationCount.ZERO
        var found: String? = null
        while (!shouldStopClaiming()) {
            val range = scheduler.claimNext() ?: break
            if (range.isEmpty) continue
            val iterator =
                indexed.candidatesInRange(range.startInclusive, range.endExclusive).iterator()
            while (iterator.hasNext()) {
                var inBatch = 0
                while (inBatch < batchSize && iterator.hasNext()) {
                    if (shouldAbortCurrent()) {
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
                if (shouldAbortCurrent()) {
                    scheduler.cancel()
                    break
                }
            }
            if (found != null) break
            if (shouldStopClaiming()) {
                scheduler.cancel()
                break
            }
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
        options: LabSearchRunOptions,
    ): Flow<LabSearchEvent> =
        DefaultLabSearchEngine(timeSource).run(challenge, plan, limits, cancellation, options)
}
