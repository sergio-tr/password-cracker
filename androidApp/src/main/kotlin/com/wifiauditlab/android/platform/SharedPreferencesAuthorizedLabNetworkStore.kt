package com.wifiauditlab.android.platform

import android.content.Context
import com.wifiauditlab.assessment.domain.audit.AuthorizedLabNetworkKey
import com.wifiauditlab.assessment.domain.audit.AuthorizedLabNetworkStore
import com.wifiauditlab.assessment.domain.wifi.SecurityFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists explicit lab/authorized markings for [com.wifiauditlab.assessment.domain.audit.VerificationMode.LAB_NETWORK_VALIDATION].
 * Keys are `ssid|FAMILY` (SSID trimmed).
 */
class SharedPreferencesAuthorizedLabNetworkStore(
    context: Context,
) : AuthorizedLabNetworkStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun isAuthorized(key: AuthorizedLabNetworkKey): Boolean =
        withContext(Dispatchers.IO) {
            prefs.getStringSet(KEY_SET, emptySet())?.contains(encode(key)) == true
        }

    override suspend fun setAuthorized(
        key: AuthorizedLabNetworkKey,
        authorized: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            val current = prefs.getStringSet(KEY_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
            val encoded = encode(key)
            if (authorized) current += encoded else current -= encoded
            prefs.edit().putStringSet(KEY_SET, current).apply()
        }
    }

    override suspend fun authorizedKeys(): Set<AuthorizedLabNetworkKey> =
        withContext(Dispatchers.IO) {
            prefs.getStringSet(KEY_SET, emptySet())
                ?.mapNotNull { decode(it) }
                ?.toSet()
                .orEmpty()
        }

    private fun encode(key: AuthorizedLabNetworkKey): String =
        "${key.ssid}|${key.securityFamily.name}"

    private fun decode(raw: String): AuthorizedLabNetworkKey? {
        val sep = raw.lastIndexOf('|')
        if (sep <= 0 || sep >= raw.length - 1) return null
        val ssid = raw.substring(0, sep)
        val family =
            runCatching { SecurityFamily.valueOf(raw.substring(sep + 1)) }.getOrNull()
                ?: return null
        return AuthorizedLabNetworkKey.of(ssid, family)
    }

    companion object {
        private const val PREFS = "authorized_lab_networks"
        private const val KEY_SET = "keys"
    }
}
