package io.cooplink.app.feature.admin.sms

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AdminSmsVM"
private val OVERDUE_STATUSES = setOf("defaulted", "overdue")

enum class RecipientGroup(val label: String) {
    ALL("All Members"),
    SPECIFIC("Specific Member"),
    OVERDUE("Members with Overdue Loans"),
    INACTIVE("Inactive Members"),
}

@Serializable
data class SmsLogRecord(val id: String, val message: String, val status: String? = null, val created_at: String? = null)

data class AdminSmsUiState(
    val isLoading: Boolean          = true,
    val members: List<MemberDetails> = emptyList(),
    val overdueMemberIds: Set<String> = emptySet(),
    val recentLogs: List<SmsLogRecord> = emptyList(),
    val recipientGroup: RecipientGroup = RecipientGroup.ALL,
    val specificMemberId: String?   = null,
    val message: String             = "",
    val isSending: Boolean          = false,
    val sendError: String?          = null,
    val snackbarMessage: String?    = null,
    val error: String?              = null,
) {
    val recipients: List<String>
        get() {
            val pool = when (recipientGroup) {
                RecipientGroup.ALL      -> members
                RecipientGroup.SPECIFIC -> members.filter { it.id == specificMemberId }
                RecipientGroup.OVERDUE  -> members.filter { it.id in overdueMemberIds }
                RecipientGroup.INACTIVE -> members.filter { !it.status.equals("active", ignoreCase = true) }
            }
            return pool.mapNotNull { it.phone }.filter { it.isNotBlank() }
        }
}

@HiltViewModel
class AdminSmsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSmsUiState())
    val state: StateFlow<AdminSmsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun onGroupChange(group: RecipientGroup) { _state.value = _state.value.copy(recipientGroup = group) }
    fun onSpecificMemberChange(memberId: String) { _state.value = _state.value.copy(specificMemberId = memberId) }
    fun onMessageChange(message: String) { if (message.length <= 160) _state.value = _state.value.copy(message = message) }
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearSendError() { _state.value = _state.value.copy(sendError = null) }

    fun send() {
        viewModelScope.launch {
            val recipients = _state.value.recipients
            if (recipients.isEmpty()) {
                _state.value = _state.value.copy(sendError = "No phone numbers found for this recipient group")
                return@launch
            }
            _state.value = _state.value.copy(isSending = true, sendError = null)
            try {
                val resp = supabase.functions.invoke(
                    function = "send-sms",
                    body = buildJsonObject {
                        put("recipients", buildJsonArray { recipients.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                        put("message", _state.value.message)
                    },
                )
                Log.d(TAG, "send-sms raw response: ${resp.bodyAsText()}")
                _state.value = _state.value.copy(isSending = false, message = "", snackbarMessage = "Message sent to ${recipients.size} recipient(s)")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS", e)
                _state.value = _state.value.copy(isSending = false, sendError = "Could not send this message. Please try again.")
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
                    val memberIds = members.map { it.id }

                    val overdueMemberIds = if (memberIds.isEmpty()) emptySet() else runCatching {
                        supabase.db["loans"]
                            .select { filter { isIn("member_id", memberIds) } }
                            .decodeList<Loan>()
                            .filter { it.status.lowercase() in OVERDUE_STATUSES }
                            .map { it.memberId }
                            .toSet()
                    }.getOrDefault(emptySet())

                    val logs = runCatching {
                        supabase.db["sms_logs"]
                            .select {
                                filter { eq("cooperative_id", coopId) }
                                order("created_at", Order.DESCENDING)
                                limit(20)
                            }
                            .decodeList<SmsLogRecord>()
                    }.getOrDefault(emptyList())

                    AdminSmsUiState(isLoading = false, members = members, overdueMemberIds = overdueMemberIds, recentLogs = logs)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load SMS screen data", e)
                _state.value = _state.value.copy(isLoading = false, error = "Could not load this screen. Pull down to retry.")
            }
        }
    }
}
