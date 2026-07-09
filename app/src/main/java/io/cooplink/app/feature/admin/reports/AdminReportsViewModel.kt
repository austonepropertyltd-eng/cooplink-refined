package io.cooplink.app.feature.admin.reports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Contribution
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.core.util.PremiumExportManager
import io.cooplink.app.core.util.retrying
import io.cooplink.app.feature.member.overview.toNaira
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AdminReportsVM"
private const val GENERIC_LOAD_ERROR = "Could not load reports. Pull down to retry."
private val DEFAULTED_STATUSES = setOf("defaulted", "overdue")
private val ACTIVE_LOAN_STATUSES = setOf("disbursed", "repaying")

data class ReportRow(
    val title: String,
    val summary: String,
    val csvHeader: List<String>,
    val csvRows: List<List<String>>,
)

enum class AgingBucket(val label: String) {
    CURRENT("Current"),
    DAYS_30("1-30 Days"),
    DAYS_60("31-60 Days"),
    DAYS_90("61-90 Days"),
    DAYS_90_PLUS("90+ Days (Default Risk)"),
}

data class AgingLoan(
    val memberName: String,
    val memberId: String,
    val outstandingBalance: Double,
    val daysOverdue: Int,
    val agingBucket: AgingBucket,
)

data class AdminReportsUiState(
    val isLoading: Boolean = true,
    val rows: List<ReportRow> = emptyList(),
    val error: String? = null,
    val cooperativeName: String? = null,
    val totalMembers: Int = 0,
    val activeLoans: Int = 0,
    val totalDisbursed: Double = 0.0,
    val agingLoans: List<AgingLoan> = emptyList(),
)

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
    private val cooperativeRepository: CooperativeRepository,
    private val memberRepository: MemberRepository,
    val inactivityManager: InactivityManager,
    val exportManager: PremiumExportManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReportsUiState())
    val state: StateFlow<AdminReportsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = retrying {
                    val coopId = authRepository.currentCooperativeId()
                        ?: throw IllegalStateException("Could not determine your cooperative")
                    val cooperativeName = cooperativeRepository.fetchCooperative(coopId)?.name

                    val members = supabase.db["members"]
                        .select { filter { eq("cooperative_id", coopId) } }
                        .decodeList<io.cooplink.app.core.domain.Member>()
                    val memberIds = members.map { it.id }

                    val loans = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["loans"]
                            .select { filter { isIn("member_id", memberIds) } }
                            .decodeList<Loan>()
                    }.getOrDefault(emptyList())

                    val contributions = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["contributions"]
                            .select { filter { isIn("member_id", memberIds) } }
                            .decodeList<Contribution>()
                    }.getOrDefault(emptyList())

                    val transactions = if (memberIds.isEmpty()) emptyList() else runCatching {
                        supabase.db["transactions"]
                            .select { filter { isIn("member_id", memberIds) } }
                            .decodeList<Transaction>()
                    }.getOrDefault(emptyList())

                    val defaulted = loans.filter { it.status.lowercase() in DEFAULTED_STATUSES }
                    val repayments = transactions.filter { it.type.contains("repay", ignoreCase = true) }
                    val totalRepayments = repayments.sumOf { it.amount }

                    // members.id (a raw UUID foreign key) is never the right
                    // thing to show in a report — every CSV row below looks
                    // this up to show the real member_number ("MEM-747074")
                    // instead, falling back to the UUID only if that member
                    // has none set.
                    val memberNames = memberRepository.fetchMembersForCooperative(coopId).associateBy { it.id }
                    fun displayIdFor(memberId: String?) = memberId?.let { memberNames[it]?.displayId } ?: memberId ?: ""

                    val rows = listOf(
                        ReportRow(
                            title      = "Loan Portfolio",
                            summary    = "${loans.size} loans · ${loans.sumOf { it.outstandingBalance }.toNaira()} outstanding",
                            csvHeader  = listOf("Member ID", "Outstanding", "Status", "Disbursed At"),
                            csvRows    = loans.map {
                                listOf(displayIdFor(it.memberId), "${it.outstandingBalance}", it.status, it.disbursedAt ?: "")
                            },
                        ),
                        ReportRow(
                            title      = "Default & Arrears",
                            summary    = "${defaulted.size} loans · ${defaulted.sumOf { it.outstandingBalance }.toNaira()} outstanding",
                            csvHeader  = listOf("Member ID", "Outstanding", "Status"),
                            csvRows    = defaulted.map {
                                listOf(displayIdFor(it.memberId), "${it.outstandingBalance}", it.status)
                            },
                        ),
                        ReportRow(
                            title      = "Savings Summary",
                            summary    = "${contributions.size} contributions · ${contributions.sumOf { it.amount }.toNaira()} total",
                            csvHeader  = listOf("Member ID", "Amount", "Status", "Created At"),
                            csvRows    = contributions.map {
                                listOf(displayIdFor(it.memberId), "${it.amount}", it.status, it.createdAt)
                            },
                        ),
                        ReportRow(
                            title      = "Collection Report",
                            summary    = "${totalRepayments.toNaira()} collected",
                            csvHeader  = listOf("Member ID", "Amount", "Type", "Created At"),
                            csvRows    = repayments.map {
                                listOf(displayIdFor(it.memberId), "${it.amount}", it.type, it.createdAt)
                            },
                        ),
                        ReportRow(
                            title      = "Audit Trail",
                            summary    = "${transactions.size} recorded transactions",
                            csvHeader  = listOf("Member ID", "Type", "Amount", "Created At"),
                            csvRows    = transactions.map {
                                listOf(displayIdFor(it.memberId), it.type, "${it.amount}", it.createdAt)
                            },
                        ),
                        ReportRow(
                            title      = "Member Activity",
                            summary    = "${members.count { it.status.equals("active", true) }} active · " +
                                "${members.count { !it.status.equals("active", true) }} inactive of ${members.size} total",
                            csvHeader  = listOf("Member ID", "Status"),
                            csvRows    = members.map { listOf(memberNames[it.id]?.displayId ?: it.id, it.status) },
                        ),
                    )

                    // loans has no amount/duration column, but due_date is
                    // real — days overdue is computed from that against the
                    // outstanding balance, rather than a fabricated schedule.
                    val today = java.util.Calendar.getInstance()
                    val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val agingLoans = loans
                        .filter { it.status.lowercase() in ACTIVE_LOAN_STATUSES && !it.dueDate.isNullOrBlank() }
                        .map { loan ->
                            val daysOverdue = runCatching {
                                val due = dateFmt.parse(loan.dueDate!!.take(10))
                                val diffMs = today.timeInMillis - (due?.time ?: today.timeInMillis)
                                (diffMs / (1000 * 60 * 60 * 24)).toInt()
                            }.getOrDefault(0)
                            val bucket = when {
                                daysOverdue <= 0  -> AgingBucket.CURRENT
                                daysOverdue <= 30 -> AgingBucket.DAYS_30
                                daysOverdue <= 60 -> AgingBucket.DAYS_60
                                daysOverdue <= 90 -> AgingBucket.DAYS_90
                                else               -> AgingBucket.DAYS_90_PLUS
                            }
                            AgingLoan(
                                memberName         = memberNames[loan.memberId]?.fullName ?: "Unnamed member",
                                memberId           = memberNames[loan.memberId]?.displayId ?: loan.memberId,
                                outstandingBalance = loan.outstandingBalance,
                                daysOverdue        = daysOverdue.coerceAtLeast(0),
                                agingBucket        = bucket,
                            )
                        }

                    AdminReportsUiState(
                        isLoading       = false, rows = rows, cooperativeName = cooperativeName,
                        totalMembers    = members.size,
                        activeLoans     = loans.count { it.status.lowercase() in ACTIVE_LOAN_STATUSES },
                        totalDisbursed  = loans.sumOf { it.outstandingBalance },
                        agingLoans      = agingLoans,
                    )
                }
                _state.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load reports", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
