package io.cooplink.app.feature.admin.repayments

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.LoanStatus
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "AdminRepaymentsVM"
private const val GENERIC_LOAD_ERROR = "Could not load repayments. Pull down to retry."

// There is no loan_repayments, repayments, or payments table anywhere in the
// schema (confirmed via probing) — a repayment is a transactions row with a
// loan_id set. transactions.type is a Postgres enum whose valid member for
// this case is "loan_repayment", not "repayment" (confirmed via probing —
// "repayment" throws invalid input value for enum transaction_type). There's
// also no payment-method column, so the method is folded into the free-text
// description.
@Serializable
private data class NewRepaymentRequest(
    val member_id: String,
    val cooperative_id: String?,
    val loan_id: String,
    val type: String = "loan_repayment",
    val amount: Double,
    val description: String? = null,
)

@Serializable
private data class LoanBalancePatch(val outstanding_balance: Double, val status: String)

data class RepaymentRow(val transaction: Transaction, val memberName: String?, val loanBalance: Double?)

data class AdminRepaymentsUiState(
    val isLoading: Boolean          = true,
    val repayments: List<RepaymentRow> = emptyList(),
    val members: List<MemberDetails> = emptyList(),
    val loansByMember: Map<String, List<Loan>> = emptyMap(),
    val error: String?               = null,
    val isSubmitting: Boolean        = false,
    val submitError: String?         = null,
    val snackbarMessage: String?     = null,
)

@HiltViewModel
class AdminRepaymentsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminRepaymentsUiState())
    val state: StateFlow<AdminRepaymentsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearSubmitError() { _state.value = _state.value.copy(submitError = null) }

    fun recordRepayment(memberId: String, loan: Loan, amount: Double, paymentDate: String, paymentMethod: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                supabase.db["transactions"].insert(
                    NewRepaymentRequest(
                        member_id      = memberId,
                        cooperative_id = coopId,
                        loan_id        = loan.id,
                        amount         = amount,
                        description    = "Repayment via $paymentMethod on $paymentDate",
                    ),
                )

                val newBalance = (loan.outstandingBalance - amount).coerceAtLeast(0.0)
                val newStatus = if (newBalance <= 0.0) LoanStatus.COMPLETED else LoanStatus.REPAYING
                supabase.db["loans"].update(LoanBalancePatch(newBalance, newStatus)) { filter { eq("id", loan.id) } }

                _state.value = _state.value.copy(isSubmitting = false, snackbarMessage = "Repayment recorded")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record repayment", e)
                _state.value = _state.value.copy(isSubmitting = false, submitError = "Could not record repayment. Please try again.")
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
                    val memberIds = members.map { it.id }

                    val loans = if (memberIds.isEmpty()) emptyList() else supabase.db["loans"]
                        .select { filter { isIn("member_id", memberIds) } }
                        .decodeList<Loan>()
                    val loanById = loans.associateBy { it.id }
                    val loansByMember = loans.groupBy { it.memberId }

                    // transactions.cooperative_id isn't reliably populated on every row —
                    // fall back to a member_id-based lookup if the direct filter is empty.
                    var repaymentTxs = supabase.db["transactions"]
                        .select {
                            filter {
                                eq("cooperative_id", coopId)
                                eq("type", "loan_repayment")
                            }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Transaction>()
                    Log.d(TAG, "repayments by cooperative_id: ${repaymentTxs.size} rows")

                    if (repaymentTxs.isEmpty() && memberIds.isNotEmpty()) {
                        repaymentTxs = supabase.db["transactions"]
                            .select {
                                filter {
                                    isIn("member_id", memberIds)
                                    eq("type", "loan_repayment")
                                }
                                order("created_at", Order.DESCENDING)
                            }
                            .decodeList<Transaction>()
                        Log.d(TAG, "repayments by member_id fallback: ${repaymentTxs.size} rows")
                    }

                    val repayments = repaymentTxs.map { tx ->
                        RepaymentRow(
                            transaction = tx,
                            memberName  = namesByMemberId[tx.memberId]?.fullName,
                            loanBalance = tx.loanId?.let { loanById[it]?.outstandingBalance },
                        )
                    }

                    AdminRepaymentsUiState(isLoading = false, repayments = repayments, members = members, loansByMember = loansByMember)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load repayments", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
