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
import javax.inject.Inject

private const val TAG = "AdminPricingPlansVM"
private const val GENERIC_LOAD_ERROR = "Could not load pricing plans. Pull down to retry."

// Separate from SubscriptionViewModel.PricingPlan (the member/admin-facing
// read model used to display plans for purchase) — this one is for platform
// management and includes columns that screen never needed: is_active
// (whether the plan is offered at all) and display_order.
@Serializable
data class AdminPricingPlan(
    val id: String,
    val plan_key: String,
    val plan_name: String,
    val base_price: Double,
    val max_members: Int? = null,
    val discount_percent: Double = 0.0,
    val discount_label: String? = null,
    val discount_active: Boolean = false,
    val is_featured: Boolean = false,
    val is_active: Boolean = true,
    val display_order: Int? = null,
)

@Serializable
private data class NewPricingPlanRequest(
    val plan_key: String,
    val plan_name: String,
    val base_price: Double,
    val max_members: Int?,
    val is_active: Boolean,
    val discount_percent: Double,
    val discount_active: Boolean,
    val is_featured: Boolean,
)

@Serializable
private data class PricingPlanPatch(
    val plan_name: String,
    val base_price: Double,
    val max_members: Int?,
    val discount_percent: Double,
    val discount_label: String?,
    val discount_active: Boolean,
    val is_featured: Boolean,
    val is_active: Boolean,
)

data class AdminPricingPlansUiState(
    val isLoading: Boolean = true,
    val plans: List<AdminPricingPlan> = emptyList(),
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val justSaved: Boolean = false,
    val snackbarMessage: String? = null,
)

/** Super-admin-only, platform-wide plan CRUD — until now pricing_plans could
 * only be read (SubscriptionViewModel), never created/edited from the app,
 * so a price or discount change had to happen directly in Supabase. */
@HiltViewModel
class AdminPricingPlansViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminPricingPlansUiState())
    val state: StateFlow<AdminPricingPlansUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSaveError() { _state.value = _state.value.copy(saveError = null) }
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val plans = supabase.db["pricing_plans"]
                    .select { order("display_order", Order.ASCENDING) }
                    .decodeList<AdminPricingPlan>()
                _state.value = _state.value.copy(isLoading = false, plans = plans)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load pricing plans", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    fun createPlan(planKey: String, planName: String, basePrice: Double, maxMembers: Int?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveError = null, justSaved = false)
            try {
                supabase.db["pricing_plans"].insert(
                    NewPricingPlanRequest(
                        plan_key = planKey, plan_name = planName, base_price = basePrice, max_members = maxMembers,
                        is_active = true, discount_percent = 0.0, discount_active = false, is_featured = false,
                    ),
                )
                _state.value = _state.value.copy(isSaving = false, justSaved = true, snackbarMessage = "Plan created")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create pricing plan", e)
                _state.value = _state.value.copy(isSaving = false, saveError = "Could not create this plan. Please try again.")
            }
        }
    }

    fun updatePlan(
        id: String, planName: String, basePrice: Double, maxMembers: Int?,
        discountPercent: Double, discountLabel: String?, discountActive: Boolean,
        isFeatured: Boolean, isActive: Boolean,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveError = null, justSaved = false)
            try {
                supabase.db["pricing_plans"].update(
                    PricingPlanPatch(
                        plan_name = planName, base_price = basePrice, max_members = maxMembers,
                        discount_percent = discountPercent, discount_label = discountLabel.takeUnless { it.isNullOrBlank() },
                        discount_active = discountActive, is_featured = isFeatured, is_active = isActive,
                    ),
                ) { filter { eq("id", id) } }
                _state.value = _state.value.copy(isSaving = false, justSaved = true, snackbarMessage = "Plan updated")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update pricing plan $id", e)
                _state.value = _state.value.copy(isSaving = false, saveError = "Could not save changes. Please try again.")
            }
        }
    }
}
