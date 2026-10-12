package com.wifiauditlab.assessment.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes whether Lab product surfaces are visible.
 * Reuses the same preference as [LabModePreferences] (default **false**).
 */
interface LabFeatureVisibility {
    val enabled: StateFlow<Boolean>
}

/**
 * Global product preference: Lab Mode (synthetic lab, registry, one-shot real probe UI, …).
 *
 * Default is **false**. Enabling does **not** authorize a LAB network-validation session
 * by itself; the authorized-AP gate still requires connection, registry, consent, and capability.
 */
interface LabModePreferences : LabFeatureVisibility {
    fun isLabModeEnabled(): Boolean

    fun setLabModeEnabled(enabled: Boolean)
}
