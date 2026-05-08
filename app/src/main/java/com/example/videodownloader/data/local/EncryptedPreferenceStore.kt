package com.example.videodownloader.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedPreferenceStore(
    context: Context,
    preferenceName: String,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun getString(key: String): String? {
        val stored = preferences.getString(key, null)?.takeIf { it.isNotBlank() } ?: return null
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            // 兼容旧版本明文 Cookie：读取后立即改写为密文。
            putString(key, stored)
            return stored
        }
        return runCatching { decrypt(stored.removePrefix(ENCRYPTED_PREFIX)) }
            .onFailure { preferences.edit().remove(key).apply() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    fun putString(key: String, value: String) {
        preferences.edit().putString(key, ENCRYPTED_PREFIX + encrypt(value)).apply()
    }

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + cipherText
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        require(bytes.size > GCM_IV_SIZE_BYTES) { "encrypted preference payload is too short" }
        val iv = bytes.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val cipherText = bytes.copyOfRange(GCM_IV_SIZE_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "video_downloader_cookie_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENCRYPTED_PREFIX = "enc:"
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
    }
}
