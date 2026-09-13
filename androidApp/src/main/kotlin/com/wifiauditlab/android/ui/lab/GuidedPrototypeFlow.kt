package com.wifiauditlab.android.ui.lab

/** Steps in the guided local-prototype loop (FIX-04). */
enum class GuidedPrototypePhase {
    /** User is filling SSID, security preset, and password. */
    Configure,

    /** Defaults applied after «Crear y probar»; bottom bar START is enabled. */
    Ready,

    /** Terminal search outcome shown; post-result actions available. */
    PostResult,
}

/** Scroll/focus target after a post-result action. */
enum class GuidedFocusTarget {
    Password,
    Security,
    Start,
}
