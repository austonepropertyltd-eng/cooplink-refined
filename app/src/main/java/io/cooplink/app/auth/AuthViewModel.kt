package io.cooplink.app.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.fragment.app.FragmentActivity
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.domain.AuthUser
import io.cooplink.app.core.security.BiometricHelper
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AuthViewModel"

sealed class AuthUiState {
    object Idle       : AuthUiState()
    data class Loading(val message: String = "Signing in") : AuthUiState()
    data class Connecting(val attempt: Int = 1, val maxAttempts: Int = 1) : AuthUiState()
    data class Success(val user: AuthUser) : AuthUiState()
    data class Error(val message: String, val retryable: Boolean = true) : AuthUiState()
}

enum class LoginMode { MEMBER, ADMIN }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val sessionPrefs: SessionPreferences,
    private val biometricHelper: BiometricHelper,
) : ViewModel() {

    private val _state     = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _loginMode = MutableStateFlow(LoginMode.MEMBER)
    val loginMode: StateFlow<LoginMode> = _loginMode.asStateFlow()

    val savedIdentifier: StateFlow<String?> = sessionPrefs.savedIdentifierFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val savedIsAdmin: StateFlow<Boolean> = sessionPrefs.savedIsAdminFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Supabase's own SDK persists the session token itself and auto-refreshes
    // it; this is just a fast synchronous check of that cache, used to decide
    // whether "Welcome back" can skip straight to biometric or still needs a
    // password (e.g. the refresh token finally expired).
    fun hasValidSession(): Boolean = repo.isLoggedIn()

    fun isBiometricAvailable(): Boolean = biometricHelper.isAvailable()

    fun authenticateBiometric(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        biometricHelper.authenticate(activity, onSuccess = onSuccess, onError = onError)
    }

    fun toggleMode() {
        _loginMode.value =
            if (_loginMode.value == LoginMode.MEMBER) LoginMode.ADMIN else LoginMode.MEMBER
    }

    fun setMode(mode: LoginMode) { _loginMode.value = mode }

    // Best-effort: fired when the login screen first appears so the edge
    // function's container is already warm by the time the user submits the
    // form. Never touches [_state] — a failure here must be invisible to the
    // user, since it isn't a real login attempt.
    fun pingEdgeFunction() {
        viewModelScope.launch {
            runCatching { repo.pingLoginFunction() }
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading()
            val isAdminMode = _loginMode.value == LoginMode.ADMIN
            val startedAt = System.currentTimeMillis()
            val result = if (!isAdminMode)
                repo.loginMember(identifier, password, onRetrying = { attempt, max -> _state.value = AuthUiState.Connecting(attempt, max) })
            else
                repo.loginAdmin(identifier, password)
            val elapsedMs = System.currentTimeMillis() - startedAt
            Log.d(TAG, "Login completed in ${elapsedMs}ms (mode=$loginMode, success=${result.isSuccess})")

            result
                .onSuccess {
                    sessionPrefs.save(identifier, isAdminMode)
                    _state.value = AuthUiState.Success(it)
                }
                .onFailure {
                    Log.e(TAG, "Login failed", it)
                    _state.value = AuthUiState.Error(it.cleanErrorMessage(), retryable = it.isRetryable())
                }
        }
    }

    /** Re-enter the app using the still-valid cached Supabase session behind a
     * remembered identifier — no password involved. Only meaningful when
     * [hasValidSession] is true; callers should gate this behind that check
     * (and a biometric prompt) rather than call it blindly. */
    fun loginWithSaved() {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading("Welcome back")
            val user = repo.fetchCurrentUser()
            _state.value = if (user != null) {
                AuthUiState.Success(user)
            } else {
                AuthUiState.Error("Your session has expired. Please log in again.")
            }
        }
    }

    fun forgetSavedAccount() {
        viewModelScope.launch { sessionPrefs.clear() }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            sessionPrefs.clearCachedRole()
            _state.value = AuthUiState.Idle
        }
    }

    fun resetPassword(identifier: String, isAdmin: Boolean, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = if (isAdmin) repo.resetAdminPassword(identifier) else repo.resetMemberPassword(identifier)
            onResult(result)
        }
    }

    fun clearError() { _state.value = AuthUiState.Idle }

    // RestException.message embeds the full request (URL, headers, bearer token) for
    // debugging — never show that on screen. .error is the short, safe reason string.
    // For everything else, map by exception type/message keyword rather than ever
    // surfacing the raw exception text (which can contain a URL).
    private fun Throwable.cleanErrorMessage(): String = when {
        this is RestException && this.statusCode == 502 ->
            "Server is starting up. Please wait a moment and try again."
        this is RestException ->
            error.ifBlank { "Something went wrong. Please try again." }.let {
                when {
                    it.contains("invalid", ignoreCase = true) && it.contains("credential", ignoreCase = true) ->
                        "Invalid Member ID or password."
                    it.contains("not found", ignoreCase = true) || it.contains("no rows", ignoreCase = true) ->
                        "We couldn't find that account. Check your Member ID and try again."
                    it.contains("locked", ignoreCase = true) || it.contains("too many", ignoreCase = true) ->
                        "Too many attempts. Please wait a few minutes and try again."
                    else -> it
                }
            }
        this is io.ktor.client.plugins.HttpRequestTimeoutException || this is kotlinx.coroutines.TimeoutCancellationException ->
            "Connection timed out. Please check your internet and try again."
        this is java.net.UnknownHostException || this is java.io.IOException ->
            "No internet connection. Check your network and try again."
        else -> "Login failed. Please try again."
    }

    // Only network/cold-start-shaped failures are worth an immediate retry
    // button — a wrong-password result should send the user back to editing
    // the form, not invite them to hammer the same bad request again.
    private fun Throwable.isRetryable(): Boolean = when {
        this is RestException && this.statusCode == 502 -> true
        this is io.ktor.client.plugins.HttpRequestTimeoutException || this is kotlinx.coroutines.TimeoutCancellationException -> true
        this is java.net.UnknownHostException || this is java.io.IOException -> true
        else -> false
    }
}
