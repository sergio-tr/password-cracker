package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import com.wifiauditlab.lab.domain.engine.CancellationController
import com.wifiauditlab.lab.domain.engine.DefaultSearchMetricsCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SearchMetricsCollectorTest {
    @Test
    fun snapshot_exposes_rate_percentage_and_remaining() {
        val collector = DefaultSearchMetricsCollector(totalBuckets = 2, searchSpace = CombinationCount.of(100))
        collector.record(totalAttempts = 25, elapsed = 1.seconds, currentBucketIndex = 1)
        val snapshot = collector.snapshot()
        assertEquals(CombinationCount.of(25), snapshot.attempts)
        assertEquals(25.0, snapshot.attemptsPerSecond)
        assertEquals(25.0, snapshot.processedPercentage)
        assertEquals(1, snapshot.currentBucketIndex)
        assertEquals(2, snapshot.totalBuckets)
        assertTrue(snapshot.estimatedRemaining != null)
    }

    @Test
    fun cancellation_controller_is_off_until_cancel() {
        val controller = CancellationController()
        assertFalse(controller.isCancelled)
        controller.cancel()
        assertTrue(controller.isCancelled)
        controller.cancel()
        assertTrue(controller.isCancelled)
    }
}
