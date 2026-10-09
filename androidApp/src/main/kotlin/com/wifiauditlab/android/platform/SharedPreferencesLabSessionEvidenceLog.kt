package com.wifiauditlab.android.platform

import android.content.Context
import com.wifiauditlab.assessment.domain.audit.BoundedInMemoryLabSessionEvidenceLog
import com.wifiauditlab.assessment.domain.audit.LabSessionEvidenceLog
import com.wifiauditlab.assessment.domain.audit.LabSessionEvidenceRecord
import com.wifiauditlab.assessment.domain.audit.LabSessionTerminationReason
import com.wifiauditlab.assessment.domain.audit.LabValidationSessionState
import com.wifiauditlab.assessment.domain.audit.VerificationMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists **sanitized** LAB session evidence only (no passwords/credentials).
 * Bounded by [BoundedInMemoryLabSessionEvidenceLog] defaults (count + age).
 */
class SharedPreferencesLabSessionEvidenceLog(
    context: Context,
    private val maxRecords: Int = BoundedInMemoryLabSessionEvidenceLog.DEFAULT_MAX_RECORDS,
    private val maxAgeMs: Long = BoundedInMemoryLabSessionEvidenceLog.DEFAULT_MAX_AGE_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : LabSessionEvidenceLog {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun append(record: LabSessionEvidenceRecord) {
        synchronized(lock) {
            val list = loadLocked().toMutableList()
            list += record
            prune(list)
            persistLocked(list)
        }
    }

    override fun recordsFor(sessionId: String): List<LabSessionEvidenceRecord> =
        synchronized(lock) { loadLocked().filter { it.sessionId == sessionId } }

    override fun all(): List<LabSessionEvidenceRecord> =
        synchronized(lock) {
            val list = loadLocked().toMutableList()
            prune(list)
            persistLocked(list)
            list.toList()
        }

    private fun prune(list: MutableList<LabSessionEvidenceRecord>) {
        val cutoff = clockMs() - maxAgeMs
        list.removeAll { it.timestampEpochMs < cutoff }
        while (list.size > maxRecords) {
            list.removeAt(0)
        }
    }

    private fun loadLocked(): List<LabSessionEvidenceRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    decode(array.getJSONObject(i))?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistLocked(list: List<LabSessionEvidenceRecord>) {
        val array = JSONArray()
        list.forEach { array.put(encode(it)) }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun encode(record: LabSessionEvidenceRecord): JSONObject =
        JSONObject()
            .put("sessionId", record.sessionId)
            .put("timestampEpochMs", record.timestampEpochMs)
            .put("event", record.event)
            .put("verificationMode", record.verificationMode.name)
            .put("networkSsid", record.networkSsid)
            .put("networkFamily", record.networkFamily)
            .put("capability", record.capability)
            .put("state", record.state?.name)
            .put("terminationReason", record.terminationReason?.name)
            .put("adapterResultCategory", record.adapterResultCategory)
            .put("operationsConsumed", record.operationsConsumed)
            .put("detail", record.detail)

    private fun decode(obj: JSONObject): LabSessionEvidenceRecord? =
        runCatching {
            LabSessionEvidenceRecord(
                sessionId = obj.getString("sessionId"),
                timestampEpochMs = obj.getLong("timestampEpochMs"),
                event = obj.getString("event"),
                verificationMode = VerificationMode.valueOf(obj.getString("verificationMode")),
                networkSsid = obj.getString("networkSsid"),
                networkFamily = obj.getString("networkFamily"),
                capability = obj.getString("capability"),
                state =
                    optionalString(obj, "state")
                        ?.let { LabValidationSessionState.valueOf(it) },
                terminationReason =
                    optionalString(obj, "terminationReason")
                        ?.let { LabSessionTerminationReason.valueOf(it) },
                adapterResultCategory = optionalString(obj, "adapterResultCategory"),
                operationsConsumed =
                    if (obj.isNull("operationsConsumed")) {
                        null
                    } else {
                        obj.getInt("operationsConsumed")
                    },
                detail = optionalString(obj, "detail"),
            )
        }.getOrNull()

    private fun optionalString(
        obj: JSONObject,
        key: String,
    ): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.getString(key)
        return value.takeIf { it.isNotEmpty() && it != "null" }
    }

    companion object {
        const val PREFS: String = "lab_session_evidence"
        private const val KEY_RECORDS: String = "records_json"
    }
}
