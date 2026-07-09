package io.cooplink.app.core.payment

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.network.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

// ── Paystack models ───────────────────────────────────────────────────────────

@Serializable
data class InitPaymentRequest(
    val amount: Long,           // in kobo (₦1 = 100 kobo)
    val email: String,
    val member_id: String,
    val reference: String,
    val callback_url: String = "cooplink://payment-callback",
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class InitPaymentResponse(
    val authorization_url: String,
    val access_code: String,
    val reference: String,
)

@Serializable
data class PaymentVerifyResponse(
    val status: String,
    val message: String,
    val reference: String,
)

// ── Payment state ─────────────────────────────────────────────────────────────

sealed class PaymentState {
    object Idle                                  : PaymentState()
    object Initializing                          : PaymentState()
    data class ReadyToOpen(val url: String,
                           val reference: String): PaymentState()
    object Verifying                             : PaymentState()
    data class Success(val reference: String)   : PaymentState()
    data class Failed(val message: String)      : PaymentState()
}

// ── PaymentManager singleton ──────────────────────────────────────────────────

@Singleton
class PaymentManager @Inject constructor(
    private val supabase: SupabaseClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun initializePayment(
        amountNaira: Double,
        email: String,
        memberId: String,
        type: String = "contribution",
    ): Result<InitPaymentResponse> = runCatching {
        val reference = "CL-${type.uppercase()}-${System.currentTimeMillis()}"
        val resp = supabase.functions.invoke(
            function = "initialize-payment",
            body = buildJsonObject {
                put("amount",    (amountNaira * 100).toLong())  // convert to kobo
                put("email",     email)
                put("member_id", memberId)
                put("reference", reference)
                put("callback_url", "cooplink://payment-callback")
            },
        )
        json.decodeFromString<InitPaymentResponse>(resp.bodyAsText())
    }

    /** Same edge function as [initializePayment], but for a cooperative
     * subscription upgrade rather than a member contribution — different
     * metadata/callback host so MainActivity can route the two independently. */
    suspend fun initializeSubscriptionPayment(
        amountNaira: Double,
        email: String,
        cooperativeId: String,
        planKey: String,
        planName: String,
    ): Result<InitPaymentResponse> = runCatching {
        val reference = "SUB-$cooperativeId-$planKey-${System.currentTimeMillis()}"
        val resp = supabase.functions.invoke(
            function = "initialize-payment",
            body = buildJsonObject {
                put("amount", (amountNaira * 100).toLong())
                put("email", email)
                put("cooperative_id", cooperativeId)
                put("plan_key", planKey)
                put("plan_name", planName)
                put("reference", reference)
                put("callback_url", "cooplink://subscription-callback")
                put("metadata", buildJsonObject {
                    put("type", "subscription")
                    put("plan", planKey)
                })
            },
        )
        json.decodeFromString<InitPaymentResponse>(resp.bodyAsText())
    }

    suspend fun verifyPayment(reference: String): Result<PaymentVerifyResponse> = runCatching {
        val resp = supabase.functions.invoke(
            function = "paystack-webhook",
            body = buildJsonObject {
                put("reference", reference)
                put("event",     "charge.success")
            },
        )
        json.decodeFromString<PaymentVerifyResponse>(resp.bodyAsText())
    }

    fun openPaystackInBrowser(context: Context, url: String) {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(android.graphics.Color.parseColor("#243C54"))
            .build()

        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url))
    }
}

// ── PaymentViewModel ──────────────────────────────────────────────────────────

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentManager: PaymentManager,
    private val supabase: SupabaseClient,
    private val deepLinkBus: PaymentDeepLinkBus,
) : ViewModel() {

    private val _state = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val state: StateFlow<PaymentState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            deepLinkBus.events.collect { uri ->
                if (uri.host == "payment-callback") {
                    uri.getQueryParameter("reference")?.let(::onCallbackReceived)
                }
            }
        }
    }

    fun startPayment(
        amountNaira: Double,
        type: String = "contribution",
    ) {
        viewModelScope.launch {
            _state.value = PaymentState.Initializing

            val session  = supabase.auth.currentSessionOrNull()
            val uid      = session?.user?.id ?: run {
                _state.value = PaymentState.Failed("Not logged in")
                return@launch
            }
            val email    = session.user?.email ?: "$uid@cooplink.io"
            val memberId = uid  // use uid as fallback

            paymentManager.initializePayment(amountNaira, email, memberId, type)
                .onSuccess {
                    _state.value = PaymentState.ReadyToOpen(it.authorization_url, it.reference)
                }
                .onFailure {
                    Log.e(TAG, "Payment initialization failed", it)
                    _state.value = PaymentState.Failed(it.toSafeMessage("Payment init failed"))
                }
        }
    }

    fun onCallbackReceived(reference: String) {
        viewModelScope.launch {
            _state.value = PaymentState.Verifying
            paymentManager.verifyPayment(reference)
                .onSuccess {
                    _state.value = if (it.status == "success")
                        PaymentState.Success(reference)
                    else
                        PaymentState.Failed("Payment not confirmed: ${it.message}")
                }
                .onFailure {
                    Log.e(TAG, "Payment verification failed", it)
                    _state.value = PaymentState.Failed(it.toSafeMessage("Verification failed"))
                }
        }
    }

    fun reset() { _state.value = PaymentState.Idle }
}

private const val TAG = "PaymentViewModel"

// RestException.message embeds the full request (URL, headers, bearer token) for
// debugging — never show that on screen. .error is the short, safe reason string.
private fun Throwable.toSafeMessage(fallback: String): String = when (this) {
    is RestException -> error.ifBlank { fallback }
    else              -> message ?: fallback
}
