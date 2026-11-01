package com.zerochat.ai.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.model.*
import com.zerochat.ai.data.repository.ChatRepository
import com.zerochat.ai.util.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

data class ChatUiState(
    val conversations: List<ConversationDto> = emptyList(),
    val filteredConversations: List<ConversationDto> = emptyList(),
    val conversationSearchQuery: String = "",
    val isConversationsLoading: Boolean = false,
    val messages: List<ChatMessageDto> = emptyList(),
    val directMessages: List<ChatMessageDto> = emptyList(),
    val currentDirectTarget: UserDto? = null,
    val inputMessage: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val searchResults: List<UserDto> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val myQrCode: QrCodeResponse? = null,
    val scannedUser: UserDto? = null,
    val isScannedUserBlocked: Boolean = false,
    val isScanningQr: Boolean = false,
    val fileInspection: FileInspectionResponse? = null,
    val isInspectingFile: Boolean = false,
    val isUploadingAttachment: Boolean = false,
    val selectedAttachment: AttachmentUploadResponse? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversationsPollJob: Job? = null
    private var directPollJob: Job? = null
    private var publicPollJob: Job? = null

    init {
        loadConversations()
        loadMessages()
    }

    fun onInputMessageChanged(text: String) {
        _uiState.update { it.copy(inputMessage = text) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(selectedAttachment = null) }
    }

    fun onConversationSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.conversations
            } else {
                state.conversations.filter { conv ->
                    conv.title.contains(query, ignoreCase = true) ||
                            (conv.lastMessage?.contains(query, ignoreCase = true) == true) ||
                            (conv.recipientUser?.username?.contains(query, ignoreCase = true) == true)
                }
            }
            state.copy(conversationSearchQuery = query, filteredConversations = filtered)
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConversationsLoading = true) }
            when (val result = chatRepository.getConversations()) {
                is ApiResult.Success -> {
                    val query = _uiState.value.conversationSearchQuery
                    val list = result.data
                    val filtered = if (query.isBlank()) {
                        list
                    } else {
                        list.filter { conv ->
                            conv.title.contains(query, ignoreCase = true) ||
                                    (conv.lastMessage?.contains(query, ignoreCase = true) == true) ||
                                    (conv.recipientUser?.username?.contains(query, ignoreCase = true) == true)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isConversationsLoading = false,
                            conversations = list,
                            filteredConversations = filtered
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isConversationsLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun startConversationsPolling(context: Context? = null) {
        conversationsPollJob?.cancel()
        conversationsPollJob = viewModelScope.launch {
            while (isActive) {
                delay(4000)
                when (val result = chatRepository.getConversations()) {
                    is ApiResult.Success -> {
                        val query = _uiState.value.conversationSearchQuery
                        val list = result.data
                        val filtered = if (query.isBlank()) {
                            list
                        } else {
                            list.filter { conv ->
                                conv.title.contains(query, ignoreCase = true) ||
                                        (conv.lastMessage?.contains(query, ignoreCase = true) == true)
                            }
                        }
                        _uiState.update {
                            it.copy(conversations = list, filteredConversations = filtered)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stopConversationsPolling() {
        conversationsPollJob?.cancel()
        conversationsPollJob = null
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isNotBlank()) {
            searchUsers(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = chatRepository.getMessages()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, messages = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun startPublicMessagesPolling() {
        publicPollJob?.cancel()
        publicPollJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                when (val result = chatRepository.getMessages()) {
                    is ApiResult.Success -> {
                        _uiState.update { it.copy(messages = result.data) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stopPublicMessagesPolling() {
        publicPollJob?.cancel()
        publicPollJob = null
    }

    fun sendMessage() {
        val text = _uiState.value.inputMessage.trim()
        val attachment = _uiState.value.selectedAttachment
        if (text.isBlank() && attachment == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            when (val result = chatRepository.sendMessage(
                content = text,
                attachmentUrl = attachment?.attachmentUrl,
                attachmentName = attachment?.attachmentName,
                attachmentType = attachment?.attachmentType,
                attachmentSize = attachment?.attachmentSize,
                attachmentSha256 = attachment?.attachmentSha256
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputMessage = "",
                            selectedAttachment = null,
                            messages = it.messages + result.data
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSending = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun setDirectTarget(user: UserDto) {
        _uiState.update { it.copy(currentDirectTarget = user, directMessages = emptyList()) }
        loadDirectMessages(user.id)
    }

    fun loadDirectMessages(targetUserId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = chatRepository.getDirectMessages(targetUserId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, directMessages = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun startDirectMessagesPolling(targetUserId: Int, context: Context? = null) {
        directPollJob?.cancel()
        directPollJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                when (val result = chatRepository.getDirectMessages(targetUserId)) {
                    is ApiResult.Success -> {
                        val currentCount = _uiState.value.directMessages.size
                        val newMessages = result.data
                        if (newMessages.size > currentCount && context != null) {
                            val latest = newMessages.last()
                            if (latest.senderId == targetUserId) {
                                NotificationHelper.showMessageNotification(
                                    context = context,
                                    notificationId = latest.id,
                                    title = latest.senderUsername,
                                    body = latest.content.ifBlank { latest.attachmentName ?: "New file attachment" },
                                    senderId = targetUserId
                                )
                            }
                        }
                        _uiState.update { it.copy(directMessages = newMessages) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stopDirectMessagesPolling() {
        directPollJob?.cancel()
        directPollJob = null
    }

    fun sendDirectMessage(targetUserId: Int) {
        val text = _uiState.value.inputMessage.trim()
        val attachment = _uiState.value.selectedAttachment
        if (text.isBlank() && attachment == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            when (val result = chatRepository.sendDirectMessage(
                targetUserId = targetUserId,
                content = text,
                attachmentUrl = attachment?.attachmentUrl,
                attachmentName = attachment?.attachmentName,
                attachmentType = attachment?.attachmentType,
                attachmentSize = attachment?.attachmentSize,
                attachmentSha256 = attachment?.attachmentSha256
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputMessage = "",
                            selectedAttachment = null,
                            directMessages = it.directMessages + result.data
                        )
                    }
                    loadConversations()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSending = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun uploadAttachment(filePart: MultipartBody.Part) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAttachment = true, errorMessage = null) }
            when (val result = chatRepository.uploadAttachment(filePart)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUploadingAttachment = false,
                            selectedAttachment = result.data,
                            successMessage = "Attachment uploaded securely: ${result.data.attachmentName}"
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isUploadingAttachment = false, errorMessage = result.message)
                    }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun loadMyQrCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = chatRepository.getMyQrCode()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, myQrCode = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun scanQrCode(qrContent: String, onSuccess: (UserDto) -> Unit = {}) {
        if (qrContent.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningQr = true, errorMessage = null) }
            when (val result = chatRepository.scanQrCode(qrContent)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isScanningQr = false,
                            scannedUser = result.data.user,
                            isScannedUserBlocked = result.data.isBlockedByMe,
                            successMessage = "Resolved user @${result.data.user.username}"
                        )
                    }
                    onSuccess(result.data.user)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isScanningQr = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun blockUser(userId: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = chatRepository.blockUser(userId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isScannedUserBlocked = true,
                            successMessage = result.data.message
                        )
                    }
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun unblockUser(userId: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = chatRepository.unblockUser(userId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isScannedUserBlocked = false,
                            successMessage = result.data.message
                        )
                    }
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun inspectFile(filePart: MultipartBody.Part) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingFile = true, errorMessage = null) }
            when (val result = chatRepository.inspectFile(filePart)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isInspectingFile = false,
                            fileInspection = result.data
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isInspectingFile = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            when (val result = chatRepository.searchUsers(query)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSearching = false, searchResults = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSearching = false) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        conversationsPollJob?.cancel()
        directPollJob?.cancel()
        publicPollJob?.cancel()
    }
}
