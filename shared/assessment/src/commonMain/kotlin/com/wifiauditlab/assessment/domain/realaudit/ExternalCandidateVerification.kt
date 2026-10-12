package com.wifiauditlab.assessment.domain.realaudit

/**
 * External black-box verification frontier for RealWifiAudit.
 *
 * The Search Engine never learns the target secret: it only submits candidates
 * and observes Match / NoMatch / Unavailable.
 *
 * Implementations live outside `:shared:lab`. Do **not** use
 * [com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier] here.
 */
interface ExternalCandidateVerificationPort {
    /** Whether a real external backend can verify candidates. */
    fun capability(): ExternalVerifierCapability

    /**
     * Verify one candidate against an opaque external oracle.
     * Must not require the application to know the target secret.
     */
    suspend fun verify(candidate: String): ExternalVerificationResult

    fun cancel()
}

sealed interface ExternalVerifierCapability {
    data object Available : ExternalVerifierCapability

    data object Unavailable : ExternalVerifierCapability
}

sealed interface ExternalVerificationResult {
    /** External oracle reports the candidate is correct. */
    data object Match : ExternalVerificationResult

    /** External oracle reports the candidate is incorrect. */
    data object NoMatch : ExternalVerificationResult

    /** Backend cannot decide (capacity, platform, not wired). */
    data object Unavailable : ExternalVerificationResult

    data object Cancelled : ExternalVerificationResult

    data class Failed(
        val reason: RealWifiAuditFailureReason,
    ) : ExternalVerificationResult
}

/**
 * Placeholder external verifier — always unavailable.
 *
 * Extension point for a future opaque backend. No Wi‑Fi implementation here.
 */
class UnsupportedExternalCandidateVerificationPort : ExternalCandidateVerificationPort {
    override fun capability(): ExternalVerifierCapability = ExternalVerifierCapability.Unavailable

    override suspend fun verify(candidate: String): ExternalVerificationResult {
        /*
         * ============================================================
         * USER_IMPLEMENTATION_POINT_EXTERNAL_VERIFIER
         * ============================================================
         *
         * Opaque external candidate verification backend.
         *
         * The Search Engine V2 submits candidates here without knowing
         * the target secret. A future implementation may talk to a
         * platform-specific oracle — never EncapsulatedPasswordVerifier,
         * never Vault known-secret, never a manual password field.
         *
         * Do not implement Wi‑Fi requestNetwork loops here without a
         * reviewed design. Current product policy: Unavailable.
         */
        return ExternalVerificationResult.Unavailable
    }

    override fun cancel() = Unit
}
