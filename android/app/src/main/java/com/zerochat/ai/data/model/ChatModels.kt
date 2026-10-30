package com.zerochat.ai.data.model

import com.google.gson.annotations.SerializedName

data class ChatMessageDto(
    @SerializedName("id") val id: Int,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("sender_username") val senderUsername: String,
    @SerializedName("sender_email") val senderEmail: String,
    @SerializedName("recipient_id") val recipientId: Int? = null,
    @SerializedName("is_founder") val isFounder: Boolean,
    @SerializedName("is_verified_badge") val isVerifiedBadge: Boolean,
    @SerializedName("content") val content: String,
    @SerializedName("attachment_url") val attachmentUrl: String? = null,
    @SerializedName("attachment_name") val attachmentName: String? = null,
    @SerializedName("attachment_type") val attachmentType: String? = null,
    @SerializedName("attachment_size") val attachmentSize: Int? = null,
    @SerializedName("attachment_sha256") val attachmentSha256: String? = null,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("is_ai") val isAi: Boolean = false
)

data class SendChatMessageRequest(
    @SerializedName("content") val content: String,
    @SerializedName("attachment_url") val attachmentUrl: String? = null,
    @SerializedName("attachment_name") val attachmentName: String? = null,
    @SerializedName("attachment_type") val attachmentType: String? = null,
    @SerializedName("attachment_size") val attachmentSize: Int? = null,
    @SerializedName("attachment_sha256") val attachmentSha256: String? = null
)

data class AttachmentUploadResponse(
    @SerializedName("attachment_url") val attachmentUrl: String,
    @SerializedName("attachment_name") val attachmentName: String,
    @SerializedName("attachment_type") val attachmentType: String,
    @SerializedName("attachment_size") val attachmentSize: Int,
    @SerializedName("attachment_sha256") val attachmentSha256: String,
    @SerializedName("threat_level") val threatLevel: String,
    @SerializedName("risk_score") val riskScore: Int
)
