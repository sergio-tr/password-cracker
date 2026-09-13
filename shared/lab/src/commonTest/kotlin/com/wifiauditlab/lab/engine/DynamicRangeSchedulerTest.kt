package com.wifiauditlab.lab.engine

import com.wifiauditlab.core.math.CombinationCount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicRangeSchedulerTest {
    @Test
    fun claims_cover_the_space_exactly_once() =
        runTest {
            val total = CombinationCount.of(100)
            val scheduler = DynamicRangeScheduler(total, CombinationCount.of(7))
            val ranges =
                buildList {
                    while (true) {
                        val next = scheduler.claimNext() ?: break
                        add(next)
                    }
                }
            assertTrue(ranges.isNotEmpty())
            assertEquals(CombinationCount.ZERO, ranges.first().startInclusive)
            assertEquals(total, ranges.last().endExclusive)
            assertEquals(total, ranges.fold(CombinationCount.ZERO) { acc, r -> acc + r.count })
            assertEquals(total, scheduler.issuedCount())
            assertTrue(scheduler.isExhausted())
            assertNull(scheduler.claimNext())
        }

    @Test
    fun concurrent_workers_see_no_gaps_or_duplicates() =
        runTest {
            val total = CombinationCount.of(1_000)
            val scheduler = DynamicRangeScheduler(total, CombinationCount.of(16))
            val claimed =
                coroutineScope {
                    (0 until 8).map {
                        async {
                            buildList {
                                while (true) {
                                    val range = scheduler.claimNext() ?: break
                                    var cursor = range.startInclusive
                                    while (cursor < range.endExclusive) {
                                        add(cursor.toLongOrNull()!!)
                                        cursor += 1L
                                    }
                                }
                            }
                        }
                    }.awaitAll().flatten()
                }
            assertEquals(1_000, claimed.size)
            assertEquals(1_000, claimed.toSet().size)
            assertEquals((0L until 1_000L).toList(), claimed.sorted())
            assertEquals(total, scheduler.issuedCount())
        }

    @Test
    fun cancel_stops_further_claims() =
        runTest {
            val scheduler =
                DynamicRangeScheduler(CombinationCount.of(100), CombinationCount.of(10))
            val first = scheduler.claimNext()
            assertEquals(CombinationCount.ZERO, first!!.startInclusive)
            scheduler.cancel()
            assertNull(scheduler.claimNext())
            assertTrue(scheduler.isCancelled())
        }
}
