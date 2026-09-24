package io.cooplink.app.feature.admin.accounting

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.LoanStatus
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.PremiumExportManager
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AdminAccountingVM"
private const val GENERIC_LOAD_ERROR = "Could not load accounting data. Pull down to retry."

data class LedgerEntry(val date: String, val description: String, val debit: Double, val credit: Double, val balance: Double)
data class TrialBalanceRow(val account: String, val debit: Double, val credit: Double)

data class AdminAccountingUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val ledger: List<LedgerEntry> = emptyList(),
    val trialBalance: List<TrialBalanceRow> = emptyList(),
    val interestEarned: Double = 0.0,
    val adminFees: Double = 0.0,
    val penaltyFees: Double = 0.0,
    val operatingCosts: Double = 0.0,
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
) {
    val netSurplus: Double get() = (interestEarned + adminFees + penaltyFees) - operatingCosts
}

@HiltViewModel
class AdminAccountingViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    val exportManager: PremiumExportManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAccountingUiState())
    val state: StateFlow<AdminAccountingUiState> = _state.asStateFlow()

    init { load() }
    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")

                    val transactions = supabase.db["transactions"]
                        .select { filter { eq("cooperative_id", coopId) }; order("created_at", Order.ASCENDING) }
                        .decodeList<Transaction>()

                    // loans.cooperative_id isn't reliably populated on every
                    // row (same gap documented elsewhere for transactions) —
                    // every other admin screen that reads loans scopes via
                    // member_id instead; filtering loans directly by
                    // cooperative_id here could silently return an empty
                    // list and understate the loan portfolio with no error.
                    val memberIds = supabase.db["members"]
                        .select(Columns.list("id")) { filter { eq("cooperative_id", coopId) } }
                        .decodeList<Map<String, String>>()
                        .mapNotNull { it["id"] }

                    val loans = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["loans"].select { filter { isIn("member_id", memberIds) } }.decodeList<Loan>()
                    }.getOrDefault(emptyList())

                    var runningBalance = 0.0
                    val ledger = transactions.map { tx ->
                        // "loan_repayment" is the actual value AdminRepaymentsViewModel writes
                        // (confirmed against AdminTransactionsViewModel's CREDIT_TYPES) — "repayment"
                        // alone was silently misclassifying every recorded repayment as a debit.
                        val isCredit = tx.type.lowercase() in setOf("contribution", "deposit", "wallet_funding", "repayment", "loan_repayment", "fee")
                        val debit = if (isCredit) 0.0 else tx.amount
                        val credit = if (isCredit) tx.amount else 0.0
                        runningBalance += credit - debit
                        LedgerEntry(tx.createdAt.take(10), tx.description ?: tx.type.replaceFirstChar { it.uppercase() }, debit, credit, runningBalance)
                    }

                    val savings = transactions.filter { it.type.lowercase() in setOf("contribution", "deposit", "wallet_funding") }.sumOf { it.amount }
                    val repayments = transactions.filter { it.type.contains("repay", true) }.sumOf { it.amount }
                    val adminFees = transactions.filter { it.type.contains("fee", true) && !it.type.contains("late", true) }.sumOf { it.amount }
                    val penaltyFees = transactions.filter { it.type.contains("fine", true) || it.type.contains("penalty", true) }.sumOf { it.amount }
                    val outstandingLoans = loans.sumOf { it.outstandingBalance }

                    // Interest income is only recognized once a loan is fully repaid
                    // (status == COMPLETED) rather than accrued proportionally as
                    // repayments come in — total_interest is frozen at application
                    // time and doesn't track partial progress toward it.
                    val interestEarned = loans.filter { it.status == LoanStatus.COMPLETED }.sumOf { it.totalInterest ?: 0.0 }

                    val trialBalance = listOf(
                        TrialBalanceRow("Member Savings", 0.0, savings),
                        TrialBalanceRow("Loan Portfolio", outstandingLoans, 0.0),
                        TrialBalanceRow("Repayments Received", 0.0, repayments),
                        TrialBalanceRow("Admin Fees", 0.0, adminFees),
                        TrialBalanceRow("Interest Earned", 0.0, interestEarned),
                        TrialBalanceRow("Late Fees / Penalties", 0.0, penaltyFees),
                    )

                    AdminAccountingUiState(
                        isLoading = false,
                        ledger = ledger,
                        trialBalance = trialBalance,
                        interestEarned = interestEarned,
                        adminFees = adminFees,
                        penaltyFees = penaltyFees,
                        operatingCosts = 0.0,
                        totalAssets = outstandingLoans,
                        totalLiabilities = savings,
                    )
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load accounting data", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
