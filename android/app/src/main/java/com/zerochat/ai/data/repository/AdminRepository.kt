package com.zerochat.ai.data.repository

import com.google.gson.Gson
import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.api.ZeroChatApiService
import com.zerochat.ai.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class AdminRepository(
    private val apiService: ZeroChatApiService
) {
    private val gson = Gson()

    private fun <T> parseError(response: Response<T>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                val map = gson.fromJson(errorBody, Map::class.java)
                map["detail"]?.toString() ?: "Admin request failed (${response.code()})"
            } else {
                "Admin request failed with status ${response.code()}"
            }
        } catch (e: Exception) {
            "An error occurred (${response.code()})"
        }
    }

    suspend fun getStats(): ApiResult<SystemStatsResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.getAdminStats()
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error(parseError(res), res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to load dashboard metrics")
        }
    }

    suspend fun getUsers(skip: Int = 0, limit: Int = 50, search: String? = null): ApiResult<List<UserDto>> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.getAdminUsers(skip, limit, search)
                if (res.isSuccessful && res.body() != null) {
                    ApiResult.Success(res.body()!!)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Failed to load user directory")
            }
        }

    suspend fun updateUserBadge(userId: Int, isVerifiedBadge: Boolean, reason: String): ApiResult<UserDto> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.updateUserBadge(userId, BadgeUpdateRequest(isVerifiedBadge, reason))
                if (res.isSuccessful && res.body() != null) {
                    ApiResult.Success(res.body()!!)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Failed to update verification badge")
            }
        }

    suspend fun updateUserStatus(userId: Int, isActive: Boolean, reason: String): ApiResult<UserDto> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.updateUserStatus(userId, StatusUpdateRequest(isActive, reason))
                if (res.isSuccessful && res.body() != null) {
                    ApiResult.Success(res.body()!!)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Failed to update user status")
            }
        }

    suspend fun getAuditLogs(skip: Int = 0, limit: Int = 50): ApiResult<List<AuditLogDto>> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.getAuditLogs(skip, limit)
                if (res.isSuccessful && res.body() != null) {
                    ApiResult.Success(res.body()!!)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Failed to retrieve audit logs")
            }
        }

    suspend fun getSecurityEvents(skip: Int = 0, limit: Int = 50): ApiResult<List<SecurityEventDto>> =
        withContext(Dispatchers.IO) {
            try {
                val res = apiService.getSecurityEvents(skip, limit)
                if (res.isSuccessful && res.body() != null) {
                    ApiResult.Success(res.body()!!)
                } else {
                    ApiResult.Error(parseError(res), res.code())
                }
            } catch (e: Exception) {
                ApiResult.Error(e.localizedMessage ?: "Failed to retrieve security events")
            }
        }
}

