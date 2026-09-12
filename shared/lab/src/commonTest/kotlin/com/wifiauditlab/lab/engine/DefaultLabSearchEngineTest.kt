package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LengthPolicy
import com.wifiauditlab.lab.domain.LimitReason
import com.wifiauditlab.lab.domain.SearchLimits
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class DefaultLabSearchEngineTest {
    private val optimizer = DefaultSearchPlanOptimizer()
    private val strategyId = BruteForceSearchStrategy.ID

    private fun plan(challenge: LabChallenge) = optimizer.optimize(challenge, strategyId)

    @Test
    fun finds_the_hidden_secret_and_reveals_it() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "07")
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(200)),
                    NeverCancel,
                ).toList()

            val found = assertIs<LabSearchEvent.CandidateFound>(events.last())
            assertEquals("07", found.candidate)
        }

    @Test
    fun stops_with_attempt_limit_when_secret_is_out_of_reach() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "99")
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(10), batchSize = 4),
                    NeverCancel,
                ).toList()

            val limit = assertIs<LabSearchEvent.LimitReached>(events.last())
            assertEquals(LimitReason.Attempts, limit.reason)
            assertEquals(CombinationCount.of(10), limit.metrics.attempts)
        }

    @Test
    fun secret_found_exactly_at_the_attempt_limit_is_still_found() =
        runTest {
            // "09" is the 10th candidate (index 9) in digit-order enumeration.
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "09")
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(10), batchSize = 4),
                    NeverCancel,
                ).toList()

            assertIs<LabSearchEvent.CandidateFound>(events.last())
        }

    @Test
    fun secret_one_past_the_limit_is_reported_as_limit_reached() =
        runTest {
            // "10" is the 11th candidate (index 10); with a cap of 10 it must not be found.
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "10")
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(10), batchSize = 4),
                    NeverCancel,
                ).toList()

            assertIs<LabSearchEvent.LimitReached>(events.last())
        }

    @Test
    fun stops_with_duration_limit() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "99")
            val engine = DefaultLabSearchEngine(timeSource = AutoAdvancingTimeSource(stepMillis = 10))

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxDuration = 25.milliseconds, batchSize = 1),
                    NeverCancel,
                ).toList()

            val limit = assertIs<LabSearchEvent.LimitReached>(events.last())
            assertEquals(LimitReason.Duration, limit.reason)
        }

    @Test
    fun cancel_while_running_emits_cancelled_almost_immediately() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "99")
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(100), batchSize = 10),
                    // cancelled at the very first batch boundary
                    CancelAfterPolls(0),
                ).toList()

            val cancelled = assertIs<LabSearchEvent.Cancelled>(events.last())
            // Only the first batch (10 candidates) was processed before stopping.
            assertEquals(CombinationCount.of(10), cancelled.metrics.attempts)
        }

    @Test
    fun cancel_across_bucket_transition() =
        runTest {
            val challenge =
                LabChallenge.withHiddenSecret(
                    Alphabet.of("ab"),
                    LengthPolicy(1, 3),
                    seed = 7,
                )
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(1_000), batchSize = 2),
                    CancelAfterPolls(2),
                ).toList()

            assertIs<LabSearchEvent.Cancelled>(events.last())
        }

    @Test
    fun completes_without_finding_when_secret_is_outside_the_plan() =
        runTest {
            val verifierChallenge = LabChallenge.withKnownSecret(Alphabet.of("ab"), secret = "ab")
            // Plan only covers length-1 candidates, so "ab" can never be reached.
            val planChallenge = LabChallenge.withKnownSecret(Alphabet.of("ab"), secret = "a")
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    verifierChallenge,
                    plan(planChallenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(100)),
                    NeverCancel,
                ).toList()

            val completed = assertIs<LabSearchEvent.Completed>(events.last())
            // "a", "b"
            assertEquals(CombinationCount.of(2), completed.metrics.attempts)
        }

    @Test
    fun emits_preparing_and_started_before_terminal_event() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "07")
            val engine = DefaultLabSearchEngine()

            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(maxAttempts = CombinationCount.of(200)),
                    NeverCancel,
                ).toList()

            assertIs<LabSearchEvent.Preparing>(events.first())
            assertTrue(events.any { it is LabSearchEvent.Started })
        }

    @Test
    fun finds_first_middle_and_last_candidates() =
        runTest {
            val engine = DefaultLabSearchEngine()
            listOf("00", "50", "99").forEach { secret ->
                val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret)
                val events =
                    engine.run(
                        challenge,
                        plan(challenge),
                        SearchLimits.of(maxAttempts = CombinationCount.of(200), batchSize = 7),
                        NeverCancel,
                    ).toList()
                val found = assertIs<LabSearchEvent.CandidateFound>(events.last())
                assertEquals(secret, found.candidate)
            }
        }

    @Test
    fun exhausted_space_attempt_count_equals_search_space() =
        runTest {
            val verifier = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "000")
            val planned = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "00")
            val events =
                DefaultLabSearchEngine().run(
                    verifier,
                    plan(planned),
                    SearchLimits.of(maxAttempts = CombinationCount.of(10_000), batchSize = 16),
                    NeverCancel,
                ).toList()
            val completed = assertIs<LabSearchEvent.Completed>(events.last())
            assertEquals(plan(planned).searchSpace, completed.metrics.attempts)
        }

    @Test
    fun two_seeded_runs_are_deterministic() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "42", seed = 11L)
            val engine = DefaultLabSearchEngine()
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(200), batchSize = 8)
            val first = engine.run(challenge, plan(challenge), limits, NeverCancel).toList()
            val second = engine.run(challenge, plan(challenge), limits, NeverCancel).toList()
            val a = assertIs<LabSearchEvent.CandidateFound>(first.last())
            val b = assertIs<LabSearchEvent.CandidateFound>(second.last())
            assertEquals(a.candidate, b.candidate)
            assertEquals(a.metrics.attempts, b.metrics.attempts)
        }

    @Test
    fun emits_aggregated_progress_not_per_candidate() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "99")
            val engine = DefaultLabSearchEngine(timeSource = AutoAdvancingTimeSource(stepMillis = 20))
            val events =
                engine.run(
                    challenge,
                    plan(challenge),
                    SearchLimits.of(
                        maxAttempts = CombinationCount.of(200),
                        progressInterval = 15.milliseconds,
                        batchSize = 5,
                    ),
                    NeverCancel,
                ).toList()
            val progress = events.filterIsInstance<LabSearchEvent.Progress>()
            assertTrue(progress.isNotEmpty())
            assertTrue(progress.size < 100)
        }

    @Test
    fun generation_failure_emits_failed_with_metrics() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, secret = "00")
            val throwing =
                object : com.wifiauditlab.lab.domain.engine.CandidateSource {
                    override val size = CombinationCount.ONE

                    override fun candidates(): Sequence<String> = sequence { error("generation exploded") }
                }
            val plan =
                com.wifiauditlab.lab.domain.LabSearchPlan(
                    strategyId,
                    listOf(
                        com.wifiauditlab.lab.domain.SearchBucket(
                            index = 0,
                            length = 1,
                            alphabet = Alphabet.DIGITS,
                            expectedRelativeWeight = 1.0,
                            searchSpaceSize = CombinationCount.of(10),
                            sourceOverride = throwing,
                        ),
                    ),
                    seed = null,
                )
            val events =
                DefaultLabSearchEngine().run(
                    challenge,
                    plan,
                    SearchLimits.of(maxAttempts = CombinationCount.of(10)),
                    NeverCancel,
                ).toList()
            val failed = assertIs<LabSearchEvent.Failed>(events.last())
            assertTrue(failed.message.contains("generation exploded"))
            assertTrue(failed.metrics != null)
        }
}
