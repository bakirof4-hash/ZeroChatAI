package com.zerochat.ai.data.model

import com.google.gson.annotations.SerializedName

data class InspectionFinding(
    @SerializedName("severity") val severity: String,
    @SerializedName("message") val message: String
)

data class FileInspectionResponse(
    @SerializedName("filename") val filename: String,
    @SerializedName("file_type") val fileType: String,
    @SerializedName("file_size") val fileSize: Long,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("threat_level") val threatLevel: String,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("findings") val findings: List<InspectionFinding>,
    @SerializedName("disclaimer") val disclaimer: String
)
