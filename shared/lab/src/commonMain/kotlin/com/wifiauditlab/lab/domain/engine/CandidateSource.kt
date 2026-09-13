package com.wifiauditlab.lab.domain.engine

import com.wifiauditlab.core.math.CombinationCount

/**
 * Produces candidates lazily and incrementally. Implementations must never
 * materialize the whole space: memory usage stays O(candidate length), not
 * O(search-space size). Enumeration is deterministic for a given configuration.
 */
interface CandidateSource {
    /** Exact number of candidates this source will yield. */
    val size: CombinationCount

    /** A cold, lazy sequence over every candidate. */
    fun candidates(): Sequence<String>
}

/** Decides whether a candidate is the solution. In the lab this is an exact match. */
fun interface CandidateVerifier {
    fun verify(candidate: String): Boolean
}

/** Cooperative stop signal checked by the engine between batches. */
interface CancellationSignal {
    val isCancelled: Boolean
}

/**
 * Externally controllable [CancellationSignal]. Backed by a coroutine-safe flag so
 * the UI thread can request a stop while the engine runs on a worker dispatcher.
 */
class CancellationController : CancellationSignal {
    private val cancelled = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val isCancelled: Boolean get() = cancelled.value

    fun cancel() {
        cancelled.value = true
    }
}

/**
 * Cooperative pause signal. Distinct from [CancellationSignal]: pause ends the
 * flow with [com.wifiauditlab.lab.domain.LabSearchEvent.Paused] and a resumable
 * cursor; cancel ends with [com.wifiauditlab.lab.domain.LabSearchEvent.Cancelled].
 */
interface PauseSignal {
    val isPauseRequested: Boolean
}

/** Default: never pause. */
object NeverPause : PauseSignal {
    override val isPauseRequested: Boolean = false
}

/** Externally controllable [PauseSignal] for Lab UI Pause / Resume. */
class PauseController : PauseSignal {
    private val paused = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val isPauseRequested: Boolean get() = paused.value

    fun pause() {
        paused.value = true
    }

    fun reset() {
        paused.value = false
    }
}
