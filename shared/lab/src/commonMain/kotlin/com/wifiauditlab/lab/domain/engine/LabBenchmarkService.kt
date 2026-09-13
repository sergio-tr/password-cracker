package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.lab.domain.BenchmarkComparison
import com.wifiauditlab.lab.domain.BenchmarkRecord
import com.wifiauditlab.lab.domain.BenchmarkScenario

interface LabBenchmarkService {
    suspend fun runScenario(scenario: BenchmarkScenario): BenchmarkRecord

    suspend fun runSuite(scenarios: List<BenchmarkScenario> = emptyList()): List<BenchmarkRecord>

    suspend fun history(): List<BenchmarkRecord>

    suspend fun clear()

    suspend fun compareWorkers(
        challengeProfile: String = "digits/len=3/known",
    ): BenchmarkComparison
}
