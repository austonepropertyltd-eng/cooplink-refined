package io.cooplink.app.feature.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.domain.Cooperative
import io.cooplink.app.core.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CooperativeSwitcherUiState(
    val isSuperAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val cooperatives: List<Cooperative> = emptyList(),
)

/** Deliberately instantiated once at the top of AdminShell (outside the
 * cooperative-keyed subtree) so it survives a switch — it's what DRIVES the
 * switch, so it can't itself be torn down by it. */
@HiltViewModel
class CooperativeSwitcherViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cooperativeRepository: CooperativeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CooperativeSwitcherUiState())
    val state: StateFlow<CooperativeSwitcherUiState> = _state.asStateFlow()

    val overrideCooperativeId: StateFlow<String?> = authRepository.overrideCooperativeId

    init {
        viewModelScope.launch {
            val isSuperAdmin = runCatching { authRepository.fetchCurrentUser()?.role == UserRole.SUPER_ADMIN }.getOrDefault(false)
            _state.value = _state.value.copy(isSuperAdmin = isSuperAdmin)
        }
    }

    fun loadCooperatives() {
        if (_state.value.cooperatives.isNotEmpty() || _state.value.isLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val cooperatives = cooperativeRepository.fetchAllCooperatives()
            _state.value = _state.value.copy(isLoading = false, cooperatives = cooperatives)
        }
    }

    fun select(cooperativeId: String) {
        authRepository.setOverrideCooperativeId(cooperativeId)
    }
}
