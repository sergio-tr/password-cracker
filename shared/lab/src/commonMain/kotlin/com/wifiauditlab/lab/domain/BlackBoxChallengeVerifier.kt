package com.wifiauditlab.lab.domain

import com.wifiauditlab.lab.domain.engine.CandidateVerifier

/**
 * Didactic black-box verifier for Lab Mode.
 *
 * The hidden secret lives **only** inside this verifier. It must never be
 * exposed to ViewModels, planners, the Search Engine, UI, or candidate
 * generation. The engine discovers the secret solely by submitting candidates
 * and observing true/false — exactly as against an unknown real-world secret.
 *
 * Distinct from [EncapsulatedPasswordVerifier], which is reserved for known-
 * password Synthetic / Prototype / test flows where the user supplies a local
 * target password.
 */
class BlackBoxChallengeVerifier private constructor(
    private val secret: String,
) : CandidateVerifier {
    override fun verify(candidate: String): Boolean = candidate == secret

    override fun toString(): String = "BlackBoxChallengeVerifier"

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
        /**
         * Encapsulates [secret] permanently inside the verifier.
         * Callers must discard their own copy after construction.
         */
        fun encapsulate(secret: String): BlackBoxChallengeVerifier {
            require(secret.isNotEmpty()) { "secret must not be empty" }
            return BlackBoxChallengeVerifier(secret)
        }
    }
}
