package com.wifiauditlab.lab.domain

import com.wifiauditlab.lab.domain.engine.CandidateVerifier
import kotlin.random.Random

/**
 * A fully synthetic search exercise: an alphabet, a length policy and a locally
 * verified secret. It is completely decoupled from any real network.
 *
 * The secret is encapsulated: it is never exposed through getters, [toString],
 * logs or serialization. Candidate verification happens through [asVerifier].
 *
 * For known-password audits, prefer [withEncapsulatedVerifier]: the planning
 * [policy] must not be derived from the target password's length or characters.
 */
class LabChallenge private constructor(
    val id: LabChallengeId,
    val policy: LabSecretPolicy,
    val seed: Long?,
    private val secret: String?,
    private val externalVerifier: CandidateVerifier?,
) {
    init {
        require(secret != null || externalVerifier != null) {
            "LabChallenge needs an encapsulated secret or an external verifier"
        }
    }

    val alphabet: Alphabet get() = policy.alphabet
    val lengthPolicy: LengthPolicy get() = policy.length

    /** Whether ordering is reproducible. Only true when a [seed] was provided. */
    val isDeterministic: Boolean get() = seed != null

    internal fun isSolution(candidate: String): Boolean =
        externalVerifier?.verify(candidate) ?: (candidate == secret)

    /**
     * Length of an internally stored secret, when present.
     * Not available for encapsulated-verifier challenges (returns null) so planners
     * cannot accidentally read the target length.
     */
    internal val secretLength: Int? get() = secret?.length

    override fun toString(): String =
        "LabChallenge(id=$id, policy=$policy, seed=$seed, verifier=${if (externalVerifier != null) "encapsulated" else "internal"})"

    companion object {
        /** Builds a challenge with a random hidden secret drawn from the space. */
        fun withHiddenSecret(
            alphabet: Alphabet,
            lengthPolicy: LengthPolicy,
            seed: Long? = null,
            id: ChallengeId = ChallengeId.random(),
            syntheticLengthWeights: Map<Int, Double> = emptyMap(),
        ): LabChallenge {
            val random = if (seed != null) Random(seed) else Random.Default
            val length =
                if (lengthPolicy.minLength == lengthPolicy.maxLength) {
                    lengthPolicy.minLength
                } else {
                    random.nextInt(lengthPolicy.minLength, lengthPolicy.maxLength + 1)
                }
            val generated =
                buildString {
                    repeat(length) { append(alphabet[random.nextInt(alphabet.size)]) }
                }
            return LabChallenge(
                id,
                LabSecretPolicy(alphabet, lengthPolicy, syntheticLengthWeights),
                seed,
                secret = generated,
                externalVerifier = null,
            )
        }

        /**
         * Builds a challenge with an explicit secret. Intended for reproducible tests
         * and benchmarks; the secret must be expressible in the given space.
         *
         * **Do not use for known-password Wi-Fi audits** — the length policy equals the
         * secret length and would contaminate the planner. Use [withEncapsulatedVerifier].
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
            val policy = LabSecretPolicy(alphabet, LengthPolicy.exactly(secret.length))
            return LabChallenge(id, policy, seed, secret = secret, externalVerifier = null)
        }

        /**
         * Known-password audit entry: [verifier] holds the target; [policy] describes a
         * **blind** search space that must not be derived from the target's length/pattern.
         */
        fun withEncapsulatedVerifier(
            policy: LabSecretPolicy,
            verifier: CandidateVerifier,
            seed: Long? = null,
            id: ChallengeId = ChallengeId.random(),
        ): LabChallenge =
            LabChallenge(
                id = id,
                policy = policy,
                seed = seed,
                secret = null,
                externalVerifier = verifier,
            )

        /**
         * Blind planning challenge: random internal secret only so strategies that peek
         * at [isSolution] in tests still work; production planners must use [policy] alone.
         */
        fun blindSearchSpace(
            alphabet: Alphabet,
            lengthPolicy: LengthPolicy,
            seed: Long? = 1L,
            id: ChallengeId = ChallengeId.random(),
        ): LabChallenge = withHiddenSecret(alphabet, lengthPolicy, seed, id)
    }
}

/**
 * Holds a known password exclusively for verification.
 * Never expose [toString]/equals that reveal the secret; planners must not receive this type.
 */
class EncapsulatedPasswordVerifier private constructor(
    private val secret: String,
) : CandidateVerifier {
    override fun verify(candidate: String): Boolean = candidate == secret

    override fun toString(): String = "EncapsulatedPasswordVerifier"

    companion object {
        fun encapsulate(secret: String): EncapsulatedPasswordVerifier {
            require(secret.isNotEmpty()) { "secret must not be empty" }
            return EncapsulatedPasswordVerifier(secret)
        }
    }
}
