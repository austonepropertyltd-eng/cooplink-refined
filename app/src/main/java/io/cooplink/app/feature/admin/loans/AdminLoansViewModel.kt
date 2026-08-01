package io.cooplink.app.feature.admin.loans

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CurrencyProvider
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.LoanPlan
import io.cooplink.app.core.domain.LoanStatus
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AdminLoansVM"
private const val GENERIC_LOAD_ERROR = "Could not load loans. Pull down to retry."
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class LoanStatusUpdate(val status: String)

// process-disbursement's response envelope — matches this codebase's other
// edge-function convention (see PaymentManager.PaymentVerifyResponse, used
// the same way for Paystack verification: status == "success"). Unconfirmed
// field names for THIS function specifically since its source isn't visible
// here — the raw body is still logged in disburse() so the real shape can be
// checked against a live response if this guess is wrong.
@Serializable
private data class DisbursementResponse(val status: String? = null, val message: String? = null)

@Serializable
private data class LoanRejectUpdate(val status: String, val rejection_reason: String)

// Records a fee/fine as a real transactions row — see NewRepaymentRequest.type
// in AdminRepaymentsViewModel for why `type` can't have a default here without
// silently vanishing from the request.
@Serializable
private data class NewFeeTransactionRequest(
    val member_id: String,
    val cooperative_id: String?,
    val loan_id: String,
    val type: String,
    val amount: Double,
    val description: String? = null,
)

@Serializable
private data class LoanPlanRequest(
    val cooperative_id: String?,
    val name: String,
    val min_amount: Double,
    val max_amount: Double,
    val active: Boolean = true,
)

@Serializable
private data class LoanPlanPatch(val name: String, val min_amount: Double, val max_amount: Double, val active: Boolean)

// notifications.user_id refers to the auth user id (MemberDetails.userId), not
// the members.id — the two are only the same by coincidence for some rows.
@Serializable
private data class NewNotificationRequest(
    val user_id: String,
    val title: String,
    val message: String,
    // No default — see NewRepaymentRequest.type in AdminRepaymentsViewModel
    // for why a defaulted property is silently dropped from the request even
    // when the call site passes exactly that value.
    val type: String,
)

data class AdminLoanRow(val loan: Loan, val memberName: String?)

data class AdminLoansUiState(
    val isLoading: Boolean            = true,
    val rows: List<AdminLoanRow>      = emptyList(),
    val plans: List<LoanPlan>         = emptyList(),
    val error: String?                = null,
    val processingLoanId: String?     = null,
    val actionError: String?          = null,
    val snackbarMessage: String?      = null,
    val isSavingPlan: Boolean         = false,
    val planError: String?            = null,
    // Loans where process-disbursement was confirmed successful but the
    // follow-up loans.status write then failed — hidden from Disbursements
    // immediately so a retry can't re-invoke the payout edge function for
    // money that may already have moved. Cleared by the next load()/refresh(),
    // which re-derives the tab from the server's current truth.
    val unsyncedDisbursedLoanIds: Set<String> = emptySet(),
) {
    val applications: List<AdminLoanRow> get() = rows.filter { it.loan.status in LoanStatus.PENDING_STATUSES }
    val disbursements: List<AdminLoanRow> get() = rows.filter { it.loan.status == LoanStatus.APPROVED && it.loan.id !in unsyncedDisbursedLoanIds }
    val active: List<AdminLoanRow>    get() = rows.filter { it.loan.status in LoanStatus.ACTIVE_STATUSES }
    val completed: List<AdminLoanRow> get() = rows.filter { it.loan.status == LoanStatus.COMPLETED }
    val defaulted: List<AdminLoanRow> get() = rows.filter { it.loan.status == LoanStatus.DEFAULTED }
}

@HiltViewModel
class AdminLoansViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val memberRepository: MemberRepository,
    private val currencyProvider: CurrencyProvider,
    val bankVerificationService: io.cooplink.app.core.payment.BankVerificationService,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminLoansUiState())
    val state: StateFlow<AdminLoansUiState> = _state.asStateFlow()

    private var membersCache: List<MemberDetails> = emptyList()
    private var realtimeChannel: RealtimeChannel? = null

    init {
        load()
        observeLoanChanges()
    }

    fun refresh() = load()
    fun clearActionError() { _state.value = _state.value.copy(actionError = null) }
    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
    fun clearPlanError() { _state.value = _state.value.copy(planError = null) }

    fun approve(loanId: String) = updateStatus(
        loanId, LoanStatus.APPROVED, "Loan approved",
        notifyTitle = "Loan Approved", notifyMessage = "Your loan application has been approved.",
    )

    fun reject(loanId: String, reason: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingLoanId = loanId, actionError = null)
            try {
                supabase.db["loans"].update(
                    LoanRejectUpdate(status = LoanStatus.REJECTED, rejection_reason = reason),
                ) { filter { eq("id", loanId) } }
                notifyMember(
                    loanId, "Loan Rejected",
                    if (reason.isNotBlank()) "Your loan application was rejected: $reason" else "Your loan application was rejected.",
                )
                _state.value = _state.value.copy(processingLoanId = null, snackbarMessage = "Loan rejected")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reject loan $loanId", e)
                _state.value = _state.value.copy(processingLoanId = null, actionError = "Could not update this loan. Please try again.")
            }
        }
    }

    private fun updateStatus(loanId: String, status: String, successMessage: String, notifyTitle: String? = null, notifyMessage: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingLoanId = loanId, actionError = null)
            try {
                supabase.db["loans"].update(LoanStatusUpdate(status)) { filter { eq("id", loanId) } }
                if (notifyTitle != null && notifyMessage != null) notifyMember(loanId, notifyTitle, notifyMessage)
                _state.value = _state.value.copy(processingLoanId = null, snackbarMessage = successMessage)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update loan $loanId to $status", e)
                _state.value = _state.value.copy(processingLoanId = null, actionError = "Could not update this loan. Please try again.")
            }
        }
    }

    // Best-effort — an in-app notification row, not a true push. Real push
    // delivery (waking a backgrounded/closed app) needs a server-side FCM
    // send with a secret key, which can't live in this client.
    private fun notifyMember(loanId: String, title: String, message: String) {
        val loan = _state.value.rows.find { it.loan.id == loanId }?.loan ?: return
        val userId = membersCache.find { it.id == loan.memberId }?.userId ?: return
        viewModelScope.launch {
            runCatching {
                // `type` must be passed explicitly — kotlinx.serialization
                // doesn't encode a property still at its default value,
                // silently dropping it from the request (same pattern
                // confirmed live in AdminRepaymentsViewModel).
                supabase.db["notifications"].insert(NewNotificationRequest(user_id = userId, title = title, message = message, type = "loans"))
            }.onFailure { Log.w(TAG, "Failed to notify member $userId for loan $loanId", it) }
        }
    }

    fun disburse(loanId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingLoanId = loanId, actionError = null)

            val disbursed = try {
                val resp = supabase.functions.invoke(
                    function = "process-disbursement",
                    body = buildJsonObject { put("loan_id", loanId) },
                )
                val rawBody = resp.bodyAsText()
                Log.d(TAG, "process-disbursement raw response: $rawBody")

                // A 2xx HTTP status alone doesn't mean the payout actually
                // happened — process-disbursement can return 200 with a
                // business-logic failure in the body. Anything that isn't an
                // explicit "success" is treated as a real failure rather than
                // assumed to have worked, since this moves real money.
                val parsed = runCatching { json.decodeFromString<DisbursementResponse>(rawBody) }.getOrNull()
                if (parsed?.status != "success") {
                    _state.value = _state.value.copy(
                        processingLoanId = null,
                        actionError = parsed?.message ?: "Disbursement was not confirmed by the payment processor.",
                    )
                    return@launch
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disburse loan $loanId", e)
                _state.value = _state.value.copy(processingLoanId = null, actionError = "Could not disburse this loan. Please try again.")
                return@launch
            }

            if (!disbursed) return@launch

            try {
                supabase.db["loans"].update(LoanStatusUpdate(LoanStatus.DISBURSED)) { filter { eq("id", loanId) } }
                val loan = _state.value.rows.find { it.loan.id == loanId }?.loan
                val amount = loan?.outstandingBalance
                val amountText = amount?.let { currencyProvider.format(it) } ?: "Your loan"

                // Best-effort — the admin fee was already computed and shown to
                // the member at application time; recording it as a transaction
                // now is bookkeeping, not something that should block or reverse
                // a disbursement that already succeeded.
                val fee = loan?.adminFee
                if (loan != null && fee != null && fee > 0.0) {
                    runCatching {
                        supabase.db["transactions"].insert(
                            NewFeeTransactionRequest(
                                member_id      = loan.memberId,
                                cooperative_id = loan.cooperativeId ?: authRepository.currentCooperativeId(),
                                loan_id        = loan.id,
                                type           = "admin_fee",
                                amount         = fee,
                                description    = "Admin fee for loan disbursement",
                            ),
                        )
                    }.onFailure { Log.w(TAG, "Failed to record admin fee transaction for loan $loanId", it) }
                }

                notifyMember(loanId, "Loan Disbursed", "$amountText has been disbursed to your account.")
                _state.value = _state.value.copy(processingLoanId = null, snackbarMessage = "Loan disbursed")
                load()
            } catch (e: Exception) {
                // The payout edge function already succeeded here — retrying
                // disburse() for this loan would re-invoke it a second time
                // for a payment that may have already gone out, so this loan
                // is hidden from Disbursements (not re-enabled) until an
                // explicit refresh re-checks its real server-side status.
                Log.e(TAG, "Disbursement succeeded but failed to sync status for loan $loanId", e)
                _state.value = _state.value.copy(
                    processingLoanId = null,
                    unsyncedDisbursedLoanIds = _state.value.unsyncedDisbursedLoanIds + loanId,
                    actionError = "Payment was sent, but we couldn't update this loan's status. Pull down to refresh before trying again.",
                )
            }
        }
    }

    fun createPlan(name: String, minAmount: Double, maxAmount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingPlan = true, planError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                supabase.db["loan_plans"].insert(LoanPlanRequest(coopId, name, minAmount, maxAmount))
                _state.value = _state.value.copy(isSavingPlan = false, snackbarMessage = "Loan package created")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create loan plan", e)
                _state.value = _state.value.copy(isSavingPlan = false, planError = "Could not create this package. Please try again.")
            }
        }
    }

    fun editPlan(planId: String, name: String, minAmount: Double, maxAmount: Double, active: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingPlan = true, planError = null)
            try {
                supabase.db["loan_plans"].update(LoanPlanPatch(name, minAmount, maxAmount, active)) { filter { eq("id", planId) } }
                _state.value = _state.value.copy(isSavingPlan = false, snackbarMessage = "Loan package updated")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update loan plan $planId", e)
                _state.value = _state.value.copy(isSavingPlan = false, planError = "Could not update this package. Please try again.")
            }
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            try {
                supabase.db["loan_plans"].delete { filter { eq("id", planId) } }
                _state.value = _state.value.copy(snackbarMessage = "Loan package deleted")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete loan plan $planId", e)
                _state.value = _state.value.copy(snackbarMessage = "Could not delete this package")
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
                    membersCache = members
                    val memberIds = members.map { it.id }
                    val namesByMemberId = members.associateBy { it.id }

                    val loans = if (memberIds.isEmpty()) emptyList() else supabase.db["loans"]
                        .select { filter { isIn("member_id", memberIds) } }
                        .decodeList<Loan>()

                    val plans = runCatching {
                        supabase.db["loan_plans"]
                            .select { filter { eq("cooperative_id", coopId) } }
                            .decodeList<LoanPlan>()
                    }.getOrDefault(emptyList())

                    val rows = loans.map { AdminLoanRow(it, namesByMemberId[it.memberId]?.fullName) }

                    AdminLoansUiState(isLoading = false, rows = rows, plans = plans)
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load loans", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }

    // Keeps the admin's loan list current when a loan changes — a member
    // applying, another admin acting on an application, or a scheduled job
    // updating status — without waiting for a manual pull-to-refresh.
    // Filtered server-side to this cooperative's rows only, matching the
    // scoping load() already applies via the member list.
    private fun observeLoanChanges() {
        viewModelScope.launch {
            val coopId = authRepository.currentCooperativeId() ?: return@launch
            val channel = supabase.realtime.channel("loans-admin-$coopId")
            realtimeChannel = channel
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "loans"
                filter("cooperative_id", FilterOperator.EQ, coopId)
            }
            channel.subscribe()
            changes.collect { load() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled by the time onCleared runs, so
        // the unsubscribe is fired on a short-lived scope of its own rather
        // than silently no-oping.
        val channel = realtimeChannel ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { supabase.realtime.removeChannel(channel) }
        }
    }
}
