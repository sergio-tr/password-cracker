package com.wifiauditlab.lab.domain

/**
 * One sanitized benchmark run for the synthetic lab.
 * Never stores challenge secrets or vault credentials.
 */
data class BenchmarkRecord(
    val benchmarkId: String,
    val timestampEpochMillis: Long,
    val engineVersion: String,
    val strategyId: String,
    val workerCount: Int,
    val challengeProfile: String,
    val searchSpaceExact: Long?,
    val attempts: Long,
    val durationMillis: Long,
    val attemptsPerSecond: Double,
    val cancellationLatencyMillis: Long?,
    val terminalResult: String,
) {
    init {
        require(benchmarkId.isNotBlank())
        require(engineVersion.isNotBlank())
        require(strategyId.isNotBlank())
        require(challengeProfile.isNotBlank())
        require(workerCount >= 1)
        require(attempts >= 0L)
        require(durationMillis >= 0L)
        require(attemptsPerSecond >= 0.0)
        require(terminalResult.isNotBlank())
    }
}

interface BenchmarkRepository {
    suspend fun append(record: BenchmarkRecord)

    suspend fun list(): List<BenchmarkRecord>

    suspend fun clear()
}

class InMemoryBenchmarkRepository(
    private val maxEntries: Int = 200,
) : BenchmarkRepository {
    private val records = ArrayDeque<BenchmarkRecord>()

    override suspend fun append(record: BenchmarkRecord) {
        records.addFirst(record)
        while (records.size > maxEntries) records.removeLast()
    }

    override suspend fun list(): List<BenchmarkRecord> = records.toList()

    override suspend fun clear() {
        records.clear()
    }
}

data class BenchmarkScenario(
    val scenarioId: String,
    val challengeProfile: String,
    val alphabetLabel: String,
    val secretLength: Int,
    val knownSecret: String,
    val strategyId: String,
    val workerCount: Int,
    val maxAttempts: Long,
)

data class BenchmarkComparison(
    val scenarioFamily: String,
    val baselineThroughput: Double?,
    val parallelThroughput: Double?,
    val speedup: Double?,
    val workerEfficiency: Double?,
    val baseline: BenchmarkRecord?,
    val parallel: BenchmarkRecord?,
)

object LabBenchmarkScenarios {
    fun defaultSuite(): List<BenchmarkScenario> {
        val strategies =
            listOf(
                "uniform-baseline",
                "length-prioritized",
                "tiered-alphabet",
                "synthetic-probability",
                "adaptive-synthetic",
            )
        val small =
            BenchmarkScenario(
                scenarioId = "small-digits-2",
                challengeProfile = "digits/len=2/known",
                alphabetLabel = "DIGITS",
                secretLength = 2,
                knownSecret = "42",
                strategyId = "length-prioritized",
                workerCount = 1,
                maxAttempts = 200,
            )
        val medium =
            BenchmarkScenario(
                scenarioId = "medium-digits-3",
                challengeProfile = "digits/len=3/known",
                alphabetLabel = "DIGITS",
                secretLength = 3,
                knownSecret = "107",
                strategyId = "length-prioritized",
                workerCount = 1,
                maxAttempts = 2_000,
            )
        val lowercase =
            BenchmarkScenario(
                scenarioId = "small-lower-2",
                challengeProfile = "lowercase/len=2/known",
                alphabetLabel = "LOWERCASE",
                secretLength = 2,
                knownSecret = "ab",
                strategyId = "length-prioritized",
                workerCount = 1,
                maxAttempts = 1_000,
            )
        val multiLength =
            BenchmarkScenario(
                scenarioId = "multi-length-digits",
                challengeProfile = "digits/len=2-3/known",
                alphabetLabel = "DIGITS",
                secretLength = 3,
                knownSecret = "007",
                strategyId = "length-prioritized",
                workerCount = 1,
                maxAttempts = 2_000,
            )
        val workers =
            listOf(1, 2, 4).map { count ->
                BenchmarkScenario(
                    scenarioId = "workers-$count",
                    challengeProfile = "digits/len=3/known",
                    alphabetLabel = "DIGITS",
                    secretLength = 3,
                    knownSecret = "314",
                    strategyId = "length-prioritized",
                    workerCount = count,
                    maxAttempts = 2_000,
                )
            }
        val strategyRuns =
            strategies.map { id ->
                BenchmarkScenario(
                    scenarioId = "strategy-$id",
                    challengeProfile = "digits/len=2/known",
                    alphabetLabel = "DIGITS",
                    secretLength = 2,
                    knownSecret = "77",
                    strategyId = id,
                    workerCount = 1,
                    maxAttempts = 500,
                )
            }
        return listOf(small, medium, lowercase, multiLength) + workers + strategyRuns
    }
}

fun newBenchmarkId(nowMillis: Long): String = "bm-$nowMillis-${(0..9999).random()}"
