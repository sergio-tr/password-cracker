package com.wifiauditlab.lab.domain.engine

/**
 * Registry of semantic backends the Search Engine may be paired with.
 *
 * Extension point: introduce a new backend by adding a [CandidateVerifier]
 * implementation and (optionally) a [CandidateVerificationBackend] case.
 * Do **not** change [com.wifiauditlab.lab.engine.WorkerAwareLabSearchEngine],
 * [com.wifiauditlab.lab.engine.DynamicRangeScheduler], or candidate generation
 * to call platform APIs.
 *
 * ```
 *                    Search Engine V2
 *                           ↓
 *                       candidate
 *                           ↓
 *                   CandidateVerifier
 *                     /           \
 *                    /             \
 *         BlackBox Lab        External backend
 *        hidden secret            pending
 * ```
 */
sealed interface CandidateVerificationBackend {
    /** Synthetic / hidden secret held inside a [com.wifiauditlab.lab.domain.LabChallenge]. */
    data object LocalSynthetic : CandidateVerificationBackend

    /**
     * Known password encapsulated for Synthetic / Prototype / tests only
     * ([com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier]).
     * Not used by RealWifiAudit.
     */
    data object LocalKnownSecret : CandidateVerificationBackend

    /**
     * Didactic Lab black-box: secret lives only in
     * [com.wifiauditlab.lab.domain.BlackBoxChallengeVerifier].
     * Search Engine / planner / UI never see it.
     */
    data object BlackBoxLab : CandidateVerificationBackend

    /**
     * RealWifiAudit external oracle — pending platform backend.
     * Wired via assessment [ExternalCandidateVerificationPort]; Lab stays free of Android.
     */
    data object ExternalPending : CandidateVerificationBackend

    /**
     * Reserved deliberately.
     *
     * There is **no** automated search backed by a real network inside Lab.
     */
    data object NetworkBackedUnsupported : CandidateVerificationBackend
}

/**
 * Architectural placeholder for a network-backed verification backend.
 *
 * It deliberately contains no Android/network implementation.
 *
 * SearchEngine → CandidateVerifier is the extension boundary.
 * Platform-specific verification belongs behind an adapter outside
 * `:shared:lab`.
 *
 * Calling [verify] always fails closed.
 */
class UnsupportedNetworkCandidateVerifier : CandidateVerifier {
    override fun verify(candidate: String): Boolean =
        throw UnsupportedOperationException(
            "Automated network-backed candidate verification is not implemented",
        )
}

/**
 * Exact-match verifier for synthetic lab secrets (local only).
 * Equivalent in behaviour to an encapsulated known secret, but named for clarity.
 */
class SyntheticExactMatchVerifier private constructor(
    private val secret: String,
) : CandidateVerifier {
    override fun verify(candidate: String): Boolean = candidate == secret

    override fun toString(): String = "SyntheticExactMatchVerifier"

    companion object {
        fun of(secret: String): SyntheticExactMatchVerifier {
            require(secret.isNotEmpty()) { "secret must not be empty" }
            return SyntheticExactMatchVerifier(secret)
        }
    }
}
