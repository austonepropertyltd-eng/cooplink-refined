package io.cooplink.app.feature.admin.loans

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.LoanPlan
import io.cooplink.app.core.domain.statusColor
import io.cooplink.app.core.domain.statusLabel
import io.cooplink.app.feature.member.overview.toNaira
import io.cooplink.app.feature.member.security.PinEntryDialog
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopSuccess
import io.cooplink.app.ui.theme.CoopTeal
import kotlinx.coroutines.launch

@Composable
fun AdminLoansScreen(viewModel: AdminLoansViewModel = hiltViewModel()) {
    var tab by remember { mutableStateOf(0) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showCreatePlan by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<LoanPlan?>(null) }
    var pendingDisburseLoanId by remember { mutableStateOf<String?>(null) }
    var pendingVerifyLoanId by remember { mutableStateOf<String?>(null) }
    var showConfetti by remember { mutableStateOf(false) }
    var rejectingLoanId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            if (it == "Loan disbursed") showConfetti = true
            viewModel.clearSnackbar()
        }
    }

    val tabLabels = listOf("Applications", "Disbursements", "Active", "Completed", "Defaulted", "Packages")

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Loans & Finance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

            item {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    tabLabels.forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = CoopError)
                            Spacer(Modifier.width(8.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            state.actionError?.let { err ->
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = CoopError)
                            Spacer(Modifier.width(8.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            when (tab) {
                0 -> loanListOrEmpty(this, state.applications, state.isLoading, "pending applications") { row ->
                    ApplicationRow(row, state.processingLoanId, onApprove = viewModel::approve, onReject = { rejectingLoanId = row.loan.id })
                }
                1 -> loanListOrEmpty(this, state.disbursements, state.isLoading, "loans waiting disbursement") { row ->
                    DisbursementRow(row, state.processingLoanId, onDisburse = { loanId -> pendingVerifyLoanId = loanId })
                }
                2 -> loanListOrEmpty(this, state.active, state.isLoading, "active loans") { row -> AdminLoanRowView(row) }
                3 -> loanListOrEmpty(this, state.completed, state.isLoading, "completed loans") { row -> AdminLoanRowView(row) }
                4 -> loanListOrEmpty(this, state.defaulted, state.isLoading, "defaulted loans") { row -> AdminLoanRowView(row) }
                5 -> {
                    item {
                        Button(onClick = { showCreatePlan = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("New Package")
                        }
                    }
                    if (state.plans.isEmpty() && !state.isLoading) {
                        item { Text("No loan packages yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f)) }
                    } else {
                        items(state.plans) { plan ->
                            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                                ListItem(
                                    headlineContent = { Text(plan.name ?: "Unnamed package") },
                                    supportingContent = {
                                        Text("${(plan.minAmount ?: 0.0).toNaira()} – ${(plan.maxAmount ?: 0.0).toNaira()}${if (!plan.active) " · Inactive" else ""}")
                                    },
                                    trailingContent = {
                                        Row {
                                            IconButton(onClick = { editingPlan = plan }) { Icon(Icons.Default.Edit, "Edit") }
                                            IconButton(onClick = { viewModel.deletePlan(plan.id) }) { Icon(Icons.Default.Delete, "Delete", tint = CoopError) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCreatePlan) {
        PlanDialog(
            title = "New Loan Package", plan = null,
            isSaving = state.isSavingPlan, error = state.planError,
            onDismiss = { showCreatePlan = false; viewModel.clearPlanError() },
            onSave = { name, min, max, _ -> viewModel.createPlan(name, min, max) },
            justSaved = !state.isSavingPlan && state.planError == null,
            onSaved = { showCreatePlan = false },
        )
    }
    editingPlan?.let { plan ->
        PlanDialog(
            title = "Edit Loan Package", plan = plan,
            isSaving = state.isSavingPlan, error = state.planError,
            onDismiss = { editingPlan = null; viewModel.clearPlanError() },
            onSave = { name, min, max, active -> viewModel.editPlan(plan.id, name, min, max, active) },
            justSaved = !state.isSavingPlan && state.planError == null,
            onSaved = { editingPlan = null },
        )
    }

    pendingVerifyLoanId?.let { loanId ->
        BankVerificationDialog(
            bankVerificationService = viewModel.bankVerificationService,
            onDismiss  = { pendingVerifyLoanId = null },
            onContinue = { pendingVerifyLoanId = null; pendingDisburseLoanId = loanId },
        )
    }

    pendingDisburseLoanId?.let { loanId ->
        PinEntryDialog(
            title     = "Confirm Disbursement",
            onSuccess = { pendingDisburseLoanId = null; viewModel.disburse(loanId) },
            onDismiss = { pendingDisburseLoanId = null },
        )
    }

    io.cooplink.app.ui.theme.ConfettiOverlay(visible = showConfetti, onComplete = { showConfetti = false })

    rejectingLoanId?.let { loanId ->
        RejectReasonDialog(
            isSubmitting = state.processingLoanId == loanId,
            onDismiss = { rejectingLoanId = null },
            onConfirm = { reason -> viewModel.reject(loanId, reason); rejectingLoanId = null },
        )
    }
}

@Composable
private fun RejectReasonDialog(isSubmitting: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Application") },
        text = {
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                label = { Text("Reason") },
                placeholder = { Text("e.g. Insufficient savings history") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CoopError),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Reject")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun loanListOrEmpty(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    rows: List<AdminLoanRow>,
    isLoading: Boolean,
    emptyLabel: String,
    content: @Composable (AdminLoanRow) -> Unit,
) {
    if (rows.isEmpty() && !isLoading) {
        scope.item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CreditScore, null, tint = CoopGold, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No $emptyLabel", color = MaterialTheme.colorScheme.onSurface.copy(.45f))
                    }
                }
            }
        }
    } else {
        scope.items(rows) { row -> content(row) }
    }
}

@Composable
private fun ApplicationRow(row: AdminLoanRow, processingLoanId: String?, onApprove: (String) -> Unit, onReject: () -> Unit) {
    val isProcessing = processingLoanId == row.loan.id
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(row.memberName ?: "Unnamed member", fontWeight = FontWeight.SemiBold)
            Text(row.loan.outstandingBalance.toNaira(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            row.loan.createdAt?.let {
                Text("Applied ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApprove(row.loan.id) }, enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = CoopSuccess)) {
                    if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Approve")
                }
                OutlinedButton(onClick = onReject, enabled = !isProcessing) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun DisbursementRow(row: AdminLoanRow, processingLoanId: String?, onDisburse: (String) -> Unit) {
    val isProcessing = processingLoanId == row.loan.id
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(row.memberName ?: "Unnamed member", fontWeight = FontWeight.SemiBold)
            Text(row.loan.outstandingBalance.toNaira(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onDisburse(row.loan.id) }, enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Disburse")
            }
        }
    }
}

@Composable
private fun AdminLoanRowView(row: AdminLoanRow) {
    val loan = row.loan
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.memberName ?: "Unnamed member", fontWeight = FontWeight.Medium)
                Surface(color = loan.statusColor().copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        loan.statusLabel(),
                        color = loan.statusColor(), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(loan.outstandingBalance.toNaira(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            loan.interestRate?.let { rate ->
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Interest Rate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Text("%.1f%%".format(rate), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PlanDialog(
    title: String,
    plan: LoanPlan?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, min: Double, max: Double, active: Boolean) -> Unit,
    justSaved: Boolean,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf(plan?.name ?: "") }
    var minAmount by remember { mutableStateOf(plan?.minAmount?.toString() ?: "") }
    var maxAmount by remember { mutableStateOf(plan?.maxAmount?.toString() ?: "") }
    var active by remember { mutableStateOf(plan?.active ?: true) }
    var hasSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(justSaved) { if (hasSubmitted && justSaved) onSaved() }

    val min = minAmount.toDoubleOrNull()
    val max = maxAmount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Package Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = minAmount, onValueChange = { minAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Min Amount (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = maxAmount, onValueChange = { maxAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Max Amount (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (plan != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = active, onCheckedChange = { active = it })
                        Text("Active")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; min?.let { mn -> max?.let { mx -> onSave(name, mn, mx, active) } } },
                enabled = name.isNotBlank() && min != null && max != null && !isSaving,
                colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BankVerificationDialog(
    bankVerificationService: io.cooplink.app.core.payment.BankVerificationService,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedBank by remember { mutableStateOf(io.cooplink.app.core.payment.NIGERIA_BANKS.first()) }
    var bankMenuExpanded by remember { mutableStateOf(false) }
    var accountNumber by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verifiedAccount by remember { mutableStateOf<io.cooplink.app.core.payment.BankAccountInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify Bank Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = bankMenuExpanded, onExpandedChange = { bankMenuExpanded = it }) {
                    OutlinedTextField(
                        value = selectedBank.name, onValueChange = {}, readOnly = true,
                        label = { Text("Bank") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bankMenuExpanded) },
                    )
                    ExposedDropdownMenu(expanded = bankMenuExpanded, onDismissRequest = { bankMenuExpanded = false }) {
                        io.cooplink.app.core.payment.NIGERIA_BANKS.forEach { bank ->
                            DropdownMenuItem(text = { Text(bank.name) }, onClick = {
                                selectedBank = bank; bankMenuExpanded = false; verifiedAccount = null; error = null
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it; verifiedAccount = null; error = null },
                    label = { Text("Account Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                verifiedAccount?.let { account ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CoopSuccess.copy(alpha = 0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CoopSuccess.copy(alpha = 0.4f)),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, null, tint = CoopSuccess)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Account Verified ✓", style = MaterialTheme.typography.titleSmall,
                                    color = CoopSuccess, fontWeight = FontWeight.Bold)
                                Text(account.accountName, style = MaterialTheme.typography.bodyMedium)
                                Text("${selectedBank.name} — ${account.accountNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (verifiedAccount != null) {
                Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = CoopSuccess)) {
                    Text("Continue")
                }
            } else {
                Button(
                    onClick = {
                        isVerifying = true
                        error = null
                        scope.launch {
                            bankVerificationService.verifyAccount(accountNumber, selectedBank.code)
                                .onSuccess { verifiedAccount = it }
                                .onFailure { error = it.message ?: "Could not verify this account" }
                            isVerifying = false
                        }
                    },
                    enabled = accountNumber.length >= 10 && !isVerifying,
                    colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
                ) {
                    if (isVerifying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Verify")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
