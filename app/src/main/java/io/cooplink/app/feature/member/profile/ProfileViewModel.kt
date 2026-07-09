package io.cooplink.app.feature.member.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.core.util.StatementRow
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "ProfileVM"
private const val GENERIC_LOAD_ERROR = "Could not load your profile. Pull down to retry."

@Serializable
private data class ProfileUpdateRequest(val full_name: String, val phone: String?)

@Serializable
private data class MemberUpdateRequest(val date_of_birth: String?, val address: String?)

@Serializable
private data class AvatarUpdateRequest(val avatar_url: String)

data class ProfileUiState(
    val isLoading: Boolean       = true,
    val member: MemberDetails?   = null,
    val error: String?           = null,
    val snackbarMessage: String? = null,
    val isSavingInfo: Boolean    = false,
    val saveInfoError: String?   = null,
    val isChangingPassword: Boolean = false,
    val passwordError: String?   = null,
    val isUploadingAvatar: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val authRepository: AuthRepository,
    private val supabase: SupabaseClient,
    private val sessionPreferences: SessionPreferences,
    val inactivityManager: InactivityManager,
    val signedUrlManager: SignedUrlManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

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

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    ProfileUiState(isLoading = false, member = memberRepository.findCurrentMember())
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    // ── Personal information ─────────────────────────────────────────────────
    // full_name/phone live in `profiles` (by user_id); date_of_birth/address
    // live in `members` (by id) — there is no single table with all four.
    fun updatePersonalInfo(fullName: String, phone: String, dateOfBirth: String, address: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingInfo = true, saveInfoError = null)
            try {
                val member = _state.value.member
                    ?: throw IllegalStateException("Could not determine your member record")
                val uid = supabase.auth.currentSessionOrNull()?.user?.id
                    ?: throw IllegalStateException("Not authenticated")

                supabase.db["profiles"]
                    .update(ProfileUpdateRequest(full_name = fullName, phone = phone.ifBlank { null })) {
                        filter { eq("user_id", uid) }
                    }
                supabase.db["members"]
                    .update(MemberUpdateRequest(
                        date_of_birth = dateOfBirth.ifBlank { null },
                        address       = address.ifBlank { null },
                    )) {
                        filter { eq("id", member.id) }
                    }

                _state.value = _state.value.copy(isSavingInfo = false, snackbarMessage = "Profile updated")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update personal info", e)
                _state.value = _state.value.copy(
                    isSavingInfo  = false,
                    saveInfoError = "Could not save your changes. Please try again.",
                )
            }
        }
    }

    fun clearSaveInfoError() {
        _state.value = _state.value.copy(saveInfoError = null)
    }

    // ── Change password ──────────────────────────────────────────────────────
    fun changePassword(newPassword: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isChangingPassword = true, passwordError = null)
            try {
                supabase.auth.updateUser {
                    password = newPassword
                }
                _state.value = _state.value.copy(
                    isChangingPassword = false,
                    snackbarMessage     = "Password changed successfully",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to change password", e)
                _state.value = _state.value.copy(
                    isChangingPassword = false,
                    passwordError       = "Could not change your password. Please try again.",
                )
            }
        }
    }

    fun clearPasswordError() {
        _state.value = _state.value.copy(passwordError = null)
    }

    // ── Statement ─────────────────────────────────────────────────────────────
    fun downloadStatement(onReady: (memberName: String?, rows: List<StatementRow>) -> Unit) {
        viewModelScope.launch {
            val member = _state.value.member
            if (member == null) {
                inactivityManager.resumeTimer()
                _state.value = _state.value.copy(snackbarMessage = "Could not determine your member record")
                return@launch
            }
            runCatching {
                // java.time needs API 26+ and desugaring isn't enabled (minSdk 24),
                // so "last 90 days" is a plain ISO-date string comparison instead —
                // lexicographic order matches chronological order for YYYY-MM-DD.
                val cutoff = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -90) }
                val cutoffDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cutoff.time)
                supabase.db["transactions"]
                    .select {
                        filter { eq("member_id", member.id) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Transaction>()
                    .filter { it.createdAt.take(10) >= cutoffDate }
            }.onSuccess { transactions ->
                val rows = transactions.map { StatementRow(it.createdAt.take(10), it.type, "${it.amount}") }
                onReady(member.fullName, rows)
            }.onFailure {
                Log.e(TAG, "Failed to prepare statement", it)
                inactivityManager.resumeTimer()
                _state.value = _state.value.copy(snackbarMessage = "Could not prepare your statement")
            }
        }
    }

    // Uploads to the private "avatars" bucket at the RLS-required
    // {authUserId}/avatar.ext path, then saves that PATH (not a public URL —
    // the bucket is private) to profiles.avatar_url. Display URLs are always
    // resolved on demand via SignedUrlManager, never stored.
    fun uploadAvatar(bytes: ByteArray, fileExtension: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploadingAvatar = true)
            try {
                val uid = supabase.auth.currentSessionOrNull()?.user?.id
                    ?: throw IllegalStateException("Not authenticated")
                val path = "$uid/avatar.$fileExtension"

                supabase.storage.from("avatars").upload(path, bytes) { upsert = true }
                supabase.db["profiles"]
                    .update(AvatarUpdateRequest(avatar_url = path)) { filter { eq("user_id", uid) } }

                _state.value = _state.value.copy(isUploadingAvatar = false, snackbarMessage = "Profile photo updated")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload avatar", e)
                _state.value = _state.value.copy(
                    isUploadingAvatar = false,
                    snackbarMessage    = "Could not update your photo. Please try again.",
                )
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
