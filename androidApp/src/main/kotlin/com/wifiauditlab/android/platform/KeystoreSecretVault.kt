package com.wifiauditlab.android.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wifiauditlab.assessment.domain.vault.NetworkSecret
import com.wifiauditlab.assessment.domain.vault.SecretId
import com.wifiauditlab.assessment.port.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecretVault] backed by the Android Keystore.
 *
 * The AES-GCM key is generated inside the hardware-backed keystore and never
 * leaves it; only the ciphertext (IV + tag + data, Base64-encoded) is persisted
 * in private shared preferences. Plaintext is handled transiently and never
 * logged. This is the Android implementation of the platform-agnostic port; iOS
 * will provide a Keychain-backed equivalent.
 */
class KeystoreSecretVault(context: Context) : SecretVault {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun create(secret: NetworkSecret): SecretId =
        withContext(Dispatchers.IO) {
            val id = SecretId.random()
            prefs.edit().putString(id.value, encrypt(secret.value)).apply()
            id
        }

    override suspend fun read(id: SecretId): NetworkSecret? =
        withContext(Dispatchers.IO) {
            prefs.getString(id.value, null)?.let { NetworkSecret(decrypt(it)) }
        }

    override suspend fun update(
        id: SecretId,
        secret: NetworkSecret,
    ) =
        withContext(Dispatchers.IO) {
            prefs.edit().putString(id.value, encrypt(secret.value)).apply()
        }

    override suspend fun delete(id: SecretId) =
        withContext(Dispatchers.IO) {
            prefs.edit().remove(id.value).apply()
        }

    private fun getOrCreateKey(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val combined = Base64.decode(stored, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }

    private companion object {
        const val PREFS = "secret_vault"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wifi_audit_lab_secret_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
