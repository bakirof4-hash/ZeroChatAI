package com.zerochat.ai.data.model

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("role") val role: String,
    @SerializedName("is_founder") val isFounder: Boolean,
    @SerializedName("is_verified_badge") val isVerifiedBadge: Boolean,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("email_verified") val emailVerified: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_login_at") val lastLoginAt: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("google_id") val googleId: String? = null,
    @SerializedName("full_name") val fullName: String? = null
) {
    val isSuperAdmin: Boolean
        get() = role == "SUPER_ADMIN" || isFounder

    val isAdmin: Boolean
        get() = role in listOf("SUPER_ADMIN", "ADMIN") || isFounder
}

