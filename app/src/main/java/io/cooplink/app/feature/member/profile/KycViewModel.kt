package io.cooplink.app.feature.member.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.KycRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.core.domain.IdType
import io.cooplink.app.core.domain.KycData
import io.cooplink.app.core.domain.KycStatus
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "KycViewModel"

data class KycUiState(
    val isLoading: Boolean       = true,
    val kycData: KycData         = KycData(),
    val isUploading: Boolean     = false,
    val error: String?           = null,
    val successMessage: String?  = null,
)

@HiltViewModel
class KycViewModel @Inject constructor(
    private val kycRepository: KycRepository,
    private val memberRepository: MemberRepository,
    private val signedUrlManager: SignedUrlManager,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(KycUiState())
    val state: StateFlow<KycUiState> = _state.asStateFlow()

    private var memberId: String? = null
    private var userId: String? = null
    // Raw storage path from the most recent upload — submitKyc() persists
    // this, while kycData.idDocumentUrl (shown on screen) is always a
    // resolved signed URL, never a raw path.
    private var pendingIdDocumentPath: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null

    init { loadKycData() }

    fun loadKycData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")
                memberId = member.id
                userId = member.userId
                val kyc = kycRepository.getKycData(member.id)
                _state.update { it.copy(kycData = kyc, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load KYC data", e)
                _state.update { it.copy(isLoading = false, error = "Could not load your KYC status. Pull down to retry.") }
            }
        }
    }

    fun uploadIdDocument(bytes: ByteArray) {
        val mid = memberId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, error = null) }
            kycRepository.uploadKycImage(mid, bytes, "id_document")
                .onSuccess { path ->
                    pendingIdDocumentPath = path
                    // uploadKycImage() just wrote this file under the LIVE
                    // session's auth uid (see KycRepository) regardless of
                    // whatever members.user_id holds — that column is null
                    // for some migrated/seeded rows, and falling back to the
                    // member's own row id there signs a URL for a path that
                    // was never written to, which Supabase happily signs
                    // anyway (signing doesn't check existence) and Coil then
                    // fails to render. Signing with the same uid the upload
                    // just used is the only way to guarantee the paths match.
                    val authUid = supabase.auth.currentSessionOrNull()?.user?.id ?: userId ?: mid
                    val displayUrl = signedUrlManager.getKycDocUrl(authUid, mid, "id_document")
                    _state.update {
                        it.copy(isUploading = false, kycData = it.kycData.copy(idDocumentUrl = displayUrl), successMessage = "ID document uploaded")
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(isUploading = false, error = "Could not upload your document. Please try again.")
                    }
                }
        }
    }

    fun submitKyc(idType: IdType, idNumber: String) {
        val mid = memberId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            kycRepository.submitKyc(mid, idType, idNumber, pendingIdDocumentPath)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            kycData   = it.kycData.copy(status = KycStatus.PENDING, idType = idType, idNumber = idNumber),
                            successMessage = "KYC submitted! Review takes 1-2 days.",
                        )
                    }
                }
                .onFailure {
                    Log.e(TAG, "Failed to submit KYC", it)
                    _state.update { it.copy(isLoading = false, error = "Could not submit your KYC. Please try again.") }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }
}
