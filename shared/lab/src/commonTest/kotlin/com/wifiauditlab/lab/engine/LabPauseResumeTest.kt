package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.InMemoryLabSessionRepository
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabChallengeDefinition
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.LabSessionCheckpoint
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchLimitsSnapshot
import com.wifiauditlab.lab.domain.SearchState
import com.wifiauditlab.lab.domain.engine.LabSearchRunOptions
import com.wifiauditlab.lab.domain.engine.PauseController
import com.wifiauditlab.lab.domain.toRawDecimal
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LabPauseResumeTest {
    private val optimizer = DefaultSearchPlanOptimizer()
    private val strategyId = LengthPrioritizedStrategy.ID

    @Test
    fun pause_emits_paused_not_cancelled() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, "99", seed = 1L)
            val plan = optimizer.optimize(challenge, strategyId)
            val pause = PauseAfterPolls(0)
            val events =
                DefaultLabSearchEngine()
                    .run(
                        challenge,
                        plan,
                        SearchLimits.of(maxAttempts = CombinationCount.of(100), batchSize = 10),
                        NeverCancel,
                        LabSearchRunOptions(pause = pause),
                    ).toList()

            val paused = assertIs<LabSearchEvent.Paused>(events.last())
            assertEquals(CombinationCount.of(10), paused.metrics.attempts)
            assertEquals(CombinationCount.of(10), paused.cursor.attemptCount)
            assertEquals(0, paused.cursor.currentBucketIndex)
            assertEquals(CombinationCount.of(10), paused.cursor.nextCandidateIndexInBucket)
        }

    @Test
    fun resume_continues_without_duplicating_attempts() =
        runTest {
            // "50" is beyond the first batch of 10 so pause wins before CandidateFound.
            // seed=null keeps natural digit order so index == decimal value.
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, "50", seed = null)
            val plan = optimizer.optimize(challenge, strategyId)
            val pause = PauseAfterPolls(0)
            val first =
                DefaultLabSearchEngine()
                    .run(
                        challenge,
                        plan,
                        SearchLimits.of(maxAttempts = CombinationCount.of(100), batchSize = 10),
                        NeverCancel,
                        LabSearchRunOptions(pause = pause),
                    ).toList()
            val paused = assertIs<LabSearchEvent.Paused>(first.last())

            val second =
                DefaultLabSearchEngine()
                    .run(
                        challenge,
                        plan,
                        SearchLimits.of(maxAttempts = CombinationCount.of(100), batchSize = 10),
                        NeverCancel,
                        LabSearchRunOptions(resumeFrom = paused.cursor),
                    ).toList()

            val found = assertIs<LabSearchEvent.CandidateFound>(second.last())
            // "50" is the 51st candidate (index 50); pause after 10, resume finds at attempt 51.
            assertEquals(CombinationCount.of(51), found.metrics.attempts)
            assertEquals("50", found.candidate)
            assertTrue(found.metrics.elapsed >= paused.metrics.elapsed)
        }

    @Test
    fun resume_preserves_session_id_and_strategy_plan() =
        runTest {
            val challenge =
                LabChallenge.withHiddenSecret(
                    Alphabet.DIGITS,
                    com.wifiauditlab.lab.domain.LengthPolicy.exactly(2),
                    seed = 9L,
                )
            val plan = optimizer.optimize(challenge, strategyId)
            val pause = PauseAfterPolls(0)
            val first =
                DefaultLabSearchEngine()
                    .run(
                        challenge,
                        plan,
                        SearchLimits.of(maxAttempts = CombinationCount.of(50), batchSize = 5),
                        NeverCancel,
                        LabSearchRunOptions(pause = pause),
                    ).toList()
            val paused = assertIs<LabSearchEvent.Paused>(first.last())

            val repo = InMemoryLabSessionRepository()
            val checkpoint =
                LabSessionCheckpoint(
                    sessionId = paused.cursor.sessionId,
                    challenge = LabChallengeDefinition.from(challenge),
                    strategyId = plan.strategyId,
                    planSchemaVersion = LabSessionCheckpoint.PLAN_SCHEMA_VERSION,
                    searchSpaceExact = plan.searchSpace.toRawDecimal(),
                    totalBuckets = plan.totalBuckets,
                    limits =
                        SearchLimitsSnapshot.from(
                            SearchLimits.of(maxAttempts = CombinationCount.of(50), batchSize = 5),
                        ),
                    cursor = paused.cursor,
                    metrics = paused.metrics,
                    state = SearchState.Paused,
                    workerCount = 1,
                    engineVersion = LabEngineVersion.CURRENT,
                )
            repo.save(checkpoint)
            val loaded = repo.load()!!
            assertTrue(loaded.matchesPlan(plan))
            assertEquals(strategyId, loaded.strategyId)

            val restoredChallenge = loaded.challenge.toChallenge()
            val restoredPlan = optimizer.optimize(restoredChallenge, loaded.strategyId)
            assertTrue(loaded.matchesPlan(restoredPlan))

            val resumed =
                DefaultLabSearchEngine()
                    .run(
                        restoredChallenge,
                        restoredPlan,
                        loaded.limits.toLimits(),
                        NeverCancel,
                        LabSearchRunOptions(resumeFrom = loaded.cursor),
                    ).toList()
            val started = assertIs<LabSearchEvent.Started>(resumed.first { it is LabSearchEvent.Started })
            assertEquals(paused.cursor.sessionId, started.sessionId)
        }

    @Test
    fun cancel_still_emits_cancelled_not_paused() =
        runTest {
            val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, "99", seed = 1L)
            val plan = optimizer.optimize(challenge, strategyId)
            val events =
                DefaultLabSearchEngine()
                    .run(
                        challenge,
                        plan,
                        SearchLimits.of(maxAttempts = CombinationCount.of(100), batchSize = 10),
                        CancelAfterPolls(0),
                        LabSearchRunOptions(pause = PauseController()),
                    ).toList()
            assertIs<LabSearchEvent.Cancelled>(events.last())
        }
}
