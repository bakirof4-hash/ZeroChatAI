package com.zerochat.ai.data.api

import android.content.Context
import com.zerochat.ai.BuildConfig
import com.zerochat.ai.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Configured with production HTTPS endpoint in release, and local dev fallback in debug
    private var baseUrl: String = BuildConfig.BASE_URL

    private var apiService: ZeroChatApiService? = null

    fun setBaseUrl(url: String, context: Context) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        TokenManager(context).saveServerUrl(baseUrl)
        apiService = null // Force re-creation
    }

    fun getBaseUrl(): String = baseUrl

    fun getService(context: Context): ZeroChatApiService {
        val tokenManager = TokenManager(context)
        val savedUrl = tokenManager.getServerUrl()
        if (!savedUrl.isNullOrBlank() && savedUrl != baseUrl) {
            baseUrl = savedUrl
            apiService = null
        }

        if (apiService == null) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokenManager))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(ZeroChatApiService::class.java)
        }
        return apiService!!
    }
}

