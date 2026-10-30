package com.zerochat.ai.data.api

import com.zerochat.ai.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ZeroChatApiService {

    // --- Authentication ---
    @POST("api/v1/auth/send-code")
    suspend fun sendVerificationCode(
        @Body request: SendCodeRequest
    ): Response<SendCodeResponse>

    @POST("api/v1/auth/verify-code")
    suspend fun verifyCode(
        @Body request: VerifyCodeRequest
    ): Response<VerifyCodeResponse>

    @GET("api/v1/auth/check-username")
    suspend fun checkUsername(
        @Query("username") username: String
    ): Response<UsernameCheckResponse>

    @POST("api/v1/auth/create-password")
    suspend fun createPasswordAndRegister(
        @Body request: CreatePasswordRequest
    ): Response<AuthTokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthTokenResponse>

    @POST("api/v1/auth/password-reset/confirm")
    suspend fun confirmPasswordReset(
        @Body request: PasswordResetRequest
    ): Response<GenericResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<GenericResponse>

    @GET("api/v1/auth/me")
    suspend fun getProfile(): Response<UserDto>

    // --- Super Admin & Founder Endpoints ---
    @GET("api/v1/admin/stats")
    suspend fun getAdminStats(): Response<SystemStatsResponse>

    @GET("api/v1/admin/users")
    suspend fun getAdminUsers(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("search") search: String? = null
    ): Response<List<UserDto>>

    @POST("api/v1/admin/users/{user_id}/badge")
    suspend fun updateUserBadge(
        @Path("user_id") userId: Int,
        @Body request: BadgeUpdateRequest
    ): Response<UserDto>

    @POST("api/v1/admin/users/{user_id}/status")
    suspend fun updateUserStatus(
        @Path("user_id") userId: Int,
        @Body request: StatusUpdateRequest
    ): Response<UserDto>

    @GET("api/v1/admin/audit-logs")
    suspend fun getAuditLogs(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 50
    ): Response<List<AuditLogDto>>

    @GET("api/v1/admin/security-events")
    suspend fun getSecurityEvents(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 50
    ): Response<List<SecurityEventDto>>

    // --- Users & QR Codes ---
    @GET("api/v1/users/me/qr")
    suspend fun getMyQrCode(): Response<QrCodeResponse>

    @GET("api/v1/users/lookup")
    suspend fun lookupUser(
        @Query("username") username: String
    ): Response<UserDto>

    @POST("api/v1/users/qr-scan")
    suspend fun scanQrCode(
        @Body request: QrScanRequest
    ): Response<QrScanResponse>

    @POST("api/v1/users/{user_id}/block")
    suspend fun blockUser(
        @Path("user_id") userId: Int
    ): Response<GenericResponse>

    @POST("api/v1/users/{user_id}/unblock")
    suspend fun unblockUser(
        @Path("user_id") userId: Int
    ): Response<GenericResponse>

    @GET("api/v1/users/blocked")
    suspend fun getBlockedUsers(): Response<List<UserDto>>

    @POST("api/v1/users/contacts")
    suspend fun saveQrContact(
        @Body request: SaveContactRequest
    ): Response<QrContactDto>

    @GET("api/v1/users/contacts")
    suspend fun getQrContacts(): Response<List<QrContactDto>>

    @DELETE("api/v1/users/contacts/{contact_user_id}")
    suspend fun deleteQrContact(
        @Path("contact_user_id") contactUserId: Int
    ): Response<GenericResponse>

    // --- Chat & Messaging ---
    @GET("api/v1/chat/messages")
    suspend fun getChatMessages(): Response<List<ChatMessageDto>>

    @POST("api/v1/chat/messages")
    suspend fun sendChatMessage(
        @Body request: SendChatMessageRequest
    ): Response<ChatMessageDto>

    @GET("api/v1/chat/direct/{target_user_id}")
    suspend fun getDirectMessages(
        @Path("target_user_id") targetUserId: Int
    ): Response<List<ChatMessageDto>>

    @POST("api/v1/chat/direct/{target_user_id}")
    suspend fun sendDirectMessage(
        @Path("target_user_id") targetUserId: Int,
        @Body request: SendChatMessageRequest
    ): Response<ChatMessageDto>

    @Multipart
    @POST("api/v1/chat/upload")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part
    ): Response<AttachmentUploadResponse>

    @GET("api/v1/chat/users/search")
    suspend fun searchUsers(
        @Query("q") query: String
    ): Response<List<UserDto>>

    // --- Offline File Security Inspection ---
    @Multipart
    @POST("api/v1/security/inspect-file")
    suspend fun inspectFile(
        @Part file: MultipartBody.Part
    ): Response<FileInspectionResponse>

    @GET("api/v1/security/history")
    suspend fun getInspectionHistory(): Response<List<FileInspectionResponse>>

    // --- In-App Updates ---
    @GET("api/v1/app/version/latest")
    suspend fun getLatestVersion(): Response<AppVersionResponse>
}
