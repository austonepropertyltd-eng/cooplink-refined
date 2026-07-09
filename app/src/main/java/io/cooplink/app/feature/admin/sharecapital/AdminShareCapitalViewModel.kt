package io.cooplink.app.feature.admin.sharecapital

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

private const val TAG = "AdminShareCapitalVM"
private const val GENERIC_LOAD_ERROR = "Could not load share capital. Pull down to retry."

// share_capital only stores a total_value per record — there is no separate
// "units" or "unit price" column anywhere in the schema.
@Serializable
private data class ShareCapitalRecord(
    val id: String,
    val member_id: String,
    val total_value: Double = 0.0,
    val created_at: String? = null,
)

@Serializable
private data class NewShareCapitalRequest(
    val member_id: String,
    val cooperative_id: String?,
    val total_value: Double,
)

data class ShareCapitalRow(val memberId: String, val memberName: String?, val totalValue: Double, val createdAt: String?)

data class AdminShareCapitalUiState(
    val isLoading: Boolean         = true,
    val rows: List<ShareCapitalRow> = emptyList(),
    val members: List<MemberDetails> = emptyList(),
    val error: String?              = null,
    val isSubmitting: Boolean       = false,
    val submitError: String?        = null,
    val snackbarMessage: String?    = null,
) {
    val totalValue: Double get() = rows.sumOf { it.totalValue }
}

@HiltViewModel
class AdminShareCapitalViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminShareCapitalUiState())
    val state: StateFlow<AdminShareCapitalUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearSubmitError() { _state.value = _state.value.copy(submitError = null) }

    fun recordSharePurchase(memberId: String, amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                supabase.db["share_capital"].insert(
                    NewShareCapitalRequest(member_id = memberId, cooperative_id = coopId, total_value = amount),
                )
                _state.value = _state.value.copy(isSubmitting = false, snackbarMessage = "Share purchase recorded")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record share purchase", e)
                _state.value = _state.value.copy(isSubmitting = false, submitError = "Could not record share purchase. Please try again.")
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
                    val namesByMemberId = members.associateBy { it.id }

                    val records = supabase.db["share_capital"]
                        .select {
                            filter { eq("cooperative_id", coopId) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<ShareCapitalRecord>()

                    val rows = records
                        .groupBy { it.member_id }
                        .map { (memberId, recs) ->
                            ShareCapitalRow(
                                memberId   = memberId,
                                memberName = namesByMemberId[memberId]?.fullName,
                                totalValue = recs.sumOf { it.total_value },
                                createdAt  = recs.maxByOrNull { it.created_at ?: "" }?.created_at,
                            )
                        }
                        .sortedByDescending { it.totalValue }

                    AdminShareCapitalUiState(isLoading = false, rows = rows, members = members)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load share capital", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
