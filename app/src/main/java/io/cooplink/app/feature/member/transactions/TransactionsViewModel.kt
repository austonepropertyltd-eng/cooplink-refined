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

@Serializable
private data class NewStandingOrderRequest(
    val member_id: String,
    val cooperative_id: String?,
    val amount: Double,
    val frequency: String,
    val start_date: String,
    val purpose: String?,
)

@Serializable
private data class NewDisputeRequest(
    val member_id: String,
    val cooperative_id: String?,
    val category: String,
    val description: String,
    val status: String = "open",
)

data class TransactionsUiState(
    val isLoading: Boolean               = true,
    val transactions: List<Transaction>  = emptyList(),
    val standingOrders: List<StandingOrder> = emptyList(),
    val disputes: List<Dispute>          = emptyList(),
    val error: String?                    = null,
    val isSubmittingOrder: Boolean        = false,
    val orderError: String?               = null,
    val isSubmittingDispute: Boolean      = false,
    val disputeError: String?             = null,
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
    fun clearOrderError() { _state.value = _state.value.copy(orderError = null) }
    fun clearDisputeError() { _state.value = _state.value.copy(disputeError = null) }

    fun createStandingOrder(amount: Double, frequency: String, startDate: String, purpose: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmittingOrder = true, orderError = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")
                supabase.db["standing_orders"].insert(
                    NewStandingOrderRequest(
                        member_id      = member.id,
                        cooperative_id = member.cooperativeId,
                        amount         = amount,
                        frequency      = frequency,
                        start_date     = startDate,
                        purpose        = purpose.ifBlank { null },
                    ),
                )
                _state.value = _state.value.copy(isSubmittingOrder = false, snackbarMessage = "Standing order created")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create standing order", e)
                _state.value = _state.value.copy(isSubmittingOrder = false, orderError = "Could not create this standing order. Please try again.")
            }
        }
    }

    fun raiseDispute(category: String, description: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmittingDispute = true, disputeError = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")
                supabase.db["disputes"].insert(
                    NewDisputeRequest(
                        member_id      = member.id,
                        cooperative_id = member.cooperativeId,
                        category       = category,
                        description    = description,
                    ),
                )
                _state.value = _state.value.copy(
                    isSubmittingDispute = false,
                    snackbarMessage     = "Dispute raised. Your cooperative admin will review within 48 hours.",
                )
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to raise dispute", e)
                _state.value = _state.value.copy(isSubmittingDispute = false, disputeError = "Could not raise this dispute. Please try again.")
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

                    val memberId = memberRepository.findCurrentMember()?.id ?: uid

                    val transactions = supabase.db["transactions"]
                        .select {
                            filter { eq("member_id", memberId) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Transaction>()

                    val standingOrders = runCatching {
                        supabase.db["standing_orders"].select { filter { eq("member_id", memberId) } }.decodeList<StandingOrder>()
                    }.getOrDefault(emptyList())

                    val disputes = runCatching {
                        supabase.db["disputes"].select { filter { eq("member_id", memberId) } }.decodeList<Dispute>()
                    }.getOrDefault(emptyList())

                    TransactionsUiState(
                        isLoading      = false,
                        transactions   = transactions,
                        standingOrders = standingOrders,
                        disputes       = disputes,
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
