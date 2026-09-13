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
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.LabSearchRunOptions
import com.wifiauditlab.lab.domain.engine.OdometerIndexedCandidateSpace
import com.wifiauditlab.lab.domain.engine.asVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Deterministic, single-worker search engine — the correctness baseline that must
 * exist and be tested before any parallelism is introduced.
 *
 * Guarantees:
 *  - lazy candidate consumption (memory O(candidate length));
 *  - cooperative cancellation checked at most every batch, so `Stop` is near-instant;
 *  - cooperative pause emitting [LabSearchEvent.Paused] with an exact resume cursor;
 *  - exact limit semantics (a secret at the last allowed attempt is still Found);
 *  - metrics aggregated per batch, never per candidate.
 *
 * A [TimeSource] is injected so time-based behavior is deterministic under test.
 */
class DefaultLabSearchEngine(
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
            require(startBucket in plan.buckets.indices || (startBucket == plan.totalBuckets && startIndexInBucket.isZero)) {
                "resume bucket index $startBucket out of range for ${plan.totalBuckets} buckets"
            }

            val initialAttemptsLong = priorAttempts.toLongOrNull() ?: 0L
            val collector =
                DefaultSearchMetricsCollector(
                    totalBuckets = plan.totalBuckets,
                    searchSpace = space,
                    initialAttempts = initialAttemptsLong,
                    initialElapsed = priorElapsed,
                    initialBucketIndex = startBucket.coerceAtMost(plan.totalBuckets - 1),
                )
            val wallStart = timeSource.markNow()
            emit(LabSearchEvent.Started(sessionId, plan, space))

            try {
                val maxAttempts: CombinationCount? = limits.maxAttempts
                var processed = priorAttempts
                var found: String? = null
                var limitReason: LimitReason? = null
                var cancelled = false
                var paused = false
                var pauseCursor: LabSearchCursor? = null
                var lastProgress = Duration.ZERO

                fun activeElapsed(): Duration = priorElapsed + wallStart.elapsedNow()

                run runLoop@{
                    for (bucket in plan.buckets) {
                        if (bucket.index < startBucket) continue
                        val fromIndex =
                            if (bucket.index == startBucket) startIndexInBucket else CombinationCount.ZERO
                        val iterator =
                            if (bucket.sourceOverride != null) {
                                val skip =
                                    fromIndex.toLongOrNull()
                                        ?: error("resume skip requires index that fits in Long for sourceOverride")
                                bucket.candidateSource(plan.seed).candidates().drop(skip.toInt()).iterator()
                            } else {
                                val indexed =
                                    OdometerIndexedCandidateSpace(bucket.alphabet, bucket.length, plan.seed)
                                require(fromIndex <= indexed.size) {
                                    "resume index $fromIndex exceeds bucket size ${indexed.size}"
                                }
                                indexed.candidatesInRange(fromIndex, indexed.size).iterator()
                            }
                        var indexInBucket = fromIndex
                        while (iterator.hasNext()) {
                            var inBatch = 0
                            batch@ while (inBatch < limits.batchSize && iterator.hasNext()) {
                                if (maxAttempts != null && processed >= maxAttempts) {
                                    limitReason = LimitReason.Attempts
                                    break@batch
                                }
                                val candidate = iterator.next()
                                processed += CombinationCount.ONE
                                indexInBucket += CombinationCount.ONE
                                inBatch++
                                if (verifier.verify(candidate)) {
                                    found = candidate
                                    break@batch
                                }
                            }

                            val elapsed = activeElapsed()
                            processed.toLongOrNull()?.let { collector.record(it, elapsed, bucket.index) }

                            if (found != null) return@runLoop
                            if (limitReason != null) return@runLoop
                            if (cancellation.isCancelled) {
                                cancelled = true
                                return@runLoop
                            }
                            if (options.pause.isPauseRequested) {
                                paused = true
                                pauseCursor =
                                    LabSearchCursor(
                                        sessionId = sessionId,
                                        currentBucketIndex = bucket.index,
                                        nextCandidateIndexInBucket = indexInBucket,
                                        attemptCount = processed,
                                        elapsedActive = elapsed,
                                    )
                                return@runLoop
                            }
                            if (limits.maxDuration != null && elapsed >= limits.maxDuration) {
                                limitReason = LimitReason.Duration
                                return@runLoop
                            }
                            if (elapsed - lastProgress >= limits.progressInterval) {
                                emit(LabSearchEvent.Progress(collector.snapshot()))
                                lastProgress = elapsed
                            }
                        }
                    }
                }

                val metrics = collector.snapshot()
                when {
                    found != null -> emit(LabSearchEvent.CandidateFound(found!!, metrics))
                    cancelled -> emit(LabSearchEvent.Cancelled(metrics))
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
}
