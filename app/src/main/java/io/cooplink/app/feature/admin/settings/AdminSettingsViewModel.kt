package io.cooplink.app.feature.admin.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.data.CurrencyProvider
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.domain.Contribution
import io.cooplink.app.core.domain.Cooperative
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.LoanStatus
import io.cooplink.app.core.domain.SavingsInterestMethod
import io.cooplink.app.core.domain.UserRole
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.InactivityManager
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "AdminSettingsVM"

@Serializable
data class RoleRow(
    val user_id: String?         = null,
    val role: String?            = null,
    val cooperative_id: String?  = null,
)

/** A role row with its member's name resolved for display — RoleRow alone
 * only has the auth user_id, which isn't something an admin can recognize. */
data class RoleAssignment(val userId: String, val role: String?, val memberName: String?)

val ASSIGNABLE_ROLES = listOf("coop_admin", "treasurer", "accountant", "loan_officer", "auditor", "member")

val ROLE_DISPLAY_NAMES = mapOf(
    "coop_admin"   to "Cooperative Admin",
    "treasurer"    to "Treasurer",
    "accountant"   to "Accountant",
    "loan_officer" to "Loan Officer",
    "auditor"      to "Auditor",
    "member"       to "Member",
)

fun roleDisplayName(role: String?): String =
    role?.let { ROLE_DISPLAY_NAMES[it] ?: it.replace("_", " ").replaceFirstChar { c -> c.uppercase() } } ?: "—"

@Serializable
data class BankAccountRow(
    val id: String,
    val cooperative_id: String? = null,
    val bank_name: String?      = null,
    val account_number: String? = null,
    val account_name: String?   = null,
)

@Serializable
private data class RolePatch(val role: String)

@Serializable
private data class NewBankAccountRequest(val cooperative_id: String?, val bank_name: String, val account_number: String, val account_name: String)

// See NewRepaymentRequest.type in AdminRepaymentsViewModel for why `type`
// can't carry a default here without silently vanishing from the request.
@Serializable
private data class NewFineTransactionRequest(
    val member_id: String,
    val cooperative_id: String?,
    val loan_id: String,
    val type: String,
    val amount: Double,
    val description: String? = null,
)

@Serializable
private data class LoanFinePatch(val last_fine_applied_at: String)

data class AdminSettingsUiState(
    val cooperative: Cooperative?    = null,
    val isSuperAdmin: Boolean        = false,
    val roles: List<RoleAssignment>  = emptyList(),
    val isLoadingRoles: Boolean      = false,
    val bankAccounts: List<BankAccountRow> = emptyList(),
    val isLoadingBankAccounts: Boolean = false,
    val isSavingBankAccount: Boolean = false,
    val bankAccountError: String?   = null,
    val updatingUserId: String?     = null,
    val snackbarMessage: String?     = null,
    val isLoading: Boolean            = true,
    val isSavingInterestSettings: Boolean = false,
    val isApplyingInterest: Boolean  = false,
    val interestError: String?       = null,
    val isSavingLateFeeSettings: Boolean = false,
    val isApplyingLateFees: Boolean  = false,
    val lateFeeError: String?        = null,
)

@HiltViewModel
class AdminSettingsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val cooperativeRepository: CooperativeRepository,
    private val memberRepository: MemberRepository,
    private val sessionPreferences: SessionPreferences,
    val inactivityManager: InactivityManager,
    val currencyProvider: CurrencyProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminSettingsUiState())
    val state: StateFlow<AdminSettingsUiState> = _state.asStateFlow()

    val autoLogoutEnabled: StateFlow<Boolean> = sessionPreferences.autoLogoutEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val requireBiometric: StateFlow<Boolean> = sessionPreferences.requireBiometricFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoLogout(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setAutoLogoutEnabled(enabled)
            inactivityManager.setAutoLogout(enabled)
        }
    }

    fun setRequireBiometric(enabled: Boolean) {
        viewModelScope.launch { sessionPreferences.setRequireBiometric(enabled) }
    }

    // Broadcasts via Supabase Realtime — every other admin/member device for
    // this cooperative (and the web platform) picks the change up immediately.
    fun changeCurrency(config: io.cooplink.app.core.domain.CurrencyConfig) {
        val coopId = _state.value.cooperative?.id ?: return
        viewModelScope.launch {
            runCatching { currencyProvider.setCurrency(coopId, config) }
                .onFailure {
                    Log.e(TAG, "Failed to change currency", it)
                    _state.value = _state.value.copy(snackbarMessage = "Could not update currency. Please try again.")
                }
        }
    }

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            // isSuperAdmin must NOT depend on a cooperative_id resolving —
            // a true platform-level super admin can have a null
            // cooperative_id on their own user_roles row (they aren't tied
            // to any single cooperative), which used to skip this entirely
            // via the early return below and made every super-admin-gated
            // item in this screen (Role Management, Payment Bank Details,
            // Subscription Requests, Pricing Plans) silently disappear for
            // exactly the accounts most likely to need them.
            val isSuperAdmin = runCatching { authRepository.fetchCurrentUser()?.role == UserRole.SUPER_ADMIN }.getOrDefault(false)
            val coopId = authRepository.currentCooperativeId()
            val coop = coopId?.let { cooperativeRepository.fetchCooperative(it) }
            _state.value = _state.value.copy(cooperative = coop, isSuperAdmin = isSuperAdmin, isLoading = false)
        }
    }

    fun loadRoles() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingRoles = true)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                val roleRows = supabase.db["user_roles"]
                    .select { filter { eq("cooperative_id", coopId) } }
                    .decodeList<RoleRow>()
                val membersByUserId = memberRepository.fetchMembersForCooperative(coopId).associateBy { it.userId }
                val roles = roleRows.mapNotNull { row ->
                    val userId = row.user_id ?: return@mapNotNull null
                    RoleAssignment(userId = userId, role = row.role, memberName = membersByUserId[userId]?.fullName)
                }
                _state.value = _state.value.copy(isLoadingRoles = false, roles = roles)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load roles", e)
                _state.value = _state.value.copy(
                    isLoadingRoles  = false,
                    snackbarMessage = "Could not load role assignments",
                )
            }
        }
    }


    fun loadBankAccounts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingBankAccounts = true)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                val accounts = supabase.db["cooperative_bank_accounts"]
                    .select { filter { eq("cooperative_id", coopId) } }
                    .decodeList<BankAccountRow>()
                _state.value = _state.value.copy(isLoadingBankAccounts = false, bankAccounts = accounts)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bank accounts", e)
                _state.value = _state.value.copy(isLoadingBankAccounts = false, snackbarMessage = "Could not load bank accounts")
            }
        }
    }

    fun addBankAccount(bankName: String, accountNumber: String, accountName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingBankAccount = true, bankAccountError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                supabase.db["cooperative_bank_accounts"].insert(
                    NewBankAccountRequest(coopId, bankName, accountNumber, accountName),
                )
                _state.value = _state.value.copy(isSavingBankAccount = false, snackbarMessage = "Bank account added")
                loadBankAccounts()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add bank account", e)
                _state.value = _state.value.copy(isSavingBankAccount = false, bankAccountError = "Could not add this account. Please try again.")
            }
        }
    }

    fun clearBankAccountError() { _state.value = _state.value.copy(bankAccountError = null) }

    fun changeRole(userId: String, newRole: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(updatingUserId = userId)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                // Scoped by cooperative_id as well as user_id so this can
                // never touch a role row belonging to a different cooperative.
                supabase.db["user_roles"].update(RolePatch(newRole)) {
                    filter { eq("user_id", userId); eq("cooperative_id", coopId) }
                }
                _state.value = _state.value.copy(updatingUserId = null, snackbarMessage = "Role updated")
                loadRoles()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to change role for $userId", e)
                _state.value = _state.value.copy(updatingUserId = null, snackbarMessage = "Could not update this role")
            }
        }
    }

    fun clearInterestError() { _state.value = _state.value.copy(interestError = null) }

    fun updateSavingsInterestSettings(rate: Double, method: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingInterestSettings = true, interestError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                // Seed a baseline the first time periodic_credit is turned
                // on, so the first "Apply Interest Now" has a real elapsed
                // period to compute against instead of an undefined one.
                val needsBaseline = method == SavingsInterestMethod.PERIODIC_CREDIT &&
                    _state.value.cooperative?.savingsInterestLastCreditedAt == null

                supabase.db["cooperatives"].update(
                    buildJsonObject {
                        put("savings_interest_rate", rate)
                        put("savings_interest_method", method)
                        if (needsBaseline) put("savings_interest_last_credited_at", nowIso())
                    },
                ) { filter { eq("id", coopId) } }

                val coop = cooperativeRepository.fetchCooperative(coopId)
                _state.value = _state.value.copy(isSavingInterestSettings = false, cooperative = coop, snackbarMessage = "Savings interest settings saved")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update savings interest settings", e)
                _state.value = _state.value.copy(isSavingInterestSettings = false, interestError = "Could not save these settings. Please try again.")
            }
        }
    }

    // Manual, admin-triggered credit — there is no background scheduler here
    // (an Android client can't run one), so "periodic" means "whenever an
    // admin taps this," not automatic. Only credits the period since
    // savings_interest_last_credited_at, so re-running it doesn't double-pay
    // the same period.
    fun applyInterestNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplyingInterest = true, interestError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                val coop = _state.value.cooperative
                val rate = coop?.savingsInterestRate ?: 0.0
                if (rate <= 0.0) throw IllegalStateException("Set an interest rate above 0% first")

                val daysElapsed = daysSince(coop?.savingsInterestLastCreditedAt)
                if (daysElapsed <= 0) throw IllegalStateException("Interest was already applied for this period")

                val members = memberRepository.fetchMembersForCooperative(coopId)
                var creditedCount = 0
                members.forEach { member ->
                    val balance = runCatching {
                        supabase.db["contributions"]
                            .select { filter { eq("member_id", member.id) } }
                            .decodeList<Contribution>()
                            .sumOf { it.amount }
                    }.getOrDefault(0.0)

                    val interest = balance * (rate / 100.0) * (daysElapsed / 365.0)
                    if (interest > 0.0) {
                        runCatching {
                            supabase.db["contributions"].insert(
                                buildJsonObject {
                                    put("member_id", member.id)
                                    put("cooperative_id", coopId)
                                    put("amount", interest)
                                    put("status", "completed")
                                },
                            )
                        }.onSuccess { creditedCount++ }
                            .onFailure { Log.w(TAG, "Failed to credit interest for member ${member.id}", it) }
                    }
                }

                supabase.db["cooperatives"].update(
                    buildJsonObject { put("savings_interest_last_credited_at", nowIso()) },
                ) { filter { eq("id", coopId) } }

                val refreshedCoop = cooperativeRepository.fetchCooperative(coopId)
                _state.value = _state.value.copy(
                    isApplyingInterest = false, cooperative = refreshedCoop,
                    snackbarMessage = "Interest credited to $creditedCount member(s)",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply savings interest", e)
                _state.value = _state.value.copy(isApplyingInterest = false, interestError = e.message ?: "Could not apply interest. Please try again.")
            }
        }
    }

    fun clearLateFeeError() { _state.value = _state.value.copy(lateFeeError = null) }

    fun updateLateFeeSettings(rate: Double, graceDays: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingLateFeeSettings = true, lateFeeError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")

                supabase.db["cooperatives"].update(
                    buildJsonObject {
                        put("late_fee_rate", rate)
                        put("late_fee_grace_days", graceDays)
                    },
                ) { filter { eq("id", coopId) } }

                val coop = cooperativeRepository.fetchCooperative(coopId)
                _state.value = _state.value.copy(isSavingLateFeeSettings = false, cooperative = coop, snackbarMessage = "Late fee settings saved")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update late fee settings", e)
                _state.value = _state.value.copy(isSavingLateFeeSettings = false, lateFeeError = "Could not save these settings. Please try again.")
            }
        }
    }

    // Manual, admin-triggered sweep — no background scheduler is possible from
    // this client. Skips any loan fined within the last day so a double-tap
    // (or re-running the sweep minutes later) can't charge the same loan twice
    // for the same lapse; running it again after that guard window re-fines
    // loans still overdue, which is the intended recurring behavior.
    fun applyLateFeesNow() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplyingLateFees = true, lateFeeError = null)
            try {
                val coopId = authRepository.currentCooperativeId()
                    ?: throw IllegalStateException("Could not determine your cooperative")
                val coop = _state.value.cooperative
                val rate = coop?.lateFeeRate ?: 0.0
                if (rate <= 0.0) throw IllegalStateException("Set a late fee rate above 0% first")
                val graceDays = coop?.lateFeeGraceDays ?: 0

                val members = memberRepository.fetchMembersForCooperative(coopId)
                val memberIds = members.map { it.id }
                val loans = if (memberIds.isEmpty()) emptyList() else supabase.db["loans"]
                    .select { filter { isIn("member_id", memberIds) } }
                    .decodeList<Loan>()

                val overdue = loans.filter { loan ->
                    loan.status in LoanStatus.ACTIVE_STATUSES &&
                        daysOverdue(loan.dueDate) > graceDays &&
                        (loan.lastFineAppliedAt == null || daysSince(loan.lastFineAppliedAt) >= 1)
                }

                var finedCount = 0
                overdue.forEach { loan ->
                    val fine = loan.outstandingBalance * (rate / 100.0)
                    if (fine <= 0.0) return@forEach
                    runCatching {
                        supabase.db["transactions"].insert(
                            NewFineTransactionRequest(
                                member_id      = loan.memberId,
                                cooperative_id = loan.cooperativeId ?: coopId,
                                loan_id        = loan.id,
                                // "penalty" — confirmed against transaction_type enum via Lovable;
                                // "fine" was never added since "penalty" already existed.
                                type           = "penalty",
                                amount         = fine,
                                description    = "Late payment fine",
                            ),
                        )
                        supabase.db["loans"].update(LoanFinePatch(nowIso())) { filter { eq("id", loan.id) } }
                    }.onSuccess { finedCount++ }
                        .onFailure { Log.w(TAG, "Failed to apply late fine for loan ${loan.id}", it) }
                }

                _state.value = _state.value.copy(
                    isApplyingLateFees = false,
                    snackbarMessage = "Late fees applied to $finedCount loan(s)",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply late fees", e)
                _state.value = _state.value.copy(isApplyingLateFees = false, lateFeeError = e.message ?: "Could not apply late fees. Please try again.")
            }
        }
    }

    // Positive when dueDate is in the past; 0 or negative (not yet due, or no
    // due date set) never qualifies as overdue.
    private fun daysOverdue(dueDate: String?): Long {
        if (dueDate.isNullOrBlank() || dueDate.length < 10) return 0
        return runCatching {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val due = fmt.parse(dueDate.take(10))?.time ?: return 0
            (System.currentTimeMillis() - due) / (1000L * 60 * 60 * 24)
        }.getOrDefault(0)
    }

    private fun nowIso(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date())
    }

    private fun daysSince(isoDate: String?): Long {
        if (isoDate.isNullOrBlank() || isoDate.length < 10) return 0
        return runCatching {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val then = fmt.parse(isoDate.take(10))?.time ?: return 0
            (System.currentTimeMillis() - then) / (1000L * 60 * 60 * 24)
        }.getOrDefault(0)
    }

    fun sendPasswordReset() {
        viewModelScope.launch {
            val email = runCatching { authRepository.fetchCurrentUser()?.email }.getOrNull()
            if (email.isNullOrBlank()) {
                _state.value = _state.value.copy(snackbarMessage = "Could not determine your email")
                return@launch
            }
            authRepository.resetAdminPassword(email)
                .onSuccess { _state.value = _state.value.copy(snackbarMessage = "Password reset email sent to $email") }
                .onFailure {
                    Log.e(TAG, "Failed to send password reset", it)
                    _state.value = _state.value.copy(snackbarMessage = "Could not send reset email")
                }
        }
    }

    fun showComingSoon(feature: String) {
        _state.value = _state.value.copy(snackbarMessage = "$feature is coming soon")
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }
}
