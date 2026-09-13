package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.Alphabet
import com.wifiauditlab.lab.domain.BenchmarkComparison
import com.wifiauditlab.lab.domain.BenchmarkRecord
import com.wifiauditlab.lab.domain.BenchmarkRepository
import com.wifiauditlab.lab.domain.BenchmarkScenario
import com.wifiauditlab.lab.domain.LabBenchmarkScenarios
import com.wifiauditlab.lab.domain.LabChallenge
import com.wifiauditlab.lab.domain.LabEngineVersion
import com.wifiauditlab.lab.domain.LabSearchEvent
import com.wifiauditlab.lab.domain.SearchLimits
import com.wifiauditlab.lab.domain.SearchStrategyId
import com.wifiauditlab.lab.domain.engine.CancellationController
import com.wifiauditlab.lab.domain.engine.LabBenchmarkService
import com.wifiauditlab.lab.domain.engine.LabSearchEngine
import com.wifiauditlab.lab.domain.engine.SearchPlanOptimizer
import com.wifiauditlab.lab.domain.newBenchmarkId
import kotlinx.coroutines.flow.toList
import kotlin.time.TimeSource

class DefaultLabBenchmarkService(
    private val repository: BenchmarkRepository,
    private val optimizer: SearchPlanOptimizer = SearchStrategyRegistry(),
    private val nowMillis: () -> Long,
    private val availableProcessors: Int = 4,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : LabBenchmarkService {
    override suspend fun runScenario(scenario: BenchmarkScenario): BenchmarkRecord {
        val alphabet = alphabetOf(scenario.alphabetLabel)
        val challenge = LabChallenge.withKnownSecret(alphabet, scenario.knownSecret)
        val plan = optimizer.optimize(challenge, SearchStrategyId(scenario.strategyId))
        val limits =
            SearchLimits.of(
                maxAttempts = CombinationCount.of(scenario.maxAttempts),
                batchSize = 64,
            )
        val engine: LabSearchEngine =
            WorkerAwareLabSearchEngine(
                timeSource = timeSource,
                availableProcessors = availableProcessors,
            ).also { it.workers = scenario.workerCount }

        val mark = timeSource.markNow()
        val events = engine.run(challenge, plan, limits, CancellationController()).toList()
        val duration = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
        val terminal = events.lastOrNull()
        val metrics =
            when (terminal) {
                is LabSearchEvent.CandidateFound -> terminal.metrics
                is LabSearchEvent.Completed -> terminal.metrics
                is LabSearchEvent.LimitReached -> terminal.metrics
                is LabSearchEvent.Cancelled -> terminal.metrics
                is LabSearchEvent.Failed -> terminal.metrics
                else -> null
            }
        val terminalName =
            when (terminal) {
                is LabSearchEvent.CandidateFound -> "Found"
                is LabSearchEvent.Completed -> "NotFound"
                is LabSearchEvent.LimitReached -> "LimitReached"
                is LabSearchEvent.Cancelled -> "Cancelled"
                is LabSearchEvent.Failed -> "Failed"
                else -> "Unknown"
            }
        val attempts = metrics?.attempts?.toLongOrNull() ?: 0L
        val throughput =
            metrics?.attemptsPerSecond
                ?: if (duration > 0L) attempts * 1000.0 / duration else 0.0
        val record =
            BenchmarkRecord(
                benchmarkId = newBenchmarkId(nowMillis()),
                timestampEpochMillis = nowMillis(),
                engineVersion = LabEngineVersion.CURRENT,
                strategyId = scenario.strategyId,
                workerCount = scenario.workerCount,
                challengeProfile = scenario.challengeProfile,
                searchSpaceExact = plan.searchSpace.toLongOrNull(),
                attempts = attempts,
                durationMillis = duration,
                attemptsPerSecond = throughput,
                cancellationLatencyMillis = null,
                terminalResult = terminalName,
            )
        repository.append(record)
        return record
    }

    override suspend fun runSuite(scenarios: List<BenchmarkScenario>): List<BenchmarkRecord> {
        val suite = scenarios.ifEmpty { LabBenchmarkScenarios.defaultSuite() }
        return suite.map { runScenario(it) }
    }

    override suspend fun history(): List<BenchmarkRecord> = repository.list()

    override suspend fun clear() = repository.clear()

    override suspend fun compareWorkers(challengeProfile: String): BenchmarkComparison {
        val baselineScenario =
            BenchmarkScenario(
                scenarioId = "compare-baseline",
                challengeProfile = challengeProfile,
                alphabetLabel = "DIGITS",
                secretLength = 3,
                knownSecret = "314",
                strategyId = "length-prioritized",
                workerCount = 1,
                maxAttempts = 2_000,
            )
        val parallelScenario = baselineScenario.copy(scenarioId = "compare-parallel", workerCount = 4)
        val baseline = runScenario(baselineScenario)
        val parallel = runScenario(parallelScenario)
        val speedup =
            if (baseline.attemptsPerSecond > 0.0) {
                parallel.attemptsPerSecond / baseline.attemptsPerSecond
            } else {
                null
            }
        val efficiency =
            speedup?.let { it / parallel.workerCount }
        return BenchmarkComparison(
            scenarioFamily = challengeProfile,
            baselineThroughput = baseline.attemptsPerSecond,
            parallelThroughput = parallel.attemptsPerSecond,
            speedup = speedup,
            workerEfficiency = efficiency,
            baseline = baseline,
            parallel = parallel,
        )
    }

    private fun alphabetOf(label: String): Alphabet =
        when (label.uppercase()) {
            "DIGITS" -> Alphabet.DIGITS
            "LOWERCASE" -> Alphabet.LOWERCASE
            "LOWER_ALPHANUMERIC" -> Alphabet.LOWER_ALPHANUMERIC
            "ALPHANUMERIC" -> Alphabet.ALPHANUMERIC
            else -> Alphabet.DIGITS
        }
}
