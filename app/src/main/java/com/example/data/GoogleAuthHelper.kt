package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    suspend fun signInWithGoogleButton(activityContext: Context): Result<FirebaseUser> {
        val hostContext = activityContext.findActivity() ?: activityContext
        val credentialManager = CredentialManager.create(hostContext)
        val serverClientId = try {
            hostContext.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e(TAG, "default_web_client_id not found", e)
            return Result.failure(IllegalStateException("Google Sign-In configuration missing"))
        }

        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(
                context = hostContext,
                request = request
            )
            handleSignInResult(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            Result.failure(Exception("Sign-in cancelled"))
        } catch (e: NoCredentialException) {
            Log.w(TAG, "GetSignInWithGoogleOption returned NoCredentialException, trying unfiltered GoogleIdOption", e)
            try {
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                val fallbackResult = credentialManager.getCredential(
                    context = hostContext,
                    request = fallbackRequest
                )
                handleSignInResult(fallbackResult.credential)
            } catch (cancelEx: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In fallback cancelled or dismissed: ${cancelEx.message}", cancelEx)
                Result.failure(Exception("Sign-in cancelled"))
            } catch (innerEx: Exception) {
                Log.e(TAG, "Google Sign-In fallback failed: ${innerEx.message}", innerEx)
                Result.failure(innerEx)
            }
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun promptSignInIfEligible(activityContext: Context): Result<FirebaseUser> {
        val hostContext = activityContext.findActivity() ?: activityContext
        return try {
            val credentialManager = CredentialManager.create(hostContext)
            val serverClientId = hostContext.getString(R.string.default_web_client_id)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = hostContext,
                request = request
            )
            handleSignInResult(result.credential)
        } catch (e: NoCredentialException) {
            Result.failure(e)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled or dismissed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun handleSignInResult(credential: androidx.credentials.Credential): Result<FirebaseUser> {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
            val user = authResult.user
            return if (user != null) {
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Firebase Auth returned null user"))
            }
        }
        return Result.failure(IllegalStateException("Unsupported credential type: ${credential.type}"))
    }

    suspend fun signOut(context: Context) {
        try {
            FirebaseAuth.getInstance().signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing credential state: ${e.message}")
        }
    }
}
