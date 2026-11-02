package com.zerochat.ai.data.model

import com.google.gson.annotations.SerializedName

data class SendCodeRequest(
    @SerializedName("email") val email: String,
    @SerializedName("purpose") val purpose: String = "REGISTRATION"
)

data class SendCodeResponse(
    @SerializedName("message") val message: String,
    @SerializedName("cooldown_seconds") val cooldownSeconds: Int,
    @SerializedName("expires_in_seconds") val expiresInSeconds: Int,
    @SerializedName("dev_code") val devCode: String? = null
)

data class VerifyCodeRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String,
    @SerializedName("purpose") val purpose: String = "REGISTRATION"
)

data class VerifyCodeResponse(
    @SerializedName("message") val message: String,
    @SerializedName("verification_token") val verificationToken: String,
    @SerializedName("email") val email: String
)

data class CreatePasswordRequest(
    @SerializedName("verification_token") val verificationToken: String,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class GoogleAuthRequest(
    @SerializedName("id_token") val idToken: String
)

data class PasswordlessLoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("verification_token") val verificationToken: String
)

data class AuthTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("session_token") val sessionToken: String,
    @SerializedName("user") val user: UserDto
)

data class PasswordResetRequest(
    @SerializedName("verification_token") val verificationToken: String,
    @SerializedName("email") val email: String,
    @SerializedName("new_password") val newPassword: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

data class LogoutRequest(
    @SerializedName("session_token") val sessionToken: String?
)

data class GenericResponse(
    @SerializedName("message") val message: String
)

