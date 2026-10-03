package com.wifiauditlab.assessment.port

/**
 * Global product preference: experimental lab-network validation features.
 *
 * Default is **false**. Enabling this does **not** authorize a LAB session by itself;
 * [com.wifiauditlab.assessment.domain.audit.AuthorizedApTestGate] still requires
 * current network, registry, consent, and capability.
 */
interface LabModePreferences {
    fun isLabModeEnabled(): Boolean

    fun setLabModeEnabled(enabled: Boolean)
}
