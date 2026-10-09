package com.wifiauditlab.assessment.domain.audit

/**
 * Sanitized session evidence for LAB_NETWORK_VALIDATION.
 * Never stores passwords, candidates, PSK, or vault secret payloads.
 */
data class LabSessionEvidenceRecord(
    val sessionId: String,
    val timestampEpochMs: Long,
    val event: String,
    val verificationMode: VerificationMode,
    val networkSsid: String,
    val networkFamily: String,
    val capability: String,
    val state: LabValidationSessionState?,
    val terminationReason: LabSessionTerminationReason?,
    val adapterResultCategory: String?,
    val operationsConsumed: Int?,
    val detail: String? = null,
) {
    override fun toString(): String =
        "LabSessionEvidenceRecord(sessionId=$sessionId, event=$event, state=$state, " +
            "termination=$terminationReason, adapter=$adapterResultCategory, network=$networkSsid/$networkFamily)"
}

interface LabSessionEvidenceLog {
    fun append(record: LabSessionEvidenceRecord)

    fun recordsFor(sessionId: String): List<LabSessionEvidenceRecord>

    fun all(): List<LabSessionEvidenceRecord>
}

class InMemoryLabSessionEvidenceLog : LabSessionEvidenceLog {
    private val records = mutableListOf<LabSessionEvidenceRecord>()

    override fun append(record: LabSessionEvidenceRecord) {
        synchronized(records) { records += record }
    }

    override fun recordsFor(sessionId: String): List<LabSessionEvidenceRecord> =
        synchronized(records) { records.filter { it.sessionId == sessionId } }

    override fun all(): List<LabSessionEvidenceRecord> =
        synchronized(records) { records.toList() }
}
