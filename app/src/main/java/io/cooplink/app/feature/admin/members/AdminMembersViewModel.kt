package io.cooplink.app.feature.admin.members

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.core.util.retrying
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AdminMembersVM"
private const val GENERIC_LOAD_ERROR = "Could not load members. Pull down to retry."

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class InviteMemberResponse(
    val member_id: String? = null,
    val id: String?        = null,
    val message: String?   = null,
)

data class ImportRow(val fullName: String, val phone: String, val email: String?)

private val IMPORT_NAME_HEADERS  = setOf("full_name", "name", "fullname", "full name")
private val IMPORT_PHONE_HEADERS = setOf("phone", "phone_number", "phone number")
private val IMPORT_EMAIL_HEADERS = setOf("email", "email_address", "email address")

/** Simple manual CSV parser (no Apache POI/.xlsx support — that's a heavy,
 * non-Android-native library and a bigger ask than "add CSV import"; the app
 * only reads plain .csv text). Header names are matched case-insensitively
 * against a few common variants. */
fun parseMemberImportCsv(csvText: String): List<ImportRow> {
    val lines = csvText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size < 2) return emptyList()

    val headers = lines[0].split(",").map { it.trim().trim('"').lowercase() }
    val nameIdx  = headers.indexOfFirst { it in IMPORT_NAME_HEADERS }
    val phoneIdx = headers.indexOfFirst { it in IMPORT_PHONE_HEADERS }
    val emailIdx = headers.indexOfFirst { it in IMPORT_EMAIL_HEADERS }
    if (nameIdx == -1 || phoneIdx == -1) return emptyList()

    return lines.drop(1).mapNotNull { line ->
        val values = line.split(",").map { it.trim().trim('"') }
        val name = values.getOrNull(nameIdx)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val phone = values.getOrNull(phoneIdx)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val email = if (emailIdx >= 0) values.getOrNull(emailIdx)?.takeIf { it.isNotBlank() } else null
        ImportRow(fullName = name, phone = phone, email = email)
    }
}

data class AdminMembersUiState(
    val isLoading: Boolean            = true,
    val members: List<MemberDetails>  = emptyList(),
    val query: String                  = "",
    val error: String?                 = null,
    val snackbarMessage: String?       = null,
    val memberIdPrefix: String          = "MEM",
    val isInviting: Boolean             = false,
    val inviteError: String?            = null,
    val isBulkProcessing: Boolean       = false,
    val bulkError: String?              = null,
    val importPreview: List<ImportRow>  = emptyList(),
    val importProgress: Int             = 0,
    val importTotal: Int                = 0,
    val importDone: Boolean             = false,
) {
    val filtered: List<MemberDetails>
        get() = if (query.isBlank()) members else members.filter {
            it.fullName?.contains(query, ignoreCase = true) == true ||
                it.displayId.contains(query, ignoreCase = true)
        }
}

@HiltViewModel
class AdminMembersViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val cooperativeRepository: CooperativeRepository,
    private val memberRepository: MemberRepository,
    val inactivityManager: InactivityManager,
    val signedUrlManager: SignedUrlManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminMembersUiState())
    val state: StateFlow<AdminMembersUiState> = _state.asStateFlow()

    // Keyed by member id — resolved once per list load instead of once per
    // row, so scrolling a long member list doesn't fire one sign-urls call
    // per avatar.
    private val _avatarUrls = MutableStateFlow<Map<String, String?>>(emptyMap())
    val avatarUrls: StateFlow<Map<String, String?>> = _avatarUrls.asStateFlow()

    init { load() }

    private fun loadAvatars(members: List<MemberDetails>) {
        viewModelScope.launch(Dispatchers.IO) {
            val signed = signedUrlManager.batchSignAvatars(members)
            _avatarUrls.update { it + signed }
        }
    }

    fun refresh() = load()

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearInviteError() { _state.value = _state.value.copy(inviteError = null) }
    fun clearBulkError() { _state.value = _state.value.copy(bulkError = null) }

    fun previewImport(csvText: String) {
        val rows = parseMemberImportCsv(csvText)
        _state.value = _state.value.copy(
            importPreview = rows,
            bulkError = if (rows.isEmpty()) "Could not read any rows — check the CSV has full_name and phone columns" else null,
        )
    }

    fun clearImportPreview() {
        _state.value = _state.value.copy(importPreview = emptyList(), importProgress = 0, importTotal = 0, importDone = false)
    }

    fun confirmImport() {
        val rows = _state.value.importPreview
        viewModelScope.launch {
            _state.value = _state.value.copy(isBulkProcessing = true, importTotal = rows.size, importProgress = 0, importDone = false)
            val coopId = authRepository.currentCooperativeId()
            var succeeded = 0
            rows.forEachIndexed { index, row ->
                runCatching {
                    supabase.functions.invoke(
                        function = "invite-member",
                        body = buildJsonObject {
                            put("full_name", row.fullName)
                            put("phone", row.phone)
                            put("email", row.email)
                            coopId?.let { put("cooperative_id", it) }
                        },
                    )
                }.onSuccess { succeeded++ }
                    .onFailure { Log.w(TAG, "Import failed for ${row.fullName}", it) }
                _state.value = _state.value.copy(importProgress = index + 1)
            }
            _state.value = _state.value.copy(
                isBulkProcessing = false,
                importDone = true,
                snackbarMessage = "$succeeded of ${rows.size} member(s) imported successfully",
            )
            load()
        }
    }

    fun sendBulkSms(memberIds: Set<String>, message: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBulkProcessing = true, bulkError = null)
            try {
                val recipients = _state.value.members.filter { it.id in memberIds }.mapNotNull { it.phone }
                supabase.functions.invoke(
                    function = "send-sms",
                    body = buildJsonObject {
                        put("recipients", buildJsonArray { recipients.forEach { add(it) } })
                        put("message", message)
                    },
                )
                _state.value = _state.value.copy(
                    isBulkProcessing = false,
                    snackbarMessage  = "Message sent to ${recipients.size} member(s)",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Bulk SMS failed", e)
                _state.value = _state.value.copy(isBulkProcessing = false, bulkError = "Could not send messages. Please try again.")
            }
        }
    }

    fun recordBulkContribution(memberIds: Set<String>, amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBulkProcessing = true, bulkError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                memberIds.forEach { memberId ->
                    supabase.db["contributions"].insert(
                        buildJsonObject {
                            put("member_id", memberId)
                            coopId?.let { put("cooperative_id", it) }
                            put("amount", amount)
                            put("status", "pending")
                        },
                    )
                }
                _state.value = _state.value.copy(
                    isBulkProcessing = false,
                    snackbarMessage  = "Contribution recorded for ${memberIds.size} member(s)",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Bulk contribution failed", e)
                _state.value = _state.value.copy(isBulkProcessing = false, bulkError = "Could not record contributions. Please try again.")
            }
        }
    }

    // members has no name/email/member_id columns, and creating a real member
    // needs an auth.users account (service-role only) — so the invite-member
    // edge function is the actual mechanism, not a direct table insert. The
    // "member ID preview" shown in the dialog is a cosmetic client-side
    // estimate; the schema has no member_id column to persist it against, so
    // whatever identifier the backend actually assigns is what's shown after.
    fun inviteMember(fullName: String, phone: String, email: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isInviting = true, inviteError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                val resp = supabase.functions.invoke(
                    function = "invite-member",
                    body = buildJsonObject {
                        put("full_name", fullName)
                        put("phone", phone)
                        put("email", email.ifBlank { null })
                        put("cooperative_id", coopId)
                    },
                )
                val rawBody = resp.bodyAsText()
                Log.d(TAG, "invite-member raw response: $rawBody")
                val data = runCatching { json.decodeFromString<InviteMemberResponse>(rawBody) }.getOrNull()
                val memberId = data?.member_id ?: data?.id ?: "pending"

                _state.value = _state.value.copy(
                    isInviting      = false,
                    snackbarMessage = "Member $fullName added successfully. Member ID: $memberId",
                )
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to invite member", e)
                _state.value = _state.value.copy(
                    isInviting  = false,
                    inviteError = "Could not add member. Please try again.",
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                val prefix = coopId?.let { cooperativeRepository.fetchCooperative(it) }
                    ?.name?.filter { it.isLetter() }?.take(3)?.uppercase()?.ifBlank { null } ?: "MEM"
                _state.value = _state.value.copy(memberIdPrefix = prefix)

                val members = retrying {
                    val cid = coopId ?: throw IllegalStateException("Could not determine your cooperative")
                    memberRepository.fetchMembersForCooperative(cid)
                }
                _state.value = _state.value.copy(isLoading = false, members = members)
                loadAvatars(members)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load members", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
