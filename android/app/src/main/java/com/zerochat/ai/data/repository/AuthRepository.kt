package com.zerochat.ai.data.repository

import com.google.gson.Gson
import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.api.ZeroChatApiService
import com.zerochat.ai.data.local.TokenManager
import com.zerochat.ai.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class AuthRepository(
    private val apiService: ZeroChatApiService,
    private val tokenManager: TokenManager
) {
    private val gson = Gson()

    private fun <T> parseError(response: Response<T>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                val map = gson.fromJson(errorBody, Map::class.java)
                map["detail"]?.toString() ?: "Request failed with code ${response.code()}"
            } else {
                "Request failed with status ${response.code()}"
            }
        } catch (e: Exception) {
            "An unexpected error occurred (${response.code()})"
        }
    }

    suspend fun sendVerificationCode(email: String, purpose: String = "REGISTRATION"): ApiResult<SendCodeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.sendVerificationCode(SendCodeRequest(email.trim(), purpose))
                if (res.isSuccessful && res.body() != null) {
                    ApiResult.Success(res.body()!!)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Failed to connect to authentication server")
            }
        }

    suspend fun verifyCode(email: String, code: String, purpose: String = "REGISTRATION"): ApiResult<VerifyCodeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.verifyCode(VerifyCodeRequest(email.trim(), code.trim(), purpose))
                if (res.isSuccessful && res.body() != null) {
                    ApiResult.Success(res.body()!!)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Verification request failed")
            }
        }

    suspend fun createPasswordAndRegister(
        verificationToken: String,
        email: String,
        username: String,
        password: String,
        confirmPassword: String
    ): ApiResult<AuthTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.createPasswordAndRegister(
                CreatePasswordRequest(
                    verificationToken = verificationToken,
                    email = email.trim(),
                    username = username.trim(),
                    password = password,
                    confirmPassword = confirmPassword
                )
            )
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                tokenManager.saveAuthTokens(body.accessToken, body.sessionToken)
                tokenManager.saveUser(body.user)
                ApiResult.Success(body)
            } else {
                ApiResult.Error(parseError(res), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Account registration failed")
        }
    }

    suspend fun login(email: String, password: String): ApiResult<AuthTokenResponse> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.login(LoginRequest(email.trim(), password))
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    tokenManager.saveAuthTokens(body.accessToken, body.sessionToken)
                    tokenManager.saveUser(body.user)
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Login failed. Check server connection.")
            }
        }

    suspend fun confirmPasswordReset(
        verificationToken: String,
        email: String,
        newPassword: String,
        confirmPassword: String
    ): ApiResult<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.confirmPasswordReset(
                PasswordResetRequest(
                    verificationToken = verificationToken,
                    email = email.trim(),
                    newPassword = newPassword,
                    confirmPassword = confirmPassword
                )
            )
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error(parseError(res), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Password reset failed")
        }
    }

    suspend fun checkUsername(username: String): ApiResult<UsernameCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.checkUsername(username.trim())
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error(parseError(res), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to validate username")
        }
    }

    suspend fun logout(): ApiResult<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val sessionToken = tokenManager.getSessionToken()
            val res = apiService.logout(LogoutRequest(sessionToken))
            tokenManager.clear()
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Success(GenericResponse("Logged out locally"))
            }
        } catch (e: Exception) {
            tokenManager.clear()
            ApiResult.Success(GenericResponse("Logged out locally"))
        }
    }

    suspend fun refreshProfile(): ApiResult<UserDto> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.getProfile()
            if (res.isSuccessful && res.body() != null) {
                val user = res.body()!!
                tokenManager.saveUser(user)
                ApiResult.Success(user)
            } else {
                ApiResult.Error(parseError(res), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to fetch user profile")
        }
    }

    fun getCachedUser(): UserDto? = tokenManager.getUser()

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
}
