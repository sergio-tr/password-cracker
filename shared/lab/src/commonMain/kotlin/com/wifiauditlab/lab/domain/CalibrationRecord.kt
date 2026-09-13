package com.wifiauditlab.lab.domain

/**
 * Durable calibration sample for synthetic lab throughput estimation.
 * No personal identifiers — only device class / ABI / app+engine versions.
 */
data class CalibrationRecord(
    val strategyId: String,
    val engineVersion: String,
    val workerCount: Int,
    val deviceClass: String,
    val abi: String,
    val appVersion: String,
    val measuredAttemptsPerSecond: Double,
    val sampleDurationMillis: Long,
    val timestampEpochMillis: Long,
) {
    init {
        require(strategyId.isNotBlank()) { "strategyId must not be blank" }
        require(engineVersion.isNotBlank()) { "engineVersion must not be blank" }
        require(deviceClass.isNotBlank()) { "deviceClass must not be blank" }
        require(abi.isNotBlank()) { "abi must not be blank" }
        require(appVersion.isNotBlank()) { "appVersion must not be blank" }
        require(workerCount >= 1) { "workerCount must be >= 1" }
        require(measuredAttemptsPerSecond > 0.0) { "measured throughput must be positive" }
        require(sampleDurationMillis >= 0L) { "sampleDurationMillis must be >= 0" }
    }

    /** True when fingerprint fields match the current runtime/config. */
    fun isCompatibleWith(
        strategyId: String,
        engineVersion: String,
        workerCount: Int,
        deviceClass: String,
        abi: String,
        appVersion: String,
    ): Boolean =
        this.strategyId == strategyId &&
            this.engineVersion == engineVersion &&
            this.workerCount == workerCount &&
            this.deviceClass == deviceClass &&
            this.abi == abi &&
            this.appVersion == appVersion

    fun isStale(
        nowEpochMillis: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    ): Boolean = nowEpochMillis - timestampEpochMillis > maxAgeMillis

    companion object {
        /** 30 days — beyond this, force a fresh synthetic sample. */
        const val DEFAULT_MAX_AGE_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L
    }
}

/**
 * Port for durable calibration persistence. Implementations must not store secrets.
 */
interface CalibrationRepository {
    suspend fun save(record: CalibrationRecord)

    suspend fun load(): CalibrationRecord?

    suspend fun clear()
}

/** In-process fake used by unit tests and as fallback when no durable store is wired. */
class InMemoryCalibrationRepository : CalibrationRepository {
    private var record: CalibrationRecord? = null

    override suspend fun save(record: CalibrationRecord) {
        this.record = record
    }

    override suspend fun load(): CalibrationRecord? = record

    override suspend fun clear() {
        record = null
    }
}

/** @deprecated Use [InMemoryCalibrationRepository]. Kept as a type alias for older call sites. */
@Deprecated("Use InMemoryCalibrationRepository", ReplaceWith("InMemoryCalibrationRepository"))
typealias InMemoryCalibrationStore = InMemoryCalibrationRepository

/** @deprecated Use [CalibrationRepository]. */
@Deprecated("Use CalibrationRepository", ReplaceWith("CalibrationRepository"))
typealias CalibrationStore = CalibrationRepository

/** Runtime fingerprint used to decide whether a stored record is still valid. */
data class CalibrationEnvironment(
    val engineVersion: String,
    val deviceClass: String,
    val abi: String,
    val appVersion: String,
)

object LabEngineVersion {
    /** Parallel indexed engine (FASE 24) is the multi-worker default. */
    const val CURRENT: String = "2"
}
