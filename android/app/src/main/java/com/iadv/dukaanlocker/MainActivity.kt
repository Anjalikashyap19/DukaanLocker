package com.iadv.dukaanlocker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.iadv.dukaanlocker.api.BiometricAuthManager
import com.iadv.dukaanlocker.api.BiometricCredentialManager
import com.iadv.dukaanlocker.api.GoogleSignInHelper
import com.iadv.dukaanlocker.api.ApiClient
import com.iadv.dukaanlocker.api.GoogleRegisterRequest
import com.iadv.dukaanlocker.api.parseErrorMessage
import kotlinx.coroutines.launch
import java.util.Locale

// Use FragmentActivity (not ComponentActivity) so BiometricPrompt works
class MainActivity : FragmentActivity() {

    lateinit var googleSignInHelper: GoogleSignInHelper
        private set
    lateinit var biometricAuthManager: BiometricAuthManager
        private set

    // Callbacks for Google Sign-Up result
    private var onGoogleSignUpSuccess: ((token: String, userId: Long, userName: String, email: String, role: String) -> Unit)? = null
    private var onGoogleSignUpError: ((Exception) -> Unit)? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { idToken ->
                // Step 1: Authenticate with Firebase
                googleSignInHelper.firebaseAuthWithGoogle(
                    idToken = idToken,
                    onSuccess = { token, firebaseUid, email, displayName ->
                        // Step 2: Register/Login with backend
                        registerWithBackend(token, firebaseUid, email, displayName)
                    },
                    onError = { exception ->
                        onGoogleSignUpError?.invoke(exception)
                    }
                )
            } ?: run {
                onGoogleSignUpError?.invoke(Exception("No ID token received from Google"))
            }
        } catch (e: ApiException) {
            onGoogleSignUpError?.invoke(e)
        }
    }

    /**
     * Call backend API to register or login the Google user.
     * Backend will create a new user if email doesn't exist, or login if it does.
     */
    private fun registerWithBackend(idToken: String, firebaseUid: String, email: String, displayName: String) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@MainActivity)
                val response = api.registerWithGoogle(
                    GoogleRegisterRequest(
                        firebaseUid = firebaseUid,
                        userName = displayName,
                        emailId = email,
                        idToken = idToken
                    )
                )
                if (response.isSuccessful) {
                    val auth = response.body()!!
                    onGoogleSignUpSuccess?.invoke(
                        auth.token,
                        auth.userId,
                        auth.userName,
                        auth.emailId,
                        auth.role
                    )
                } else {
                    val errorMsg = response.parseErrorMessage()
                    onGoogleSignUpError?.invoke(Exception(errorMsg))
                }
            } catch (e: Exception) {
                onGoogleSignUpError?.invoke(e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLanguage()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        googleSignInHelper = GoogleSignInHelper(this)
        biometricAuthManager = BiometricAuthManager(this)
        
        setContent {
            DukaanLockerApp(
                onLanguageChanged = { code ->
                    LockerStorage.saveLanguage(this, code)
                },
                onGoogleSignIn = {
                    // Launch Google Sign-In flow
                    val signInIntent = googleSignInHelper.getSignInIntent()
                    googleSignInLauncher.launch(signInIntent)
                },
                onGoogleSignUpResult = { token, userId, userName, email, role ->
                    // Save auth and navigate to home
                    val authResponse = com.iadv.dukaanlocker.api.AuthResponse(
                        token = token,
                        tokenType = "Bearer",
                        userId = userId,
                        userName = userName,
                        mobileNumber = "",
                        emailId = email,
                        role = role
                    )
                    ApiClient.saveAuth(this, authResponse)
                    Toast.makeText(this, "Welcome, $userName!", Toast.LENGTH_SHORT).show()
                    // The UI will automatically navigate due to isLoggedIn state change
                },
                onGoogleSignUpError = { exception ->
                    Toast.makeText(this, "Google Sign-Up failed: ${exception.message}", Toast.LENGTH_LONG).show()
                },
                // App Unlock: Uses device credential (biometric OR PIN/pattern)
                onAppUnlock = { onSuccess, onError ->
                    biometricAuthManager.authenticateWithDeviceCredential(
                        activity = this@MainActivity,
                        title = "Unlock Dukaan Locker",
                        subtitle = "Use fingerprint, face, or device PIN to unlock",
                        onSuccess = { onSuccess() },
                        onError = { errorMessage -> onError(errorMessage) },
                        onFailed = { onError("Authentication failed. Please try again.") }
                    )
                },
                // Biometric Login: Uses CryptoObject for secure Keystore DECRYPTION
                // Returns CryptoObject on success so DukaanLockerApp can decrypt credentials
                onBiometricLogin = { onSuccess, onError ->
                    // Read the stored IV to create a DECRYPT_MODE cipher
                    val prefs = getSharedPreferences("biometric_credentials", MODE_PRIVATE)
                    val ivBase64 = prefs.getString("encryption_iv", null)
                    val hasData = prefs.contains("encrypted_data")
                    android.util.Log.d("BiometricLogin", "IV present: ${ivBase64 != null}, data present: $hasData, all keys: ${prefs.all.keys}")
                    if (ivBase64 == null) {
                        onError("No stored credentials found (hasData=$hasData)")
                        return@DukaanLockerApp
                    }
                    val iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP)
                    
                    biometricAuthManager.authenticateWithDecryptCrypto(
                        activity = this@MainActivity,
                        iv = iv,
                        title = "Biometric Login",
                        subtitle = "Use your fingerprint to sign in",
                        onSuccess = { cryptoObject ->
                            // Pass the CryptoObject (DECRYPT_MODE) to DukaanLockerApp
                            onSuccess(cryptoObject)
                        },
                        onError = { errorMessage -> onError(errorMessage) },
                        onFailed = { onError("Fingerprint not recognized. Please try again.") }
                    )
                },
                // Authenticate for ENABLING biometric login (ENCRYPT_MODE, no stored credentials needed)
                onAuthenticateForEnable = { onSuccess, onError ->
                    biometricAuthManager.authenticateWithCrypto(
                        activity = this@MainActivity,
                        title = "Enable Biometric Login",
                        subtitle = "Scan fingerprint to enable biometric login",
                        onSuccess = { cryptoObject ->
                            onSuccess(cryptoObject)
                        },
                        onError = { errorMessage -> onError(errorMessage) },
                        onFailed = { onError("Fingerprint not recognized. Please try again.") }
                    )
                },
                // Enable biometric login after successful authentication
                onEnableBiometricLogin = { cipher, token, userId, userName, email, role ->
                    // Encrypt and store credentials using the authenticated cipher from CryptoObject
                    val stored = BiometricCredentialManager.storeCredentials(
                        context = this,
                        cryptoCipher = cipher,
                        token = token,
                        userId = userId,
                        userName = userName,
                        email = email,
                        role = role
                    )
                    if (stored) {
                        LockerStorage.saveBiometricLoginEnabled(this, true)
                        true
                    } else {
                        false
                    }
                }
            )
        }
    }

    private fun applyLanguage() {
        val code = LockerStorage.getLanguage(this)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
