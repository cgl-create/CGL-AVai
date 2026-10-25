package com.cgl.friend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts every persisted value with AES-256-GCM using a non-exportable Android Keystore key.
 * API keys, chat history, imported excerpts, and user memory are stored as ciphertext in app-private
 * SharedPreferences. Android backup is disabled in the manifest to avoid restoring ciphertext
 * without the device-bound Keystore key.
 */
class SecureStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "friend_encrypted_data_v1",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getString(name: String, defaultValue: String = ""): String {
        val blob = preferences.getString(name, null) ?: return defaultValue
        return try {
            val combined = Base64.decode(blob, Base64.NO_WRAP)
            require(combined.size > IV_BYTES)
            val iv = combined.copyOfRange(0, IV_BYTES)
            val encrypted = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            defaultValue
        }
    }

    @Synchronized
    fun putString(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        preferences.edit().putString(name, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun remove(name: String) {
        preferences.edit().remove(name).apply()
    }

    @Synchronized
    fun clearAll() {
        preferences.edit().clear().apply()
        // Keep the Keystore key; there is no need to rotate it for an ordinary app data reset.
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.cgl.friend.local_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
