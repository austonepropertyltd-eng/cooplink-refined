package io.cooplink.app.feature.admin.accounting

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.PremiumExportManager
import io.cooplink.app.core.util.retrying
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

                    val loans = runCatching {
                        supabase.db["loans"].select { filter { eq("cooperative_id", coopId) } }.decodeList<Loan>()
                    }.getOrDefault(emptyList())

                    var runningBalance = 0.0
                    val ledger = transactions.map { tx ->
                        val isCredit = tx.type.lowercase() in setOf("contribution", "deposit", "wallet_funding", "repayment", "fee")
                        val debit = if (isCredit) 0.0 else tx.amount
                        val credit = if (isCredit) tx.amount else 0.0
                        runningBalance += credit - debit
                        LedgerEntry(tx.createdAt.take(10), tx.description ?: tx.type.replaceFirstChar { it.uppercase() }, debit, credit, runningBalance)
                    }

                    val savings = transactions.filter { it.type.lowercase() in setOf("contribution", "deposit", "wallet_funding") }.sumOf { it.amount }
                    val repayments = transactions.filter { it.type.contains("repay", true) }.sumOf { it.amount }
                    val adminFees = transactions.filter { it.type.contains("fee", true) }.sumOf { it.amount }
                    val outstandingLoans = loans.sumOf { it.outstandingBalance }

                    val trialBalance = listOf(
                        TrialBalanceRow("Member Savings", 0.0, savings),
                        TrialBalanceRow("Loan Portfolio", outstandingLoans, 0.0),
                        TrialBalanceRow("Repayments Received", 0.0, repayments),
                        TrialBalanceRow("Admin Fees", 0.0, adminFees),
                    )

                    // loans has no interest_rate-times-balance accrual tracking,
                    // so "interest earned" is approximated from repayments minus
                    // principal reduction isn't derivable either — the only real
                    // income figures the schema actually supports are fees.
                    AdminAccountingUiState(
                        isLoading = false,
                        ledger = ledger,
                        trialBalance = trialBalance,
                        interestEarned = 0.0,
                        adminFees = adminFees,
                        penaltyFees = 0.0,
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
