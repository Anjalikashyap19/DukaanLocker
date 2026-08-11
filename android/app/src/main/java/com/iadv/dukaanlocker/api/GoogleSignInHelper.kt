package com.iadv.dukaanlocker.api

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class GoogleSignInHelper(private val context: Context) {
    
    // TODO: Replace with your actual Web Client ID from Firebase Console
    // Go to Firebase Console > Authentication > Sign-in method > Google > Web SDK configuration
    private val webClientId = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(webClientId)
        .requestEmail()
        .build()
    
    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent
    
    fun signOut() {
        googleSignInClient.signOut()
        firebaseAuth.signOut()
    }
    
    fun firebaseAuthWithGoogle(
        idToken: String,
        onSuccess: (String, String, String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    user?.let {
                        onSuccess(
                            it.uid,
                            it.email ?: "",
                            it.displayName ?: ""
                        )
                    }
                } else {
                    task.exception?.let { onError(it) }
                }
            }
    }
    
    fun isUserSignedIn(): Boolean = firebaseAuth.currentUser != null
    
    fun getCurrentUser() = firebaseAuth.currentUser
}