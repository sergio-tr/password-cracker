package com.wifiauditlab.lab.domain.audit

/**
 * Blind search-space profile for shared Wi‑Fi passphrases (PSK/SAE).
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
}
