package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.lab.domain.CalibrationEnvironment
import com.wifiauditlab.lab.domain.CalibrationRecord

/**
 * Runs a short, synthetic, local benchmark and persists the measured throughput.
 * Never used to raise the limits the user configured.
 */
interface SearchCalibrationService {
    suspend fun calibrate(
        strategyId: String,
        workerCount: Int = 1,
        force: Boolean = false,
    ): CalibrationRecord

    /**
     * Returns the last stored record if it is compatible with [strategyId]/[workerCount]
     * and the current environment, and not stale; otherwise null.
     */
    suspend fun loadUsable(
        strategyId: String,
        workerCount: Int = 1,
    ): CalibrationRecord?

    suspend fun lastRecord(): CalibrationRecord?

    suspend fun clear()
}

fun interface WorkloadClock {
    fun measureAttemptsPerSecond(): Double
}

/** Supplies device/app fingerprint for calibration invalidation (no PII). */
fun interface CalibrationEnvironmentProvider {
    fun current(): CalibrationEnvironment
}
