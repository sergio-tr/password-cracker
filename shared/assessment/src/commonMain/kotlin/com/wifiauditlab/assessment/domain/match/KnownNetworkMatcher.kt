package com.wifiauditlab.assessment.domain.match

import com.wifiauditlab.assessment.domain.vault.SavedWifiNetwork
import com.wifiauditlab.assessment.domain.wifi.WifiObservation

/** Confidence behind a probable match. */
enum class MatchConfidence { EXACT_BSSID, SSID_AND_FAMILY, SSID_ONLY }

/**
 * Outcome of matching an observation against saved networks. Ambiguity is a
 * first-class result: the app never silently picks one of several candidates.
 */
sealed interface NetworkMatchResult {
    data class Exact(val network: SavedWifiNetwork) : NetworkMatchResult

    data class Probable(
        val network: SavedWifiNetwork,
        val confidence: MatchConfidence,
        val reason: String,
    ) : NetworkMatchResult

    data object Unknown : NetworkMatchResult

    data class Ambiguous(val candidates: List<SavedWifiNetwork>) : NetworkMatchResult
}

interface KnownNetworkMatcher {
    fun match(
        observation: WifiObservation,
        candidates: Collection<SavedWifiNetwork>,
    ): NetworkMatchResult
}

/**
 * Default matcher. Identity is SSID + security family; a known BSSID is a strong
 * additional signal but not the identity, because one saved network may span
 * several access points.
 */
class DefaultKnownNetworkMatcher : KnownNetworkMatcher {
    override fun match(
        observation: WifiObservation,
        candidates: Collection<SavedWifiNetwork>,
    ): NetworkMatchResult {
        val ssid = observation.identity.normalizedSsid
        val family = observation.securityProfile.family

        val sameSsid = candidates.filter { it.identity.normalizedSsid == ssid }
        if (sameSsid.isEmpty()) return NetworkMatchResult.Unknown

        val bssidHits = sameSsid.filter { observation.bssid in it.knownBssids }
        when (bssidHits.size) {
            1 -> return NetworkMatchResult.Exact(bssidHits.single())
            in 2..Int.MAX_VALUE -> return NetworkMatchResult.Ambiguous(bssidHits)
        }

        val familyMatches = sameSsid.filter { it.securityFamily == family }
        return when {
            familyMatches.size == 1 ->
                NetworkMatchResult.Probable(
                    familyMatches.single(),
                    MatchConfidence.SSID_AND_FAMILY,
                    "El SSID y la familia de seguridad coinciden, pero este punto de acceso es nuevo.",
                )
            familyMatches.size > 1 -> NetworkMatchResult.Ambiguous(familyMatches)
            sameSsid.size == 1 ->
                NetworkMatchResult.Probable(
                    sameSsid.single(),
                    MatchConfidence.SSID_ONLY,
                    "El SSID coincide, pero la seguridad observada difiere de la guardada.",
                )
            else -> NetworkMatchResult.Ambiguous(sameSsid)
        }
    }
}
