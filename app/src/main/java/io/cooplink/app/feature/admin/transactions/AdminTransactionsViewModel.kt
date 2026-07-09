package io.cooplink.app.feature.admin.transactions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

private const val TAG = "AdminTransactionsVM"
private const val GENERIC_LOAD_ERROR = "Could not load transactions. Pull down to retry."

private val CREDIT_TYPES = setOf("contribution", "deposit", "wallet_funding", "credit", "loan_repayment", "repayment")

enum class DateRangeFilter(val label: String) { ALL("All Time"), WEEK("This Week"), MONTH("This Month"), QUARTER("Last 3 Months") }
enum class CreditDebitFilter(val label: String) { ALL("All"), CREDIT("Credit"), DEBIT("Debit") }

data class LedgerRow(val transaction: Transaction, val memberName: String?, val runningBalance: Double) {
    val isCredit: Boolean get() = transaction.type in CREDIT_TYPES
}

data class AdminTransactionsUiState(
    val isLoading: Boolean          = true,
    val rows: List<LedgerRow>       = emptyList(),
    val members: List<MemberDetails> = emptyList(),
    val typeFilter: String          = "All",
    val memberFilter: String?       = null,
    val dateRangeFilter: DateRangeFilter = DateRangeFilter.ALL,
    val creditDebitFilter: CreditDebitFilter = CreditDebitFilter.ALL,
    val query: String                = "",
    val error: String?              = null,
) {
    val types: List<String> get() = listOf("All") + rows.map { it.transaction.type }.distinct().sorted()

    val filtered: List<LedgerRow>
        get() {
            val cutoff = when (dateRangeFilter) {
                DateRangeFilter.ALL     -> null
                DateRangeFilter.WEEK    -> daysAgoDateString(7)
                DateRangeFilter.MONTH   -> daysAgoDateString(30)
                DateRangeFilter.QUARTER -> daysAgoDateString(90)
            }
            return rows
                .filter { typeFilter == "All" || it.transaction.type == typeFilter }
                .filter { memberFilter == null || it.transaction.memberId == memberFilter }
                .filter {
                    when (creditDebitFilter) {
                        CreditDebitFilter.ALL    -> true
                        CreditDebitFilter.CREDIT -> it.isCredit
                        CreditDebitFilter.DEBIT  -> !it.isCredit
                    }
                }
                .filter { cutoff == null || it.transaction.createdAt.take(10) >= cutoff }
                .filter {
                    query.isBlank() ||
                        it.memberName?.contains(query, ignoreCase = true) == true ||
                        it.transaction.reference?.contains(query, ignoreCase = true) == true
                }
        }

    val totalIn: Double get() = filtered.filter { it.isCredit }.sumOf { it.transaction.amount }
    val totalOut: Double get() = filtered.filter { !it.isCredit }.sumOf { it.transaction.amount }

    private fun daysAgoDateString(days: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
}

@HiltViewModel
class AdminTransactionsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminTransactionsUiState())
    val state: StateFlow<AdminTransactionsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun onTypeFilterChange(type: String) { _state.value = _state.value.copy(typeFilter = type) }
    fun onMemberFilterChange(memberId: String?) { _state.value = _state.value.copy(memberFilter = memberId) }
    fun onDateRangeChange(range: DateRangeFilter) { _state.value = _state.value.copy(dateRangeFilter = range) }
    fun onCreditDebitChange(filter: CreditDebitFilter) { _state.value = _state.value.copy(creditDebitFilter = filter) }
    fun onQueryChange(query: String) { _state.value = _state.value.copy(query = query) }

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

                    // transactions.cooperative_id isn't reliably populated on every row —
                    // fall back to a member_id-based lookup if the direct filter comes up empty.
                    var transactions = supabase.db["transactions"]
                        .select {
                            filter { eq("cooperative_id", coopId) }
                            order("created_at", Order.ASCENDING)
                        }
                        .decodeList<Transaction>()
                    Log.d(TAG, "transactions by cooperative_id: ${transactions.size} rows")

                    if (transactions.isEmpty() && memberIds.isNotEmpty()) {
                        transactions = supabase.db["transactions"]
                            .select {
                                filter { isIn("member_id", memberIds) }
                                order("created_at", Order.ASCENDING)
                            }
                            .decodeList<Transaction>()
                        Log.d(TAG, "transactions by member_id fallback: ${transactions.size} rows")
                    }

                    var balance = 0.0
                    val rows = transactions.map { tx ->
                        balance += if (tx.type in CREDIT_TYPES) tx.amount else -tx.amount
                        LedgerRow(tx, namesByMemberId[tx.memberId]?.fullName, balance)
                    }.reversed()

                    val prior = _state.value
                    AdminTransactionsUiState(
                        isLoading         = false, rows = rows, members = members,
                        typeFilter        = prior.typeFilter, memberFilter = prior.memberFilter,
                        dateRangeFilter   = prior.dateRangeFilter, creditDebitFilter = prior.creditDebitFilter,
                        query             = prior.query,
                    )
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load transactions", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
