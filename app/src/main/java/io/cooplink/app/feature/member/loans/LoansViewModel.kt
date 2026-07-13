package io.cooplink.app.feature.member.loans

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.LoanPlan
import io.cooplink.app.core.domain.LoanStatus
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "LoansVM"
private const val GENERIC_LOAD_ERROR = "Could not load your loans. Pull down to retry."

@Serializable
private data class NewLoanRequest(
    val member_id: String,
    val cooperative_id: String?,
    val plan_id: String?,
    val outstanding_balance: Double,
    // loans.amount_requested is a NOT NULL column — the amount applied for
    // never changes after submission, unlike outstanding_balance which drops
    // as repayments come in, so both are populated with the same starting
    // value at insert time.
    val amount_requested: Double,
    val interest_rate: Double,
    val status: String = LoanStatus.APPLIED,
)

data class LoansUiState(
    val isLoading: Boolean       = true,
    val loans: List<Loan>       = emptyList(),
    val plans: List<LoanPlan>   = emptyList(),
    val error: String?          = null,
    val isSubmitting: Boolean   = false,
    val submitError: String?    = null,
    val justSubmitted: Boolean  = false,
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoansUiState())
    val state: StateFlow<LoansUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    fun acknowledgeSubmitted() {
        _state.value = _state.value.copy(justSubmitted = false)
    }

    fun clearSubmitError() {
        _state.value = _state.value.copy(submitError = null)
    }

    fun submitApplication(planId: String?, amount: Double, interestRate: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")

                supabase.db["loans"].insert(
                    NewLoanRequest(
                        member_id           = member.id,
                        cooperative_id      = member.cooperativeId,
                        plan_id             = planId,
                        outstanding_balance = amount,
                        amount_requested    = amount,
                        interest_rate       = interestRate,
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false, justSubmitted = true)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit loan application", e)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    submitError  = "Could not submit your application. Please try again.",
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val uid = supabase.auth.currentSessionOrNull()?.user?.id
                        ?: throw IllegalStateException("Not authenticated")

                    val member = memberRepository.findCurrentMember()
                    val memberId = member?.id ?: uid

                    val loans = supabase.db["loans"]
                        .select {
                            filter { eq("member_id", memberId) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Loan>()

                    val plans = member?.cooperativeId?.let { coopId ->
                        runCatching {
                            supabase.db["loan_plans"]
                                .select {
                                    filter {
                                        eq("cooperative_id", coopId)
                                        eq("active", true)
                                    }
                                }
                                .decodeList<LoanPlan>()
                        }.getOrNull()
                    } ?: emptyList()

                    LoansUiState(isLoading = false, loans = loans, plans = plans)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load loans", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
