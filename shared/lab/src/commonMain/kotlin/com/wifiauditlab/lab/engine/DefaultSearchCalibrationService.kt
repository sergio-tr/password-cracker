package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.CalibrationEnvironment
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.CalibrationRepository
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.engine.CalibrationEnvironmentProvider
import com.wifiauditlab.lab.domain.engine.OdometerCandidateSource
import com.wifiauditlab.lab.domain.engine.SearchCalibrationService
import com.wifiauditlab.lab.domain.engine.SearchPerformanceEstimator
import com.wifiauditlab.lab.domain.engine.WorkloadClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DefaultSearchCalibrationService(
    private val repository: CalibrationRepository,
    private val environmentProvider: CalibrationEnvironmentProvider,
    private val nowMillis: () -> Long,
    private val clock: WorkloadClock = WorkloadClock { measureSyntheticThroughput() },
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val maxAgeMillis: Long = CalibrationRecord.DEFAULT_MAX_AGE_MILLIS,
) : SearchCalibrationService {
    override suspend fun calibrate(
        strategyId: String,
        workerCount: Int,
        force: Boolean,
    ): CalibrationRecord {
        if (!force) {
            loadUsable(strategyId, workerCount)?.let { return it }
        }
        val env = environmentProvider.current()
        val mark = timeSource.markNow()
        val measured = clock.measureAttemptsPerSecond()
        val sampleDurationMillis = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
        val record =
            CalibrationRecord(
                strategyId = strategyId,
                engineVersion = env.engineVersion,
                workerCount = workerCount,
                deviceClass = env.deviceClass,
                abi = env.abi,
                appVersion = env.appVersion,
                measuredAttemptsPerSecond = measured,
                sampleDurationMillis = sampleDurationMillis,
                timestampEpochMillis = nowMillis(),
            )
        repository.save(record)
        return record
    }

    override suspend fun loadUsable(
        strategyId: String,
        workerCount: Int,
    ): CalibrationRecord? {
        val env = environmentProvider.current()
        val stored = repository.load() ?: return null
        if (stored.isStale(nowMillis(), maxAgeMillis)) return null
        if (!stored.isCompatibleWith(
                strategyId = strategyId,
                engineVersion = env.engineVersion,
                workerCount = workerCount,
                deviceClass = env.deviceClass,
                abi = env.abi,
                appVersion = env.appVersion,
            )
        ) {
            return null
        }
        return stored
    }

    override suspend fun lastRecord(): CalibrationRecord? = repository.load()

    override suspend fun clear() = repository.clear()

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

        fun defaultEnvironment(
            deviceClass: String = "generic",
            abi: String = "jvm",
            appVersion: String = "test",
            engineVersion: String = LabEngineVersion.CURRENT,
        ): CalibrationEnvironment =
            CalibrationEnvironment(
                engineVersion = engineVersion,
                deviceClass = deviceClass,
                abi = abi,
                appVersion = appVersion,
            )
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
