package com.arcx.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arcx.core.data.di.KeyVaultDataStore
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for BYOK API keys.
 *
 * The master key never leaves the AndroidKeyStore, so the ciphertext sitting on disk is useless
 * on any other device — an adb backup, a copied `/data` image or a synced file gives an attacker
 * nothing. Only the ciphertext is persisted here, and nothing in this class ever writes a key
 * into Room or into the settings preferences.
 *
 * No user-authentication requirement is set on the key: workflows fire from a share sheet or a
 * text-selection popup, and demanding a biometric prompt mid-run would break every entry point.
 */
@Singleton
class KeystoreVault @Inject constructor(
    @KeyVaultDataStore private val dataStore: DataStore<Preferences>,
) {

    suspend fun put(providerId: String, key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val ciphertext = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        dataStore.edit { it[entryKey(providerId)] = Base64.encodeToString(payload, Base64.NO_WRAP) }
    }

    /**
     * Returns null rather than throwing when the stored blob cannot be decrypted. A factory reset
     * or a restore onto a new device leaves the ciphertext behind while destroying the Keystore
     * key that made sense of it; the user simply has to paste the key again, which is not a crash.
     */
    suspend fun get(providerId: String): String? {
        val stored = dataStore.data.first()[entryKey(providerId)] ?: return null
        return try {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            require(payload.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                masterKey(),
                GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES),
            )
            String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            discardUnreadable(providerId, e)
            null
        } catch (e: IllegalArgumentException) {
            discardUnreadable(providerId, e)
            null
        }
    }

    suspend fun remove(providerId: String) {
        dataStore.edit { it.remove(entryKey(providerId)) }
    }

    suspend fun has(providerId: String): Boolean =
        dataStore.data.first().contains(entryKey(providerId))

    private suspend fun discardUnreadable(providerId: String, cause: Throwable) {
        Log.w(TAG, "Discarding unreadable key for $providerId: ${cause.javaClass.simpleName}")
        remove(providerId)
    }

    /** Synchronized so two concurrent runs cannot both generate the alias and race to store it. */
    @Synchronized
    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun entryKey(providerId: String) = stringPreferencesKey("$ENTRY_PREFIX$providerId")

    private companion object {
        const val TAG = "KeystoreVault"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "arcx_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        // GCM's canonical IV length; the platform generates it, we only prefix it to the blob.
        const val IV_BYTES = 12
        const val ENTRY_PREFIX = "provider_key_"
    }
}
