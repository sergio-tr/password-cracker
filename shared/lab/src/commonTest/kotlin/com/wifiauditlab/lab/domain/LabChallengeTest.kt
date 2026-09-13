package com.wifiauditlab.lab.domain

import com.wifiauditlab.core.math.CombinationCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabChallengeTest {
    @Test
    fun policy_describes_the_declared_search_space() {
        val policy = LabSecretPolicy(Alphabet.DIGITS, LengthPolicy(1, 2))
        assertEquals(CombinationCount.of(10 + 100), policy.searchSpace)
        assertEquals("LabSecretPolicy(alphabet=Alphabet(size=10), length=LengthPolicy(minLength=1, maxLength=2))", policy.toString())
    }

    @Test
    fun known_secret_is_never_leaked_by_toString() {
        val challenge =
            LabChallenge.withKnownSecret(
                alphabet = Alphabet.LOWER_ALPHANUMERIC,
                secret = "hunter2",
            )
        assertIs<LabChallengeId>(challenge.id)
        assertEquals(Alphabet.LOWER_ALPHANUMERIC, challenge.policy.alphabet)
        assertFalse(challenge.toString().contains("hunter2"))
        assertFalse(challenge.policy.toString().contains("hunter2"))
        assertTrue(challenge.isSolution("hunter2"))
        assertFalse(challenge.isSolution("0000000"))
    }

    @Test
    fun hidden_secret_is_deterministic_when_seeded() {
        val first =
            LabChallenge.withHiddenSecret(
                alphabet = Alphabet.DIGITS,
                lengthPolicy = LengthPolicy.exactly(3),
                seed = 42L,
            )
        val second =
            LabChallenge.withHiddenSecret(
                alphabet = Alphabet.DIGITS,
                lengthPolicy = LengthPolicy.exactly(3),
                seed = 42L,
            )
        assertTrue(first.isDeterministic)
        assertEquals(first.secretLength, second.secretLength)
        assertTrue(first.isSolution((0..999).map { it.toString().padStart(3, '0') }.first { first.isSolution(it) }))
        assertTrue(second.isSolution((0..999).map { it.toString().padStart(3, '0') }.first { first.isSolution(it) }))
    }

    @Test
    fun encapsulated_verifier_keeps_blind_policy() {
        val challenge =
            LabChallenge.withEncapsulatedVerifier(
                policy = LabSecretPolicy(Alphabet.DIGITS, LengthPolicy(1, 6)),
                verifier = EncapsulatedPasswordVerifier.encapsulate("hunter2"),
                seed = 3L,
            )
        assertNull(challenge.secretLength)
        assertEquals(6, challenge.lengthPolicy.maxLength)
        assertTrue(challenge.isSolution("hunter2"))
        assertFalse(challenge.toString().contains("hunter2"))
    }

    @Test
    fun search_result_maps_only_terminal_events() {
        val session = SearchSessionId("session-1")
        val metrics = SearchMetrics.initial(1, CombinationCount.of(10))
        assertEquals(
            SearchOutcome.Found,
            LabSearchResult.fromTerminal(session, LabSearchEvent.CandidateFound("07", metrics))?.outcome,
        )
        assertEquals(
            SearchOutcome.NotFound,
            LabSearchResult.fromTerminal(session, LabSearchEvent.Completed(metrics))?.outcome,
        )
        assertEquals(
            SearchOutcome.LimitReached,
            LabSearchResult.fromTerminal(session, LabSearchEvent.LimitReached(LimitReason.Attempts, metrics))?.outcome,
        )
        assertEquals(
            SearchOutcome.Cancelled,
            LabSearchResult.fromTerminal(session, LabSearchEvent.Cancelled(metrics))?.outcome,
        )
        assertEquals(
            SearchOutcome.Failed,
            LabSearchResult.fromTerminal(session, LabSearchEvent.Failed("boom", null))?.outcome,
        )
        assertNull(LabSearchResult.fromTerminal(session, LabSearchEvent.Preparing))
    }

    @Test
    fun bucket_policy_is_the_slice_alphabet_and_length() {
        val bucket =
            SearchBucket(
                index = 0,
                length = 2,
                alphabet = Alphabet.DIGITS,
                expectedRelativeWeight = 1.0,
                searchSpaceSize = CombinationCount.of(100),
            )
        assertEquals(LengthPolicy.exactly(2), bucket.policy.length)
        assertEquals(Alphabet.DIGITS, bucket.policy.alphabet)
    }
}
