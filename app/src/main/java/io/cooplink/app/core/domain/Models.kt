package io.cooplink.app.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Auth ──────────────────────────────────────────────────────────────────────

enum class UserRole {
    SUPER_ADMIN, COOP_ADMIN, TREASURER, ACCOUNTANT,
    LOAN_OFFICER, AUDITOR, MEMBER, GOV_ADMIN, FED_ADMIN, UNKNOWN;

    val isAdmin: Boolean get() = this != MEMBER && this != UNKNOWN
}

data class AuthUser(
    val id: String,
    val email: String?,
    val memberId: String?,
    val role: UserRole,
    val cooperativeId: String?,
    val tenantId: String?,
    val fullName: String?,
    val avatarUrl: String?,
)

// ── Member ────────────────────────────────────────────────────────────────────
// Matches the real `members` table — it holds no identity fields (name/email/
// phone/avatar); those live in the separate `profiles` table keyed by user_id.

@Serializable
data class Member(
    val id: String,
    @SerialName("user_id")        val userId: String?        = null,
    @SerialName("cooperative_id") val cooperativeId: String? = null,
    val status: String            = "active",
    @SerialName("date_of_birth")  val dateOfBirth: String?   = null,
    val gender: String?           = null,
    val address: String?          = null,
    val occupation: String?       = null,
    @SerialName("created_at")     val createdAt: String?     = null,
    // The real formatted human-readable ID (confirmed a valid column via
    // schema probing) — e.g. "MEM-747074". Nullable/defaulted since we've
    // never actually seen a populated value; falls back to the row id if null.
    @SerialName("member_number")  val memberNumber: String?  = null,
    // Also a real column (confirmed via schema probing) — the same value
    // used to sign in (see AuthRepository.loginMember's login_id param).
    // Populated for accounts where member_number itself is blank.
    @SerialName("login_id")       val loginId: String?       = null,
    // Real column (confirmed via schema probing) — raw string, mapped to
    // KycStatus via KycStatus.fromRaw().
    @SerialName("kyc_status")     val kycStatus: String?     = null,
)

// ── Profile ───────────────────────────────────────────────────────────────────
// The real home of a user's display identity.

@Serializable
data class Profile(
    val id: String,
    @SerialName("user_id")    val userId: String?    = null,
    @SerialName("full_name")  val fullName: String?  = null,
    val phone: String?         = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

// ── Member details (combined view) ───────────────────────────────────────────
// What screens actually display: a member row merged with its profile and the
// auth session's email, plus savings computed from contributions (there is no
// stored wallet/savings-balance column anywhere in the schema).

data class MemberDetails(
    val id: String,
    val userId: String?,
    val cooperativeId: String?,
    val status: String,
    val fullName: String?,
    val phone: String?,
    val avatarUrl: String?,
    val email: String?,
    val totalSavings: Double = 0.0,
    val createdAt: String? = null,
    // The real formatted ID column (members.member_number, e.g. "MEM-747074").
    val memberNumber: String? = null,
    // Also real (members.login_id) — the same value used to sign in; a
    // populated fallback for accounts where member_number is blank.
    val loginId: String? = null,
    // Fallback only ever populated for the signed-in user's own record (from
    // their auth session's user_metadata) — kept in case member_number is
    // unpopulated for some accounts.
    val formattedMemberId: String? = null,
    val kycStatus: KycStatus = KycStatus.NOT_SUBMITTED,
) {
    /** What to actually show as "the member ID" — prefers the real formatted
     * column, then login_id, then the auth-metadata fallback, then the row's
     * own UUID as a last resort (only reachable if none of the above are set
     * for this member — a data gap, not something the UI can fabricate). */
    val displayId: String get() =
        memberNumber?.takeIf { it.isNotBlank() }
            ?: loginId?.takeIf { it.isNotBlank() }
            ?: formattedMemberId?.takeIf { it.isNotBlank() }
            ?: id
}

// ── Loan ──────────────────────────────────────────────────────────────────────
enum class InterestType { FLAT, REDUCING }

// ── Savings interest ─────────────────────────────────────────────────────────
// Admin-configurable per cooperative (cooperatives.savings_interest_rate/method).
object SavingsInterestMethod {
    // Live annualized projection shown to members — no money moves, purely informational.
    const val SIMPLE_ESTIMATE = "simple_estimate"
    // An admin manually triggers crediting accrued interest as a real
    // contributions transaction — there's no automatic scheduler; re-running
    // this only credits the period since savings_interest_last_credited_at.
    const val PERIODIC_CREDIT = "periodic_credit"
}

// loan_plans only defines a name + amount bounds — there is no interest_rate,
// admin_fee_rate, or duration column anywhere in the schema. The calculator's
// rate/admin-fee/duration bounds are applied as fixed platform-wide constants
// (see LoansScreen) rather than sourced per-plan.
@Serializable
data class LoanPlan(
    val id: String,
    val name: String?              = null,
    @SerialName("cooperative_id")  val cooperativeId: String? = null,
    @SerialName("min_amount")      val minAmount: Double? = null,
    @SerialName("max_amount")      val maxAmount: Double? = null,
    val active: Boolean             = true,
)

// outstanding_balance is the current balance and shrinks as repayments come
// in — amount_requested (added later as a NOT NULL column, see LoansViewModel
// insert) is the original, unchanging loan size and is what any historical/
// as-disbursed figure should use instead.
@Serializable
data class Loan(
    val id: String,
    @SerialName("member_id")           val memberId: String,
    @SerialName("plan_id")             val planId: String?    = null,
    @SerialName("outstanding_balance") val outstandingBalance: Double = 0.0,
    @SerialName("amount_requested")    val amountRequested: Double? = null,
    @SerialName("interest_rate")       val interestRate: Double? = null,
    val status: String                 = "pending",
    @SerialName("disbursed_at")        val disbursedAt: String? = null,
    @SerialName("approved_at")         val approvedAt: String?  = null,
    @SerialName("cooperative_id")      val cooperativeId: String? = null,
    @SerialName("created_at")          val createdAt: String?   = null,
    @SerialName("due_date")            val dueDate: String?     = null,
    // Real columns (confirmed via schema probing).
    @SerialName("duration_months")     val durationMonths: Int?    = null,
    @SerialName("monthly_payment")     val monthlyPayment: Double? = null,
    @SerialName("admin_fee")           val adminFee: Double?       = null,
    @SerialName("rejection_reason")    val rejectionReason: String? = null,
    @SerialName("approved_by")         val approvedBy: String?     = null,
    // Interest portion of outstanding_balance at application time, frozen —
    // outstanding_balance shrinks with repayments so this is the only place
    // Accounting can read real interest income from later.
    @SerialName("total_interest")      val totalInterest: Double?  = null,
    // Set by AdminSettingsViewModel.applyLateFeesNow() to prevent double-charging
    // the same loan if the admin runs the fine sweep twice close together.
    @SerialName("last_fine_applied_at") val lastFineAppliedAt: String? = null,
)

// ── Transaction ───────────────────────────────────────────────────────────────

@Serializable
data class Transaction(
    val id: String,
    @SerialName("member_id")     val memberId: String?   = null,
    val type: String                = "unknown",
    val amount: Double               = 0.0,
    val description: String?     = null,
    val reference: String?       = null,
    @SerialName("cooperative_id") val cooperativeId: String? = null,
    @SerialName("loan_id")       val loanId: String?     = null,
    @SerialName("created_at")    val createdAt: String    = "",
)

// ── Contribution ──────────────────────────────────────────────────────────────

@Serializable
data class Contribution(
    val id: String,
    @SerialName("member_id")      val memberId: String,
    val amount: Double,
    val status: String             = "pending",
    val reference: String?         = null,
    @SerialName("cooperative_id")  val cooperativeId: String? = null,
    @SerialName("created_at")      val createdAt: String,
)

/** Read-only view of a cooperative's own receiving bank account — shown to
 * members who want to pay a contribution/repayment via bank transfer. Not to
 * be confused with `payment_bank_details`, which is CoopLink's own platform
 * account for cooperative subscription billing and must stay admin-only. */
@Serializable
data class CooperativeBankAccount(
    @SerialName("bank_name")      val bankName: String?      = null,
    @SerialName("account_number") val accountNumber: String? = null,
    @SerialName("account_name")   val accountName: String?   = null,
)

// ── Cooperative ───────────────────────────────────────────────────────────────

@Serializable
data class Cooperative(
    val id: String,
    val name: String?                = null,
    val slug: String?                = null,
    @SerialName("whatsapp_number")   val whatsappNumber: String? = null,
    val currency: String             = "NGN",
    @SerialName("logo_url")          val logoUrl: String?        = null,
    val address: String?             = null,
    val phone: String?               = null,
    val email: String?               = null,
    @SerialName("primary_color")     val primaryColor: String?   = null,
    @SerialName("secondary_color")   val secondaryColor: String? = null,
    @SerialName("organization_type") val organizationType: String? = null,
    @SerialName("savings_interest_rate")   val savingsInterestRate: Double? = null,
    @SerialName("savings_interest_method") val savingsInterestMethod: String? = null,
    @SerialName("savings_interest_last_credited_at") val savingsInterestLastCreditedAt: String? = null,
    @SerialName("late_fee_rate")       val lateFeeRate: Double?  = null,
    @SerialName("late_fee_grace_days") val lateFeeGraceDays: Int? = null,
) {
    val orgType: OrganizationType get() = OrganizationType.fromOrDefault(organizationType)

    val isMicrofinance: Boolean get() = organizationType == "microfinance"

    // Org types with no loans/repayments/disbursements module on the web
    // platform — mirrored here so the same modules are hidden in the app.
    val hasLoansModule: Boolean get() = organizationType !in listOf(
        "investment_club", "thrift_ajo", "savings_group",
    )
}
