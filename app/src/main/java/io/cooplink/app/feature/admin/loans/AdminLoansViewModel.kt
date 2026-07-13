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
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AdminLoansVM"
private const val GENERIC_LOAD_ERROR = "Could not load loans. Pull down to retry."

@Serializable
private data class LoanStatusUpdate(val status: String)

@Serializable
private data class LoanRejectUpdate(val status: String, val rejection_reason: String)

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
    val type: String = "loans",
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
) {
    val applications: List<AdminLoanRow> get() = rows.filter { it.loan.status in LoanStatus.PENDING_STATUSES }
    val disbursements: List<AdminLoanRow> get() = rows.filter { it.loan.status == LoanStatus.APPROVED }
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

    init { load() }

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
                supabase.db["notifications"].insert(NewNotificationRequest(user_id = userId, title = title, message = message))
            }.onFailure { Log.w(TAG, "Failed to notify member $userId for loan $loanId", it) }
        }
    }

    fun disburse(loanId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingLoanId = loanId, actionError = null)
            try {
                val resp = supabase.functions.invoke(
                    function = "process-disbursement",
                    body = buildJsonObject { put("loan_id", loanId) },
                )
                Log.d(TAG, "process-disbursement raw response: ${resp.bodyAsText()}")

                supabase.db["loans"].update(LoanStatusUpdate(LoanStatus.DISBURSED)) { filter { eq("id", loanId) } }
                val amount = _state.value.rows.find { it.loan.id == loanId }?.loan?.outstandingBalance
                val amountText = amount?.let { currencyProvider.format(it) } ?: "Your loan"
                notifyMember(loanId, "Loan Disbursed", "$amountText has been disbursed to your account.")
                _state.value = _state.value.copy(processingLoanId = null, snackbarMessage = "Loan disbursed")
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disburse loan $loanId", e)
                _state.value = _state.value.copy(processingLoanId = null, actionError = "Could not disburse this loan. Please try again.")
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
}
