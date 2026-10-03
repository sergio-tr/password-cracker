package com.wifiauditlab.assessment.domain.audit

/**
 * How password candidates are verified for a product audit session.
 *
 * See [docs/adr/ADR-004-authorized-ap-lab-test.md] and
 * [docs/14-authorized-lab-validation-plan.md].
 */
enum class VerificationMode {
    /** In-memory encapsulated verifier; never talks to the AP. */
    LOCAL_AUDIT,

    /**
     * Product name: LAB_NETWORK_VALIDATION.
     * Platform probe against the **currently connected** AP, only when the
     * fail-closed [AuthorizedApTestGate] admits the session (F1+ execution).
     */
    LAB_NETWORK_VALIDATION,
}
