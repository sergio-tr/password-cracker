package com.wifiauditlab.lab.engine

import com.wifiauditlab.lab.domain.BenchmarkScenario
import com.wifiauditlab.lab.domain.InMemoryBenchmarkRepository
import com.wifiauditlab.lab.domain.LabBenchmarkScenarios
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LabBenchmarkServiceTest {
    private fun service(repository: InMemoryBenchmarkRepository = InMemoryBenchmarkRepository()) =
        DefaultLabBenchmarkService(
            repository = repository,
            nowMillis = { 1_700_000_000_000L },
            availableProcessors = 4,
        )

    @Test
    fun runScenario_persists_sanitized_metrics_without_secret() =
        runTest {
            val repository = InMemoryBenchmarkRepository()
            val svc = service(repository)
            val scenario =
                BenchmarkScenario(
                    scenarioId = "unit-small",
                    challengeProfile = "digits/len=2/known",
                    alphabetLabel = "DIGITS",
                    secretLength = 2,
                    knownSecret = "42",
                    strategyId = "length-prioritized",
                    workerCount = 1,
                    maxAttempts = 200,
                )
            val record = svc.runScenario(scenario)
            assertEquals("Found", record.terminalResult)
            assertTrue(record.attempts > 0)
            assertTrue(record.attemptsPerSecond >= 0.0)
            assertFalse(record.challengeProfile.contains("42"))
            assertEquals(record, repository.list().single())
            val exportedLike = "${record.benchmarkId}|${record.terminalResult}|${record.attempts}"
            assertFalse(exportedLike.contains(scenario.knownSecret))
        }

    @Test
    fun runSuite_covers_default_scenarios() =
        runTest {
            val records = service().runSuite(LabBenchmarkScenarios.defaultSuite().take(3))
            assertEquals(3, records.size)
            assertTrue(records.all { it.engineVersion.isNotBlank() })
        }

    @Test
    fun compareWorkers_returns_baseline_and_parallel() =
        runTest {
            val comparison = service().compareWorkers()
            assertNotNull(comparison.baseline)
            assertNotNull(comparison.parallel)
            assertEquals(1, comparison.baseline!!.workerCount)
            assertEquals(4, comparison.parallel!!.workerCount)
            assertNotNull(comparison.speedup)
        }

    @Test
    fun clear_removes_history() =
        runTest {
            val repository = InMemoryBenchmarkRepository()
            val svc = service(repository)
            svc.runScenario(LabBenchmarkScenarios.defaultSuite().first())
            assertTrue(svc.history().isNotEmpty())
            svc.clear()
            assertTrue(svc.history().isEmpty())
        }
}
