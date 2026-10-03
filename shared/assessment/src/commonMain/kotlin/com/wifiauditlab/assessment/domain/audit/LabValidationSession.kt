package com.wifiauditlab.assessment.domain.audit

/**
 * Hard limits for a single [LabValidationSession].
 * F1 allows at most one abstract validation operation — no candidate loops.
 */
data class LabSessionBudget(
    val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
    val maxOperations: Int = DEFAULT_MAX_OPERATIONS,
    val operationTimeoutMs: Long = DEFAULT_OPERATION_TIMEOUT_MS,
) {
    init {
        require(maxDurationMs > 0) { "maxDurationMs must be > 0" }
        require(maxOperations > 0) { "maxOperations must be > 0" }
        require(operationTimeoutMs > 0) { "operationTimeoutMs must be > 0" }
    }

    companion object {
        const val DEFAULT_MAX_DURATION_MS: Long = 30_000
        const val DEFAULT_MAX_OPERATIONS: Int = 1
        const val DEFAULT_OPERATION_TIMEOUT_MS: Long = 10_000

        fun standard(): LabSessionBudget = LabSessionBudget()
    }
}

enum class LabValidationSessionState {
    Created,
    Admitted,
    Running,
    Stopping,
    Completed,
    Cancelled,
    Denied,
    Failed,
    ;

    val isTerminal: Boolean
        get() =
            this == Completed ||
                this == Cancelled ||
                this == Denied ||
                this == Failed
}

enum class LabSessionTerminationReason {
    Completed,
    UserCancelled,
    NetworkChanged,
    NetworkLost,
    LabModeDisabled,
    NetworkAuthorizationRevoked,
    BudgetExhausted,
    Timeout,
    CapabilityUnavailable,
    GateDenied,
    AdapterUnavailable,
    AdapterRejected,
    AdapterError,
    ConcurrentSessionRejected,
}

/**
 * Context already admitted by [AuthorizedApTestGate].
 * The adapter must not choose or override the target.
 */
data class AuthorizedValidationContext(
    val sessionId: String,
    val networkSnapshot: LabNetworkSnapshot,
    val budget: LabSessionBudget,
) {
    override fun toString(): String =
        "AuthorizedValidationContext(sessionId=$sessionId, network=${networkSnapshot.ssid}/${networkSnapshot.securityFamily}, budget=$budget)"
}

/** Typed adapter outcome for LAB_NETWORK_VALIDATION (F1). */
sealed interface NetworkValidationResult {
    data object Succeeded : NetworkValidationResult

    data object Rejected : NetworkValidationResult

    data class Unavailable(
        val reason: ApAuthUnavailableReason,
    ) : NetworkValidationResult

    data object Cancelled : NetworkValidationResult

    data class PlatformError(
        val errorCategory: String,
    ) : NetworkValidationResult {
        override fun toString(): String = "PlatformError(errorCategory=$errorCategory)"
    }

    val category: String
        get() =
            when (this) {
                Succeeded -> "Succeeded"
                Rejected -> "Rejected"
                is Unavailable -> "Unavailable:${reason.name}"
                Cancelled -> "Cancelled"
                is PlatformError -> "PlatformError:$errorCategory"
            }
}

data class LabValidationSession(
    val sessionId: String,
    val verificationMode: VerificationMode = VerificationMode.LAB_NETWORK_VALIDATION,
    val networkSnapshot: LabNetworkSnapshot,
    val createdAtEpochMs: Long,
    val startedAtEpochMs: Long? = null,
    val finishedAtEpochMs: Long? = null,
    val state: LabValidationSessionState,
    val consentGranted: Boolean,
    val capabilitySnapshot: ApAuthCapability,
    val admissionDenials: List<AuthorizedApTestDenial> = emptyList(),
    val budget: LabSessionBudget,
    val operationsConsumed: Int = 0,
    val terminationReason: LabSessionTerminationReason? = null,
    val resultSummary: String? = null,
) {
    init {
        require(verificationMode == VerificationMode.LAB_NETWORK_VALIDATION) {
            "LabValidationSession only supports LAB_NETWORK_VALIDATION"
        }
    }

    override fun toString(): String =
        "LabValidationSession(id=$sessionId, state=$state, network=${networkSnapshot.ssid}/${networkSnapshot.securityFamily}, " +
            "termination=$terminationReason, result=$resultSummary)"
}
