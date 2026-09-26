package com.wifiauditlab.android.ui.lab

/**
 * One-shot plaintext seed for Lab LocalPrototype (e.g. Vault reveal → Lab).
 * Cleared on consume; never logged.
 */
class LabPasswordSeedStore {
    @Volatile
    private var seed: String? = null

    fun set(password: String) {
        require(password.isNotEmpty()) { "password seed must not be empty" }
        seed = password
    }

    fun clear() {
        seed = null
    }

    /** Returns and clears the pending password, or null if none. */
    fun consume(): String? {
        val value = seed
        seed = null
        return value
    }
}
