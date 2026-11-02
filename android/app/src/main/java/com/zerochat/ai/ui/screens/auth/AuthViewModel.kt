package com.zerochat.ai.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerochat.ai.data.api.ApiResult
import com.zerochat.ai.data.model.UserDto
import com.zerochat.ai.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class AuthUiState(
    val email: String = "",
    val emailError: String? = null,
    val code: String = "",
    val codeError: String? = null,
    val username: String = "",
    val usernameError: String? = null,
    val isUsernameAvailable: Boolean? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val verificationToken: String? = null,
    val devCode: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val cooldownSeconds: Int = 0,
    val expiresInSeconds: Int = 0,
    val currentUser: UserDto? = null,
    val isLoggedIn: Boolean = false,
    val isVerifiedSuccess: Boolean = false,
    val isRegisteredSuccess: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var cooldownJob: Job? = null
    private var expiryJob: Job? = null
    private var checkUsernameJob: Job? = null

    private val emailPattern = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$"
    )

    init {
        val user = authRepository.getCachedUser()
        val loggedIn = authRepository.isLoggedIn()
        _uiState.update { it.copy(currentUser = user, isLoggedIn = loggedIn) }
    }

    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = if (email.isNotBlank() && !emailPattern.matcher(email.trim()).matches()) {
                    "Please enter a valid email address"
                } else null,
                errorMessage = null
            )
        }
    }

    fun onCodeChanged(code: String) {
        _uiState.update { it.copy(code = code, codeError = null, errorMessage = null) }
    }

    fun onUsernameChanged(username: String) {
        val clean = username.trim()
        val formatError = if (clean.isNotBlank() && clean.length < 3) {
            "Username must be at least 3 characters"
        } else if (clean.isNotBlank() && !clean.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            "Letters, numbers, and underscores only"
        } else null

        _uiState.update {
            it.copy(
                username = username,
                usernameError = formatError,
                isUsernameAvailable = null,
                errorMessage = null
            )
        }

        checkUsernameJob?.cancel()
        if (clean.length >= 3 && formatError == null) {
            checkUsernameJob = viewModelScope.launch {
                delay(400) // Debounce
                when (val res = authRepository.checkUsername(clean)) {
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isUsernameAvailable = res.data.available,
                                usernameError = if (!res.data.available) "Username is already taken" else null
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, errorMessage = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit = {}) {
        if (idToken.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Google authentication failed: Empty ID token received.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.googleSignIn(idToken)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = result.data.user,
                            isLoggedIn = true,
                            successMessage = "Welcome, ${result.data.user.username}!"
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

    fun sendVerificationCode(purpose: String = "REGISTRATION", onSuccess: () -> Unit = {}) {
        val email = _uiState.value.email.trim()
        if (email.isBlank() || !emailPattern.matcher(email).matches()) {
            _uiState.update { it.copy(emailError = "Please enter a valid email address") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.sendVerificationCode(email, purpose)) {
                is ApiResult.Success -> {
                    val data = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = data.message,
                            cooldownSeconds = data.cooldownSeconds,
                            expiresInSeconds = data.expiresInSeconds,
                            devCode = data.devCode,
                            code = ""
                        )
                    }
                    startCooldownTimer(data.cooldownSeconds)
                    startExpiryTimer(data.expiresInSeconds)
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun verifyCode(purpose: String = "REGISTRATION", onSuccess: (String) -> Unit = {}) {
        val email = _uiState.value.email.trim()
        val code = _uiState.value.code.trim()

        if (code.length != 6) {
            _uiState.update { it.copy(codeError = "Code must be exactly 6 digits") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.verifyCode(email, code, purpose)) {
                is ApiResult.Success -> {
                    val token = result.data.verificationToken
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            verificationToken = token,
                            isVerifiedSuccess = true,
                            successMessage = "Code verified successfully"
                        )
                    }
                    onSuccess(token)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun passwordlessLogin(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        val token = state.verificationToken
        val email = state.email.trim()

        if (token.isNullOrBlank() || email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Verification session expired. Please request a new code.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.passwordlessLogin(email, token)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = result.data.user,
                            isLoggedIn = true,
                            successMessage = "Welcome, ${result.data.user.username}!"
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

    fun createPasswordAndRegister(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        val token = state.verificationToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Verification session expired. Please verify email again.") }
            return
        }

        if (state.password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters long") }
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.createPasswordAndRegister(
                verificationToken = token,
                email = state.email,
                username = state.username.ifBlank { "User" },
                password = state.password,
                confirmPassword = state.confirmPassword
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = result.data.user,
                            isLoggedIn = true,
                            isRegisteredSuccess = true,
                            successMessage = "Account created successfully"
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

    fun login(onSuccess: () -> Unit = {}) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your email and password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.login(email, password)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = result.data.user,
                            isLoggedIn = true,
                            successMessage = "Welcome, ${result.data.user.username}!"
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

    fun confirmPasswordReset(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        val token = state.verificationToken ?: return

        if (state.password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters long") }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = authRepository.confirmPasswordReset(
                verificationToken = token,
                email = state.email,
                newPassword = state.password,
                confirmPassword = state.confirmPassword
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Password successfully reset. Please log in."
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

    fun logout(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update {
                AuthUiState(
                    currentUser = null,
                    isLoggedIn = false
                )
            }
            onSuccess()
        }
    }

    private fun startCooldownTimer(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                _uiState.update { it.copy(cooldownSeconds = remaining) }
                delay(1000L)
                remaining--
            }
            _uiState.update { it.copy(cooldownSeconds = 0) }
        }
    }

    private fun startExpiryTimer(seconds: Int) {
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                _uiState.update { it.copy(expiresInSeconds = remaining) }
                delay(1000L)
                remaining--
            }
            _uiState.update { it.copy(expiresInSeconds = 0) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cooldownJob?.cancel()
        expiryJob?.cancel()
    }
}
