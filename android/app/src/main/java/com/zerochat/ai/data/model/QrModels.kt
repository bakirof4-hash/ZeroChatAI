package com.zerochat.ai.data.model

import com.google.gson.annotations.SerializedName

data class QrCodeResponse(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("is_founder") val isFounder: Boolean,
    @SerializedName("is_verified_badge") val isVerifiedBadge: Boolean,
    @SerializedName("qr_payload") val qrPayload: String,
    @SerializedName("deep_link") val deepLink: String
)

data class QrScanRequest(
    @SerializedName("qr_content") val qrContent: String
)

data class QrScanResponse(
    @SerializedName("user") val user: UserDto,
    @SerializedName("is_blocked_by_me") val isBlockedByMe: Boolean
)

data class UsernameCheckResponse(
    @SerializedName("username") val username: String,
    @SerializedName("available") val available: Boolean,
    @SerializedName("message") val message: String
)
