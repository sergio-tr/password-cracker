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

    /**
     * Product name: Prueba Wi‑Fi real.
     * One-shot explicit credential against a **FixedLabAccessPoint** (SSID+BSSID).
     * Reuses the same adapter/orchestrator stack; never the Search Engine.
     */
    SINGLE_REAL_CREDENTIAL_VALIDATION,
}

/** Modes that may invoke [NetworkValidationAdapter] (never the local Search Engine). */
fun VerificationMode.isPlatformNetworkValidation(): Boolean =
    this == VerificationMode.LAB_NETWORK_VALIDATION ||
        this == VerificationMode.SINGLE_REAL_CREDENTIAL_VALIDATION
