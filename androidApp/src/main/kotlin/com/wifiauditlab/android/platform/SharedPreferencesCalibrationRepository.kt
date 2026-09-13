package com.wifiauditlab.android.platform

import android.content.Context
import android.os.Build
import com.wifiauditlab.lab.domain.CalibrationEnvironment
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.CalibrationRepository
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.engine.CalibrationEnvironmentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable calibration store using private SharedPreferences (lab-only; no vault secrets).
 */
class SharedPreferencesCalibrationRepository(
    context: Context,
) : CalibrationRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun save(record: CalibrationRecord) =
        withContext(Dispatchers.IO) {
            prefs
                .edit()
                .putString(KEY_STRATEGY_ID, record.strategyId)
                .putString(KEY_ENGINE_VERSION, record.engineVersion)
                .putInt(KEY_WORKER_COUNT, record.workerCount)
                .putString(KEY_DEVICE_CLASS, record.deviceClass)
                .putString(KEY_ABI, record.abi)
                .putString(KEY_APP_VERSION, record.appVersion)
                .putString(KEY_THROUGHPUT, record.measuredAttemptsPerSecond.toString())
                .putLong(KEY_SAMPLE_DURATION, record.sampleDurationMillis)
                .putLong(KEY_TIMESTAMP, record.timestampEpochMillis)
                .putBoolean(KEY_PRESENT, true)
                .commit()
            Unit
        }

    override suspend fun load(): CalibrationRecord? =
        withContext(Dispatchers.IO) {
            if (!prefs.getBoolean(KEY_PRESENT, false)) return@withContext null
            val strategyId = prefs.getString(KEY_STRATEGY_ID, null) ?: return@withContext null
            val engineVersion = prefs.getString(KEY_ENGINE_VERSION, null) ?: return@withContext null
            val deviceClass = prefs.getString(KEY_DEVICE_CLASS, null) ?: return@withContext null
            val abi = prefs.getString(KEY_ABI, null) ?: return@withContext null
            val appVersion = prefs.getString(KEY_APP_VERSION, null) ?: return@withContext null
            val throughput =
                prefs.getString(KEY_THROUGHPUT, null)?.toDoubleOrNull() ?: return@withContext null
            runCatching {
                CalibrationRecord(
                    strategyId = strategyId,
                    engineVersion = engineVersion,
                    workerCount = prefs.getInt(KEY_WORKER_COUNT, 1),
                    deviceClass = deviceClass,
                    abi = abi,
                    appVersion = appVersion,
                    measuredAttemptsPerSecond = throughput,
                    sampleDurationMillis = prefs.getLong(KEY_SAMPLE_DURATION, 0L),
                    timestampEpochMillis = prefs.getLong(KEY_TIMESTAMP, 0L),
                )
            }.getOrNull()
        }

    override suspend fun clear() =
        withContext(Dispatchers.IO) {
            prefs.edit().clear().commit()
            Unit
        }

    companion object {
        private const val PREFS = "lab_calibration"
        private const val KEY_PRESENT = "present"
        private const val KEY_STRATEGY_ID = "strategy_id"
        private const val KEY_ENGINE_VERSION = "engine_version"
        private const val KEY_WORKER_COUNT = "worker_count"
        private const val KEY_DEVICE_CLASS = "device_class"
        private const val KEY_ABI = "abi"
        private const val KEY_APP_VERSION = "app_version"
        private const val KEY_THROUGHPUT = "throughput"
        private const val KEY_SAMPLE_DURATION = "sample_duration_ms"
        private const val KEY_TIMESTAMP = "timestamp_ms"
    }
}

class AndroidCalibrationEnvironmentProvider(
    private val context: Context,
) : CalibrationEnvironmentProvider {
    override fun current(): CalibrationEnvironment {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" }
        val appVersion =
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { "unknown" }
        val deviceClass =
            when {
                Build.VERSION.SDK_INT >= 31 -> "android-api${Build.VERSION.SDK_INT}"
                else -> "android-api${Build.VERSION.SDK_INT}"
            }
        return CalibrationEnvironment(
            engineVersion = LabEngineVersion.CURRENT,
            deviceClass = deviceClass,
            abi = abi,
            appVersion = appVersion,
        )
    }
}
