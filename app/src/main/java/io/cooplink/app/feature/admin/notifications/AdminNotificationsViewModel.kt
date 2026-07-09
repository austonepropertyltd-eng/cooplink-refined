package io.cooplink.app.feature.admin.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "AdminNotificationsVM"

enum class NotificationTarget(val label: String) { ALL("All Members"), SPECIFIC("Specific Member") }

@Serializable
private data class NewNotificationRequest(
    val user_id: String,
    val cooperative_id: String?,
    val title: String,
    val message: String,
    val type: String = "admin_broadcast",
    val is_read: Boolean = false,
)

@Serializable
data class SentNotification(
    val id: String,
    val title: String?   = null,
    val message: String? = null,
    val created_at: String,
)

data class AdminNotificationsUiState(
    val isLoading: Boolean          = true,
    val members: List<MemberDetails> = emptyList(),
    val sent: List<SentNotification> = emptyList(),
    val target: NotificationTarget  = NotificationTarget.ALL,
    val specificMemberId: String?   = null,
    val title: String               = "",
    val message: String             = "",
    val isSending: Boolean          = false,
    val sendError: String?          = null,
    val snackbarMessage: String?    = null,
    val error: String?              = null,
)

@HiltViewModel
class AdminNotificationsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminNotificationsUiState())
    val state: StateFlow<AdminNotificationsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun onTargetChange(target: NotificationTarget) { _state.value = _state.value.copy(target = target) }
    fun onSpecificMemberChange(memberId: String) { _state.value = _state.value.copy(specificMemberId = memberId) }
    fun onTitleChange(title: String) { _state.value = _state.value.copy(title = title) }
    fun onMessageChange(message: String) { _state.value = _state.value.copy(message = message) }
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearSendError() { _state.value = _state.value.copy(sendError = null) }

    fun send() {
        viewModelScope.launch {
            val s = _state.value
            val targets = when (s.target) {
                NotificationTarget.ALL      -> s.members
                NotificationTarget.SPECIFIC -> s.members.filter { it.id == s.specificMemberId }
            }.mapNotNull { it.userId }

            if (targets.isEmpty()) {
                _state.value = s.copy(sendError = "No members found for this target")
                return@launch
            }

            _state.value = s.copy(isSending = true, sendError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                targets.forEach { userId ->
                    supabase.db["notifications"].insert(
                        NewNotificationRequest(user_id = userId, cooperative_id = coopId, title = s.title, message = s.message),
                    )
                }
                _state.value = _state.value.copy(
                    isSending = false, title = "", message = "",
                    snackbarMessage = "Notification sent to ${targets.size} member(s)",
                )
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send notification", e)
                _state.value = _state.value.copy(isSending = false, sendError = "Could not send this notification. Please try again.")
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")

                    val members = memberRepository.fetchMembersForCooperative(coopId)

                    val sent = runCatching {
                        supabase.db["notifications"]
                            .select {
                                filter { eq("cooperative_id", coopId) }
                                order("created_at", Order.DESCENDING)
                                limit(30)
                            }
                            .decodeList<SentNotification>()
                    }.getOrDefault(emptyList())

                    AdminNotificationsUiState(isLoading = false, members = members, sent = sent)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load notifications screen", e)
                _state.value = _state.value.copy(isLoading = false, error = "Could not load this screen. Pull down to retry.")
            }
        }
    }
}
