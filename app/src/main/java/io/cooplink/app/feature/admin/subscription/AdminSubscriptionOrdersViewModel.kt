package io.cooplink.app.feature.admin.subscription

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.network.SupabaseClient
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AdminSubOrdersVM"
private const val GENERIC_LOAD_ERROR = "Could not load subscription requests. Pull down to retry."

// A cooperative's bank-transfer (or Paystack, though that path already
// auto-activates on verification) subscription payment. Written by
// SubscriptionViewModel.recordBankTransferIntent/applyPlanUpgrade but never
// read back anywhere until now — bank-transfer upgrades had no way to ever
// actually get reviewed and activated from the app.
@Serializable
data class SubscriptionOrder(
    val id: String,
    val cooperative_id: String,
    val plan_key: String,
    val plan_name: String,
    val final_price: Double,
    val billing_period: String,
    val billing_months: Int,
    val payment_method: String,
    val bank_transfer_reference: String? = null,
    val paystack_reference: String? = null,
    val status: String,
    val created_at: String? = null,
)

@Serializable
private data class PlanMaxMembers(val max_members: Int? = null)

data class SubscriptionOrderRow(val order: SubscriptionOrder, val cooperativeName: String?)

data class AdminSubscriptionOrdersUiState(
    val isLoading: Boolean = true,
    val rows: List<SubscriptionOrderRow> = emptyList(),
    val error: String? = null,
    val processingOrderId: String? = null,
    val snackbarMessage: String? = null,
)

/** Super-admin-only, platform-wide (not cooperative-scoped) — reviews
 * pending bank-transfer subscription payments across every cooperative and
 * activates or rejects them. */
@HiltViewModel
class AdminSubscriptionOrdersViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val cooperativeRepository: CooperativeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSubscriptionOrdersUiState())
    val state: StateFlow<AdminSubscriptionOrdersUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val orders = supabase.db["subscription_orders"]
                    .select { filter { eq("status", "pending") }; order("created_at", Order.DESCENDING) }
                    .decodeList<SubscriptionOrder>()
                val cooperativeNames = cooperativeRepository.fetchAllCooperatives().associate { it.id to it.name }
                val rows = orders.map { SubscriptionOrderRow(it, cooperativeNames[it.cooperative_id]) }
                _state.value = _state.value.copy(isLoading = false, rows = rows)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load subscription orders", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    fun approve(row: SubscriptionOrderRow) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingOrderId = row.order.id)
            try {
                val plan = runCatching {
                    supabase.db["pricing_plans"]
                        .select { filter { eq("plan_key", row.order.plan_key) } }
                        .decodeSingleOrNull<PlanMaxMembers>()
                }.getOrNull()

                supabase.db["cooperatives"].update(
                    buildJsonObject {
                        put("plan_name", row.order.plan_name)
                        put("subscription_status", "active")
                        put("max_members", plan?.max_members ?: 999_999)
                    },
                ) { filter { eq("id", row.order.cooperative_id) } }

                supabase.db["subscription_orders"].update(
                    buildJsonObject { put("status", "completed") },
                ) { filter { eq("id", row.order.id) } }

                _state.value = _state.value.copy(
                    processingOrderId = null,
                    snackbarMessage = "${row.cooperativeName ?: "Cooperative"} upgraded to ${row.order.plan_name}",
                )
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to approve subscription order ${row.order.id}", e)
                _state.value = _state.value.copy(processingOrderId = null, snackbarMessage = "Could not approve this request. Please try again.")
            }
        }
    }

    fun reject(row: SubscriptionOrderRow) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingOrderId = row.order.id)
            try {
                supabase.db["subscription_orders"].update(
                    buildJsonObject { put("status", "rejected") },
                ) { filter { eq("id", row.order.id) } }
                _state.value = _state.value.copy(processingOrderId = null, snackbarMessage = "Request rejected")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reject subscription order ${row.order.id}", e)
                _state.value = _state.value.copy(processingOrderId = null, snackbarMessage = "Could not reject this request. Please try again.")
            }
        }
    }
}
