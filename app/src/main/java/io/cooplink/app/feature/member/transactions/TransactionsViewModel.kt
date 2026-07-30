package io.cooplink.app.feature.member.transactions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.MemberRepository
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

private const val TAG = "TransactionsVM"
private const val GENERIC_LOAD_ERROR = "Could not load your transactions. Pull down to retry."

@Serializable
data class StandingOrder(
    val id: String,
    val amount: Double     = 0.0,
    val frequency: String? = null,
    val start_date: String? = null,
    val purpose: String?    = null,
    val active: Boolean     = true,
)

@Serializable
data class Dispute(
    val id: String,
    val description: String? = null,
    val status: String        = "open",
    val category: String?     = null,
    val created_at: String?   = null,
)

data class TransactionsUiState(
    val isLoading: Boolean               = true,
    val transactions: List<Transaction>  = emptyList(),
    val standingOrders: List<StandingOrder> = emptyList(),
    val disputes: List<Dispute>          = emptyList(),
    // Distinguishes "genuinely no standing orders/disputes" from "the
    // sub-fetch failed" — both used to collapse to an empty list with no
    // signal to the member that something didn't load.
    val standingOrdersLoadFailed: Boolean = false,
    val disputesLoadFailed: Boolean       = false,
    val error: String?                    = null,
    val snackbarMessage: String?          = null,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val uid = supabase.auth.currentSessionOrNull()?.user?.id
                        ?: throw IllegalStateException("Not authenticated")

                    val memberId = memberRepository.findCurrentMember()?.id ?: uid

                    val transactions = supabase.db["transactions"]
                        .select {
                            filter { eq("member_id", memberId) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Transaction>()

                    val standingOrdersResult = runCatching {
                        supabase.db["standing_orders"].select { filter { eq("member_id", memberId) } }.decodeList<StandingOrder>()
                    }.onFailure { Log.w(TAG, "Failed to load standing orders", it) }

                    val disputesResult = runCatching {
                        supabase.db["disputes"].select { filter { eq("member_id", memberId) } }.decodeList<Dispute>()
                    }.onFailure { Log.w(TAG, "Failed to load disputes", it) }

                    TransactionsUiState(
                        isLoading               = false,
                        transactions            = transactions,
                        standingOrders          = standingOrdersResult.getOrDefault(emptyList()),
                        disputes                = disputesResult.getOrDefault(emptyList()),
                        standingOrdersLoadFailed = standingOrdersResult.isFailure,
                        disputesLoadFailed      = disputesResult.isFailure,
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
