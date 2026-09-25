package com.wifiauditlab.lab.domain.audit

/**
 * Blind search-space profile for shared Wi‑Fi secrets (PSK/SAE passphrase or WEP hex).
 * Describes mechanism class for planning only — never carries password material.
 */
enum class SharedPasswordSearchProfile {
    /** Classic WPA/WPA2-Personal PSK passphrase rules. */
    WPA2_PERSONAL_PSK,

    /** WPA3-Personal SAE passphrase (same 8–63 printable rules for local search space). */
    WPA3_PERSONAL_PSK,

    /** Transition WPA2/WPA3 personal — same PSK passphrase constraints. */
    WPA2_WPA3_TRANSITION_PSK,

    /** Generic personal PSK when family is only known as "personal". */
    WPA_PERSONAL_PSK,

    /**
     * Legacy WEP hex key entry (40-bit = 10 hex digits, 104-bit = 26 hex digits).
     * ASCII WEP passphrases of 5/13 characters are out of scope for this profile.
     */
    WEP_HEX,
}

fun SharedPasswordSearchProfile.isWifiPskProfile(): Boolean =
    when (this) {
        SharedPasswordSearchProfile.WPA2_PERSONAL_PSK,
        SharedPasswordSearchProfile.WPA3_PERSONAL_PSK,
        SharedPasswordSearchProfile.WPA2_WPA3_TRANSITION_PSK,
        SharedPasswordSearchProfile.WPA_PERSONAL_PSK,
        -> true
        SharedPasswordSearchProfile.WEP_HEX -> false
    }

fun SharedPasswordSearchProfile.isWepHexProfile(): Boolean =
    this == SharedPasswordSearchProfile.WEP_HEX
