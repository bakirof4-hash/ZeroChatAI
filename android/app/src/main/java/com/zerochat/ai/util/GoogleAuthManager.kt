package com.zerochat.ai.util

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleAuthManager {

    private const val TAG = "ZeroChat.GoogleAuth"

    /**
     * Checks if Google Play Services is available on the current device.
     * Works uniformly across Samsung, Xiaomi, POCO, Pixel, OPPO, Vivo, realme,
     * as well as de-Googled devices (Huawei HMS, GrapheneOS, etc.).
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    /**
     * Attempts modern Google Identity authentication using Android Credential Manager.
     * Backported to Android 4.4+ and native on Android 14+.
     *
     * If Credential Manager succeeds, invokes onTokenReceived with the Google ID Token.
     * If user cancels the prompt, quietly completes.
     * If Credential Manager is unsupported or fails, invokes onFallbackToLegacy.
     */
    suspend fun signInWithCredentialManager(
        context: Context,
        webClientId: String,
        onTokenReceived: (String) -> Unit,
        onError: (String) -> Unit,
        onFallbackToLegacy: () -> Unit
    ) {
        if (!isGooglePlayServicesAvailable(context)) {
            Log.w(TAG, "Google Play Services is unavailable on this device.")
            onError("Google Play Services is not available on this device. Please use one-time verification code.")
            return
        }

        if (webClientId.isBlank()) {
            Log.w(TAG, "Web Client ID is empty. Falling back to legacy Google Sign-In.")
            onFallbackToLegacy()
            return
        }

        val credentialManager = CredentialManager.create(context)

        val googleIdOption = try {
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not build GetGoogleIdOption: ${e.message}, invoking fallback", e)
            onFallbackToLegacy()
            return
        }

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                if (idToken.isNotBlank()) {
                    Log.i(TAG, "Credential Manager successfully acquired Google ID token.")
                    onTokenReceived(idToken)
                    return
                }
            }

            Log.w(TAG, "Unrecognized credential type: ${credential.type}, invoking fallback")
            onFallbackToLegacy()

        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User dismissed Google Sign-In account picker.")
            // User cancelled; no action required
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager GetCredentialException (${e.type}): ${e.message}. Triggering fallback.")
            onFallbackToLegacy()
        } catch (e: Exception) {
            Log.w(TAG, "Credential Manager error: ${e.message}. Triggering fallback.", e)
            onFallbackToLegacy()
        }
    }

    /**
     * Builds standard GoogleSignInClient for legacy fallback or older devices/custom ROMs.
     */
    fun createLegacyGoogleSignInClient(context: Context, webClientId: String): GoogleSignInClient {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        if (webClientId.isNotBlank()) {
            gsoBuilder.requestIdToken(webClientId)
        }

        return GoogleSignIn.getClient(context, gsoBuilder.build())
    }
}
