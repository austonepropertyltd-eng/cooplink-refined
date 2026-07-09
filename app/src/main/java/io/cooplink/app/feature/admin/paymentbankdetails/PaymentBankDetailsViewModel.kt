package io.cooplink.app.feature.admin.paymentbankdetails

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

private const val TAG = "PaymentBankDetailsVM"

@Serializable
data class PaymentBankDetail(
    val id: String,
    val bank_name: String,
    val account_name: String,
    val account_number: String,
    val is_active: Boolean = true,
    val created_at: String? = null,
)

@Serializable
private data class NewPaymentBankDetailRequest(
    val bank_name: String,
    val account_name: String,
    val account_number: String,
    val is_active: Boolean,
)

@Serializable
private data class PaymentBankDetailPatch(
    val bank_name: String,
    val account_name: String,
    val account_number: String,
    val is_active: Boolean,
)

data class PaymentBankDetailsUiState(
    val isLoading: Boolean               = true,
    val details: List<PaymentBankDetail> = emptyList(),
    val error: String?                   = null,
    val isSaving: Boolean                = false,
    val saveError: String?               = null,
    val snackbarMessage: String?         = null,
)

/** Manages the platform-wide (not per-cooperative) receiving account shown to
 * every cooperative's admin on the bank-transfer subscription payment sheet
 * — super-admin-only, edited here instead of hardcoded so VFG Technology can
 * change banks without an app release. */
@HiltViewModel
class PaymentBankDetailsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentBankDetailsUiState())
    val state: StateFlow<PaymentBankDetailsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearSaveError() { _state.value = _state.value.copy(saveError = null) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val details = supabase.db["payment_bank_details"]
                    .select { order("created_at", Order.DESCENDING) }
                    .decodeList<PaymentBankDetail>()
                _state.value = _state.value.copy(isLoading = false, details = details)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load payment bank details", e)
                _state.value = _state.value.copy(isLoading = false, error = "Could not load bank details. Pull down to retry.")
            }
        }
    }

    fun addDetail(bankName: String, accountName: String, accountNumber: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveError = null)
            try {
                supabase.db["payment_bank_details"].insert(
                    NewPaymentBankDetailRequest(bankName, accountName, accountNumber, is_active = true),
                )
                _state.value = _state.value.copy(isSaving = false, snackbarMessage = "Bank details added")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add payment bank detail", e)
                _state.value = _state.value.copy(isSaving = false, saveError = "Could not save. Please try again.")
            }
        }
    }

    fun updateDetail(detail: PaymentBankDetail, bankName: String, accountName: String, accountNumber: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveError = null)
            try {
                supabase.db["payment_bank_details"]
                    .update(PaymentBankDetailPatch(bankName, accountName, accountNumber, detail.is_active)) {
                        filter { eq("id", detail.id) }
                    }
                _state.value = _state.value.copy(isSaving = false, snackbarMessage = "Bank details updated")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update payment bank detail ${detail.id}", e)
                _state.value = _state.value.copy(isSaving = false, saveError = "Could not save. Please try again.")
            }
        }
    }

    // Only one account should ever be the active one shown to admins — flip
    // every other row off first so activating a new one implicitly retires
    // the old one instead of showing two "active" accounts at once.
    fun setActive(detail: PaymentBankDetail) {
        viewModelScope.launch {
            try {
                _state.value.details.filter { it.is_active && it.id != detail.id }.forEach { other ->
                    runCatching {
                        supabase.db["payment_bank_details"]
                            .update(PaymentBankDetailPatch(other.bank_name, other.account_name, other.account_number, false)) {
                                filter { eq("id", other.id) }
                            }
                    }
                }
                supabase.db["payment_bank_details"]
                    .update(PaymentBankDetailPatch(detail.bank_name, detail.account_name, detail.account_number, true)) {
                        filter { eq("id", detail.id) }
                    }
                _state.value = _state.value.copy(snackbarMessage = "${detail.bank_name} set as the active receiving account")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set active payment bank detail ${detail.id}", e)
                _state.value = _state.value.copy(snackbarMessage = "Could not update. Please try again.")
            }
        }
    }

    fun deleteDetail(detail: PaymentBankDetail) {
        viewModelScope.launch {
            try {
                supabase.db["payment_bank_details"].delete { filter { eq("id", detail.id) } }
                _state.value = _state.value.copy(snackbarMessage = "Bank details removed")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete payment bank detail ${detail.id}", e)
                _state.value = _state.value.copy(snackbarMessage = "Could not delete. Please try again.")
            }
        }
    }
}
