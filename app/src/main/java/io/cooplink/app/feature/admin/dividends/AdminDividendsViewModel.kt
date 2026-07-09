package io.cooplink.app.feature.admin.dividends

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

private const val TAG = "AdminDividendsVM"
private const val GENERIC_LOAD_ERROR = "Could not load dividend data. Pull down to retry."

enum class DistributionMethod(val label: String) {
    EQUAL_SHARE("Equal share (÷ all active members)"),
    PRO_RATA_SAVINGS("Pro-rata by savings"),
    PRO_RATA_CONTRIBUTIONS("Pro-rata by contributions"),
}

data class DividendPreviewRow(val member: MemberDetails, val basis: Double, val share: Double)

data class DividendBatch(val date: String, val totalAmount: Double, val memberCount: Int)

@Serializable
private data class DividendAllocationInsert(val cooperative_id: String, val member_id: String, val amount: Double)

data class AdminDividendsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalDistributedAllTime: Double = 0.0,
    val lastDistributionDate: String? = null,
    val eligibleMemberCount: Int = 0,
    val history: List<DividendBatch> = emptyList(),
    val isDeclaring: Boolean = false,
    val declareError: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class AdminDividendsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminDividendsUiState())
    val state: StateFlow<AdminDividendsUiState> = _state.asStateFlow()

    private var members: List<MemberDetails> = emptyList()
    private var savingsByMember: Map<String, Double> = emptyMap()

    init { load() }
    fun refresh() = load()
    fun clearDeclareError() { _state.value = _state.value.copy(declareError = null) }
    fun clearSuccessMessage() { _state.value = _state.value.copy(successMessage = null) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")

                    members = memberRepository.fetchMembersForCooperative(coopId)
                    val memberIds = members.map { it.id }

                    val contributions = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["contributions"].select { filter { isIn("member_id", memberIds) } }.decodeList<Contribution>()
                    }.getOrDefault(emptyList())
                    savingsByMember = contributions.groupBy { it.memberId }.mapValues { (_, list) -> list.sumOf { it.amount } }

                    // dividend_allocations only has cooperative_id/member_id/amount/
                    // created_at — no batch/period id — so a single "distribution
                    // event" is inferred from rows sharing the same calendar date.
                    val allocations = runCatching {
                        supabase.db["dividend_allocations"]
                            .select { filter { eq("cooperative_id", coopId) }; order("created_at", Order.DESCENDING) }
                            .decodeList<DividendAllocationRow>()
                    }.getOrDefault(emptyList())

                    val history = allocations.groupBy { it.created_at.take(10) }
                        .map { (date, rows) -> DividendBatch(date, rows.sumOf { it.amount }, rows.size) }
                        .sortedByDescending { it.date }

                    AdminDividendsUiState(
                        isLoading               = false,
                        totalDistributedAllTime = allocations.sumOf { it.amount },
                        lastDistributionDate    = history.firstOrNull()?.date,
                        eligibleMemberCount     = members.count { it.status.equals("active", true) },
                        history                 = history,
                    )
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dividend data", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    fun buildPreview(totalAmount: Double, method: DistributionMethod): List<DividendPreviewRow> {
        val eligible = members.filter { it.status.equals("active", true) }
        if (eligible.isEmpty() || totalAmount <= 0) return emptyList()

        return when (method) {
            DistributionMethod.EQUAL_SHARE -> {
                val share = totalAmount / eligible.size
                eligible.map { DividendPreviewRow(it, 1.0, share) }
            }
            DistributionMethod.PRO_RATA_SAVINGS -> {
                val totalSavings = eligible.sumOf { savingsByMember[it.id] ?: 0.0 }
                eligible.map {
                    val basis = savingsByMember[it.id] ?: 0.0
                    DividendPreviewRow(it, basis, if (totalSavings > 0) totalAmount * (basis / totalSavings) else 0.0)
                }
            }
            DistributionMethod.PRO_RATA_CONTRIBUTIONS -> {
                val totalContrib = eligible.sumOf { savingsByMember[it.id] ?: 0.0 }
                eligible.map {
                    val basis = savingsByMember[it.id] ?: 0.0
                    DividendPreviewRow(it, basis, if (totalContrib > 0) totalAmount * (basis / totalContrib) else 0.0)
                }
            }
        }
    }

    fun declareDividend(preview: List<DividendPreviewRow>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeclaring = true, declareError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                preview.filter { it.share > 0 }.forEach { row ->
                    supabase.db["dividend_allocations"].insert(
                        DividendAllocationInsert(cooperative_id = coopId, member_id = row.member.id, amount = row.share),
                    )
                }

                _state.value = _state.value.copy(isDeclaring = false, successMessage = "Dividend distributed to ${preview.size} member(s)")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to declare dividend", e)
                _state.value = _state.value.copy(isDeclaring = false, declareError = "Could not record this distribution. Please try again.")
            }
        }
    }
}

@kotlinx.serialization.Serializable
private data class DividendAllocationRow(
    val id: String? = null,
    val member_id: String? = null,
    val amount: Double = 0.0,
    val created_at: String = "",
)
