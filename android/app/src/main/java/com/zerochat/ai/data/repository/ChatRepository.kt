package com.zerochat.ai.data.repository

import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.api.ZeroChatApiService
import com.zerochat.ai.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody

class ChatRepository(
    private val apiService: ZeroChatApiService
) {
    suspend fun getConversations(): ApiResult<List<ConversationDto>> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.getConversations()
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Failed to fetch conversations (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to connect to conversations list")
        }
    }

    suspend fun getMessages(): ApiResult<List<ChatMessageDto>> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.getChatMessages()
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Failed to fetch messages (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Failed to connect to chat channel")
        }
    }

    suspend fun sendMessage(
        content: String,
        attachmentUrl: String? = null,
        attachmentName: String? = null,
        attachmentType: String? = null,
        attachmentSize: Int? = null,
        attachmentSha256: String? = null
    ): ApiResult<ChatMessageDto> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.sendChatMessage(
                SendChatMessageRequest(
                    content = content.trim(),
                    attachmentUrl = attachmentUrl,
                    attachmentName = attachmentName,
                    attachmentType = attachmentType,
                    attachmentSize = attachmentSize,
                    attachmentSha256 = attachmentSha256
                )
            )
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Failed to post message (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Message delivery failed")
        }
    }

    suspend fun getDirectMessages(targetUserId: Int): ApiResult<List<ChatMessageDto>> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.getDirectMessages(targetUserId)
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Failed to load direct messages (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Direct channel error")
        }
    }

    suspend fun sendDirectMessage(
        targetUserId: Int,
        content: String,
        attachmentUrl: String? = null,
        attachmentName: String? = null,
        attachmentType: String? = null,
        attachmentSize: Int? = null,
        attachmentSha256: String? = null
    ): ApiResult<ChatMessageDto> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.sendDirectMessage(
                targetUserId,
                SendChatMessageRequest(
                    content = content.trim(),
                    attachmentUrl = attachmentUrl,
                    attachmentName = attachmentName,
                    attachmentType = attachmentType,
                    attachmentSize = attachmentSize,
                    attachmentSha256 = attachmentSha256
                )
            )
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Failed to deliver direct message (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Direct message delivery failed")
        }
    }

    suspend fun uploadAttachment(filePart: MultipartBody.Part): ApiResult<AttachmentUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.uploadAttachment(filePart)
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Upload failed (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "File upload failed")
        }
    }

    suspend fun searchUsers(query: String): ApiResult<List<UserDto>> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.searchUsers(query.trim())
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Search request failed (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Search query failed")
        }
    }

    suspend fun getMyQrCode(): ApiResult<QrCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.getMyQrCode()
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Failed to fetch QR code (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "QR code generation failed")
        }
    }

    suspend fun lookupUser(username: String): ApiResult<UserDto> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.lookupUser(username.trim())
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("User not found (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "User lookup failed")
        }
    }

    suspend fun scanQrCode(qrContent: String): ApiResult<QrScanResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.scanQrCode(QrScanRequest(qrContent.trim()))
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Invalid QR code or access restricted (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "QR resolution error")
        }
    }

    suspend fun blockUser(userId: Int): ApiResult<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.blockUser(userId)
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Block failed (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Block action failed")
        }
    }

    suspend fun unblockUser(userId: Int): ApiResult<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.unblockUser(userId)
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Unblock failed (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Unblock action failed")
        }
    }

    suspend fun inspectFile(filePart: MultipartBody.Part): ApiResult<FileInspectionResponse> = withContext(Dispatchers.IO) {
        try {
            val res = apiService.inspectFile(filePart)
            if (res.isSuccessful && res.body() != null) {
                ApiResult.Success(res.body()!!)
            } else {
                ApiResult.Error("Static file inspection failed (${res.code()})", res.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "File inspection failed")
        }
    }
}
