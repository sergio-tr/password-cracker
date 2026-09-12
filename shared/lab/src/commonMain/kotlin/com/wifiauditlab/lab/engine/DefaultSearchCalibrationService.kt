package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.CalibrationStore
import com.wifiauditlab.lab.domain.engine.OdometerCandidateSource
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.domain.engine.WorkloadClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DefaultSearchCalibrationService(
    private val store: CalibrationStore,
    private val nowMillis: () -> Long,
    private val clock: WorkloadClock = WorkloadClock { measureSyntheticThroughput() },
) : SearchCalibrationService {
    override suspend fun calibrate(
        strategyType: String,
        workerCount: Int,
        deviceClass: String,
    ): CalibrationRecord {
        val measured = clock.measureAttemptsPerSecond()
        val record =
            CalibrationRecord(
                timestampEpochMillis = nowMillis(),
                deviceClass = deviceClass,
                strategyType = strategyType,
                workerCount = workerCount,
                measuredAttemptsPerSecond = measured,
            )
        store.save(record)
        return record
    }

    override suspend fun lastRecord(): CalibrationRecord? = store.load()

    companion object {
        private const val SAMPLE_CANDIDATES: Int = 8_192

        fun measureSyntheticThroughput(timeSource: TimeSource = TimeSource.Monotonic): Double {
            val source = OdometerCandidateSource(Alphabet.DIGITS, length = 4)
            val mark = timeSource.markNow()
            var seen = 0
            for (candidate in source.candidates()) {
                candidate.length
                seen++
                if (seen >= SAMPLE_CANDIDATES) break
            }
            val seconds = mark.elapsedNow().inWholeMicroseconds / 1_000_000.0
            return if (seconds <= 0.0) SAMPLE_CANDIDATES.toDouble() else seen / seconds
        }
    }
}

/**
 * Uses the last calibration throughput when present. Never mutates search limits.
 */
class CalibratedThroughputEstimator(
    private val fallback: SearchPerformanceEstimator = FixedThroughputEstimator(),
) : SearchPerformanceEstimator {
    @Volatile
    private var measured: Double? = null

    override fun expectedThroughput(): Double = measured ?: fallback.expectedThroughput()

    override fun estimateDuration(space: CombinationCount): Duration? {
        val exact = space.toLongOrNull() ?: return null
        return (exact / expectedThroughput()).seconds
    }

    override fun refine(measuredAttemptsPerSecond: Double) {
        require(measuredAttemptsPerSecond > 0.0)
        measured = measuredAttemptsPerSecond
    }
}
