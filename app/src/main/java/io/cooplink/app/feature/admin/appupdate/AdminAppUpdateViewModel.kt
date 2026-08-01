package io.cooplink.app.feature.admin.appupdate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.AppReleaseConfig
import io.cooplink.app.core.data.AppUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AdminAppUpdateVM"

data class AdminAppUpdateUiState(
    val isLoading: Boolean       = true,
    val config: AppReleaseConfig? = null,
    val isSaving: Boolean        = false,
    val error: String?           = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class AdminAppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminAppUpdateUiState())
    val state: StateFlow<AdminAppUpdateUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val config = repository.fetchConfig()
            _state.value = _state.value.copy(
                isLoading = false,
                config    = config,
                error     = if (config == null) {
                    "Could not load app update settings — the app_release_config table may not be set up yet."
                } else null,
            )
        }
    }

    fun save(
        minSupportedVersionCode: Int,
        latestVersionCode: Int?,
        latestVersionName: String,
        updateMessage: String,
        playStoreUrl: String,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                repository.updateConfig(
                    minSupportedVersionCode = minSupportedVersionCode,
                    latestVersionCode       = latestVersionCode,
                    latestVersionName       = latestVersionName.ifBlank { null },
                    updateMessage           = updateMessage.ifBlank { null },
                    playStoreUrl            = playStoreUrl.ifBlank { null },
                )
                _state.value = _state.value.copy(isSaving = false, snackbarMessage = "App update settings saved")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save app update config", e)
                _state.value = _state.value.copy(isSaving = false, snackbarMessage = "Could not save. Please try again.")
            }
        }
    }
}
