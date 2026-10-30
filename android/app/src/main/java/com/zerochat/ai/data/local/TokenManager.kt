package com.zerochat.ai.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.zerochat.ai.data.model.UserDto

class TokenManager(context: Context) {

    private val gson = Gson()
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "zerochat_encrypted_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for non-supported hardware or dev environments
        context.getSharedPreferences("zerochat_standard_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "jwt_access_token"
        private const val KEY_SESSION_TOKEN = "auth_session_token"
        private const val KEY_CACHED_USER = "cached_user_json"
        private const val KEY_SERVER_URL = "custom_server_base_url"
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    fun clearServerUrl() {
        prefs.edit().remove(KEY_SERVER_URL).apply()
    }

    fun saveAuthTokens(accessToken: String, sessionToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_SESSION_TOKEN, sessionToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getSessionToken(): String? = prefs.getString(KEY_SESSION_TOKEN, null)

    fun saveUser(user: UserDto) {
        prefs.edit().putString(KEY_CACHED_USER, gson.toJson(user)).apply()
    }

    fun getUser(): UserDto? {
        val json = prefs.getString(KEY_CACHED_USER, null) ?: return null
        return try {
            gson.fromJson(json, UserDto::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }
}

