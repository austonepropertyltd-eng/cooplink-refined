package io.cooplink.app.core.domain

import androidx.compose.ui.graphics.Color

// Real enum values on `loans.status` (confirmed via schema probing against
// the loan_status_values field returned by get_cooperative_analytics).
object LoanStatus {
    const val APPLIED      = "applied"
    const val UNDER_REVIEW = "under_review"
    const val APPROVED     = "approved"
    const val REJECTED     = "rejected"
    const val DISBURSED    = "disbursed"
    const val REPAYING     = "repaying"
    const val COMPLETED    = "completed"
    const val DEFAULTED    = "defaulted"
    const val WITHDRAWN    = "withdrawn"

    val ACTIVE_STATUSES = listOf(DISBURSED, REPAYING)
    val PENDING_STATUSES = listOf(APPLIED, UNDER_REVIEW)
    val CLOSED_STATUSES = listOf(COMPLETED, REJECTED, WITHDRAWN)
    val ALL_ADMIN_VISIBLE = listOf(
        APPLIED, UNDER_REVIEW, APPROVED, DISBURSED, REPAYING,
        COMPLETED, DEFAULTED, REJECTED, WITHDRAWN,
    )
}

fun Loan.isActive(): Boolean = status in LoanStatus.ACTIVE_STATUSES
fun Loan.isPending(): Boolean = status in LoanStatus.PENDING_STATUSES
fun Loan.isClosed(): Boolean = status in LoanStatus.CLOSED_STATUSES
fun Loan.canRepay(): Boolean = status == LoanStatus.REPAYING || status == LoanStatus.DISBURSED

fun Loan.statusColor(): Color = when (status) {
    LoanStatus.APPLIED      -> Color(0xFF718096)
    LoanStatus.UNDER_REVIEW -> Color(0xFFF4B400)
    LoanStatus.APPROVED     -> Color(0xFF3182CE)
    LoanStatus.DISBURSED    -> Color(0xFF1FA67A)
    LoanStatus.REPAYING     -> Color(0xFF0F8B8D)
    LoanStatus.COMPLETED    -> Color(0xFF1FA67A)
    LoanStatus.DEFAULTED    -> Color(0xFFE53E3E)
    LoanStatus.REJECTED     -> Color(0xFFE53E3E)
    LoanStatus.WITHDRAWN    -> Color(0xFF718096)
    else                    -> Color(0xFF718096)
}

fun Loan.statusLabel(): String = when (status) {
    LoanStatus.APPLIED      -> "Applied"
    LoanStatus.UNDER_REVIEW -> "Under Review"
    LoanStatus.APPROVED     -> "Approved"
    LoanStatus.DISBURSED    -> "Disbursed"
    LoanStatus.REPAYING     -> "Repaying"
    LoanStatus.COMPLETED    -> "Completed ✓"
    LoanStatus.DEFAULTED    -> "Defaulted"
    LoanStatus.REJECTED     -> "Rejected"
    LoanStatus.WITHDRAWN    -> "Withdrawn"
    else                    -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
}
