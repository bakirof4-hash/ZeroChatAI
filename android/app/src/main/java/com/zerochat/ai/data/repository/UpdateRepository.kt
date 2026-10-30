package com.zerochat.ai.data.repository

import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.api.ZeroChatApiService
import com.zerochat.ai.data.model.AppVersionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateRepository(private val apiService: ZeroChatApiService) {

    suspend fun getLatestVersion(): ApiResult<AppVersionResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLatestVersion()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error("Failed to fetch latest version (${response.code()})")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error while checking for updates")
        }
    }
}

