package com.zerochat.ai.data.api

import android.content.Context
import com.zerochat.ai.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Default development endpoint pointing to host machine from Android Emulator
    // Can be easily switched to production HTTPS endpoint
    private var baseUrl: String = "http://10.0.2.2:8000/"

    private var apiService: ZeroChatApiService? = null

    fun setBaseUrl(url: String, context: Context) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        apiService = null // Force re-creation
    }

    fun getBaseUrl(): String = baseUrl

    fun getService(context: Context): ZeroChatApiService {
        if (apiService == null) {
            val tokenManager = TokenManager(context)

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
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

