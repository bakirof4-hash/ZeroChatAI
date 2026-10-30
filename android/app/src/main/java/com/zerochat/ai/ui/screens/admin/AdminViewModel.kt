package com.zerochat.ai.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.model.*
import com.zerochat.ai.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val stats: SystemStatsResponse? = null,
    val users: List<UserDto> = emptyList(),
    val auditLogs: List<AuditLogDto> = emptyList(),
    val securityEvents: List<SecurityEventDto> = emptyList(),
    val selectedTab: Int = 0, // 0: Overview, 1: Users, 2: Security Events, 3: Audit Logs, 4: SMTP Settings
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AdminViewModel(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null, successMessage = null) }
        when (tab) {
            0 -> loadStats()
            1 -> loadUsers()
            2 -> loadSecurityEvents()
            3 -> loadAuditLogs()
            4 -> loadStats()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadUsers(search = query.ifBlank { null })
    }

    fun loadDashboardData() {
        loadStats()
        loadUsers()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = adminRepository.getStats()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, stats = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun loadUsers(search: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = adminRepository.getUsers(search = search)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, users = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun toggleUserBadge(userId: Int, currentStatus: Boolean) {
        val newStatus = !currentStatus
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = adminRepository.updateUserBadge(
                userId = userId,
                isVerifiedBadge = newStatus,
                reason = "Admin toggle: ${if (newStatus) "Granted" else "Revoked"}"
            )) {
                is ApiResult.Success -> {
                    // Update user in local state
                    val updatedList = _uiState.value.users.map {
                        if (it.id == userId) it.copy(isVerifiedBadge = newStatus) else it
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            users = updatedList,
                            successMessage = "Verification badge successfully updated."
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun toggleUserStatus(userId: Int, currentActive: Boolean) {
        val newStatus = !currentActive
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = adminRepository.updateUserStatus(
                userId = userId,
                isActive = newStatus,
                reason = "Admin toggle: ${if (newStatus) "Unbanned" else "Banned"}"
            )) {
                is ApiResult.Success -> {
                    val updatedList = _uiState.value.users.map {
                        if (it.id == userId) it.copy(isActive = newStatus) else it
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            users = updatedList,
                            successMessage = "User status updated."
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun loadAuditLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = adminRepository.getAuditLogs()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, auditLogs = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun loadSecurityEvents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = adminRepository.getSecurityEvents()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, securityEvents = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }
}

