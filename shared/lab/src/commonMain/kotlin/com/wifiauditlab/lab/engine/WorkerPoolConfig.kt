package com.wifiauditlab.lab.engine

/**
 * Bounded worker pool. More workers are not assumed to be faster; the count is
 * clamped to a device-aware maximum so queues never grow without bound.
 */
data class WorkerPoolConfig(
    val requested: Int,
    val maxWorkers: Int = DEFAULT_MAX_WORKERS,
) {
    init {
        require(requested >= 1) { "requested workers must be >= 1" }
        require(maxWorkers >= 1) { "max workers must be >= 1" }
    }

    val workerCount: Int = requested.coerceAtMost(maxWorkers)

    companion object {
        const val DEFAULT_MAX_WORKERS: Int = 8

        /** Throughput (attempts/s) below which parallel overhead usually hurts. */
        const val LOW_THROUGHPUT_THRESHOLD: Double = 5_000.0

        /** Throughput (attempts/s) above which full CPU parallelism is justified. */
        const val HIGH_THROUGHPUT_THRESHOLD: Double = 50_000.0

        fun forDevice(
            requested: Int,
            availableProcessors: Int,
        ): WorkerPoolConfig {
            val max = availableProcessors.coerceIn(1, DEFAULT_MAX_WORKERS)
            return WorkerPoolConfig(requested.coerceAtLeast(1), max)
        }

        /**
         * Suggests a worker count from calibrated throughput and CPU count.
         * [override] wins when present (still clamped). Never uses
         * `availableProcessors` alone as the sole signal when throughput is known.
         */
        fun recommendedWorkerCount(
            availableProcessors: Int,
            calibratedAttemptsPerSecond: Double? = null,
            override: Int? = null,
            maxWorkers: Int = DEFAULT_MAX_WORKERS,
        ): Int {
            val cpuCap = availableProcessors.coerceIn(1, maxWorkers)
            if (override != null) {
                return override.coerceIn(1, cpuCap)
            }
            val throughput = calibratedAttemptsPerSecond
            val heuristic =
                when {
                    throughput == null -> (cpuCap / 2).coerceAtLeast(1)
                    throughput < LOW_THROUGHPUT_THRESHOLD -> 1
                    throughput < HIGH_THROUGHPUT_THRESHOLD -> (cpuCap / 2).coerceAtLeast(1)
                    else -> cpuCap
                }
            return heuristic.coerceIn(1, cpuCap)
        }
    }
}

data class IndexRange(
    val start: Long,
    val count: Long,
)

fun partitionIndexSpace(
    size: Long,
    workers: Int,
): List<IndexRange> {
    require(size >= 0) { "size must be non-negative" }
    if (size == 0L) return emptyList()
    val n = workers.coerceAtLeast(1)
    val base = size / n
    val remainder = size % n
    var start = 0L
    return buildList {
        repeat(n) { index ->
            val count = base + if (index < remainder) 1 else 0
            if (count > 0) add(IndexRange(start, count))
            start += count
        }
    }
}

/** Parallel engine generation selected by [WorkerAwareLabSearchEngine]. */
enum class LabParallelEngineVersion {
    /** Static partition + sequence drop (FASE 12/23 baseline parallel). */
    V1,

    /** Indexed space + dynamic range scheduler (FASE 24). */
    V2,
}
