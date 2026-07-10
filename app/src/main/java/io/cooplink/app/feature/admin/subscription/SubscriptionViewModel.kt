package io.cooplink.app.feature.admin.subscription

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CurrencyProvider
import io.cooplink.app.core.domain.BillingPeriod
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
import kotlinx.serialization.json.Json
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

// Matches calculate_subscription_price(p_plan_key, p_billing_period) exactly
// (confirmed via direct RPC probing) — folds both the plan's own
// discount_active/discount_percent AND the period discount into one figure,
// which is why this is used for every period (including monthly) rather than
// only for the 3/6/12-month options.
@Serializable
data class SubscriptionPricing(
    @SerialName("plan_key")             val planKey: String,
    @SerialName("plan_name")            val planName: String,
    @SerialName("billing_period")       val billingPeriod: String,
    @SerialName("billing_months")       val billingMonths: Int,
    @SerialName("base_monthly_price")   val baseMonthlyPrice: Double,
    @SerialName("discounted_monthly")   val discountedMonthly: Double,
    @SerialName("plan_discount")        val planDiscount: Double,
    @SerialName("period_discount")      val periodDiscount: Double,
    @SerialName("total_discount")       val totalDiscount: Double,
    @SerialName("total_price")          val totalPrice: Double,
    @SerialName("amount_saved")         val amountSaved: Double,
    @SerialName("price_per_month")      val pricePerMonth: Double,
    @SerialName("expires_after_months") val expiresAfterMonths: Int,
)

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
    val billing_period: String,
    val billing_months: Int,
    val amount_saved: Double,
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
    val selectedPeriod: BillingPeriod = BillingPeriod.MONTHLY,
    val pricingByPlan: Map<String, SubscriptionPricing> = emptyMap(),
    val isPricingLoading: Boolean    = false,
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
    val currencyProvider: CurrencyProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
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
                loadPricingForPeriod(_state.value.selectedPeriod)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load subscription info", e)
                _state.update { it.copy(isLoading = false, error = "Could not load subscription plans. Pull down to retry.") }
            }
        }
    }

    fun selectPeriod(period: BillingPeriod) {
        _state.update { it.copy(selectedPeriod = period) }
        viewModelScope.launch { loadPricingForPeriod(period) }
    }

    private suspend fun loadPricingForPeriod(period: BillingPeriod) {
        val payablePlans = _state.value.plans.filter { it.isPayable }
        if (payablePlans.isEmpty()) return

        _state.update { it.copy(isPricingLoading = true) }
        val priced = coroutineScope {
            payablePlans.map { plan ->
                async {
                    val pricing = runCatching {
                        val resp = supabase.db.rpc(
                            "calculate_subscription_price",
                            buildJsonObject { put("p_plan_key", plan.planKey); put("p_billing_period", period.key) },
                        )
                        json.decodeFromString<SubscriptionPricing>(resp.data)
                    }.getOrElse {
                        Log.w(TAG, "calculate_subscription_price RPC failed for ${plan.planKey}/${period.key} — using client-side fallback", it)
                        val discount = period.defaultDiscount.toDouble()
                        val total = plan.basePrice * period.months * (1 - discount / 100)
                        SubscriptionPricing(
                            planKey = plan.planKey, planName = plan.planName,
                            billingPeriod = period.key, billingMonths = period.months,
                            baseMonthlyPrice = plan.basePrice, discountedMonthly = plan.basePrice * (1 - discount / 100),
                            planDiscount = 0.0, periodDiscount = discount, totalDiscount = discount,
                            totalPrice = total, amountSaved = (plan.basePrice * period.months) - total,
                            pricePerMonth = total / period.months, expiresAfterMonths = period.months,
                        )
                    }
                    plan.planKey to pricing
                }
            }.map { it.await() }.toMap()
        }
        _state.update { it.copy(pricingByPlan = priced, isPricingLoading = false) }
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
        val pricing = _state.value.pricingByPlan[plan.planKey]
        val amount = pricing?.totalPrice ?: plan.finalPrice
        viewModelScope.launch {
            _state.update { it.copy(payment = SubscriptionPaymentState.Initializing) }
            val email = supabase.auth.currentSessionOrNull()?.user?.email
            if (email == null) {
                _state.update { it.copy(payment = SubscriptionPaymentState.Failed("Could not determine your account email")) }
                return@launch
            }
            paymentManager.initializeSubscriptionPayment(amount, email, coopId, plan.planKey, plan.planName)
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
        val period = _state.value.selectedPeriod
        val pricing = _state.value.pricingByPlan[plan.planKey]

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
                    discount_percent    = pricing?.totalDiscount ?: plan.discountPercent,
                    final_price         = pricing?.totalPrice ?: plan.finalPrice,
                    billing_period      = period.key,
                    billing_months      = period.months,
                    amount_saved        = pricing?.amountSaved ?: 0.0,
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
        val period = _state.value.selectedPeriod
        val pricing = _state.value.pricingByPlan[plan.planKey]
        viewModelScope.launch {
            runCatching {
                supabase.db["subscription_orders"].insert(
                    NewSubscriptionOrderRequest(
                        cooperative_id          = coopId,
                        plan_key                = plan.planKey,
                        plan_name               = plan.planName,
                        base_price              = plan.basePrice,
                        discount_percent        = pricing?.totalDiscount ?: plan.discountPercent,
                        final_price             = pricing?.totalPrice ?: plan.finalPrice,
                        billing_period          = period.key,
                        billing_months          = period.months,
                        amount_saved            = pricing?.amountSaved ?: 0.0,
                        payment_method          = "bank_transfer",
                        bank_transfer_reference = reference,
                        status                  = "pending",
                    ),
                )
            }.onFailure { Log.e(TAG, "Failed to record bank transfer intent", it) }
        }
    }
}
