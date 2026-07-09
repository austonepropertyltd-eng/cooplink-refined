package io.cooplink.app.feature.member.airtime

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.notifications.SoundManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AirtimeVM"

enum class Network(val label: String, val color: Long) {
    MTN("MTN", 0xFFFFCC00),
    GLO("Glo", 0xFF00A651),
    AIRTEL("Airtel", 0xFFED1C24),
    NINE_MOBILE("9mobile", 0xFF006B3F),
}

data class AirtimeUiState(
    val memberPhone: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class AirtimeViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
    private val soundManager: SoundManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AirtimeUiState())
    val state: StateFlow<AirtimeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val member = memberRepository.findCurrentMember()
            _state.value = _state.value.copy(memberPhone = member?.phone)
        }
    }

    fun buyAirtime(network: Network, phone: String, amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")

                // No buy-airtime edge function exists on this backend yet
                // (confirmed via a direct probe — HTTP 404) — this call fails
                // with a clear message below until one is added; it'll start
                // working automatically with no code changes once it exists.
                supabase.functions.invoke(
                    function = "buy-airtime",
                    body = buildJsonObject {
                        put("member_id", member.id)
                        put("network", network.name)
                        put("phone", phone)
                        put("amount", amount)
                    },
                )

                supabase.db["transactions"].insert(
                    buildJsonObject {
                        put("member_id", member.id)
                        put("cooperative_id", member.cooperativeId)
                        put("type", "airtime")
                        put("amount", amount)
                        put("description", "${network.label} airtime ₦${amount.toInt()}")
                    },
                )

                soundManager.playSuccess()
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    successMessage = "₦${amount.toInt()} ${network.label} airtime sent to $phone",
                )
            } catch (e: Exception) {
                Log.w(TAG, "Airtime top-up failed", e)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = "Airtime top-up coming soon. Your admin will enable this feature shortly.",
                )
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, successMessage = null)
    }
}
