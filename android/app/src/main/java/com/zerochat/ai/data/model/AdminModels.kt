package com.zerochat.ai.data.model

import com.google.gson.annotations.SerializedName

data class BadgeUpdateRequest(
    @SerializedName("is_verified_badge") val isVerifiedBadge: Boolean,
    @SerializedName("reason") val reason: String
)

data class StatusUpdateRequest(
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("reason") val reason: String
)

data class SystemStatsResponse(
    @SerializedName("total_users") val totalUsers: Int,
    @SerializedName("verified_users") val verifiedUsers: Int,
    @SerializedName("admin_users") val adminUsers: Int,
    @SerializedName("active_sessions") val activeSessions: Int,
    @SerializedName("total_security_events") val totalSecurityEvents: Int,
    @SerializedName("smtp_enabled") val smtpEnabled: Boolean,
    @SerializedName("smtp_host") val smtpHost: String,
    @SerializedName("app_version") val appVersion: String
)

data class AuditLogDto(
    @SerializedName("id") val id: Int,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("actor_email") val actorEmail: String,
    @SerializedName("action") val action: String,
    @SerializedName("target_email") val targetEmail: String?,
    @SerializedName("details") val details: String?,
    @SerializedName("ip_address") val ipAddress: String?
)

data class SecurityEventDto(
    @SerializedName("id") val id: Int,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("email") val email: String?,
    @SerializedName("ip_address") val ipAddress: String?,
    @SerializedName("severity") val severity: String,
    @SerializedName("details") val details: String?
)

