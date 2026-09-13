package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class LabSessionCheckpointTest {
    @Test
    fun challenge_definition_round_trips_secret_via_seed_without_plaintext() {
        val original =
            LabChallenge.withHiddenSecret(
                alphabet = Alphabet.DIGITS,
                lengthPolicy = LengthPolicy.exactly(3),
                seed = 42L,
            )
        val definition = LabChallengeDefinition.from(original)
        val restored = definition.toChallenge()
        assertEquals(original.id, restored.id)
        assertEquals(original.seed, restored.seed)
        assertFalse(definition.toString().contains("secret"))
        // Same seed regenerates the same hidden secret.
        val probe = (0..999).map { it.toString().padStart(3, '0') }.first { original.isSolution(it) }
        assertTrue(restored.isSolution(probe))
    }

    @Test
    fun definition_requires_seed() {
        val challenge = LabChallenge.withKnownSecret(Alphabet.DIGITS, "12", seed = null)
        assertFailsWith<IllegalArgumentException> { LabChallengeDefinition.from(challenge) }
    }

    @Test
    fun in_memory_repository_persists_and_clears() =
        kotlinx.coroutines.test.runTest {
            val repo = InMemoryLabSessionRepository()
            val challenge =
                LabChallenge.withHiddenSecret(
                    Alphabet.DIGITS,
                    LengthPolicy.exactly(2),
                    seed = 7L,
                )
            val definition = LabChallengeDefinition.from(challenge)
            val session = SearchSessionId("s1")
            val metrics = SearchMetrics.initial(1, CombinationCount.of(100))
            val checkpoint =
                LabSessionCheckpoint(
                    sessionId = session,
                    challenge = definition,
                    strategyId = SearchStrategyId("length-prioritized"),
                    planSchemaVersion = LabSessionCheckpoint.PLAN_SCHEMA_VERSION,
                    searchSpaceExact = "100",
                    totalBuckets = 1,
                    limits =
                        SearchLimitsSnapshot(
                            maxDurationMillis = 30_000,
                            maxAttemptsExact = "1000",
                            progressIntervalMillis = 250,
                            batchSize = 64,
                        ),
                    cursor =
                        LabSearchCursor(
                            sessionId = session,
                            currentBucketIndex = 0,
                            nextCandidateIndexInBucket = CombinationCount.of(10),
                            attemptCount = CombinationCount.of(10),
                            elapsedActive = 5.milliseconds,
                        ),
                    metrics = metrics.copy(attempts = CombinationCount.of(10), elapsed = 5.milliseconds),
                    state = SearchState.Paused,
                    workerCount = 1,
                    engineVersion = LabEngineVersion.CURRENT,
                )
            repo.save(checkpoint)
            assertEquals(checkpoint, repo.load())
            repo.clear()
            assertEquals(null, repo.load())
        }

    @Test
    fun limits_snapshot_round_trip() {
        val limits =
            SearchLimits.of(
                maxDuration = 12.milliseconds,
                maxAttempts = CombinationCount.of(99),
                batchSize = 32,
            )
        val restored = SearchLimitsSnapshot.from(limits).toLimits()
        assertEquals(limits.maxAttempts, restored.maxAttempts)
        assertEquals(limits.batchSize, restored.batchSize)
        assertEquals(limits.maxDuration, restored.maxDuration)
    }
}
