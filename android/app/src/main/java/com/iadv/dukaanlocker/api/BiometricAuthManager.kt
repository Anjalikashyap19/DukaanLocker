package com.iadv.dukaanlocker.api

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages biometric (fingerprint/face) and device credential (PIN/pattern) authentication.
 * Uses Jetpack Biometric library for secure authentication.
 *
 * Two modes:
 * 1. App Unlock (device credential) - for mandatory app unlock on every launch
 * 2. Biometric Login (with CryptoObject) - for secure auto-login after logout
 */
class BiometricAuthManager(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    /**
     * Callback interface for biometric authentication results.
     */
    interface BiometricCallback {
        fun onBiometricSuccess()
        fun onBiometricError(errorCode: Int, errorMessage: String)
        fun onBiometricFailed()
    }

    /**
     * Callback interface for biometric authentication with CryptoObject result.
     * Used for biometric login - provides the authenticated cipher for Keystore decryption.
     */
    interface CryptoBiometricCallback {
        fun onBiometricSuccess(cryptoObject: BiometricPrompt.CryptoObject)
        fun onBiometricError(errorCode: Int, errorMessage: String)
        fun onBiometricFailed()
    }

    /**
     * Check if biometric authentication is available on this device.
     * @return true if biometric authentication is available
     */
    fun isBiometricAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Check if device has any authentication method (biometric OR device credential).
     * Used for mandatory app unlock.
     * @return true if biometric or device credential is available
     */
    fun isDeviceSecure(): Boolean {
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Get the reason why biometric is not available.
     * @return A human-readable error message, or null if biometric is available
     */
    fun getBiometricUnavailableReason(): String? {
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware available"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics enrolled. Please set up fingerprint or face unlock in device settings"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Security update required for biometric"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Biometric not supported on this device"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "Unknown biometric status"
            else -> "Biometric not available"
        }
    }

    // ── Get Keystore cipher for CryptoObject ──────────────────────────────────

    /**
     * Get the Android Keystore key used for biometric credential encryption.
     * This key is bound to biometric authentication.
     */
    private fun getKeystoreKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = keyStore.getEntry(BiometricCredentialManager.KEY_ALIAS, null)
            (entry as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a Cipher initialized with the Keystore key for encryption.
     * Used when creating the CryptoObject for biometric authentication.
     */
    fun getCryptoCipher(): Cipher? {
        return try {
            val key = getKeystoreKey() ?: return null
            val cipher = Cipher.getInstance(BiometricCredentialManager.TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a Cipher initialized with the Keystore key for decryption.
     * Used after biometric success to decrypt stored credentials.
     * @param iv The initialization vector used during encryption
     */
    fun getDecryptionCipher(iv: ByteArray): Cipher? {
        return try {
            val key = getKeystoreKey() ?: return null
            val cipher = Cipher.getInstance(BiometricCredentialManager.TRANSFORMATION)
            val spec = GCMParameterSpec(BiometricCredentialManager.GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            cipher
        } catch (e: Exception) {
            null
        }
    }

    // ── Biometric Login (with CryptoObject) ───────────────────────────────────

    /**
     * Show biometric authentication prompt WITH CryptoObject.
     * This is used for biometric login - the CryptoObject cryptographically
     * binds the authentication to the Android Keystore key.
     *
     * After successful authentication, the CryptoObject in the result contains
     * the authenticated cipher that can be used to decrypt Keystore-encrypted data.
     *
     * @param activity The activity to show the prompt in (must be FragmentActivity)
     * @param title Title of the biometric prompt
     * @param subtitle Subtitle/description of the prompt
     * @param callback Callback with CryptoObject on success
     */
    fun authenticateWithCrypto(
        activity: FragmentActivity,
        title: String = "Biometric Login",
        subtitle: String = "Use your fingerprint to sign in",
        callback: CryptoBiometricCallback
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        // Create cipher with Keystore key for CryptoObject
        val cipher = getCryptoCipher()
        if (cipher == null) {
            callback.onBiometricError(0, "Failed to initialize biometric key. Please try again.")
            return
        }

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)

        val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val crypto = result.cryptoObject
                if (crypto != null) {
                    callback.onBiometricSuccess(crypto)
                } else {
                    callback.onBiometricError(0, "Authentication succeeded but crypto object is null")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback.onBiometricError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onBiometricFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, biometricCallback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }

    // ── Biometric Login (with CryptoObject + lambda callbacks) ────────────────

    /**
     * Convenience method for biometric login with lambda callbacks.
     * Returns the CryptoObject on success for Keystore encryption.
     */
    fun authenticateWithCrypto(
        activity: FragmentActivity,
        title: String = "Biometric Login",
        subtitle: String = "Use your fingerprint to sign in",
        onSuccess: (BiometricPrompt.CryptoObject) -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        authenticateWithCrypto(
            activity = activity,
            title = title,
            subtitle = subtitle,
            callback = object : CryptoBiometricCallback {
                override fun onBiometricSuccess(cryptoObject: BiometricPrompt.CryptoObject) = onSuccess(cryptoObject)
                override fun onBiometricError(errorCode: Int, errorMessage: String) = onError(errorMessage)
                override fun onBiometricFailed() = onFailed()
            }
        )
    }

    // ── Biometric Login with DECRYPT mode (for decryption after biometric auth) ──

    /**
     * Show biometric authentication prompt WITH a DECRYPT_MODE CryptoObject.
     * This is used for biometric login - the CryptoObject cryptographically
     * binds the authentication to the Android Keystore key for DECRYPTION.
     *
     * @param activity The activity to show the prompt in (must be FragmentActivity)
     * @param iv The initialization vector used during encryption (stored in SharedPreferences)
     * @param title Title of the biometric prompt
     * @param subtitle Subtitle/description of the prompt
     * @param callback Callback with CryptoObject on success
     */
    fun authenticateWithDecryptCrypto(
        activity: FragmentActivity,
        iv: ByteArray,
        title: String = "Biometric Login",
        subtitle: String = "Use your fingerprint to sign in",
        callback: CryptoBiometricCallback
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        // Create cipher with Keystore key for DECRYPTION
        val cipher = getDecryptionCipher(iv)
        if (cipher == null) {
            callback.onBiometricError(0, "Failed to initialize biometric key. Please try again.")
            return
        }

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)

        val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val crypto = result.cryptoObject
                if (crypto != null) {
                    callback.onBiometricSuccess(crypto)
                } else {
                    callback.onBiometricError(0, "Authentication succeeded but crypto object is null")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback.onBiometricError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onBiometricFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, biometricCallback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }

    /**
     * Convenience method for biometric login (decryption) with lambda callbacks.
     */
    fun authenticateWithDecryptCrypto(
        activity: FragmentActivity,
        iv: ByteArray,
        title: String = "Biometric Login",
        subtitle: String = "Use your fingerprint to sign in",
        onSuccess: (BiometricPrompt.CryptoObject) -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        authenticateWithDecryptCrypto(
            activity = activity,
            iv = iv,
            title = title,
            subtitle = subtitle,
            callback = object : CryptoBiometricCallback {
                override fun onBiometricSuccess(cryptoObject: BiometricPrompt.CryptoObject) = onSuccess(cryptoObject)
                override fun onBiometricError(errorCode: Int, errorMessage: String) = onError(errorMessage)
                override fun onBiometricFailed() = onFailed()
            }
        )
    }

    // ── App Unlock (without CryptoObject) ─────────────────────────────────────

    /**
     * Show biometric authentication prompt (for biometric login feature).
     * Uses biometric only - no fallback to PIN/pattern.
     * @param activity The activity to show the prompt in (must be FragmentActivity)
     * @param title Title of the biometric prompt
     * @param subtitle Subtitle/description of the prompt
     * @param callback Callback for authentication results
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Verify your identity to continue",
        callback: BiometricCallback
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback.onBiometricSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback.onBiometricError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onBiometricFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, biometricCallback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Show device authentication prompt (for mandatory app unlock).
     * Accepts biometric OR device credential (PIN/pattern/password).
     * @param activity The activity to show the prompt in (must be FragmentActivity)
     * @param title Title of the authentication prompt
     * @param subtitle Subtitle/description of the prompt
     * @param callback Callback for authentication results
     */
    fun authenticateWithDeviceCredential(
        activity: FragmentActivity,
        title: String = "Unlock Dukaan Locker",
        subtitle: String = "Verify your identity to continue",
        callback: BiometricCallback
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback.onBiometricSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback.onBiometricError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onBiometricFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, biometricCallback)

        // Use DEVICE_CREDENTIAL so it accepts biometric, PIN, pattern, or password
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // ── Convenience methods with lambda callbacks ─────────────────────────────

    /**
     * Show the biometric authentication prompt with lambda callbacks.
     * This is a convenience method for simpler usage.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Authentication",
        subtitle: String = "Verify your identity to continue",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            callback = object : BiometricCallback {
                override fun onBiometricSuccess() = onSuccess()
                override fun onBiometricError(errorCode: Int, errorMessage: String) = onError(errorMessage)
                override fun onBiometricFailed() = onFailed()
            }
        )
    }

    /**
     * Show device authentication with lambda callbacks.
     * Convenience method for app unlock flow.
     */
    fun authenticateWithDeviceCredential(
        activity: FragmentActivity,
        title: String = "Unlock Dukaan Locker",
        subtitle: String = "Verify your identity to continue",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        authenticateWithDeviceCredential(
            activity = activity,
            title = title,
            subtitle = subtitle,
            callback = object : BiometricCallback {
                override fun onBiometricSuccess() = onSuccess()
                override fun onBiometricError(errorCode: Int, errorMessage: String) = onError(errorMessage)
                override fun onBiometricFailed() = onFailed()
            }
        )
    }
}
