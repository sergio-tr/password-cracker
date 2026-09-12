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

class ParallelLabSearchEngineTest {
    private val optimizer = DefaultSearchPlanOptimizer()

    private fun plan(challenge: LabChallenge) = optimizer.optimize(challenge, LengthPrioritizedStrategy.ID)

    @Test
    fun partitions_cover_the_space_without_gaps_or_overlap() {
        val ranges = partitionIndexSpace(10, 4)
        assertEquals(10, ranges.sumOf { it.count })
        assertEquals(0, ranges.first().start)
        assertEquals(ranges.size, ranges.map { it.start }.distinct().size)
    }

    @Test
    fun two_workers_find_the_same_secret_as_the_baseline() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "42")
            val planned = plan(challenge)
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(200), batchSize = 8)
            val baseline =
                DefaultLabSearchEngine().run(challenge, planned, limits, NeverCancel).toList()
                    .last()
            val parallel =
                ParallelLabSearchEngine(WorkerPoolConfig(2)).run(challenge, planned, limits, NeverCancel).toList()
                    .last()
            val foundBaseline = assertIs<LabSearchEvent.CandidateFound>(baseline)
            val foundParallel = assertIs<LabSearchEvent.CandidateFound>(parallel)
            assertEquals(foundBaseline.candidate, foundParallel.candidate)
            assertTrue(foundParallel.metrics.attempts <= planned.searchSpace)
        }

    @Test
    fun exhausted_parallel_run_does_not_exceed_the_space() =
        runTest {
            val verifier = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "000")
            val planned = plan(LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "00"))
            val events =
                ParallelLabSearchEngine(WorkerPoolConfig(4)).run(
                    verifier,
                    planned,
                    SearchLimits.of(maxAttempts = CombinationCount.of(10_000), batchSize = 16),
                    NeverCancel,
                ).toList()
            val completed = assertIs<LabSearchEvent.Completed>(events.last())
            assertEquals(planned.searchSpace, completed.metrics.attempts)
        }

    @Test
    fun device_cap_clamps_requested_workers() {
        val pool = WorkerPoolConfig.forDevice(requested = 16, availableProcessors = 2)
        assertEquals(2, pool.workerCount)
        assertEquals(1, WorkerPoolConfig(1).workerCount)
    }

    @Test
    fun worker_aware_engine_keeps_single_worker_as_reference() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "07")
            val planned = plan(challenge)
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(200))
            val facade = WorkerAwareLabSearchEngine(availableProcessors = 4)
            facade.workers = 1
            val one = facade.run(challenge, planned, limits, NeverCancel).toList().last()
            facade.workers = 2
            val two = facade.run(challenge, planned, limits, NeverCancel).toList().last()
            assertEquals(
                assertIs<LabSearchEvent.CandidateFound>(one).candidate,
                assertIs<LabSearchEvent.CandidateFound>(two).candidate,
            )
        }
}
