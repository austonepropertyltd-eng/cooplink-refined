package io.cooplink.app.feature.admin.statements

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.domain.Contribution
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AdminStatementsVM"
private const val GENERIC_LOAD_ERROR = "Could not load monthly statements. Pull down to retry."

data class MonthlyStatement(
    val month: String,
    val contributionsCollected: Double,
    val loanDisbursements: Double,
    val repaymentsReceived: Double,
) {
    val netPosition: Double get() = contributionsCollected + repaymentsReceived - loanDisbursements
}

data class AdminStatementsUiState(
    val isLoading: Boolean               = true,
    val statements: List<MonthlyStatement> = emptyList(),
    val error: String?                    = null,
)

@HiltViewModel
class AdminStatementsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminStatementsUiState())
    val state: StateFlow<AdminStatementsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")

                    val memberIds = supabase.db["members"]
                        .select(Columns.list("id")) { filter { eq("cooperative_id", coopId) } }
                        .decodeList<Map<String, String>>()
                        .mapNotNull { it["id"] }

                    val contributions = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["contributions"].select { filter { isIn("member_id", memberIds) } }.decodeList<Contribution>()
                    }.getOrDefault(emptyList())

                    val transactions = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["transactions"].select { filter { isIn("member_id", memberIds) } }.decodeList<Transaction>()
                    }.getOrDefault(emptyList())
                    val repayments = transactions.filter { it.type.contains("repay", ignoreCase = true) }

                    val loans = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["loans"].select { filter { isIn("member_id", memberIds) } }.decodeList<Loan>()
                    }.getOrDefault(emptyList())
                    val disbursed = loans.filter { !it.disbursedAt.isNullOrBlank() }

                    val months = (contributions.map { it.createdAt.take(7) } +
                        repayments.map { it.createdAt.take(7) } +
                        disbursed.mapNotNull { it.disbursedAt?.take(7) })
                        .distinct().sortedDescending()

                    val statements = months.map { month ->
                        MonthlyStatement(
                            month                   = month,
                            contributionsCollected  = contributions.filter { it.createdAt.take(7) == month }.sumOf { it.amount },
                            loanDisbursements       = disbursed.filter { it.disbursedAt?.take(7) == month }.sumOf { it.outstandingBalance },
                            repaymentsReceived      = repayments.filter { it.createdAt.take(7) == month }.sumOf { it.amount },
                        )
                    }

                    AdminStatementsUiState(isLoading = false, statements = statements)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load monthly statements", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
