package com.wifiauditlab.lab.domain

import kotlin.random.Random

/**
 * A fully synthetic search exercise: an alphabet, a length policy and a locally
 * generated hidden secret. It is completely decoupled from any real network.
 *
 * The secret is encapsulated: it is never exposed through getters, [toString],
 * logs or serialization. Candidate verification happens through [asVerifier],
 * whose implementation lives in this module.
 */
class LabChallenge private constructor(
    val id: ChallengeId,
    val alphabet: Alphabet,
    val lengthPolicy: LengthPolicy,
    val seed: Long?,
    private val secret: String,
) {
    /** Whether ordering is reproducible. Only true when a [seed] was provided. */
    val isDeterministic: Boolean get() = seed != null

    internal fun isSolution(candidate: String): Boolean = candidate == secret

    /** Length of the hidden secret. Exposed only for post-hoc lab reporting. */
    internal val secretLength: Int get() = secret.length

    override fun toString(): String =
        "LabChallenge(id=$id, alphabet=$alphabet, lengthPolicy=$lengthPolicy, seed=$seed)"

    companion object {
        /** Builds a challenge with a random hidden secret drawn from the space. */
        fun withHiddenSecret(
            alphabet: Alphabet,
            lengthPolicy: LengthPolicy,
            seed: Long? = null,
            id: ChallengeId = ChallengeId.random(),
        ): LabChallenge {
            val random = if (seed != null) Random(seed) else Random.Default
            val length = if (lengthPolicy.minLength == lengthPolicy.maxLength) {
                lengthPolicy.minLength
            } else {
                random.nextInt(lengthPolicy.minLength, lengthPolicy.maxLength + 1)
            }
            val secret = buildString {
                repeat(length) { append(alphabet[random.nextInt(alphabet.size)]) }
            }
            return LabChallenge(id, alphabet, lengthPolicy, seed, secret)
        }

        /**
         * Builds a challenge with an explicit secret. Intended for reproducible tests
         * and benchmarks; the secret must be expressible in the given space.
         */
        fun withKnownSecret(
            alphabet: Alphabet,
            secret: String,
            seed: Long? = null,
            id: ChallengeId = ChallengeId.random(),
        ): LabChallenge {
            require(secret.isNotEmpty()) { "secret must not be empty" }
            require(secret.all { alphabet.symbols.contains(it) }) {
                "secret contains symbols outside the alphabet"
            }
            val policy = LengthPolicy.exactly(secret.length)
            return LabChallenge(id, alphabet, policy, seed, secret)
        }
    }
}
