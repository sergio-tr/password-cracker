package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.lab.domain.BlackBoxChallengeVerifier
import com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandidateVerificationBackendTest {
    @Test
    fun encapsulatedAndSynthetic_areLocalExactMatch() {
        val known = EncapsulatedPasswordVerifier.encapsulate("lab-secret-1")
        val synthetic = SyntheticExactMatchVerifier.of("lab-secret-1")
        assertTrue(known.verify("lab-secret-1"))
        assertTrue(synthetic.verify("lab-secret-1"))
        assertFalse(known.verify("other"))
        assertFalse(synthetic.verify("other"))
        assertEquals(CandidateVerificationBackend.LocalKnownSecret, CandidateVerificationBackend.LocalKnownSecret)
        assertEquals(CandidateVerificationBackend.LocalSynthetic, CandidateVerificationBackend.LocalSynthetic)
        assertEquals(CandidateVerificationBackend.BlackBoxLab, CandidateVerificationBackend.BlackBoxLab)
        assertEquals(CandidateVerificationBackend.ExternalPending, CandidateVerificationBackend.ExternalPending)
    }

    @Test
    fun unsupportedNetworkBackend_alwaysThrows() {
        val verifier = UnsupportedNetworkCandidateVerifier()
        val error =
            assertFailsWith<UnsupportedOperationException> {
                verifier.verify("anything")
            }
        assertTrue(error.message!!.contains("not implemented"))
        assertEquals(
            CandidateVerificationBackend.NetworkBackedUnsupported,
            CandidateVerificationBackend.NetworkBackedUnsupported,
        )
    }

    @Test
    fun secrets_neverAppearInToString() {
        val secret = "super-secret-value"
        assertFalse(EncapsulatedPasswordVerifier.encapsulate(secret).toString().contains(secret))
        assertFalse(SyntheticExactMatchVerifier.of(secret).toString().contains(secret))
        assertFalse(BlackBoxChallengeVerifier.encapsulate(secret).toString().contains(secret))
    }
}
