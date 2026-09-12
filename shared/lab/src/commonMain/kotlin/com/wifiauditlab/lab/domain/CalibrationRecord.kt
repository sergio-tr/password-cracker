package com.wifiauditlab.lab.domain

data class CalibrationRecord(
    val timestampEpochMillis: Long,
    val deviceClass: String,
    val strategyType: String,
    val workerCount: Int,
    val measuredAttemptsPerSecond: Double,
) {
    init {
        require(deviceClass.isNotBlank()) { "device class must not be blank" }
        require(strategyType.isNotBlank()) { "strategy type must not be blank" }
        require(workerCount >= 1) { "worker count must be >= 1" }
        require(measuredAttemptsPerSecond > 0.0) { "measured throughput must be positive" }
    }
}

interface CalibrationStore {
    suspend fun save(record: CalibrationRecord)

    suspend fun load(): CalibrationRecord?
}

class InMemoryCalibrationStore : CalibrationStore {
    private var record: CalibrationRecord? = null

    override suspend fun save(record: CalibrationRecord) {
        this.record = record
    }

    override suspend fun load(): CalibrationRecord? = record
}
