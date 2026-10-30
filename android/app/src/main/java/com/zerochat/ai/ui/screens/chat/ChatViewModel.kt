package com.zerochat.ai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.model.*
import com.zerochat.ai.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

data class ChatUiState(
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
    val isInspectingFile: Boolean = false
)

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    fun onInputMessageChanged(text: String) {
        _uiState.update { it.copy(inputMessage = text) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
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

    fun sendMessage() {
        val text = _uiState.value.inputMessage.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            when (val result = chatRepository.sendMessage(text)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputMessage = "",
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

    fun sendDirectMessage(targetUserId: Int) {
        val text = _uiState.value.inputMessage.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            when (val result = chatRepository.sendDirectMessage(targetUserId, text)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputMessage = "",
                            directMessages = it.directMessages + result.data
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
}
