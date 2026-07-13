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

data class LoanCalculation(
    val monthlyPayment: Double,
    val totalInterest: Double,
    val adminFee: Double,
    val amountDisbursed: Double,
    val totalRepayment: Double,
    val interestType: InterestType,
)

// loans has no amount/principal column anywhere in the schema (confirmed via
// schema probing) — outstanding_balance is the only loan-size figure that
// exists, so it doubles as "the current balance" throughout the app; there
// is no original-principal value to compute a repayment percentage against.
@Serializable
data class Loan(
    val id: String,
    @SerialName("member_id")           val memberId: String,
    @SerialName("plan_id")             val planId: String?    = null,
    @SerialName("outstanding_balance") val outstandingBalance: Double = 0.0,
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
) {
    val orgType: OrganizationType get() = OrganizationType.fromOrDefault(organizationType)

    val isMicrofinance: Boolean get() = organizationType == "microfinance"

    // Org types with no loans/repayments/disbursements module on the web
    // platform — mirrored here so the same modules are hidden in the app.
    val hasLoansModule: Boolean get() = organizationType !in listOf(
        "investment_club", "thrift_ajo", "savings_group",
    )
}
