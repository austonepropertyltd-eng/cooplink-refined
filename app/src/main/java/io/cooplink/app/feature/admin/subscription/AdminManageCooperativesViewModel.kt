package io.cooplink.app.feature.admin.subscription

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

private const val TAG = "AdminManageCoopsVM"
private const val GENERIC_LOAD_ERROR = "Could not load cooperatives. Pull down to retry."

object CooperativeStatus {
    const val ACTIVE = "active"
    const val SUSPENDED = "suspended"
}

@Serializable
data class CooperativeManagementRow(
    val id: String,
    val name: String? = null,
    val plan_name: String? = null,
    val subscription_status: String? = null,
    val max_members: Int? = null,
)

data class AdminManageCooperativesUiState(
    val isLoading: Boolean = true,
    val rows: List<CooperativeManagementRow> = emptyList(),
    val plans: List<AdminPricingPlan> = emptyList(),
    val error: String? = null,
    val processingId: String? = null,
    val snackbarMessage: String? = null,
)

/** Super-admin-only, platform-wide (every cooperative, not just the one
 * currently selected via the switcher) — directly changes a cooperative's
 * plan or suspends/reactivates it, without needing a pending subscription
 * order to review first. Writes go straight to `cooperatives`, the same
 * table the web platform reads, so there's no separate sync step. */
@HiltViewModel
class AdminManageCooperativesViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminManageCooperativesUiState())
    val state: StateFlow<AdminManageCooperativesUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val rows = supabase.db["cooperatives"]
                    .select(io.github.jan.supabase.postgrest.query.Columns.list("id", "name", "plan_name", "subscription_status", "max_members")) {
                        order("name", Order.ASCENDING)
                    }
                    .decodeList<CooperativeManagementRow>()
                val plans = supabase.db["pricing_plans"]
                    .select { filter { eq("is_active", true) }; order("display_order", Order.ASCENDING) }
                    .decodeList<AdminPricingPlan>()
                _state.value = _state.value.copy(isLoading = false, rows = rows, plans = plans)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cooperatives", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    fun changePlan(row: CooperativeManagementRow, plan: AdminPricingPlan) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingId = row.id)
            try {
                supabase.db["cooperatives"].update(
                    buildJsonObject {
                        put("plan_name", plan.plan_name)
                        put("max_members", plan.max_members ?: 999_999)
                        put("subscription_status", CooperativeStatus.ACTIVE)
                    },
                ) { filter { eq("id", row.id) } }
                _state.value = _state.value.copy(processingId = null, snackbarMessage = "${row.name ?: "Cooperative"} moved to ${plan.plan_name}")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to change plan for ${row.id}", e)
                _state.value = _state.value.copy(processingId = null, snackbarMessage = "Could not change this cooperative's plan. Please try again.")
            }
        }
    }

    fun setStatus(row: CooperativeManagementRow, status: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingId = row.id)
            try {
                supabase.db["cooperatives"].update(
                    buildJsonObject { put("subscription_status", status) },
                ) { filter { eq("id", row.id) } }
                val verb = if (status == CooperativeStatus.SUSPENDED) "suspended" else "reactivated"
                _state.value = _state.value.copy(processingId = null, snackbarMessage = "${row.name ?: "Cooperative"} $verb")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set status for ${row.id}", e)
                _state.value = _state.value.copy(processingId = null, snackbarMessage = "Could not update this cooperative. Please try again.")
            }
        }
    }
}
