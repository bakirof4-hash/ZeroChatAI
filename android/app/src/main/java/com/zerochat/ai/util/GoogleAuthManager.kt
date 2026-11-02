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

import com.zerochat.ai.BuildConfig
import com.zerochat.ai.data.local.TokenManager

object GoogleAuthManager {

    private const val TAG = "ZeroChat.GoogleAuth"

    const val APPLICATION_PACKAGE_NAME = "com.zerochat.ai"
    const val KNOWN_SHA1_FINGERPRINT = "B5:72:A3:FD:10:F6:5B:A0:60:B3:AD:8F:D4:0E:D8:07:D6:89:FA:F7"

    /**
     * Resolves the Web Client ID for Google OAuth.
     * Prioritizes runtime user/developer override from TokenManager (via ServerConfigDialog),
     * followed by build-time BuildConfig.GOOGLE_WEB_CLIENT_ID.
     */
    fun resolveWebClientId(context: Context): String {
        val runtimeCustom = TokenManager(context).getGoogleWebClientId()
        if (!runtimeCustom.isNullOrBlank()) {
            return runtimeCustom.trim()
        }
        return BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
    }

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
     * Formats Google Sign-In ApiException error codes with actionable troubleshooting messages.
     */
    fun getErrorMessageForStatusCode(statusCode: Int, rawMessage: String?): String {
        return when (statusCode) {
            10 -> { // CommonStatusCodes.DEVELOPER_ERROR
                "Google Sign-In failed (10: DEVELOPER_ERROR).\n\n" +
                "Kerakli sozlamalar tekshiruvi:\n" +
                "1. Google Cloud / Firebase Android Client package name: 'com.zerochat.ai'\n" +
                "2. Signing Certificate SHA-1: B5:72:A3:FD:10:F6:5B:A0:60:B3:AD:8F:D4:0E:D8:07:D6:89:FA:F7\n" +
                "3. Ilovaga 'Web application' Client ID berilishi shart (Android Client ID emas)."
            }
            7 -> "Tarmoq xatosi (7: NETWORK_ERROR). Internet aloqasini tekshiring."
            12500 -> "Google hisobi xatosi (12500: SIGN_IN_FAILED). Play Services versiyasini yangilang."
            else -> "Google Sign-In failed ($statusCode): ${rawMessage ?: "Noma'lum xatolik"}"
        }
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

        val effectiveClientId = webClientId.ifBlank { resolveWebClientId(context) }
        if (effectiveClientId.isBlank()) {
            Log.w(TAG, "Web Client ID is empty.")
            onError(
                "Google Sign-In sozlanmagan: Web Client ID kiritilmagan.\n" +
                "Server sozlamalarida yoki local.properties faylida GOOGLE_WEB_CLIENT_ID ni ko'rsating."
            )
            return
        }

        val credentialManager = CredentialManager.create(context)

        val googleIdOption = try {
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(effectiveClientId)
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
    fun createLegacyGoogleSignInClient(context: Context, webClientId: String? = null): GoogleSignInClient {
        val effectiveClientId = if (!webClientId.isNullOrBlank()) webClientId else resolveWebClientId(context)
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        if (effectiveClientId.isNotBlank()) {
            gsoBuilder.requestIdToken(effectiveClientId)
        }

        return GoogleSignIn.getClient(context, gsoBuilder.build())
    }
}

