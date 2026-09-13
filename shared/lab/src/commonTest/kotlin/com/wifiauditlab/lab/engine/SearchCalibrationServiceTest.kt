package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.CalibrationRecord
import com.wifiauditlab.lab.domain.DurationRange
import com.wifiauditlab.lab.domain.InMemoryCalibrationRepository
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.SearchLimits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SearchCalibrationServiceTest {
    private val now = 1_700_000_000_000L
    private val env = DefaultSearchCalibrationService.defaultEnvironment(deviceClass = "test", abi = "test-abi")

    private fun service(
        repository: InMemoryCalibrationRepository = InMemoryCalibrationRepository(),
        clock: () -> Double = { 12_500.0 },
        nowMillis: () -> Long = { now },
        environment: () -> com.wifiauditlab.lab.domain.CalibrationEnvironment = { env },
        maxAgeMillis: Long = CalibrationRecord.DEFAULT_MAX_AGE_MILLIS,
    ) = DefaultSearchCalibrationService(
        repository = repository,
        environmentProvider = { environment() },
        nowMillis = nowMillis,
        clock = { clock() },
        maxAgeMillis = maxAgeMillis,
    )

    @Test
    fun calibrate_create_and_read_persists_positive_throughput() =
        runTest {
            val repository = InMemoryCalibrationRepository()
            val limits = SearchLimits.of(maxAttempts = CombinationCount.of(1_000))
            val svc = service(repository)

            val record = svc.calibrate(strategyId = "length-prioritized", workerCount = 1)

            assertEquals(12_500.0, record.measuredAttemptsPerSecond)
            assertEquals("test", record.deviceClass)
            assertEquals(1, record.workerCount)
            assertEquals(LabEngineVersion.CURRENT, record.engineVersion)
            assertEquals(record, svc.lastRecord())
            assertEquals(CombinationCount.of(1_000), limits.maxAttempts)
            assertTrue(record.sampleDurationMillis >= 0L)
        }

    @Test
    fun calibrate_update_overwrites_previous_record() =
        runTest {
            val repository = InMemoryCalibrationRepository()
            val svc = service(repository, clock = { 10_000.0 })
            svc.calibrate(strategyId = "length-prioritized", workerCount = 1, force = true)
            val updated = service(repository, clock = { 20_000.0 }).calibrate(
                strategyId = "length-prioritized",
                workerCount = 1,
                force = true,
            )
            assertEquals(20_000.0, updated.measuredAttemptsPerSecond)
            assertEquals(20_000.0, repository.load()!!.measuredAttemptsPerSecond)
        }

    @Test
    fun loadUsable_returns_null_when_stale() =
        runTest {
            val repository = InMemoryCalibrationRepository()
            val svc =
                service(
                    repository = repository,
                    maxAgeMillis = 1_000L,
                    nowMillis = { now },
                )
            svc.calibrate(strategyId = "length-prioritized", workerCount = 1, force = true)

            val later =
                service(
                    repository = repository,
                    maxAgeMillis = 1_000L,
                    nowMillis = { now + 5_000L },
                )
            assertNull(later.loadUsable(strategyId = "length-prioritized", workerCount = 1))
            assertNotNull(later.lastRecord())
        }

    @Test
    fun loadUsable_returns_null_when_incompatible_fingerprint() =
        runTest {
            val repository = InMemoryCalibrationRepository()
            service(repository).calibrate(strategyId = "length-prioritized", workerCount = 1, force = true)

            val otherEnv = env.copy(engineVersion = "999")
            val svc = service(repository, environment = { otherEnv })
            assertNull(svc.loadUsable(strategyId = "length-prioritized", workerCount = 1))
        }

    @Test
    fun loadUsable_returns_null_when_worker_or_strategy_differs() =
        runTest {
            val repository = InMemoryCalibrationRepository()
            service(repository).calibrate(strategyId = "length-prioritized", workerCount = 1, force = true)
            val svc = service(repository)
            assertNull(svc.loadUsable(strategyId = "length-prioritized", workerCount = 4))
            assertNull(svc.loadUsable(strategyId = "uniform", workerCount = 1))
        }

    @Test
    fun calibrate_without_force_reuses_compatible_record_as_fallback() =
        runTest {
            val repository = InMemoryCalibrationRepository()
            var ticks = 0
            val svc =
                service(
                    repository = repository,
                    clock = {
                        ticks++
                        15_000.0
                    },
                )
            val first = svc.calibrate(strategyId = "length-prioritized", workerCount = 1, force = true)
            val second = svc.calibrate(strategyId = "length-prioritized", workerCount = 1, force = false)
            assertEquals(first, second)
            assertEquals(1, ticks)
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
