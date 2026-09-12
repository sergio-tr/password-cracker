package com.wifiauditlab.android.instrumentation

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifiauditlab.android.platform.KeystoreSecretVault
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0: Keystore-backed vault on a real device/emulator.
 * Never logs plaintext; assertions use redacted failure messages.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretVaultInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "secret_vault"
    private val plaintext = "instrumentation-secret-01"
    private val updatedPlaintext = "instrumentation-secret-02"

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

            assertTrue("toString must be redacted", secret.toString() == NetworkSecret.REDACTED)
            assertFalse("toString must not leak plaintext", secret.toString().contains(plaintext))

            val id = vault.create(secret)
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val stored = prefs.getString(id.value, null)
            assertNotNull("ciphertext must be persisted", stored)
            assertTrue("ciphertext must differ from plaintext", stored != plaintext)

            val readBack = vault.read(id)
            assertNotNull("read must return a secret", readBack)
            assertTrue("round-trip mismatch (values redacted)", readBack!!.value == plaintext)
            assertTrue("read toString must be redacted", readBack.toString() == NetworkSecret.REDACTED)

            vault.update(id, NetworkSecret(updatedPlaintext))
            val afterUpdate = prefs.getString(id.value, null)
            assertNotNull(afterUpdate)
            assertTrue("updated ciphertext must differ from new plaintext", afterUpdate != updatedPlaintext)
            assertTrue("updated ciphertext must differ from old plaintext", afterUpdate != plaintext)
            assertTrue(
                "update round-trip mismatch (values redacted)",
                vault.read(id)!!.value == updatedPlaintext,
            )

            val vault2 = KeystoreSecretVault(context)
            assertTrue(
                "recreated adapter must decrypt (values redacted)",
                vault2.read(id)!!.value == updatedPlaintext,
            )

            vault2.delete(id)
            assertNull(vault2.read(id))
            assertNull(prefs.getString(id.value, null))
        }
}
