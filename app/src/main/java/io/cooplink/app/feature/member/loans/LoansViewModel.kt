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
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    // The full amount owed if approved — principal PLUS interest computed by
    // the reducing-balance EMI formula in LoansScreen.calcLoan(), not the raw
    // principal. Interest was previously calculated only for the "Repayment
    // Summary" preview shown to the member and then discarded at submit time,
    // so every loan's balance silently tracked principal only and admins
    // recording repayments were never actually collecting any interest.
    val outstanding_balance: Double,
    // loans.amount_requested is a NOT NULL column — the raw principal applied
    // for, which never changes after submission, unlike outstanding_balance
    // which starts at principal+interest and drops as repayments come in.
    val amount_requested: Double,
    val interest_rate: Double,
    val monthly_payment: Double,
    val admin_fee: Double,
    val duration_months: Int,
    // The interest portion alone, set once and never touched again — unlike
    // outstanding_balance (principal+interest, shrinks with repayments) this
    // is what lets Accounting recognize real interest income later instead
    // of a hardcoded 0.
    val total_interest: Double,
    val status: String = LoanStatus.APPLIED,
)

data class LoansUiState(
    val isLoading: Boolean       = true,
    val loans: List<Loan>       = emptyList(),
    val plans: List<LoanPlan>   = emptyList(),
    // Distinguishes "cooperative genuinely has no active loan plans" from
    // "the plans fetch failed" — both used to collapse into an empty list,
    // shown to the member as a misleading "No loan plans are available".
    val plansLoadFailed: Boolean = false,
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

    private var realtimeChannel: RealtimeChannel? = null

    init {
        load()
        observeLoanChanges()
    }

    fun refresh() = load()

    fun acknowledgeSubmitted() {
        _state.value = _state.value.copy(justSubmitted = false)
    }

    fun clearSubmitError() {
        _state.value = _state.value.copy(submitError = null)
    }

    fun submitApplication(
        planId: String?, amount: Double, interestRate: Double,
        totalRepayable: Double, monthlyPayment: Double, adminFee: Double, durationMonths: Int,
    ) {
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
                        outstanding_balance = totalRepayable,
                        amount_requested    = amount,
                        interest_rate       = interestRate,
                        monthly_payment     = monthlyPayment,
                        admin_fee           = adminFee,
                        duration_months     = durationMonths,
                        // outstanding_balance (= totalRepayable) is principal + interest,
                        // so subtracting principal recovers the interest portion alone.
                        total_interest      = totalRepayable - amount,
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

                    val plansResult = member?.cooperativeId?.let { coopId ->
                        runCatching {
                            supabase.db["loan_plans"]
                                .select {
                                    filter {
                                        eq("cooperative_id", coopId)
                                        eq("active", true)
                                    }
                                }
                                .decodeList<LoanPlan>()
                        }.onFailure { Log.w(TAG, "Failed to load loan plans", it) }
                    }

                    LoansUiState(
                        isLoading       = false,
                        loans           = loans,
                        plans           = plansResult?.getOrNull() ?: emptyList(),
                        plansLoadFailed = plansResult?.isFailure == true,
                    )
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load loans", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    // Reflects admin decisions (approval, rejection, disbursement) as soon as
    // they happen, instead of the member needing to pull to refresh.
    private fun observeLoanChanges() {
        viewModelScope.launch {
            val member = memberRepository.findCurrentMember()
            val memberId = member?.id ?: supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
            val channel = supabase.realtime.channel("loans-member-$memberId")
            realtimeChannel = channel
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "loans"
                filter("member_id", FilterOperator.EQ, memberId)
            }
            channel.subscribe()
            changes.collect { load() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled by the time onCleared runs, so
        // the unsubscribe is fired on a short-lived scope of its own rather
        // than silently no-oping.
        val channel = realtimeChannel ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { supabase.realtime.removeChannel(channel) }
        }
    }
}
