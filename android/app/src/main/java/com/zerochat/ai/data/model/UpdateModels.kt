package com.zerochat.ai.data.model

import com.google.gson.annotations.SerializedName

data class AppVersionResponse(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("release_notes") val releaseNotes: String,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("apk_sha256") val apkSha256: String,
    @SerializedName("apk_size") val apkSize: Long,
    @SerializedName("min_supported_version_code") val minSupportedVersionCode: Int = 1,
    @SerializedName("is_mandatory") val isMandatory: Boolean = false,
    @SerializedName("published_at") val publishedAt: String? = null
)

