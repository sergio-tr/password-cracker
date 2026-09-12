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
                val maxAttempts: Long? = limits.maxAttempts?.toLongOrNull()
                var processed = 0L
                var found: String? = null
                var limitReason: LimitReason? = null
                var cancelled = false
                var lastProgress = Duration.ZERO

                run runLoop@{
                    for (bucket in plan.buckets) {
                        val iterator = bucket.candidateSource(plan.seed).candidates().iterator()
                        while (iterator.hasNext()) {
                            var inBatch = 0
                            batch@ while (inBatch < limits.batchSize && iterator.hasNext()) {
                                if (maxAttempts != null && processed >= maxAttempts) {
                                    limitReason = LimitReason.Attempts
                                    break@batch
                                }
                                val candidate = iterator.next()
                                processed++
                                inBatch++
                                if (verifier.verify(candidate)) {
                                    found = candidate
                                    break@batch
                                }
                            }

                            val elapsed = start.elapsedNow()
                            collector.record(processed, elapsed, bucket.index)

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
