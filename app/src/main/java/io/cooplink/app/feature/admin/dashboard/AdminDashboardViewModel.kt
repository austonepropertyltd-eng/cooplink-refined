package io.cooplink.app.feature.admin.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AdminDashboardVM"
private const val GENERIC_LOAD_ERROR = "Could not load dashboard data. Pull down to retry."

private val json = Json { ignoreUnknownKeys = true }

// Matches the real cooperative-analytics response, captured via Log.d:
// {"health_score":75,"health_label":"Good","summary":{"total_members":1,
//  "active_members":1,"inactive_members":1,"member_activity_rate":0,
//  "total_savings":0,"total_loans_disbursed":0,"outstanding_loans":0,
//  "repayment_rate":100,"default_rate":0,"contribution_compliance":100,
//  "overdue_loans_count":0,"high_risk_loans":0}, ...}
@Serializable
private data class CooperativeAnalyticsResponse(
    val health_score: Double? = null,
    val health_label: String? = null,
    val summary: AnalyticsSummary? = null,
)

@Serializable
private data class AnalyticsSummary(
    val total_members: Int?           = null,
    val active_members: Int?          = null,
    val inactive_members: Int?        = null,
    val total_loans_disbursed: Double? = null,
    val outstanding_loans: Double?    = null,
    val repayment_rate: Double?       = null,
    val default_rate: Double?         = null,
    val member_activity_rate: Double? = null,
    val contribution_compliance: Double? = null,
)

enum class DashboardPeriod(val label: String, val days: Int) {
    TODAY("Today", 1),
    THIS_WEEK("This Week", 7),
    THIS_MONTH("This Month", 30),
    THIS_YEAR("This Year", 365),
}

// Flat shape of the get_cooperative_analytics(p_cooperative_id, p_days) RPC
// (confirmed via direct probing) — a separate, additional data source from
// the cooperative-analytics edge function above; the two don't share fields,
// so both are kept rather than one replacing the other (see CoopLink Task #48).
data class CoopAnalytics(
    val totalMembers: Int          = 0,
    val activeLoans: Int           = 0,
    val totalDisbursed: Double     = 0.0,
    val totalRepaid: Double        = 0.0,
    val totalSavings: Double       = 0.0,
    val monthlyCollections: Double = 0.0,
    val defaultRate: Double        = 0.0,
    val healthScore: Int           = 0,
    val pendingKyc: Int            = 0,
    val pendingLoans: Int          = 0,
    val outstandingBalance: Double = 0.0,
)

data class AdminDashboardUiState(
    val isLoading: Boolean         = true,
    val selectedPeriod: DashboardPeriod = DashboardPeriod.THIS_MONTH,
    val totalMembers: Int          = 0,
    val activeMembers: Int         = 0,
    val totalDisbursed: Double     = 0.0,
    val outstandingLoans: Double   = 0.0,
    val repaymentRatePct: Double   = 0.0,
    val defaultRatePct: Double     = 0.0,
    val memberActivityPct: Double  = 0.0,
    val contributionCompliancePct: Double = 0.0,
    val healthScorePct: Double     = 100.0,
    val healthLabel: String?       = null,
    val analytics: CoopAnalytics   = CoopAnalytics(),
    val error: String?             = null,
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminDashboardUiState())
    val state: StateFlow<AdminDashboardUiState> = _state.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null
    private var loadJob: kotlinx.coroutines.Job? = null

    init { load() }

    fun refresh() = load()

    // Not yet confirmed whether cooperative-analytics actually honors this —
    // no way to verify from here (requires a real authenticated session and
    // its source isn't visible). If the KPIs don't change when switching
    // periods once tested live, the edge function needs a "days" param added
    // server-side, not a client-side fix.
    fun setPeriod(period: DashboardPeriod) {
        _state.value = _state.value.copy(selectedPeriod = period)
        load()
    }

    private fun load() {
        // Rapid period taps (or a refresh while the initial load is still in
        // flight) would otherwise launch multiple concurrent loads with no
        // ordering guarantee — a slower response for an older period could
        // overwrite a faster, more-recently-requested one, leaving the KPIs
        // and the selected-period chip out of sync.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val period = _state.value.selectedPeriod
            _state.value = _state.value.copy(isLoading = true, error = null)
            var loadedCoopId: String? = null
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")
                    loadedCoopId = coopId

                    coroutineScope {
                        val summaryDeferred = async {
                            val resp = supabase.functions.invoke(
                                function = "cooperative-analytics",
                                body = buildJsonObject { put("cooperative_id", coopId); put("days", period.days) },
                            )
                            val rawBody = resp.bodyAsText()
                            Log.d(TAG, "cooperative-analytics raw response: $rawBody")
                            json.decodeFromString<CooperativeAnalyticsResponse>(rawBody)
                        }
                        // Independent of the edge function above — a separate
                        // data source, so its own failure shouldn't take down
                        // the primary KPI cards/health sheet.
                        val analyticsDeferred = async { fetchCoopAnalytics(coopId, period.days) }

                        val data = summaryDeferred.await()
                        val summary = data.summary
                        val analytics = analyticsDeferred.await()

                        AdminDashboardUiState(
                            isLoading        = false,
                            selectedPeriod   = period,
                            totalMembers     = summary?.total_members ?: 0,
                            activeMembers    = summary?.active_members ?: 0,
                            totalDisbursed   = summary?.total_loans_disbursed ?: 0.0,
                            outstandingLoans = summary?.outstanding_loans ?: 0.0,
                            repaymentRatePct = summary?.repayment_rate ?: 0.0,
                            defaultRatePct   = summary?.default_rate ?: 0.0,
                            memberActivityPct = summary?.member_activity_rate ?: 0.0,
                            contributionCompliancePct = summary?.contribution_compliance ?: 0.0,
                            healthScorePct   = data.health_score ?: 100.0,
                            healthLabel      = data.health_label,
                            analytics        = analytics,
                        )
                    }
                }
                _state.value = result
                loadedCoopId?.let { subscribeToRealtimeChanges(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dashboard analytics", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    // Dashboard KPIs come from server-side aggregates (RPC/edge function),
    // not raw rows, so a change event just re-triggers load() rather than
    // patching state in-place — debounced since e.g. a bulk repayment import
    // fires many transaction changes at once.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun subscribeToRealtimeChanges(cooperativeId: String) {
        if (realtimeChannel != null) return
        viewModelScope.launch {
            runCatching {
                val ch = supabase.realtime.channel("admin_dashboard_$cooperativeId")
                val loanChanges = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "loans"
                    filter("cooperative_id", FilterOperator.EQ, cooperativeId)
                }
                val memberChanges = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "members"
                    filter("cooperative_id", FilterOperator.EQ, cooperativeId)
                }
                val transactionChanges = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "transactions"
                    filter("cooperative_id", FilterOperator.EQ, cooperativeId)
                }
                merge(loanChanges, memberChanges, transactionChanges)
                    .debounce(500)
                    .onEach { load() }
                    .launchIn(viewModelScope)
                ch.subscribe()
                realtimeChannel = ch
            }.onFailure { Log.w(TAG, "Realtime dashboard subscription failed — KPI cards won't update live", it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeChannel?.let { ch -> viewModelScope.launch { runCatching { ch.unsubscribe() } } }
    }

    private suspend fun fetchCoopAnalytics(cooperativeId: String, days: Int): CoopAnalytics = runCatching {
        val result = supabase.db.rpc(
            "get_cooperative_analytics",
            buildJsonObject { put("p_cooperative_id", cooperativeId); put("p_days", days) },
        )
        Log.d(TAG, "get_cooperative_analytics raw response: ${result.data}")
        val obj = json.parseToJsonElement(result.data).jsonObject
        fun intOf(key: String) = obj[key]?.jsonPrimitive?.int ?: 0
        fun doubleOf(key: String) = obj[key]?.jsonPrimitive?.double ?: 0.0

        CoopAnalytics(
            totalMembers       = intOf("total_members"),
            activeLoans        = intOf("active_loans"),
            totalDisbursed     = doubleOf("total_disbursed"),
            totalRepaid        = doubleOf("total_repaid"),
            totalSavings       = doubleOf("total_savings"),
            monthlyCollections = doubleOf("monthly_collections"),
            defaultRate        = doubleOf("default_rate"),
            healthScore        = intOf("health_score"),
            pendingKyc         = intOf("pending_kyc"),
            pendingLoans       = intOf("pending_loans"),
            outstandingBalance = doubleOf("outstanding_balance"),
        )
    }.onFailure { Log.w(TAG, "get_cooperative_analytics RPC failed", it) }.getOrDefault(CoopAnalytics())
}
