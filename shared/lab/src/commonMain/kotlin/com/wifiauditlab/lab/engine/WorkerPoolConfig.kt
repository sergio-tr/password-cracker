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

        fun forDevice(
            requested: Int,
            availableProcessors: Int,
        ): WorkerPoolConfig {
            val max = availableProcessors.coerceIn(1, DEFAULT_MAX_WORKERS)
            return WorkerPoolConfig(requested.coerceAtLeast(1), max)
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
