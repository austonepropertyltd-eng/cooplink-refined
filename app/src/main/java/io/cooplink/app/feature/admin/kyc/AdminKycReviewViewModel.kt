package io.cooplink.app.feature.admin.kyc

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.KycRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.KycData
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.notifications.SoundManager
import io.cooplink.app.core.util.retrying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AdminKycReviewVM"
private const val GENERIC_LOAD_ERROR = "Could not load KYC submissions. Pull down to retry."

data class KycReviewRow(val member: MemberDetails, val kyc: KycData)

data class AdminKycReviewUiState(
    val isLoading: Boolean          = true,
    val rows: List<KycReviewRow>    = emptyList(),
    val error: String?              = null,
    val processingMemberId: String? = null,
    val snackbarMessage: String?    = null,
)

@HiltViewModel
class AdminKycReviewViewModel @Inject constructor(
    private val kycRepository: KycRepository,
    private val memberRepository: MemberRepository,
    private val authRepository: AuthRepository,
    private val soundManager: SoundManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminKycReviewUiState())
    val state: StateFlow<AdminKycReviewUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }

    fun approve(row: KycReviewRow) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingMemberId = row.member.id)
            kycRepository.approveKyc(row.member.id)
                .onSuccess {
                    kycRepository.notifyKycDecision(row.member.phone, "Your KYC has been approved. You now have full access to CoopLink.")
                    _state.value = _state.value.copy(processingMemberId = null, snackbarMessage = "${row.member.fullName ?: "Member"} approved")
                    soundManager.playSuccess()
                    load()
                }
                .onFailure {
                    Log.w(TAG, "Approve failed for ${row.member.id}", it)
                    _state.value = _state.value.copy(
                        processingMemberId = null,
                        snackbarMessage = "Could not update this member's KYC status. Please try again.",
                    )
                }
        }
    }

    fun reject(row: KycReviewRow, reason: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingMemberId = row.member.id)
            kycRepository.rejectKyc(row.member.id, reason)
                .onSuccess {
                    kycRepository.notifyKycDecision(row.member.phone, "Your KYC was not approved. Reason: $reason. Please resubmit with correct documents.")
                    _state.value = _state.value.copy(processingMemberId = null, snackbarMessage = "${row.member.fullName ?: "Member"} rejected")
                    load()
                }
                .onFailure {
                    Log.w(TAG, "Reject failed for ${row.member.id}", it)
                    _state.value = _state.value.copy(
                        processingMemberId = null,
                        snackbarMessage = "Could not update this member's KYC status. Please try again.",
                    )
                }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")

                    val members = memberRepository.fetchMembersForCooperative(coopId)
                    val kycByMemberId = kycRepository.getKycDataForCooperative(coopId)

                    val rows = members.mapNotNull { m -> kycByMemberId[m.id]?.let { KycReviewRow(m, it) } }

                    AdminKycReviewUiState(isLoading = false, rows = rows)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load KYC submissions", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
