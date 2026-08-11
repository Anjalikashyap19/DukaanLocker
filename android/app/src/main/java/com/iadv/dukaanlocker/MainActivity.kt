package com.iadv.dukaanlocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.iadv.dukaanlocker.api.GoogleSignInHelper
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private var onGoogleSignInResult: ((String, String, String) -> Unit)? = null
    private var onGoogleSignInError: ((Exception) -> Unit)? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { idToken ->
                googleSignInHelper.firebaseAuthWithGoogle(
                    idToken = idToken,
                    onSuccess = { uid, email, displayName ->
                        onGoogleSignInResult?.invoke(uid, email, displayName)
                    },
                    onError = { exception ->
                        onGoogleSignInError?.invoke(exception)
                    }
                )
            }
        } catch (e: ApiException) {
            onGoogleSignInError?.invoke(e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLanguage()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        googleSignInHelper = GoogleSignInHelper(this)
        
        setContent {
            DukaanLockerApp(
                onLanguageChanged = { code ->
                    LockerStorage.saveLanguage(this, code)
                },
                onGoogleSignIn = {
                    onGoogleSignInResult = { uid, email, displayName ->
                        // Handle successful Google Sign-In
                        // This will be connected to DukaanLockerApp
                    }
                    onGoogleSignInError = { exception ->
                        // Handle Google Sign-In error
                        android.util.Log.e("GoogleSignIn", "Error: ${exception.message}")
                    }
                    val signInIntent = googleSignInHelper.getSignInIntent()
                    googleSignInLauncher.launch(signInIntent)
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
