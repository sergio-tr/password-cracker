package com.wifiauditlab.assessment.domain.audit

/**
 * Single-candidate network validation probe (platform).
 *
 * Product name from the plan: NetworkValidationAdapter.
 * Must never be used as the high-throughput [com.wifiauditlab.lab.domain.engine.CandidateVerifier]
 * for the parallel search engine. See ADR-004.
 *
 * F0: production [AndroidValidationAdapter] reports Unavailable (platform limit).
 * F1+: real [verifyOnce] via WifiNetworkSpecifier path when capability allows.
 */
interface NetworkValidationAdapter {
    suspend fun capability(): ApAuthCapability

    /**
     * Attempts one passphrase against the **current** connection target described
     * by [request]. Implementations must re-check connection identity and fail closed.
     * F0 adapters must not perform active AP authentication.
     */
    suspend fun validateOnce(request: ApAuthProbeRequest): ApAuthProbeResult
}

/** ADR-004 name retained as alias of [NetworkValidationAdapter]. */
typealias PlatformApAuthProbe = NetworkValidationAdapter

data class ApAuthProbeRequest(
    val ssid: String,
    val bssidHint: String?,
    val family: com.wifiauditlab.assessment.domain.wifi.SecurityFamily,
    val passphrase: String,
) {
    override fun toString(): String =
        "ApAuthProbeRequest(ssid=$ssid, bssidHint=$bssidHint, family=$family, passphrase=••••••••)"
}

sealed interface ApAuthProbeResult {
    data object Accepted : ApAuthProbeResult

    data object Rejected : ApAuthProbeResult

    data class Unavailable(
        val reason: ApAuthUnavailableReason,
    ) : ApAuthProbeResult

    data class Aborted(
        val reason: ApAuthProbeAbortReason,
    ) : ApAuthProbeResult
}

enum class ApAuthProbeAbortReason {
    ConnectionChanged,
    Cancelled,
    GateDenied,
    Error,
}

/**
 * JVM / unit-test adapter. Never talks to a real AP.
 * Default capability is Unavailable so production-like tests stay fail-closed unless
 * a test explicitly constructs [SimulatedValidationAdapter] with Available.
 */
class SimulatedValidationAdapter(
    private val capabilityValue: ApAuthCapability =
        ApAuthCapability.Unavailable(ApAuthUnavailableReason.NotImplemented),
    private val result: ApAuthProbeResult =
        ApAuthProbeResult.Unavailable(ApAuthUnavailableReason.NotImplemented),
) : NetworkValidationAdapter {
    override suspend fun capability(): ApAuthCapability = capabilityValue

    override suspend fun validateOnce(request: ApAuthProbeRequest): ApAuthProbeResult = result
}

/**
 * Stock Android / Play build (F0): AP validation path not available.
 * Documents [ApAuthUnavailableReason.PlatformApiLimitation] — no silent/batch AP auth API.
 */
class AndroidValidationAdapter(
    private val reason: ApAuthUnavailableReason = ApAuthUnavailableReason.PlatformApiLimitation,
) : NetworkValidationAdapter {
    override suspend fun capability(): ApAuthCapability = ApAuthCapability.Unavailable(reason)

    override suspend fun validateOnce(request: ApAuthProbeRequest): ApAuthProbeResult =
        ApAuthProbeResult.Unavailable(reason)
}

/** ADR-004 name retained as alias of [AndroidValidationAdapter]. */
typealias UnavailablePlatformApAuthProbe = AndroidValidationAdapter
