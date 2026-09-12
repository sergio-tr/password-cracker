package com.wifiauditlab.android.instrumentation

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.platform.KeystoreSecretVault
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0: Keystore-backed vault on a real device/emulator.
 * Never logs the secret plaintext.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretVaultInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "secret_vault"
    private val plaintext = "instrumentation-secret-01"

    @Before
    fun clearPrefsBefore() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun clearPrefsAfter() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun create_read_update_delete_and_ciphertext_not_plaintext() =
        runBlocking {
            val vault = KeystoreSecretVault(context)
            val secret = NetworkSecret(plaintext)

            assertEquals(NetworkSecret.REDACTED, secret.toString())
            assertFalse(secret.toString().contains(plaintext))

            val id = vault.create(secret)
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val stored = prefs.getString(id.value, null)
            assertNotNull(stored)
            assertNotEquals(plaintext, stored)

            val readBack = vault.read(id)
            assertNotNull(readBack)
            assertEquals(plaintext, readBack!!.value)
            assertEquals(NetworkSecret.REDACTED, readBack.toString())
            assertFalse(readBack.toString().contains(plaintext))

            vault.update(id, NetworkSecret("instrumentation-secret-02"))
            val afterUpdate = prefs.getString(id.value, null)
            assertNotNull(afterUpdate)
            assertNotEquals("instrumentation-secret-02", afterUpdate)
            assertNotEquals(plaintext, afterUpdate)
            assertEquals("instrumentation-secret-02", vault.read(id)!!.value)

            // Fresh instance must still decrypt with the same keystore key + prefs.
            val vault2 = KeystoreSecretVault(context)
            assertEquals("instrumentation-secret-02", vault2.read(id)!!.value)

            vault2.delete(id)
            assertNull(vault2.read(id))
            assertNull(prefs.getString(id.value, null))
        }
}
