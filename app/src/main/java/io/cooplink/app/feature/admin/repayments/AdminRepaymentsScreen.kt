package io.cooplink.app.feature.admin.repayments

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
import io.cooplink.app.core.domain.Loan
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminRepaymentsScreen(viewModel: AdminRepaymentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val currency = currentCurrency()
    val context = LocalContext.current
    var showRecordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Repayments", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { showRecordDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Record")
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

            if (state.repayments.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No repayments recorded yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f))
                        }
                    }
                }
            } else {
                items(state.repayments) { row ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.memberName ?: "Unnamed member", fontWeight = FontWeight.Medium)
                                Text(row.transaction.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                                row.loanBalance?.let {
                                    Text("Balance remaining: ${currency.format(it)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(currency.format(row.transaction.amount), fontWeight = FontWeight.SemiBold, color = CoopGold)
                                Spacer(Modifier.height(4.dp))
                                Surface(color = CoopGold.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                    Text("Completed", color = CoopGold, style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showRecordDialog) {
        RecordRepaymentDialog(
            state = state,
            onDismiss = { showRecordDialog = false; viewModel.clearSubmitError() },
            onSubmit  = { memberId, loan, amount, date, method -> viewModel.recordRepayment(memberId, loan, amount, date, method) },
            onSaved   = { showRecordDialog = false },
        )
    }
}

private val PAYMENT_METHODS = listOf("Cash", "Transfer", "Paystack")

@Composable
private fun RecordRepaymentDialog(
    state: AdminRepaymentsUiState,
    onDismiss: () -> Unit,
    onSubmit: (memberId: String, loan: Loan, amount: Double, date: String, method: String) -> Unit,
    onSaved: () -> Unit,
) {
    val currency = currentCurrency()
    var selectedMemberId by remember { mutableStateOf(state.members.firstOrNull()?.id) }
    var memberMenuExpanded by remember { mutableStateOf(false) }
    var selectedLoan by remember { mutableStateOf<Loan?>(null) }
    var loanMenuExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var paymentDate by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PAYMENT_METHODS.first()) }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    var hasSubmitted by remember { mutableStateOf(false) }
    val parsed = amount.toDoubleOrNull()
    val justSubmitted = !state.isSubmitting && state.submitError == null
    val memberLoans = state.loansByMember[selectedMemberId] ?: emptyList()

    LaunchedEffect(selectedMemberId) { selectedLoan = memberLoans.firstOrNull() }
    LaunchedEffect(justSubmitted) { if (hasSubmitted && justSubmitted) onSaved() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Repayment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.submitError?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                ExposedDropdownMenuBox(expanded = memberMenuExpanded, onExpandedChange = { memberMenuExpanded = it }) {
                    OutlinedTextField(
                        value = state.members.firstOrNull { it.id == selectedMemberId }?.fullName ?: "Select a member",
                        onValueChange = {}, readOnly = true, label = { Text("Member") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = memberMenuExpanded, onDismissRequest = { memberMenuExpanded = false }) {
                        state.members.forEach { m ->
                            DropdownMenuItem(text = { Text(m.fullName ?: "Unnamed member") },
                                onClick = { selectedMemberId = m.id; memberMenuExpanded = false })
                        }
                    }
                }

                if (memberLoans.isEmpty()) {
                    Text("This member has no loans on file.", style = MaterialTheme.typography.bodySmall)
                } else {
                    ExposedDropdownMenuBox(expanded = loanMenuExpanded, onExpandedChange = { loanMenuExpanded = it }) {
                        OutlinedTextField(
                            value = selectedLoan?.let { "${currency.format(it.outstandingBalance)} outstanding" } ?: "Select a loan",
                            onValueChange = {}, readOnly = true, label = { Text("Loan") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loanMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = loanMenuExpanded, onDismissRequest = { loanMenuExpanded = false }) {
                            memberLoans.forEach { loan ->
                                DropdownMenuItem(text = { Text("${currency.format(loan.outstandingBalance)} · ${loan.status}") },
                                    onClick = { selectedLoan = loan; loanMenuExpanded = false })
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₦)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = paymentDate, onValueChange = { paymentDate = it },
                    label = { Text("Payment Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = methodMenuExpanded, onExpandedChange = { methodMenuExpanded = it }) {
                    OutlinedTextField(
                        value = paymentMethod, onValueChange = {}, readOnly = true, label = { Text("Payment Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = methodMenuExpanded, onDismissRequest = { methodMenuExpanded = false }) {
                        PAYMENT_METHODS.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { paymentMethod = m; methodMenuExpanded = false }) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasSubmitted = true
                    selectedMemberId?.let { mid -> selectedLoan?.let { loan -> parsed?.let { onSubmit(mid, loan, it, paymentDate, paymentMethod) } } }
                },
                enabled = selectedMemberId != null && selectedLoan != null && parsed != null && parsed > 0 && paymentDate.isNotBlank() && !state.isSubmitting,
                colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
            ) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
