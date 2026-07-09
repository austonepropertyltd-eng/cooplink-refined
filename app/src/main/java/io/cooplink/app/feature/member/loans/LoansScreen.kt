package io.cooplink.app.feature.member.loans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.InterestType
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.LoanPlan
import io.cooplink.app.core.domain.isActive
import io.cooplink.app.core.domain.statusColor
import io.cooplink.app.core.domain.statusLabel
import io.cooplink.app.feature.member.overview.toNaira
import io.cooplink.app.feature.member.security.PinEntryDialog
import io.cooplink.app.ui.theme.*
import kotlin.math.pow

// ── Loan calculator engine ────────────────────────────────────────────────────
// loan_plans only carries a name + amount bounds — there's no interest_rate,
// admin_fee_rate, or duration column in the schema, so those stay fixed
// platform-wide constants rather than per-plan values.
private const val DEFAULT_INTEREST_RATE = 24.0
private const val DEFAULT_ADMIN_FEE_PCT = 1.5

data class LoanCalc(
    val monthly: Double, val interest: Double, val adminFee: Double,
    val disbursed: Double, val total: Double,
)

fun calcLoan(principal: Double, rate: Double, adminPct: Double,
             months: Int, type: InterestType): LoanCalc {
    val mr       = rate / 100.0 / 12.0
    val adminFee = principal * adminPct / 100.0
    val monthly: Double
    val interest: Double
    when (type) {
        InterestType.FLAT -> {
            interest = principal * (rate / 100.0) * (months / 12.0)
            monthly  = (principal + interest) / months
        }
        InterestType.REDUCING -> {
            monthly  = if (mr == 0.0) principal / months
            else principal * mr * (1 + mr).pow(months) / ((1 + mr).pow(months) - 1)
            interest = monthly * months - principal
        }
    }
    return LoanCalc(monthly, interest, adminFee, principal - adminFee, monthly * months)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun LoansScreen(
    autoOpenDialog: Boolean = false,
    viewModel: LoansViewModel = hiltViewModel(),
) {
    var showApply by remember { mutableStateOf(autoOpenDialog) }
    var pendingApplication by remember { mutableStateOf<Triple<String?, Double, Double>?>(null) }
    var showConfetti by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.justSubmitted) {
        if (state.justSubmitted) {
            showApply = false
            showConfetti = true
            snackbarHostState.showSnackbar("Loan application submitted successfully")
            viewModel.acknowledgeSubmitted()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh    = viewModel::refresh,
            modifier     = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("My Loans", style = MaterialTheme.typography.headlineMedium,
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Button(onClick = { showApply = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MemberGreen)) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Apply")
                        }
                    }
                }

                state.error?.let { err ->
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f)),
                            shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = CoopError)
                                Spacer(Modifier.width(8.dp))
                                Text(err, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (state.isLoading && state.loans.isEmpty()) {
                    items(3) { SkeletonCard() }
                } else if (state.loans.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                            shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CreditScore, null, tint = Color.White.copy(.18f),
                                        modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No loans yet. Tap Apply to get started", color = Color.White.copy(.4f))
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { showApply = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MemberGreen)) {
                                        Text("Apply")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(state.loans) { loan -> LoanRow(loan) }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showApply) {
        ApplyDialog(
            plans        = state.plans,
            isSubmitting = state.isSubmitting,
            error        = state.submitError,
            onDismiss    = { showApply = false; viewModel.clearSubmitError() },
            onSubmit     = { planId, amount, rate -> showApply = false; pendingApplication = Triple(planId, amount, rate) },
        )
    }

    pendingApplication?.let { (planId, amount, rate) ->
        PinEntryDialog(
            title     = "Confirm Loan Application",
            onSuccess = { pendingApplication = null; viewModel.submitApplication(planId, amount, rate) },
            onDismiss = { pendingApplication = null },
        )
    }

    io.cooplink.app.ui.theme.ConfettiOverlay(visible = showConfetti, onComplete = { showConfetti = false })
}

@Composable
private fun LoanRow(loan: Loan) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Outstanding Balance", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(.55f))
                Surface(color = loan.statusColor().copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        loan.statusLabel(),
                        color = loan.statusColor(), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(loan.outstandingBalance.toNaira(), style = MaterialTheme.typography.headlineSmall,
                color = Color.White, fontWeight = FontWeight.Bold)
            loan.interestRate?.let { rate ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Interest Rate", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.45f))
                    Text("%.1f%%".format(rate), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
            loan.monthlyPayment?.takeIf { loan.isActive() }?.let { payment ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monthly Payment", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.45f))
                    Text(payment.toNaira(), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
            // loans has no principal/amount column, so a repaid-percentage
            // progress bar can't be computed honestly — due_date is the one
            // real repayment-timing column, shown here instead.
            loan.dueDate?.takeIf { loan.isActive() }?.let { due ->
                Spacer(Modifier.height(8.dp))
                val isOverdue = due.take(10) < todayIsoDate()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isOverdue) "Overdue Since" else "Next Payment Due",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.45f))
                    Text(due.take(10), style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) CoopError else Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun todayIsoDate(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

// ── Apply Dialog with live calculator ────────────────────────────────────────

@Composable
private fun ApplyDialog(
    plans: List<LoanPlan>,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (planId: String?, amount: Double, interestRate: Double) -> Unit,
) {
    var selectedPlan by remember(plans) { mutableStateOf(plans.firstOrNull()) }
    var planMenuExpanded by remember { mutableStateOf(false) }
    var amount   by remember { mutableStateOf("") }
    var months   by remember { mutableStateOf("12") }
    val type     = InterestType.REDUCING

    val calc by remember(amount, months) {
        derivedStateOf {
            val p = amount.toDoubleOrNull() ?: 0.0
            val m = months.toIntOrNull() ?: 12
            if (p > 0 && m > 0) calcLoan(p, DEFAULT_INTEREST_RATE, DEFAULT_ADMIN_FEE_PCT, m, type) else null
        }
    }

    val amountValue = amount.toDoubleOrNull()
    val withinBounds = selectedPlan?.let { plan ->
        (plan.minAmount == null || (amountValue ?: 0.0) >= plan.minAmount) &&
            (plan.maxAmount == null || (amountValue ?: 0.0) <= plan.maxAmount)
    } ?: true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CoopDarkSurface,
        title = { Text("Apply for Loan", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                error?.let {
                    Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall)
                }

                if (plans.isEmpty()) {
                    Text("No loan plans are available for your cooperative yet.",
                        color = Color.White.copy(.6f), style = MaterialTheme.typography.bodySmall)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = planMenuExpanded,
                        onExpandedChange = { planMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedPlan?.name ?: "Select a plan",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Loan Plan") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = dialogFieldColors(),
                        )
                        ExposedDropdownMenu(expanded = planMenuExpanded, onDismissRequest = { planMenuExpanded = false }) {
                            plans.forEach { plan ->
                                DropdownMenuItem(
                                    text = { Text(plan.name ?: "Loan Plan") },
                                    onClick = { selectedPlan = plan; planMenuExpanded = false },
                                )
                            }
                        }
                    }
                }

                val boundsLabel = selectedPlan?.let { plan ->
                    when {
                        plan.minAmount != null && plan.maxAmount != null ->
                            "Amount (${plan.minAmount.toNaira()} – ${plan.maxAmount.toNaira()})"
                        else -> "Amount (₦)"
                    }
                } ?: "Amount (₦)"

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(boundsLabel) },
                    isError = !withinBounds,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors())

                OutlinedTextField(
                    value = months, onValueChange = { months = it.filter { c -> c.isDigit() } },
                    label = { Text("Duration (months)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors())

                calc?.let { c ->
                    HorizontalDivider(color = Color.White.copy(.1f))
                    Text("Repayment Summary", style = MaterialTheme.typography.labelMedium, color = MemberGold)
                    CalcRow("Monthly Payment",  "₦%.2f".format(c.monthly))
                    CalcRow("Total Interest",   "₦%.2f".format(c.interest))
                    CalcRow("Admin Fee (${DEFAULT_ADMIN_FEE_PCT}%)", "₦%.2f".format(c.adminFee))
                    CalcRow("Amount Disbursed", "₦%.2f".format(c.disbursed))
                    HorizontalDivider(color = Color.White.copy(.1f))
                    CalcRow("Total Repayment",  "₦%.2f".format(c.total), highlight = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { amountValue?.let { onSubmit(selectedPlan?.id, it, DEFAULT_INTEREST_RATE) } },
                enabled = calc != null && withinBounds && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MemberGreen)) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Submit Application")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(.5f)) }
        },
    )
}

@Composable
private fun CalcRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = if (highlight) Color.White else Color.White.copy(.55f),
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = if (highlight) MemberGold else Color.White,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MemberGold,   unfocusedBorderColor  = Color.White.copy(.22f),
    focusedLabelColor    = MemberGold,   unfocusedLabelColor   = Color.White.copy(.4f),
    focusedTextColor     = Color.White,  unfocusedTextColor    = Color.White,
    cursorColor          = MemberGold,
)
