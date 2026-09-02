package com.iadv.dukaanlocker.api

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.iadv.dukaanlocker.BuildConfig

class GoogleSignInHelper(private val context: Context) {
    
    private val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(webClientId)
        .requestEmail()
        .build()
    
    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    fun getSignInIntent(): Intent {
        // Sign out first so Google always shows the account chooser;
        // otherwise it silently reuses the previously authorized account
        // and skips the "select a Gmail account" screen.
        googleSignInClient.signOut()
        return googleSignInClient.signInIntent
    }
    
    fun signOut() {
        googleSignInClient.signOut()
        firebaseAuth.signOut()
    }
    
    /**
     * Authenticate with Google and get user info for registration.
     * Returns (firebaseUid, email, displayName) for Google Sign-Up flow.
     */
    fun firebaseAuthWithGoogle(
        idToken: String,
        onSuccess: (idToken: String, firebaseUid: String, email: String, displayName: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    user?.let {
                        onSuccess(
                            idToken,
                            it.uid,
                            it.email ?: "",
                            it.displayName ?: "Google User"
                        )
                    } ?: onError(Exception("No user found after Google authentication"))
                } else {
                    task.exception?.let { onError(it) }
                }
            }
    }
    
    fun isUserSignedIn(): Boolean = firebaseAuth.currentUser != null
    
    fun getCurrentUser() = firebaseAuth.currentUser
}