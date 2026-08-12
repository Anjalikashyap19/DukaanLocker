package com.iadv.dukaanlocker.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages encrypted storage of login credentials using Android Keystore.
 * Used for biometric login - credentials are encrypted and can only be
 * decrypted after biometric authentication via CryptoObject.
 */
object BiometricCredentialManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "DukaanLockerBiometricKey"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH = 128
    private const val PREFS_NAME = "biometric_credentials"
    private const val KEY_ENCRYPTED_TOKEN = "encrypted_token"
    private const val KEY_ENCRYPTED_USER_ID = "encrypted_user_id"
    private const val KEY_ENCRYPTED_USER_NAME = "encrypted_user_name"
    private const val KEY_ENCRYPTED_EMAIL = "encrypted_email"
    private const val KEY_ENCRYPTED_ROLE = "encrypted_role"
    private const val KEY_IV = "encryption_iv"

    /**
     * Generate or retrieve the Android Keystore key.
     * This key is bound to the device's biometric/PIN authentication.
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Check if key already exists
        keyStore.getEntry(KEY_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt a single value using Android Keystore.
     * Returns encrypted bytes and IV.
     */
    private fun encryptValue(value: String): Pair<ByteArray, ByteArray> {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray())

        return Pair(encrypted, iv)
    }

    /**
     * Decrypt a single value using Android Keystore.
     */
    private fun decryptValue(encryptedData: ByteArray, iv: ByteArray): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return String(cipher.doFinal(encryptedData))
    }

    /**
     * Check if biometric login credentials are stored.
     */
    fun hasStoredCredentials(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_ENCRYPTED_TOKEN)
    }

    /**
     * Encrypt and store login credentials.
     * Each field is encrypted with its own cipher instance to avoid GCM reuse.
     * @return true if credentials were successfully stored
     */
    fun storeCredentials(
        context: Context,
        token: String,
        userId: Long,
        userName: String,
        email: String,
        role: String
    ): Boolean {
        return try {
            // Encrypt each value separately to avoid GCM cipher reuse
            val (encryptedToken, _) = encryptValue(token)
            val (encryptedUserId, _) = encryptValue(userId.toString())
            val (encryptedUserName, _) = encryptValue(userName)
            val (encryptedEmail, _) = encryptValue(email)
            val (encryptedRole, iv) = encryptValue(role)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_ENCRYPTED_TOKEN, Base64.encodeToString(encryptedToken, Base64.NO_WRAP))
                putString(KEY_ENCRYPTED_USER_ID, Base64.encodeToString(encryptedUserId, Base64.NO_WRAP))
                putString(KEY_ENCRYPTED_USER_NAME, Base64.encodeToString(encryptedUserName, Base64.NO_WRAP))
                putString(KEY_ENCRYPTED_EMAIL, Base64.encodeToString(encryptedEmail, Base64.NO_WRAP))
                putString(KEY_ENCRYPTED_ROLE, Base64.encodeToString(encryptedRole, Base64.NO_WRAP))
                putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                apply()
            }
            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Key was invalidated (e.g., biometric enrollment changed)
            clearCredentials(context)
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Decrypt and retrieve stored credentials using a CryptoObject cipher.
     * This is the SECURE method - requires biometric authentication via CryptoObject.
     *
     * @param context Android context
     * @param cryptoCipher The authenticated cipher from BiometricPrompt.CryptoObject
     * @return Decrypted credentials or null if decryption fails
     */
    fun getCredentialsWithCipher(context: Context, cryptoCipher: Cipher): BiometricCredentials? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encryptedToken = prefs.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
            val encryptedUserId = prefs.getString(KEY_ENCRYPTED_USER_ID, null) ?: return null
            val encryptedUserName = prefs.getString(KEY_ENCRYPTED_USER_NAME, null) ?: return null
            val encryptedEmail = prefs.getString(KEY_ENCRYPTED_EMAIL, null) ?: return null
            val encryptedRole = prefs.getString(KEY_ENCRYPTED_ROLE, null) ?: return null

            // Use the authenticated cipher to decrypt the first value (token)
            // For other values, we need separate cipher instances initialized with the IV
            val iv = cryptoCipher.iv

            // Decrypt token using the CryptoObject cipher
            val token = String(cryptoCipher.doFinal(Base64.decode(encryptedToken, Base64.NO_WRAP)))

            // For other fields, create new cipher instances with the same key but different IVs
            val key = getOrCreateKey()

            val userId = decryptWithKey(key,
                Base64.decode(encryptedUserId, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP)
            ).toLongOrNull() ?: -1

            val userName = decryptWithKey(key,
                Base64.decode(encryptedUserName, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP)
            )

            val email = decryptWithKey(key,
                Base64.decode(encryptedEmail, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP)
            )

            val role = decryptWithKey(key,
                Base64.decode(encryptedRole, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP)
            )

            BiometricCredentials(
                token = token,
                userId = userId,
                userName = userName,
                email = email,
                role = role
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Key was invalidated - credentials are no longer accessible
            clearCredentials(context)
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypt a value using a specific key and IV.
     */
    private fun decryptWithKey(key: SecretKey, encryptedData: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return String(cipher.doFinal(encryptedData))
    }

    /**
     * Clear stored credentials (called on logout or key invalidation).
     */
    fun clearCredentials(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}

/**
 * Data class for decrypted biometric login credentials.
 */
data class BiometricCredentials(
    val token: String,
    val userId: Long,
    val userName: String,
    val email: String,
    val role: String
)
