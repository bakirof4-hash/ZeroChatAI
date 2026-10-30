package com.zerochat.ai.data.api

import com.zerochat.ai.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // Append JWT Access Token if available
        val token = tokenManager.getAccessToken()
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        builder.addHeader("Accept", "application/json")
        builder.addHeader("User-Agent", "ZeroChat-AI-Android/1.0.0")

        val response = chain.proceed(builder.build())

        // If server returns 401 Unauthorized, automatically invalidate local session
        if (response.code == 401) {
            tokenManager.clear()
        }

        return response
    }
}

