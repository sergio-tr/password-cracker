package com.wifiauditlab.android.platform

import android.content.Context
import com.wifiauditlab.lab.domain.BenchmarkRecord
import com.wifiauditlab.lab.domain.BenchmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded durable history of sanitized lab benchmarks (no secrets).
 */
class SharedPreferencesBenchmarkRepository(
    context: Context,
    private val maxEntries: Int = 100,
) : BenchmarkRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun append(record: BenchmarkRecord) =
        withContext(Dispatchers.IO) {
            val current = loadUnlocked().toMutableList()
            current.add(0, record)
            while (current.size > maxEntries) current.removeAt(current.lastIndex)
            persist(current)
        }

    override suspend fun list(): List<BenchmarkRecord> =
        withContext(Dispatchers.IO) { loadUnlocked() }

    override suspend fun clear() =
        withContext(Dispatchers.IO) {
            prefs.edit().clear().commit()
            Unit
        }

    private fun loadUnlocked(): List<BenchmarkRecord> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                runCatching { obj.toRecord() }.getOrNull()?.let { add(it) }
            }
        }
    }

    private fun persist(records: List<BenchmarkRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_JSON, array.toString()).commit()
    }

    companion object {
        private const val PREFS = "lab_benchmarks"
        private const val KEY_JSON = "records"

        fun BenchmarkRecord.toJson(): JSONObject =
            JSONObject()
                .put("benchmarkId", benchmarkId)
                .put("timestampEpochMillis", timestampEpochMillis)
                .put("engineVersion", engineVersion)
                .put("strategyId", strategyId)
                .put("workerCount", workerCount)
                .put("challengeProfile", challengeProfile)
                .put("searchSpaceExact", searchSpaceExact)
                .put("attempts", attempts)
                .put("durationMillis", durationMillis)
                .put("attemptsPerSecond", attemptsPerSecond)
                .put("cancellationLatencyMillis", cancellationLatencyMillis)
                .put("terminalResult", terminalResult)

        fun JSONObject.toRecord(): BenchmarkRecord =
            BenchmarkRecord(
                benchmarkId = getString("benchmarkId"),
                timestampEpochMillis = getLong("timestampEpochMillis"),
                engineVersion = getString("engineVersion"),
                strategyId = getString("strategyId"),
                workerCount = getInt("workerCount"),
                challengeProfile = getString("challengeProfile"),
                searchSpaceExact = if (isNull("searchSpaceExact")) null else getLong("searchSpaceExact"),
                attempts = getLong("attempts"),
                durationMillis = getLong("durationMillis"),
                attemptsPerSecond = getDouble("attemptsPerSecond"),
                cancellationLatencyMillis =
                    if (isNull("cancellationLatencyMillis")) {
                        null
                    } else {
                        getLong("cancellationLatencyMillis")
                    },
                terminalResult = getString("terminalResult"),
            )

        fun exportJson(records: List<BenchmarkRecord>): String {
            val array = JSONArray()
            records.forEach { array.put(it.toJson()) }
            return array.toString(2)
        }

        fun exportCsv(records: List<BenchmarkRecord>): String {
            val header =
                "benchmarkId,timestampEpochMillis,engineVersion,strategyId,workerCount," +
                    "challengeProfile,searchSpaceExact,attempts,durationMillis,attemptsPerSecond," +
                    "cancellationLatencyMillis,terminalResult"
            val rows =
                records.map { r ->
                    listOf(
                        r.benchmarkId,
                        r.timestampEpochMillis,
                        r.engineVersion,
                        r.strategyId,
                        r.workerCount,
                        r.challengeProfile,
                        r.searchSpaceExact ?: "",
                        r.attempts,
                        r.durationMillis,
                        r.attemptsPerSecond,
                        r.cancellationLatencyMillis ?: "",
                        r.terminalResult,
                    ).joinToString(",")
                }
            return (listOf(header) + rows).joinToString("\n")
        }
    }
}
