package io.cooplink.app.feature.member.standingorders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.toDomain
import io.cooplink.app.core.data.toEntity
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "StandingOrdersVM"
private const val GENERIC_LOAD_ERROR = "Could not load your standing orders. Pull down to retry."

object OrderFrequency {
    const val WEEKLY   = "weekly"
    const val BIWEEKLY = "biweekly"
    const val MONTHLY  = "monthly"
    val ALL = listOf(WEEKLY, BIWEEKLY, MONTHLY)
}

// Mirrors the standing_orders_purpose_check constraint confirmed on-device
// (contribution/repayment are the only values the DB accepts; free text
// caused every submission to fail with a check-constraint violation).
object StandingOrderPurpose {
    const val CONTRIBUTION = "contribution"
    const val REPAYMENT    = "repayment"
    val ALL = listOf(CONTRIBUTION, REPAYMENT)
}

@Serializable
data class StandingOrder(
    val id: String,
    val member_id: String,
    val cooperative_id: String? = null,
    val amount: Double,
    val frequency: String,
    val day_of_month: Int? = null,
    val purpose: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val active: Boolean = true,
    val created_at: String? = null,
)

@Serializable
private data class NewStandingOrderRequest(
    val member_id: String,
    val cooperative_id: String?,
    val amount: Double,
    val frequency: String,
    val day_of_month: Int?,
    val purpose: String,
    val start_date: String,
)

@Serializable
private data class StandingOrderActivePatch(val active: Boolean)

data class StandingOrdersUiState(
    val isLoading: Boolean            = true,
    val orders: List<StandingOrder>   = emptyList(),
    val error: String?                = null,
    val isSubmitting: Boolean         = false,
    val submitError: String?          = null,
)

@HiltViewModel
class StandingOrdersViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
    private val database: CoopLinkDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(StandingOrdersUiState())
    val state: StateFlow<StandingOrdersUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSubmitError() { _state.value = _state.value.copy(submitError = null) }

    fun createOrder(amount: Double, frequency: String, dayOfMonth: Int?, purpose: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")
                supabase.db["standing_orders"].insert(
                    NewStandingOrderRequest(
                        member_id      = member.id,
                        cooperative_id = member.cooperativeId,
                        amount         = amount,
                        frequency      = frequency,
                        day_of_month   = dayOfMonth,
                        purpose        = purpose,
                        start_date     = todayIsoDate(),
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create standing order", e)
                _state.value = _state.value.copy(isSubmitting = false, submitError = "Could not create this standing order. Please try again.")
            }
        }
    }

    fun setActive(order: StandingOrder, active: Boolean) {
        viewModelScope.launch {
            try {
                supabase.db["standing_orders"]
                    .update(StandingOrderActivePatch(active)) { filter { eq("id", order.id) } }
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle standing order ${order.id}", e)
                _state.value = _state.value.copy(submitError = "Could not update this standing order. Please try again.")
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val uidForCache = supabase.auth.currentSessionOrNull()?.user?.id
            if (uidForCache != null) {
                runCatching {
                    val cachedMemberId = database.memberDao().getByUserId(uidForCache)?.id ?: return@runCatching
                    val cached = database.standingOrderDao().observeForMember(cachedMemberId).first()
                    if (cached.isNotEmpty()) _state.value = _state.value.copy(orders = cached.map { it.toDomain() })
                }.onFailure { Log.w(TAG, "Failed to read standing orders cache", it) }
            }

            try {
                val result = retrying {
                    val member = memberRepository.findCurrentMember()
                        ?: throw IllegalStateException("Could not determine your member record")

                    val orders = supabase.db["standing_orders"]
                        .select {
                            filter { eq("member_id", member.id) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<StandingOrder>()

                    StandingOrdersUiState(isLoading = false, orders = orders)
                }
                _state.value = result

                runCatching {
                    database.standingOrderDao().upsertAll(result.orders.map { it.toEntity() })
                }.onFailure { Log.w(TAG, "Failed to write standing orders cache", it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load standing orders", e)
                val hasCachedContent = _state.value.orders.isNotEmpty()
                _state.value = _state.value.copy(isLoading = false, error = if (hasCachedContent) null else GENERIC_LOAD_ERROR)
            }
        }
    }
}

private fun todayIsoDate(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
