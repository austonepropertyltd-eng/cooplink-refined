package io.cooplink.app.feature.admin.branding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.domain.Cooperative
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.InactivityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "AdminBrandingVM"

@Serializable
private data class BrandingUpdateRequest(
    val name: String,
    val logo_url: String?,
    val primary_color: String?,
    val secondary_color: String?,
    val whatsapp_number: String?,
)

data class AdminBrandingUiState(
    val isLoading: Boolean       = true,
    val cooperative: Cooperative? = null,
    val isSaving: Boolean        = false,
    val isUploadingLogo: Boolean = false,
    val error: String?           = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class AdminBrandingViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val cooperativeRepository: CooperativeRepository,
    val inactivityManager: InactivityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminBrandingUiState())
    val state: StateFlow<AdminBrandingUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val coopId = authRepository.currentCooperativeId()
            val coop = coopId?.let { cooperativeRepository.fetchCooperative(it) }
            _state.value = _state.value.copy(isLoading = false, cooperative = coop)
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }

    // "avatars"-style Storage buckets were already confirmed absent elsewhere
    // in this app (KYC docs, profile photos) — this fails the same honest way
    // rather than pretending the upload succeeded.
    fun uploadLogo(bytes: ByteArray, extension: String, onResult: (url: String?) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploadingLogo = true)
            try {
                val coopId = authRepository.currentCooperativeId() ?: throw IllegalStateException("No cooperative")
                val path = "$coopId/logo.$extension"
                supabase.storage.from("branding").upload(path, bytes) { upsert = true }
                val url = supabase.storage.from("branding").publicUrl(path)
                _state.value = _state.value.copy(isUploadingLogo = false)
                onResult(url)
            } catch (e: Exception) {
                Log.w(TAG, "Logo upload failed", e)
                _state.value = _state.value.copy(
                    isUploadingLogo = false,
                    snackbarMessage = "Logo uploads aren't set up for your cooperative yet — paste a hosted image URL instead",
                )
                onResult(null)
            }
        }
    }

    fun save(name: String, logoUrl: String, primaryColor: String, secondaryColor: String, whatsapp: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                supabase.db["cooperatives"].update(
                    BrandingUpdateRequest(
                        name            = name,
                        logo_url        = logoUrl.ifBlank { null },
                        primary_color   = primaryColor.ifBlank { null },
                        secondary_color = secondaryColor.ifBlank { null },
                        whatsapp_number = whatsapp.ifBlank { null },
                    ),
                ) { filter { eq("id", coopId) } }

                val coop = cooperativeRepository.fetchCooperative(coopId)
                _state.value = _state.value.copy(isSaving = false, cooperative = coop, snackbarMessage = "Branding saved")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save branding", e)
                _state.value = _state.value.copy(isSaving = false, error = "Could not save your changes. Please try again.")
            }
        }
    }
}
