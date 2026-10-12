package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.engine.CancellationSignal
import com.wifiauditlab.lab.engine.DefaultSearchPlanOptimizer
import com.wifiauditlab.lab.engine.UniformBaselineStrategy
import com.wifiauditlab.lab.engine.WorkerAwareLabSearchEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Didactic black-box: Search Engine discovers an unknown secret via CandidateVerifier only.
 */
class BlackBoxChallengeVerifierTest {
    @Test
    fun search_engine_cannot_access_hidden_secret_via_public_api() {
        val secret = "black-box-secret-42"
        val verifier = BlackBoxChallengeVerifier.encapsulate(secret)
        val challenge =
            LabChallenge.withBlackBoxVerifier(
                policy = LabSecretPolicy(Alphabet.DIGITS, LengthPolicy.exactly(secret.length)),
                verifier = verifier,
                seed = 1L,
            )

        assertFalse(verifier.toString().contains(secret))
        assertFalse(challenge.toString().contains(secret))
        assertNull(challenge.secretLength)
        assertEquals("BlackBoxChallengeVerifier", verifier.toString())
        assertEquals(secret.length, challenge.lengthPolicy.minLength)
        assertTrue(challenge.alphabet.size > 0)
    }

    @Test
    fun incorrect_candidate_returns_false_correct_returns_true() {
        val secret = "hunter2"
        val verifier = BlackBoxChallengeVerifier.encapsulate(secret)
        assertFalse(verifier.verify("wrong"))
        assertFalse(verifier.verify(""))
        assertTrue(verifier.verify(secret))
    }

    @Test
    fun engine_finds_black_box_secret_without_knowing_it() =
        runTest {
            // Avoid short secrets that appear as substrings in class names (e.g. "ab" in "lab").
            val secret = "zz"
            val verifier = BlackBoxChallengeVerifier.encapsulate(secret)
            val policy = LabSecretPolicy(Alphabet.LOWERCASE, LengthPolicy.exactly(2))
            val challenge =
                LabChallenge.withBlackBoxVerifier(
                    policy = policy,
                    verifier = verifier,
                    seed = 9L,
                )
            val plan = DefaultSearchPlanOptimizer().optimize(challenge, UniformBaselineStrategy.ID)
            val engine = WorkerAwareLabSearchEngine(availableProcessors = 2)
            engine.workers = 1

            val events =
                engine
                    .run(
                        challenge,
                        plan,
                        SearchLimits(
                            maxDuration = null,
                            maxAttempts = CombinationCount.of(10_000),
                            progressInterval = 50.milliseconds,
                            batchSize = 256,
                        ),
                        NeverCancel,
                    ).toList()

            val found = events.filterIsInstance<LabSearchEvent.CandidateFound>().single()
            assertEquals(secret, found.candidate)
            assertFalse(engine.toString().contains(secret))
        }

    private object NeverCancel : CancellationSignal {
        override val isCancelled: Boolean = false
    }
}
