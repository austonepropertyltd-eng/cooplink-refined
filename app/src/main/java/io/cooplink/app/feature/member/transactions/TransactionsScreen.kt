package io.cooplink.app.feature.member.transactions

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.feature.member.overview.TransactionRow
import io.cooplink.app.feature.member.overview.toNaira
import io.cooplink.app.ui.theme.*

private val FREQUENCIES = listOf("Weekly", "Monthly", "Quarterly")
private val DISPUTE_REASONS = listOf("Wrong amount", "Unauthorized", "Not received", "Other")

@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showNewOrderDialog by remember { mutableStateOf(false) }
    var showRaiseDisputeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSnackbar()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Transaction History", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
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

            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Standing Orders", style = MaterialTheme.typography.titleMedium,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { showNewOrderDialog = true }) { Text("+ New", color = MemberGold) }
                        }
                        if (state.standingOrders.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("No standing orders yet", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.45f))
                        } else {
                            Spacer(Modifier.height(8.dp))
                            state.standingOrders.forEach { order ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(order.purpose ?: "Standing Order", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                        Text("${order.frequency ?: "—"} · from ${order.start_date ?: "—"}",
                                            color = Color.White.copy(.5f), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(order.amount.toNaira(), color = MemberGold, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Disputes", style = MaterialTheme.typography.titleMedium,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { showRaiseDisputeDialog = true }) { Text("Raise dispute", color = MemberGold) }
                        }
                        if (state.disputes.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("No disputes raised", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.45f))
                        } else {
                            Spacer(Modifier.height(8.dp))
                            state.disputes.forEach { d ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.category ?: "Dispute", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                        Text(d.description ?: "", color = Color.White.copy(.5f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                    Text(d.status.replaceFirstChar { it.uppercase() }, color = MemberGold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            if (state.transactions.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                        shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, null, tint = CoopTeal, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No transactions yet", color = Color.White.copy(.45f))
                            }
                        }
                    }
                }
            } else {
                items(state.transactions) { tx -> TransactionRow(tx) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showNewOrderDialog) {
        NewStandingOrderDialog(
            isSubmitting = state.isSubmittingOrder,
            error        = state.orderError,
            onDismiss    = { showNewOrderDialog = false; viewModel.clearOrderError() },
            onSubmit     = { amount, freq, date, purpose -> viewModel.createStandingOrder(amount, freq, date, purpose) },
            justSubmitted = !state.isSubmittingOrder && state.orderError == null,
            onSubmitted  = { showNewOrderDialog = false },
        )
    }

    if (showRaiseDisputeDialog) {
        RaiseDisputeDialog(
            isSubmitting = state.isSubmittingDispute,
            error        = state.disputeError,
            onDismiss    = { showRaiseDisputeDialog = false; viewModel.clearDisputeError() },
            onSubmit     = { category, description -> viewModel.raiseDispute(category, description) },
            justSubmitted = !state.isSubmittingDispute && state.disputeError == null,
            onSubmitted  = { showRaiseDisputeDialog = false },
        )
    }
}

@Composable
private fun NewStandingOrderDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (amount: Double, frequency: String, startDate: String, purpose: String) -> Unit,
    justSubmitted: Boolean,
    onSubmitted: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(FREQUENCIES.first()) }
    var freqMenuExpanded by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }
    val parsed = amount.toDoubleOrNull()

    LaunchedEffect(justSubmitted) { if (hasSubmitted && justSubmitted) onSubmitted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CoopDarkSurface,
        title = { Text("New Standing Order", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors(),
                )

                ExposedDropdownMenuBox(expanded = freqMenuExpanded, onExpandedChange = { freqMenuExpanded = it }) {
                    OutlinedTextField(
                        value = frequency, onValueChange = {}, readOnly = true, label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), colors = dialogFieldColors(),
                    )
                    ExposedDropdownMenu(expanded = freqMenuExpanded, onDismissRequest = { freqMenuExpanded = false }) {
                        FREQUENCIES.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { frequency = f; freqMenuExpanded = false }) }
                    }
                }

                OutlinedTextField(
                    value = startDate, onValueChange = { startDate = it },
                    label = { Text("Start Date (YYYY-MM-DD)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors(),
                )
                OutlinedTextField(
                    value = purpose, onValueChange = { purpose = it },
                    label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; parsed?.let { onSubmit(it, frequency, startDate, purpose) } },
                enabled = parsed != null && parsed > 0 && startDate.isNotBlank() && !isSubmitting,
                colors  = ButtonDefaults.buttonColors(containerColor = MemberGold),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CoopNavyDeep)
                else Text("Create", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(.5f)) } },
    )
}

@Composable
private fun RaiseDisputeDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (category: String, description: String) -> Unit,
    justSubmitted: Boolean,
    onSubmitted: () -> Unit,
) {
    var category by remember { mutableStateOf(DISPUTE_REASONS.first()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(justSubmitted) { if (hasSubmitted && justSubmitted) onSubmitted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CoopDarkSurface,
        title = { Text("Raise a Dispute", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true, label = { Text("Reason") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), colors = dialogFieldColors(),
                    )
                    ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DISPUTE_REASONS.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { category = r; menuExpanded = false }) }
                    }
                }

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Describe what happened (include transaction reference or date if known)") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(), colors = dialogFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; onSubmit(category, description) },
                enabled = description.isNotBlank() && !isSubmitting,
                colors  = ButtonDefaults.buttonColors(containerColor = MemberGold),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CoopNavyDeep)
                else Text("Submit", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(.5f)) } },
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MemberGold,   unfocusedBorderColor  = Color.White.copy(.22f),
    focusedLabelColor    = MemberGold,   unfocusedLabelColor   = Color.White.copy(.4f),
    focusedTextColor     = Color.White,  unfocusedTextColor    = Color.White,
    cursorColor          = MemberGold,
)
