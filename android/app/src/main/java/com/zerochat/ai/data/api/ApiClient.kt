package com.zerochat.ai.data.api

import android.content.Context
import android.os.Build
import com.zerochat.ai.BuildConfig
import com.zerochat.ai.data.local.TokenManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object ApiClient {

    const val DEFAULT_PRODUCTION_URL = "https://api.zerochat.ai/"
    const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:8000/"

    private val hostSelectionInterceptor = HostSelectionInterceptor()
    private var baseUrl: String = ""
    private var apiService: ZeroChatApiService? = null
    private var isInitialized = false

    /**
     * Accurately detects whether the app is running in an Android Studio / QEMU emulator.
     * Safely handles null values in local JVM unit tests or unusual OEM builds.
     */
    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val product = Build.PRODUCT ?: ""
        val hardware = Build.HARDWARE ?: ""

        return (fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || manufacturer.contains("Genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk" == product
                || hardware.contains("goldfish")
                || hardware.contains("ranchu"))
    }

    /**
     * Returns human-readable device identification for developer diagnostics.
     */
    fun getDeviceDescription(): String {
        val model = Build.MODEL ?: "Device"
        val manufacturer = Build.MANUFACTURER ?: "Android"
        return if (isEmulator()) {
            "Android Emulator ($model)"
        } else {
            "Physical Device ($manufacturer $model)"
        }
    }

    /**
     * Resolves the appropriate base URL based on device environment and user preferences.
     * NEVER allows physical devices to use 10.0.2.2 (which causes ConnectException).
     */
    fun resolveInitialBaseUrl(context: Context): String {
        val tokenManager = TokenManager(context)
        val savedUrl = tokenManager.getServerUrl()

        if (!savedUrl.isNullOrBlank()) {
            // Guard: If a physical device previously saved 10.0.2.2, clean it out!
            if (savedUrl.contains("10.0.2.2") && !isEmulator()) {
                tokenManager.clearServerUrl()
            } else {
                return normalizeUrl(savedUrl)
            }
        }

        // On emulators in debug mode, use the emulator-specific loopback
        if (isEmulator() && BuildConfig.DEBUG) {
            return normalizeUrl(BuildConfig.EMULATOR_URL)
        }

        // On physical devices (and production release builds), use production HTTPS or build-configured URL
        val configuredUrl = BuildConfig.BASE_URL
        if (configuredUrl.contains("10.0.2.2") && !isEmulator()) {
            return DEFAULT_PRODUCTION_URL
        }

        return normalizeUrl(configuredUrl)
    }

    private fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return if (clean.endsWith("/")) clean else "$clean/"
    }

    fun setBaseUrl(url: String, context: Context) {
        val normalized = normalizeUrl(url)
        baseUrl = normalized
        hostSelectionInterceptor.setHostUrl(normalized)
        TokenManager(context).saveServerUrl(normalized)
    }

    fun resetToDefault(context: Context): String {
        val tokenManager = TokenManager(context)
        tokenManager.clearServerUrl()
        val defaultUrl = if (isEmulator() && BuildConfig.DEBUG) {
            normalizeUrl(BuildConfig.EMULATOR_URL)
        } else {
            normalizeUrl(BuildConfig.BASE_URL)
        }
        setBaseUrl(defaultUrl, context)
        return defaultUrl
    }

    fun getBaseUrl(): String = baseUrl

    fun getService(context: Context): ZeroChatApiService {
        if (!isInitialized) {
            baseUrl = resolveInitialBaseUrl(context)
            hostSelectionInterceptor.setHostUrl(baseUrl)
            isInitialized = true
        }

        if (apiService == null) {
            val tokenManager = TokenManager(context)

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(hostSelectionInterceptor)
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

    /**
     * Converts raw connection exceptions into clear, actionable user messages.
     */
    fun formatNetworkError(e: Throwable): String {
        val msg = e.localizedMessage ?: ""
        return when {
            msg.contains("10.0.2.2") && !isEmulator() -> {
                "Cannot connect to 10.0.2.2 from a physical Android phone. Tap Server Settings (top right) to connect to your PC's IP (e.g. 192.168.1.68) or https://api.zerochat.ai/."
            }
            e is ConnectException || msg.contains("Failed to connect") -> {
                "Failed to connect to backend at $baseUrl. Ensure server is running and your device is on the same network."
            }
            e is SocketTimeoutException || msg.contains("timeout") -> {
                "Connection to $baseUrl timed out. The server took too long to respond."
            }
            e is UnknownHostException -> {
                "Server address could not be resolved. Please check your network connection or server URL."
            }
            else -> {
                msg.ifBlank { "Network connection failed. Please check backend status." }
            }
        }
    }

    /**
     * Interceptor allowing dynamic runtime URL modification across all requests without
     * recreating Retrofit or ViewModel singletons.
     */
    class HostSelectionInterceptor : Interceptor {
        @Volatile
        private var hostUrl: HttpUrl? = null

        fun setHostUrl(url: String) {
            val normalized = if (url.endsWith("/")) url else "$url/"
            hostUrl = normalized.toHttpUrlOrNull()
        }

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            val target = hostUrl
            if (target != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(target.scheme)
                    .host(target.host)
                    .port(target.port)
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }
            return chain.proceed(request)
        }
    }
}
