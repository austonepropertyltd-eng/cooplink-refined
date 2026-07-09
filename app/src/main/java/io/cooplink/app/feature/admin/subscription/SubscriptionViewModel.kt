package io.cooplink.app.feature.admin.subscription

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.payment.PaymentDeepLinkBus
import io.cooplink.app.core.payment.PaymentManager
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.feature.admin.paymentbankdetails.PaymentBankDetail
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "SubscriptionVM"
private const val DEFAULT_MAX_MEMBERS = 100

@Serializable
data class PricingPlan(
    val id: String,
    @SerialName("plan_key")            val planKey: String,
    @SerialName("plan_name")           val planName: String,
    @SerialName("base_price")          val basePrice: Double,
    @SerialName("max_members")         val maxMembers: Int?    = null,
    @SerialName("discount_percent")    val discountPercent: Double = 0.0,
    @SerialName("discount_label")      val discountLabel: String? = null,
    @SerialName("discount_active")     val discountActive: Boolean = false,
    @SerialName("discount_expires_at") val discountExpiresAt: String? = null,
    @SerialName("is_featured")         val isFeatured: Boolean = false,
    val features: List<String> = emptyList(),
) {
    val finalPrice: Double get() =
        if (discountActive && discountPercent > 0) basePrice * (1 - discountPercent / 100) else basePrice

    val hasDiscount: Boolean get() = discountActive && discountPercent > 0

    val displayPrice: String get() = when (planKey) {
        "starter"    -> "Free"
        "enterprise" -> "Custom"
        else         -> "₦${"%,.0f".format(finalPrice)}"
    }

    val isPayable: Boolean get() = planKey != "starter" && planKey != "enterprise" && finalPrice > 0
}

@Serializable
private data class CooperativeSubscriptionColumns(
    val plan_name: String?           = null,
    val trial_ends_at: String?       = null,
    val subscription_status: String? = null,
    val max_members: Int?            = null,
)

@Serializable
private data class SubscriptionOrderPatch(
    val plan_name: String,
    val subscription_status: String,
    val max_members: Int,
)

@Serializable
private data class NewSubscriptionOrderRequest(
    val cooperative_id: String,
    val plan_key: String,
    val plan_name: String,
    val base_price: Double,
    val discount_percent: Double,
    val final_price: Double,
    val payment_method: String,
    val bank_transfer_reference: String? = null,
    val paystack_reference: String?      = null,
    val status: String,
)

sealed class SubscriptionPaymentState {
    object Idle                                    : SubscriptionPaymentState()
    object Initializing                            : SubscriptionPaymentState()
    data class ReadyToOpen(val url: String)        : SubscriptionPaymentState()
    object Verifying                               : SubscriptionPaymentState()
    data class Success(val planName: String)       : SubscriptionPaymentState()
    data class Failed(val message: String)         : SubscriptionPaymentState()
}

data class SubscriptionUiState(
    val isLoading: Boolean          = true,
    val cooperativeId: String?      = null,
    val plans: List<PricingPlan>    = emptyList(),
    val currentPlanName: String     = "Free Trial",
    val subscriptionStatus: String  = "trial",
    val maxMembers: Int             = DEFAULT_MAX_MEMBERS,
    val membersUsed: Int            = 0,
    val trialEndsAt: String?        = null,
    val bankDetail: PaymentBankDetail? = null,
    val error: String?              = null,
    val payment: SubscriptionPaymentState = SubscriptionPaymentState.Idle,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val paymentManager: PaymentManager,
    private val deepLinkBus: PaymentDeepLinkBus,
    private val inactivityManager: InactivityManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null
    private var cooperativeId: String? = null
    private var pendingPlan: PricingPlan? = null

    init {
        load()
        viewModelScope.launch {
            deepLinkBus.events.collect { uri ->
                if (uri.host == "subscription-callback") {
                    (uri.getQueryParameter("reference") ?: uri.getQueryParameter("trxref"))
                        ?.let(::onPaystackCallback)
                }
            }
        }
    }

    fun refresh() = load()
    fun resetPayment() { _state.update { it.copy(payment = SubscriptionPaymentState.Idle) } }

    fun openPaystack(context: android.content.Context, url: String) {
        paymentManager.openPaystackInBrowser(context, url)
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                cooperativeId = coopId

                coroutineScope {
                    val plansDeferred = async {
                        supabase.db["pricing_plans"]
                            .select {
                                filter { eq("is_active", true) }
                                order("display_order", Order.ASCENDING)
                            }
                            .decodeList<PricingPlan>()
                    }
                    val membersUsedDeferred = async {
                        runCatching {
                            supabase.db["members"]
                                .select(Columns.list("id")) { filter { eq("cooperative_id", coopId) } }
                                .decodeList<Map<String, String>>()
                                .size
                        }.getOrDefault(0)
                    }
                    val coopInfoDeferred = async {
                        runCatching {
                            supabase.db["cooperatives"]
                                .select(Columns.list("plan_name", "trial_ends_at", "subscription_status", "max_members")) {
                                    filter { eq("id", coopId) }
                                }
                                .decodeSingleOrNull<CooperativeSubscriptionColumns>()
                        }.getOrNull()
                    }
                    val bankDetailDeferred = async {
                        runCatching {
                            supabase.db["payment_bank_details"]
                                .select { filter { eq("is_active", true) } }
                                .decodeSingleOrNull<PaymentBankDetail>()
                        }.getOrNull()
                    }

                    val plans = plansDeferred.await()
                    val membersUsed = membersUsedDeferred.await()
                    val coopInfo = coopInfoDeferred.await()
                    val bankDetail = bankDetailDeferred.await()

                    _state.update {
                        it.copy(
                            isLoading           = false,
                            cooperativeId       = coopId,
                            plans               = plans,
                            currentPlanName     = coopInfo?.plan_name ?: "Free Trial",
                            subscriptionStatus  = coopInfo?.subscription_status ?: "trial",
                            maxMembers          = coopInfo?.max_members ?: DEFAULT_MAX_MEMBERS,
                            membersUsed         = membersUsed,
                            trialEndsAt         = coopInfo?.trial_ends_at,
                            bankDetail          = bankDetail,
                        )
                    }
                }
                subscribeToRealtimeDiscounts()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load subscription info", e)
                _state.update { it.copy(isLoading = false, error = "Could not load subscription plans. Pull down to retry.") }
            }
        }
    }

    // Keeps discount badges in sync live — e.g. a flash sale toggled on from
    // the backend appears without the admin needing to refresh the screen.
    private fun subscribeToRealtimeDiscounts() {
        if (realtimeChannel != null) return
        viewModelScope.launch {
            runCatching {
                val channel = supabase.realtime.channel("pricing_updates")
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "pricing_plans"
                }.onEach { action ->
                    val updated = runCatching { action.decodeRecord<PricingPlan>() }.getOrNull() ?: return@onEach
                    _state.update { s -> s.copy(plans = s.plans.map { if (it.planKey == updated.planKey) updated else it }) }
                }.launchIn(viewModelScope)
                channel.subscribe()
                realtimeChannel = channel
            }.onFailure { Log.w(TAG, "Realtime discount subscription failed — badges won't update live", it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeChannel?.let { ch -> viewModelScope.launch { runCatching { ch.unsubscribe() } } }
    }

    // ── Paystack ──────────────────────────────────────────────────────────────
    fun startPaystackPayment(plan: PricingPlan) {
        val coopId = cooperativeId ?: return
        pendingPlan = plan
        viewModelScope.launch {
            _state.update { it.copy(payment = SubscriptionPaymentState.Initializing) }
            val email = supabase.auth.currentSessionOrNull()?.user?.email
            if (email == null) {
                _state.update { it.copy(payment = SubscriptionPaymentState.Failed("Could not determine your account email")) }
                return@launch
            }
            paymentManager.initializeSubscriptionPayment(plan.finalPrice, email, coopId, plan.planKey, plan.planName)
                .onSuccess { resp ->
                    _state.update { it.copy(payment = SubscriptionPaymentState.ReadyToOpen(resp.authorization_url)) }
                }
                .onFailure {
                    Log.e(TAG, "Failed to initialize subscription payment", it)
                    _state.update { s -> s.copy(payment = SubscriptionPaymentState.Failed(it.message ?: "Could not start payment")) }
                }
        }
    }

    private fun onPaystackCallback(reference: String) {
        val coopId = cooperativeId
        val plan = pendingPlan
        inactivityManager.resumeTimer()
        viewModelScope.launch {
            _state.update { it.copy(payment = SubscriptionPaymentState.Verifying) }
            paymentManager.verifyPayment(reference)
                .onSuccess { verify ->
                    if (verify.status == "success" && coopId != null && plan != null) {
                        applyPlanUpgrade(coopId, plan, reference)
                    } else {
                        _state.update { it.copy(payment = SubscriptionPaymentState.Failed("Payment not confirmed: ${verify.message}")) }
                    }
                }
                .onFailure {
                    Log.e(TAG, "Subscription payment verification failed", it)
                    _state.update { s -> s.copy(payment = SubscriptionPaymentState.Failed(it.message ?: "Could not verify payment")) }
                }
        }
    }

    private suspend fun applyPlanUpgrade(coopId: String, plan: PricingPlan, paystackReference: String) {
        runCatching {
            supabase.db["cooperatives"].update(
                SubscriptionOrderPatch(
                    plan_name           = plan.planName,
                    subscription_status = "active",
                    max_members         = plan.maxMembers ?: 999_999,
                ),
            ) { filter { eq("id", coopId) } }
        }.onFailure { Log.e(TAG, "Failed to update cooperative after successful payment", it) }

        runCatching {
            supabase.db["subscription_orders"].insert(
                NewSubscriptionOrderRequest(
                    cooperative_id      = coopId,
                    plan_key            = plan.planKey,
                    plan_name           = plan.planName,
                    base_price          = plan.basePrice,
                    discount_percent    = plan.discountPercent,
                    final_price         = plan.finalPrice,
                    payment_method      = "paystack",
                    paystack_reference  = paystackReference,
                    status              = "completed",
                ),
            )
        }.onFailure { Log.w(TAG, "Failed to record subscription order for paystack payment", it) }

        _state.update {
            it.copy(
                payment            = SubscriptionPaymentState.Success(plan.planName),
                currentPlanName    = plan.planName,
                subscriptionStatus = "active",
                maxMembers         = plan.maxMembers ?: 999_999,
            )
        }
    }

    // ── Bank transfer ─────────────────────────────────────────────────────────
    fun recordBankTransferIntent(plan: PricingPlan, reference: String) {
        val coopId = cooperativeId ?: return
        viewModelScope.launch {
            runCatching {
                supabase.db["subscription_orders"].insert(
                    NewSubscriptionOrderRequest(
                        cooperative_id          = coopId,
                        plan_key                = plan.planKey,
                        plan_name               = plan.planName,
                        base_price              = plan.basePrice,
                        discount_percent        = plan.discountPercent,
                        final_price             = plan.finalPrice,
                        payment_method          = "bank_transfer",
                        bank_transfer_reference = reference,
                        status                  = "pending",
                    ),
                )
            }.onFailure { Log.e(TAG, "Failed to record bank transfer intent", it) }
        }
    }
}
