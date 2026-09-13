package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.SearchLimits
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Correctness + micro-benchmark evidence for Search Engine V2.
 *
 * Absolute timings vary by machine; the structured table is emitted to stdout
 * for docs. V2 must match baseline findings and must not be slower than V1
 * parallel on this synthetic workload when kept as the multi-worker default.
 */
class SearchEngineV2BenchmarkTest {
    private val optimizer = DefaultSearchPlanOptimizer()

    private fun plan(challenge: LabChallenge) = optimizer.optimize(challenge, LengthPrioritizedStrategy.ID)

    @Test
    fun indexed_parallel_finds_same_secret_as_baseline() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "42")
            val planned = plan(challenge)
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(200), batchSize = 8)
            val baseline =
                DefaultLabSearchEngine().run(challenge, planned, limits, NeverCancel).toList().last()
            val v2 =
                IndexedParallelLabSearchEngine(WorkerPoolConfig(4))
                    .run(challenge, planned, limits, NeverCancel)
                    .toList()
                    .last()
            assertEquals(
                assertIs<LabSearchEvent.CandidateFound>(baseline).candidate,
                assertIs<LabSearchEvent.CandidateFound>(v2).candidate,
            )
        }

    @Test
    fun exhausted_indexed_parallel_attempts_equal_space() =
        runTest {
            val verifier = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "000")
            val planned = plan(LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "00"))
            val events =
                IndexedParallelLabSearchEngine(WorkerPoolConfig(4)).run(
                    verifier,
                    planned,
                    SearchLimits.of(maxAttempts = CombinationCount.of(10_000), batchSize = 16),
                    NeverCancel,
                ).toList()
            val completed = assertIs<LabSearchEvent.Completed>(events.last())
            assertEquals(planned.searchSpace, completed.metrics.attempts)
        }

    @Test
    fun worker_aware_defaults_to_v2_for_parallel() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "07")
            val planned = plan(challenge)
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(200))
            val facade = WorkerAwareLabSearchEngine(availableProcessors = 4)
            assertEquals(LabParallelEngineVersion.V2, facade.parallelVersion)
            facade.workers = 2
            val found =
                assertIs<LabSearchEvent.CandidateFound>(
                    facade.run(challenge, planned, limits, NeverCancel).toList().last(),
                )
            assertEquals("07", found.candidate)
        }

    @Test
    fun recommended_worker_count_uses_throughput_and_cpu_clamp() {
        assertEquals(1, WorkerPoolConfig.recommendedWorkerCount(8, calibratedAttemptsPerSecond = 100.0))
        assertEquals(8, WorkerPoolConfig.recommendedWorkerCount(8, calibratedAttemptsPerSecond = 80_000.0))
        assertEquals(4, WorkerPoolConfig.recommendedWorkerCount(8, calibratedAttemptsPerSecond = 10_000.0))
        assertEquals(3, WorkerPoolConfig.recommendedWorkerCount(8, calibratedAttemptsPerSecond = 80_000.0, override = 3))
        assertEquals(2, WorkerPoolConfig.recommendedWorkerCount(4, calibratedAttemptsPerSecond = null))
    }

    @Test
    fun micro_benchmark_table_baseline_vs_indexed_vs_parallel() =
        runTest {
            // Exhaustive scan over a modest space so throughput is comparable.
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "999")
            val planned = plan(LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "99"))
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(10_000), batchSize = 32)
            val timeSource = TimeSource.Monotonic

            data class Row(val label: String, val millis: Long, val attempts: Long)

            suspend fun measure(
                label: String,
                block: suspend () -> LabSearchEvent,
            ): Row {
                val mark = timeSource.markNow()
                val terminal = block()
                val millis = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
                val attempts =
                    when (terminal) {
                        is LabSearchEvent.Completed -> terminal.metrics.attempts.toLongOrNull() ?: 0L
                        is LabSearchEvent.CandidateFound -> terminal.metrics.attempts.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                return Row(label, millis, attempts)
            }

            val rows =
                listOf(
                    measure("sequential-baseline") {
                        DefaultLabSearchEngine().run(challenge, planned, limits, NeverCancel).toList().last()
                    },
                    measure("indexed-sequential") {
                        IndexedSequentialLabSearchEngine().run(challenge, planned, limits, NeverCancel).toList().last()
                    },
                    measure("parallel-v1") {
                        ParallelLabSearchEngine(WorkerPoolConfig(4))
                            .run(challenge, planned, limits, NeverCancel)
                            .toList()
                            .last()
                    },
                    measure("parallel-v2-indexed") {
                        IndexedParallelLabSearchEngine(WorkerPoolConfig(4))
                            .run(challenge, planned, limits, NeverCancel)
                            .toList()
                            .last()
                    },
                )

            rows.forEach { row ->
                assertEquals(planned.searchSpace.toLongOrNull(), row.attempts)
            }

            val v1 = rows.first { it.label == "parallel-v1" }
            val v2 = rows.first { it.label == "parallel-v2-indexed" }
            // Keep V2 as default only when it is not clearly worse than V1 on this workload.
            assertTrue(
                v2.millis <= v1.millis * 2 + 50,
                "V2 (${v2.millis}ms) unexpectedly much slower than V1 (${v1.millis}ms)",
            )

            println("FASE24_ENGINE_BENCH")
            println("| engine | duration_ms | attempts |")
            println("| --- | ---: | ---: |")
            rows.forEach { row ->
                println("| ${row.label} | ${row.millis} | ${row.attempts} |")
            }
        }
}
