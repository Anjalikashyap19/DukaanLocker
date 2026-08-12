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
 *
 * Strategy: We encrypt all fields as a SINGLE concatenated string in one cipher operation.
 * This avoids the issue of reusing the cipher after doFinal() (which causes
 * IllegalBlockSizeException) and avoids creating new cipher instances (which causes
 * Key user not authenticated because biometric auth is bound to the original cipher).
 */
object BiometricCredentialManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "DukaanLockerBiometricKey"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH = 128
    private const val PREFS_NAME = "biometric_credentials"
    private const val KEY_ENCRYPTED_DATA = "encrypted_data"
    private const val KEY_IV = "encryption_iv"
    private const val DELIMITER = "|||"

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
     * Ensure the Android Keystore key exists. Creates it if needed.
     * Must be called before getCryptoCipher() on first use.
     */
    fun ensureKeyExists(): Boolean {
        return try {
            getOrCreateKey()
            true
        } catch (e: Exception) {
            android.util.Log.e("BiometricCredential", "Failed to create Keystore key", e)
            false
        }
    }

    /**
     * Check if biometric login credentials are stored.
     */
    fun hasStoredCredentials(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_ENCRYPTED_DATA)
    }

    /**
     * Encrypt and store login credentials using the authenticated CryptoObject cipher.
     *
     * Strategy: Concatenate all fields into a single string with a delimiter,
     * then encrypt it in ONE cipher operation. This avoids:
     * - IllegalBlockSizeException (cipher can only doFinal once)
     * - Key user not authenticated (new cipher instances don't have biometric auth)
     *
     * @param context Android context
     * @param cryptoCipher The authenticated cipher from BiometricPrompt.CryptoObject
     * @return true if credentials were successfully stored
     */
    fun storeCredentials(
        context: Context,
        cryptoCipher: Cipher,
        token: String,
        userId: Long,
        userName: String,
        email: String,
        role: String
    ): Boolean {
        return try {
            // Clear any stale old-format credentials first
            clearCredentials(context)
            
            // Concatenate all fields into a single string
            val plainData = listOf(token, userId.toString(), userName, email, role)
                .joinToString(DELIMITER)

            android.util.Log.d("BiometricCredential", "Storing credentials, plainData length: ${plainData.length}")

            // Encrypt the entire string in ONE operation using the authenticated cipher
            val encrypted = cryptoCipher.doFinal(plainData.toByteArray())
            val iv = cryptoCipher.iv

            android.util.Log.d("BiometricCredential", "Encryption successful, encrypted size: ${encrypted.size}, iv size: ${iv.size}")

            // Store the encrypted data and IV
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_ENCRYPTED_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                apply()
            }
            
            // Verify the write succeeded
            val verifyData = prefs.getString(KEY_ENCRYPTED_DATA, null)
            val verifyIv = prefs.getString(KEY_IV, null)
            android.util.Log.d("BiometricCredential", "Verification: data=${verifyData != null}, iv=${verifyIv != null}")
            
            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            android.util.Log.e("BiometricCredential", "KeyPermanentlyInvalidatedException", e)
            clearCredentials(context)
            false
        } catch (e: Exception) {
            android.util.Log.e("BiometricCredential", "storeCredentials failed: ${e.javaClass.simpleName}: ${e.message}", e)
            false
        }
    }

    /**
     * Decrypt and retrieve stored credentials using a CryptoObject cipher.
     *
     * Decrypts the single encrypted blob and splits by delimiter to get all fields.
     *
     * @param context Android context
     * @param cryptoCipher The authenticated cipher from BiometricPrompt.CryptoObject
     * @return Decrypted credentials or null if decryption fails
     */
    fun getCredentialsWithCipher(context: Context, cryptoCipher: Cipher): BiometricCredentials? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encryptedData = prefs.getString(KEY_ENCRYPTED_DATA, null)
            android.util.Log.d("BiometricCredential", "getCredentialsWithCipher: encryptedData=${encryptedData != null}")
            if (encryptedData == null) return null

            // The cipher is already in DECRYPT_MODE from the CryptoObject
            // Just use it directly to decrypt
            val decryptedBytes = cryptoCipher.doFinal(Base64.decode(encryptedData, Base64.NO_WRAP))
            val decrypted = String(decryptedBytes)
            android.util.Log.d("BiometricCredential", "Decryption successful, parts: ${decrypted.split(DELIMITER).size}")

            // Split by delimiter to get individual fields
            val parts = decrypted.split(DELIMITER)
            if (parts.size != 5) {
                clearCredentials(context)
                return null
            }

            BiometricCredentials(
                token = parts[0],
                userId = parts[1].toLongOrNull() ?: -1,
                userName = parts[2],
                email = parts[3],
                role = parts[4]
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearCredentials(context)
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
