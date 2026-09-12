package com.iadv.dukaanlocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import java.util.Locale

// Use FragmentActivity (not ComponentActivity) so BiometricPrompt works
class MainActivity : FragmentActivity() {

    lateinit var googleSignInHelper: GoogleSignInHelper
        private set
    lateinit var biometricAuthManager: BiometricAuthManager
        private set

    // Callbacks for Google Sign-Up result
    private var onGoogleSignUpSuccess: ((token: String, userId: Long, userName: String, email: String, mobileNumber: String, role: String) -> Unit)? = null
    private var onGoogleSignUpError: ((Exception) -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.d("FCM", "POST_NOTIFICATIONS permission granted=$granted")
    }

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
                        // Step 2: Login with backend (login only — never auto-registers)
                        loginWithBackend(token, firebaseUid, email, displayName)
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
     * Call backend API to LOGIN an existing Google user.
     * If the email has no account, the backend returns 404 and the
     * error is surfaced to the user (no auto-registration).
     */
    private fun loginWithBackend(idToken: String, firebaseUid: String, email: String, displayName: String) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@MainActivity)
                val response = api.loginWithGoogle(
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
                        auth.mobileNumber,
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

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { applyLanguage(it) })
    }

    private fun applyLanguage(base: Context): Context {
        val code = readLanguageFromPrefs(base)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun readLanguageFromPrefs(context: Context): String {
        return try {
            val prefsFile = context.getSharedPreferences("dukaan_locker_v2", MODE_PRIVATE)
            prefsFile.getString("language", "en") ?: "en"
        } catch (_: Exception) {
            "en"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        registerFcmTokenIfAuthenticated()
        
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
                registerGoogleAuthHandlers = { onSuccess, onError ->
                    // Deliver the composable's handlers so the backend/auth callbacks
                    // can update Compose state and navigate after a successful login
                    onGoogleSignUpSuccess = onSuccess
                    onGoogleSignUpError = onError
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Re-register the current token on launch when an existing authenticated
     * session is restored. New login flows register it from AppViewModel after
     * the JWT has been saved.
     */
    private fun registerFcmTokenIfAuthenticated() {
        if (!ApiClient.isLoggedIn(this)) {
            android.util.Log.d("FCM", "Deferring token registration until login")
            return
        }

        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    android.util.Log.e("FCM", "Unable to obtain FCM token", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (token.isNullOrBlank()) {
                    android.util.Log.e("FCM", "Firebase returned an empty FCM token")
                    return@addOnCompleteListener
                }
                lifecycleScope.launch {
                    ApiClient.registerDeviceToken(this@MainActivity, token)
                }
            }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "dukaan_notifications_v2",
                "Dukaan Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for document expiry, missing documents, and more"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setShowBadge(true)
                lockscreenVisibility = androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerFcmToken() {
        registerFcmTokenIfAuthenticated()
    }

}
