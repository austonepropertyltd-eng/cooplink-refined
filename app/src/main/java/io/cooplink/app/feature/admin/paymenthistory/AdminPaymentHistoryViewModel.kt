package io.cooplink.app.feature.admin.paymenthistory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.core.util.PremiumExportManager
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AdminPaymentHistoryVM"
private const val GENERIC_LOAD_ERROR = "Could not load payment history. Pull down to retry."
// "loan_repayment" is the actual value AdminRepaymentsViewModel writes
// (confirmed against AdminTransactionsViewModel's CREDIT_TYPES) — "repayment"
// alone was silently misclassifying every recorded repayment as a debit.
private val CREDIT_TYPES = setOf("contribution", "deposit", "wallet_funding", "repayment", "loan_repayment")

enum class PaymentTypeFilter(val label: String) {
    ALL("All"), CONTRIBUTIONS("Contributions"), REPAYMENTS("Repayments"),
    DISBURSEMENTS("Disbursements"), FEES("Fees"),
}

data class PaymentRow(val transaction: Transaction, val memberName: String, val memberDisplayId: String)

data class AdminPaymentHistoryUiState(
    val isLoading: Boolean            = true,
    val error: String?                 = null,
    val allPayments: List<PaymentRow>  = emptyList(),
    val query: String                  = "",
    val filter: PaymentTypeFilter       = PaymentTypeFilter.ALL,
    val totalIn: Double                = 0.0,
    val totalOut: Double               = 0.0,
) {
    val visiblePayments: List<PaymentRow>
        get() = allPayments
            .filter {
                when (filter) {
                    PaymentTypeFilter.ALL           -> true
                    PaymentTypeFilter.CONTRIBUTIONS -> it.transaction.type.contains("contribution", true)
                    PaymentTypeFilter.REPAYMENTS    -> it.transaction.type.contains("repay", true)
                    PaymentTypeFilter.DISBURSEMENTS -> it.transaction.type.contains("disburs", true)
                    PaymentTypeFilter.FEES          -> it.transaction.type.contains("fee", true)
                }
            }
            .filter { query.isBlank() || it.memberName.contains(query, true) || it.memberDisplayId.contains(query, true) }
}

@HiltViewModel
class AdminPaymentHistoryViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
    val inactivityManager: InactivityManager,
    val exportManager: PremiumExportManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminPaymentHistoryUiState())
    val state: StateFlow<AdminPaymentHistoryUiState> = _state.asStateFlow()

    init { load() }
    fun refresh() = load()
    fun onQueryChange(query: String) { _state.value = _state.value.copy(query = query) }
    fun onFilterChange(filter: PaymentTypeFilter) { _state.value = _state.value.copy(filter = filter) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")

                    val members = memberRepository.fetchMembersForCooperative(coopId).associateBy { it.id }

                    val transactions = supabase.db["transactions"]
                        .select { filter { eq("cooperative_id", coopId) }; order("created_at", Order.DESCENDING) }
                        .decodeList<Transaction>()

                    val rows = transactions.map { tx ->
                        val member = members[tx.memberId]
                        PaymentRow(tx, member?.fullName ?: "Unknown member", member?.displayId ?: (tx.memberId ?: "—"))
                    }

                    val totalIn  = transactions.filter { it.type.lowercase() in CREDIT_TYPES }.sumOf { it.amount }
                    val totalOut = transactions.filter { it.type.lowercase() !in CREDIT_TYPES }.sumOf { it.amount }

                    AdminPaymentHistoryUiState(isLoading = false, allPayments = rows, totalIn = totalIn, totalOut = totalOut)
                }
                _state.value = result.copy(query = _state.value.query, filter = _state.value.filter)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load payment history", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
