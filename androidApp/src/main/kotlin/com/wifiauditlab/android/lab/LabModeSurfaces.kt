package com.wifiauditlab.android.lab

/**
 * Single place that answers "is this Lab product surface visible?".
 *
 * Lab Mode OFF **hides** Lab UI; it does **not** remove Lab code, DI wiring,
 * Search Engine, Guided/Advanced flows, calibration, or benchmarks from the app.
 * When Lab Mode is turned ON again, the same surfaces reappear at runtime.
 */
object LabModeSurfaces {
    fun showLabTab(labModeEnabled: Boolean): Boolean = labModeEnabled

    fun showLabEntryCtas(labModeEnabled: Boolean): Boolean = labModeEnabled

    fun showLabRegistryActions(labModeEnabled: Boolean): Boolean = labModeEnabled

    fun showLabSettingsExtras(labModeEnabled: Boolean): Boolean = labModeEnabled

    fun showLabVerificationOptions(labModeEnabled: Boolean): Boolean = labModeEnabled

    /** Safe top-level route when leaving a Lab destination after Lab Mode is disabled. */
    const val SAFE_TOP_LEVEL_AFTER_LAB_HIDDEN: String = "nearby"
}
