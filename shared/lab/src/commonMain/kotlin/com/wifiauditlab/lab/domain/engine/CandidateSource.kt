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

/**
 * Verification boundary used by the search engine.
 *
 * Current supported implementations verify candidates locally
 * ([com.wifiauditlab.lab.domain.EncapsulatedPasswordVerifier],
 * [SyntheticExactMatchVerifier], or challenge-internal secrets via
 * `LabChallenge.asVerifier()`).
 *
 * A different verification backend can be introduced behind this
 * interface without changing the search engine, scheduler or
 * candidate generation pipeline.
 *
 * Network-backed automated candidate verification is intentionally
 * not implemented in this project — see [UnsupportedNetworkCandidateVerifier]
 * and [CandidateVerificationBackend.NetworkBackedUnsupported].
 *
 * The Search Engine must never import Android Wi‑Fi APIs
 * (`WifiManager`, `ConnectivityManager`, `NetworkValidationAdapter`)
 * or `:shared:assessment`.
 */
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
