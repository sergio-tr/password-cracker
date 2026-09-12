package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.lab.domain.CalibrationRecord

/**
 * Runs a short, synthetic, local benchmark and persists the measured throughput.
 * Never used to raise the limits the user configured.
 */
interface SearchCalibrationService {
    suspend fun calibrate(
        strategyType: String,
        workerCount: Int = 1,
        deviceClass: String = "generic",
    ): CalibrationRecord

    suspend fun lastRecord(): CalibrationRecord?
}

fun interface WorkloadClock {
    fun measureAttemptsPerSecond(): Double
}
