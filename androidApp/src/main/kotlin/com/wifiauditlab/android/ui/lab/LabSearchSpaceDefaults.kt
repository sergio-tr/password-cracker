package com.wifiauditlab.android.ui.lab

/**
 * Soft search-space defaults for Lab UI. Protocol maxima (e.g. PSK 63) remain valid
 * Wi‑Fi limits but are not offered as practical Lab defaults — exhaustive search
 * beyond [SOFT_MAX_LENGTH] is rarely feasible on-device.
 */
object LabSearchSpaceDefaults {
    /** Practical default/soft ceiling for PSK and synthetic length ranges. */
    const val SOFT_MAX_LENGTH: Int = 16

    /** Absolute UI clamp for Advanced length fields (still far below PSK 63). */
    const val HARD_UI_MAX_LENGTH: Int = 24

    const val SYNTHETIC_MIN_LENGTH: Int = 1

    /** Capacity used only to allocate progressive stages when the user opts into until-cancelled. */
    const val UNBOUNDED_STAGE_CAPACITY: Long = 100_000_000L
}
