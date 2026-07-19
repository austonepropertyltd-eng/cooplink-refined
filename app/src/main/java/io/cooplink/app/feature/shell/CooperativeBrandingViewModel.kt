package io.cooplink.app.feature.shell

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.domain.OrganizationType
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CooperativeBrandingVM"

data class CooperativeBrandingUiState(
    val name: String?         = null,
    val logoUrl: String?      = null,
    val primaryColor: String? = null,
    val organizationType: String? = null,
    val whatsappNumber: String? = null,
) {
    val orgType: OrganizationType get() = OrganizationType.fromOrDefault(organizationType)
    val isMicrofinance: Boolean get() = organizationType == "microfinance"
    val hasLoansModule: Boolean get() = organizationType !in listOf(
        "investment_club", "thrift_ajo", "savings_group",
    )
}

/** Deliberately instantiated ONCE per shell (MemberShell/AdminShell) and
 * passed down as a parameter to child screens — calling hiltViewModel() for
 * this independently from within a per-route composable (e.g. ProfileScreen)
 * scopes to that route's own NavBackStackEntry, creating a second instance
 * that starts from null and races its own fetch, which is what caused the
 * logo/name to flicker between screens. */
@HiltViewModel
class CooperativeBrandingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cooperativeRepository: CooperativeRepository,
    private val sessionPreferences: SessionPreferences,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(CooperativeBrandingUiState())
    val state: StateFlow<CooperativeBrandingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Instant paint from the last-known branding while the live
            // fetch below resolves, instead of showing the generic
            // "CoopLink" fallback for a beat on every fresh launch.
            val cached = sessionPreferences.cachedBrandingFlow.first()
            if (cached.name != null || cached.logoUrl != null) {
                _state.value = CooperativeBrandingUiState(cached.name, cached.logoUrl, cached.primaryColor)
            }

            runCatching {
                // Fast path: one RPC call keyed off the raw auth uid, no
                // user_roles round-trip first. Only covers member accounts
                // (the function is scoped to `members`), so staff/admin
                // sessions fall through to the original two-call lookup.
                val uid = supabase.auth.currentSessionOrNull()?.user?.id
                uid?.let { cooperativeRepository.fetchCooperativeForUser(it) }
                    ?: run {
                        val cooperativeId = authRepository.fetchCurrentUser()?.cooperativeId
                            ?: return@runCatching null
                        cooperativeRepository.fetchCooperative(cooperativeId)
                    }
            }.onSuccess { coop ->
                if (coop != null) {
                    _state.value = CooperativeBrandingUiState(
                        name = coop.name, logoUrl = coop.logoUrl, primaryColor = coop.primaryColor,
                        organizationType = coop.organizationType, whatsappNumber = coop.whatsappNumber,
                    )
                    sessionPreferences.saveCachedBranding(coop.name, coop.logoUrl, coop.primaryColor)
                }
            }.onFailure { Log.w(TAG, "Failed to load cooperative branding", it) }
        }
    }
}
