package com.wifiauditlab.assessment.domain.audit

/**
 * In-memory evidence with bounded retention (count + age).
 * Suitable for JVM tests; Android persists via SharedPreferences implementation.
 */
class BoundedInMemoryLabSessionEvidenceLog(
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : LabSessionEvidenceLog {
    private val records = mutableListOf<LabSessionEvidenceRecord>()

    override fun append(record: LabSessionEvidenceRecord) {
        synchronized(records) {
            records += record
            pruneLocked()
        }
    }

    override fun recordsFor(sessionId: String): List<LabSessionEvidenceRecord> =
        synchronized(records) { records.filter { it.sessionId == sessionId } }

    override fun all(): List<LabSessionEvidenceRecord> =
        synchronized(records) {
            pruneLocked()
            records.toList()
        }

    private fun pruneLocked() {
        val cutoff = clockMs() - maxAgeMs
        records.removeAll { it.timestampEpochMs < cutoff }
        while (records.size > maxRecords) {
            records.removeAt(0)
        }
    }

    companion object {
        const val DEFAULT_MAX_RECORDS: Int = 100
        const val DEFAULT_MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
