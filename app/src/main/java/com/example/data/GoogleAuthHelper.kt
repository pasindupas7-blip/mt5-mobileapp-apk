package com.example.data

import android.content.Context
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

    suspend fun signInWithGoogleButton(activityContext: Context): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val serverClientId = activityContext.getString(R.string.default_web_client_id)

            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            handleSignInResult(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign-in cancelled"))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun promptSignInIfEligible(activityContext: Context): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val serverClientId = activityContext.getString(R.string.default_web_client_id)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            handleSignInResult(result.credential)
        } catch (e: NoCredentialException) {
            Result.failure(e)
        } catch (e: GetCredentialCancellationException) {
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
