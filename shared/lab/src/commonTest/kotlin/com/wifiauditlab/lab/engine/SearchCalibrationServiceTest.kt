package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.DurationRange
import com.wifiauditlab.lab.domain.InMemoryCalibrationStore
import com.wifiauditlab.lab.domain.SearchLimits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SearchCalibrationServiceTest {
    @Test
    fun calibrate_persists_positive_throughput_without_changing_limits() =
        runTest {
            val store = InMemoryCalibrationStore()
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(1_000))
            val service =
                DefaultSearchCalibrationService(
                    store = store,
                    nowMillis = { 1_700_000_000_000L },
                    clock = { 12_500.0 },
                )

            val record = service.calibrate(strategyType = "length-prioritized", workerCount = 1, deviceClass = "test")

            assertEquals(12_500.0, record.measuredAttemptsPerSecond)
            assertEquals("test", record.deviceClass)
            assertEquals(1, record.workerCount)
            assertEquals(record, service.lastRecord())
            assertEquals(CombinationCount.of(1_000), limits.maxAttempts)
        }

    @Test
    fun calibrated_estimator_uses_measured_throughput() {
        val estimator = CalibratedThroughputEstimator()
        estimator.refine(1_000.0)
        assertEquals(1_000.0, estimator.expectedThroughput())
        assertEquals(10.seconds, estimator.estimateDuration(CombinationCount.of(10_000)))
    }

    @Test
    fun duration_range_is_an_approximation_not_an_exact_clock() {
        val range = DurationRange.fromPoint(5.minutes)
        assertTrue(range.min < range.max)
        assertTrue(range.toApproximateString().startsWith("approximately "))
        assertTrue("min" in range.toApproximateString())
    }
}
