package io.cooplink.app.feature.admin.contributions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Contribution
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

private const val TAG = "AdminContributionsVM"
private const val GENERIC_LOAD_ERROR = "Could not load contributions. Pull down to retry."

@Serializable
private data class NewContributionRequest(
    val member_id: String,
    val cooperative_id: String?,
    val amount: Double,
    // No default — see NewRepaymentRequest.type in AdminRepaymentsViewModel
    // for why a defaulted property is silently dropped from the request even
    // when the call site passes exactly that value.
    val status: String,
)

data class AdminContributionRow(
    val id: String,
    val memberId: String,
    val memberName: String?,
    val amount: Double,
    val status: String,
    val createdAt: String,
)

data class MemberSavingsRow(val memberId: String, val memberName: String?, val total: Double)

data class AdminContributionsUiState(
    val isLoading: Boolean                    = true,
    val contributions: List<AdminContributionRow> = emptyList(),
    val members: List<MemberDetails>          = emptyList(),
    val filter: String                         = "All",
    val error: String?                         = null,
    val isSubmitting: Boolean                  = false,
    val submitError: String?                   = null,
    val snackbarMessage: String?               = null,
) {
    val filtered: List<AdminContributionRow>
        get() = if (filter == "All") contributions
        else contributions.filter { it.status.equals(filter, ignoreCase = true) }

    val poolTotal: Double get() = contributions.sumOf { it.amount }

    val groupedByMember: List<MemberSavingsRow>
        get() = contributions.groupBy { it.memberId }
            .map { (memberId, rows) -> MemberSavingsRow(memberId, rows.first().memberName, rows.sumOf { it.amount }) }
            .sortedByDescending { it.total }
}

@HiltViewModel
class AdminContributionsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminContributionsUiState())
    val state: StateFlow<AdminContributionsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    fun onFilterChange(filter: String) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearSubmitError() { _state.value = _state.value.copy(submitError = null) }

    // contributions has no "type" column, so admin-recorded entries are marked
    // completed immediately (the admin is recording money already received).
    fun recordContribution(memberId: String, amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                supabase.db["contributions"].insert(
                    // `status` must be passed explicitly — kotlinx.serialization
                    // doesn't encode a property still at its default value, so
                    // relying on the default alone drops the column from the
                    // request entirely and fails on a NOT NULL constraint
                    // (confirmed live for the identical pattern in AdminRepaymentsViewModel).
                    NewContributionRequest(member_id = memberId, cooperative_id = coopId, amount = amount, status = "completed"),
                )
                _state.value = _state.value.copy(isSubmitting = false, snackbarMessage = "Contribution recorded")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record contribution", e)
                _state.value = _state.value.copy(isSubmitting = false, submitError = "Could not record contribution. Please try again.")
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

                    val contributions = supabase.db["contributions"]
                        .select {
                            filter { eq("cooperative_id", coopId) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Contribution>()
                        .map {
                            AdminContributionRow(
                                id         = it.id,
                                memberId   = it.memberId,
                                memberName = namesByMemberId[it.memberId]?.fullName,
                                amount     = it.amount,
                                status     = it.status,
                                createdAt  = it.createdAt,
                            )
                        }

                    AdminContributionsUiState(isLoading = false, contributions = contributions, members = members)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load contributions", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
