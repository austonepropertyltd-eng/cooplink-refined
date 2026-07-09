package io.cooplink.app.feature.admin.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.domain.Cooperative
import io.cooplink.app.core.domain.UserRole
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.InactivityManager
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "AdminSettingsVM"

@Serializable
data class RoleRow(
    val user_id: String?         = null,
    val role: String?            = null,
    val cooperative_id: String?  = null,
)

val ASSIGNABLE_ROLES = listOf("coop_admin", "treasurer", "accountant", "loan_officer", "auditor", "member")

@Serializable
data class BankAccountRow(
    val id: String,
    val cooperative_id: String? = null,
    val bank_name: String?      = null,
    val account_number: String? = null,
    val account_name: String?   = null,
)

@Serializable
private data class CooperativeProfilePatch(
    val name: String, val address: String?, val phone: String?,
    val email: String?, val whatsapp_number: String?, val logo_url: String?,
)

@Serializable
private data class RolePatch(val role: String)

@Serializable
private data class NewBankAccountRequest(val cooperative_id: String?, val bank_name: String, val account_number: String, val account_name: String)

data class AdminSettingsUiState(
    val cooperative: Cooperative?    = null,
    val isSuperAdmin: Boolean        = false,
    val roles: List<RoleRow>         = emptyList(),
    val isLoadingRoles: Boolean      = false,
    val bankAccounts: List<BankAccountRow> = emptyList(),
    val isLoadingBankAccounts: Boolean = false,
    val isSavingProfile: Boolean     = false,
    val profileError: String?       = null,
    val isSavingBankAccount: Boolean = false,
    val bankAccountError: String?   = null,
    val updatingUserId: String?     = null,
    val snackbarMessage: String?     = null,
    val isLoading: Boolean            = true,
)

@HiltViewModel
class AdminSettingsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val cooperativeRepository: CooperativeRepository,
    private val sessionPreferences: SessionPreferences,
    val inactivityManager: InactivityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSettingsUiState())
    val state: StateFlow<AdminSettingsUiState> = _state.asStateFlow()

    val autoLogoutEnabled: StateFlow<Boolean> = sessionPreferences.autoLogoutEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val requireBiometric: StateFlow<Boolean> = sessionPreferences.requireBiometricFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoLogout(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setAutoLogoutEnabled(enabled)
            inactivityManager.setAutoLogout(enabled)
        }
    }

    fun setRequireBiometric(enabled: Boolean) {
        viewModelScope.launch { sessionPreferences.setRequireBiometric(enabled) }
    }

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val coopId = authRepository.currentCooperativeId()
            if (coopId == null) {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }
            val coop = cooperativeRepository.fetchCooperative(coopId)
            val isSuperAdmin = runCatching { authRepository.fetchCurrentUser()?.role == UserRole.SUPER_ADMIN }.getOrDefault(false)
            _state.value = _state.value.copy(cooperative = coop, isSuperAdmin = isSuperAdmin, isLoading = false)
        }
    }

    fun loadRoles() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingRoles = true)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                val roles = supabase.db["user_roles"]
                    .select { filter { eq("cooperative_id", coopId) } }
                    .decodeList<RoleRow>()
                _state.value = _state.value.copy(isLoadingRoles = false, roles = roles)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load roles", e)
                _state.value = _state.value.copy(
                    isLoadingRoles  = false,
                    snackbarMessage = "Could not load role assignments",
                )
            }
        }
    }

    fun updateCooperativeProfile(name: String, address: String, phone: String, email: String, whatsappNumber: String, logoUrl: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingProfile = true, profileError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                supabase.db["cooperatives"].update(
                    CooperativeProfilePatch(
                        name = name, address = address.ifBlank { null }, phone = phone.ifBlank { null },
                        email = email.ifBlank { null }, whatsapp_number = whatsappNumber.ifBlank { null },
                        logo_url = logoUrl.ifBlank { null },
                    ),
                ) { filter { eq("id", coopId) } }
                val coop = cooperativeRepository.fetchCooperative(coopId)
                _state.value = _state.value.copy(isSavingProfile = false, cooperative = coop, snackbarMessage = "Cooperative profile updated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update cooperative profile", e)
                _state.value = _state.value.copy(isSavingProfile = false, profileError = "Could not save changes. Please try again.")
            }
        }
    }

    fun clearProfileError() { _state.value = _state.value.copy(profileError = null) }

    fun loadBankAccounts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingBankAccounts = true)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                val accounts = supabase.db["cooperative_bank_accounts"]
                    .select { filter { eq("cooperative_id", coopId) } }
                    .decodeList<BankAccountRow>()
                _state.value = _state.value.copy(isLoadingBankAccounts = false, bankAccounts = accounts)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bank accounts", e)
                _state.value = _state.value.copy(isLoadingBankAccounts = false, snackbarMessage = "Could not load bank accounts")
            }
        }
    }

    fun addBankAccount(bankName: String, accountNumber: String, accountName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingBankAccount = true, bankAccountError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                supabase.db["cooperative_bank_accounts"].insert(
                    NewBankAccountRequest(coopId, bankName, accountNumber, accountName),
                )
                _state.value = _state.value.copy(isSavingBankAccount = false, snackbarMessage = "Bank account added")
                loadBankAccounts()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add bank account", e)
                _state.value = _state.value.copy(isSavingBankAccount = false, bankAccountError = "Could not add this account. Please try again.")
            }
        }
    }

    fun clearBankAccountError() { _state.value = _state.value.copy(bankAccountError = null) }

    fun changeRole(userId: String, newRole: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(updatingUserId = userId)
            try {
                supabase.db["user_roles"].update(RolePatch(newRole)) { filter { eq("user_id", userId) } }
                _state.value = _state.value.copy(updatingUserId = null, snackbarMessage = "Role updated")
                loadRoles()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to change role for $userId", e)
                _state.value = _state.value.copy(updatingUserId = null, snackbarMessage = "Could not update this role")
            }
        }
    }

    fun sendPasswordReset() {
        viewModelScope.launch {
            val email = runCatching { authRepository.fetchCurrentUser()?.email }.getOrNull()
            if (email.isNullOrBlank()) {
                _state.value = _state.value.copy(snackbarMessage = "Could not determine your email")
                return@launch
            }
            authRepository.resetAdminPassword(email)
                .onSuccess { _state.value = _state.value.copy(snackbarMessage = "Password reset email sent to $email") }
                .onFailure {
                    Log.e(TAG, "Failed to send password reset", it)
                    _state.value = _state.value.copy(snackbarMessage = "Could not send reset email")
                }
        }
    }

    fun showComingSoon(feature: String) {
        _state.value = _state.value.copy(snackbarMessage = "$feature is coming soon")
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }
}
