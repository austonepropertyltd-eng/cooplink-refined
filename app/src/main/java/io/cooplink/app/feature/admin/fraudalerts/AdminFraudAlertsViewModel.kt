package io.cooplink.app.feature.admin.fraudalerts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.KycRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.util.retrying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AdminFraudAlertsVM"
private const val GENERIC_LOAD_ERROR = "Could not scan for fraud alerts. Pull down to retry."

data class DuplicateSignal(val label: String, val value: String, val members: List<MemberDetails>)

data class AdminFraudAlertsUiState(
    val isLoading: Boolean = true,
    val signals: List<DuplicateSignal> = emptyList(),
    val error: String? = null,
)

/** Flags members within the same cooperative who share an identity value that
 * should be unique per person — BVN, NIN, or phone number. This is the
 * classic co-op fraud pattern (one person opening multiple member accounts
 * to draw multiple loans/take multiple shares of a payout). There's no
 * per-member bank account number stored anywhere in this schema (only the
 * cooperative's own receiving accounts exist, in cooperative_bank_accounts),
 * so that signal isn't checked — BVN/NIN/phone are the only unique-per-person
 * fields that actually exist to compare. */
@HiltViewModel
class AdminFraudAlertsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
    private val kycRepository: KycRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminFraudAlertsUiState())
    val state: StateFlow<AdminFraudAlertsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")

                    val members = memberRepository.fetchMembersForCooperative(coopId)
                    val kycByMemberId = kycRepository.getKycDataForCooperative(coopId)

                    val signals = buildList {
                        addAll(duplicatesOf("Duplicate BVN", members) { m -> kycByMemberId[m.id]?.bvn })
                        addAll(duplicatesOf("Duplicate NIN", members) { m -> kycByMemberId[m.id]?.nin })
                        addAll(duplicatesOf("Duplicate Phone Number", members) { m -> m.phone })
                    }

                    AdminFraudAlertsUiState(isLoading = false, signals = signals)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fraud alerts", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    private fun duplicatesOf(label: String, members: List<MemberDetails>, valueOf: (MemberDetails) -> String?): List<DuplicateSignal> =
        members
            .mapNotNull { m -> valueOf(m)?.trim()?.takeIf { it.isNotBlank() }?.let { it to m } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .map { (value, matchingMembers) -> DuplicateSignal(label, value, matchingMembers) }
}
