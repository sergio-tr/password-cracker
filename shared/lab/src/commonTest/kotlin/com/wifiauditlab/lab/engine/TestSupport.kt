package com.wifiauditlab.lab.engine

import com.wifiauditlab.lab.domain.engine.CancellationSignal
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

/**
 * Deterministic time source that advances by a fixed step on every read, so the
 * engine crosses duration thresholds after a predictable number of batches.
 */
@OptIn(ExperimentalTime::class)
class AutoAdvancingTimeSource(private val stepMillis: Long) : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
    private var now = 0L
    override fun read(): Long {
        val current = now
        now += stepMillis
        return current
    }
}

/** Cancellation signal that reports cancelled after being polled [afterPolls] times. */
class CancelAfterPolls(private val afterPolls: Int) : CancellationSignal {
    private var polls = 0
    override val isCancelled: Boolean
        get() = ++polls > afterPolls
}

/** Never cancels. */
object NeverCancel : CancellationSignal {
    override val isCancelled: Boolean = false
}
