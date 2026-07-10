package io.cooplink.app.feature.admin.contributions

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
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopTeal

private val FILTERS = listOf("All", "Completed", "Pending", "Failed")

@Composable
fun AdminContributionsScreen(viewModel: AdminContributionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
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
                    Text("Contributions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { showRecordDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Record")
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTERS.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick  = { viewModel.onFilterChange(f) },
                            label    = { Text(f) },
                        )
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

            if (state.filtered.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Savings, null, tint = CoopTeal, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No contributions recorded yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f))
                            }
                        }
                    }
                }
            } else {
                items(state.filtered) { row -> ContributionRow(row) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showRecordDialog) {
        RecordAmountDialog(
            title        = "Record Contribution",
            members      = state.members,
            isSubmitting = state.isSubmitting,
            error        = state.submitError,
            onDismiss    = { showRecordDialog = false; viewModel.clearSubmitError() },
            onSubmit     = { memberId, amount -> viewModel.recordContribution(memberId, amount) },
            justSubmitted = !state.isSubmitting && state.submitError == null,
            onSubmitted   = { showRecordDialog = false },
        )
    }
}

@Composable
fun AdminSavingsScreen(viewModel: AdminContributionsViewModel = hiltViewModel()) {
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
                    Text("Savings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { showRecordDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Deposit")
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Total Savings Pool", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Text(currency.format(state.poolTotal), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = CoopGold)
                    }
                }
            }

            if (state.groupedByMember.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No savings recorded yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f))
                        }
                    }
                }
            } else {
                items(state.groupedByMember) { row ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        ListItem(
                            headlineContent = { Text(row.memberName ?: "Unnamed member") },
                            trailingContent = { Text(currency.format(row.total), fontWeight = FontWeight.SemiBold, color = CoopGold) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showRecordDialog) {
        RecordAmountDialog(
            title        = "Record Savings Deposit",
            members      = state.members,
            isSubmitting = state.isSubmitting,
            error        = state.submitError,
            onDismiss    = { showRecordDialog = false; viewModel.clearSubmitError() },
            onSubmit     = { memberId, amount -> viewModel.recordContribution(memberId, amount) },
            justSubmitted = !state.isSubmitting && state.submitError == null,
            onSubmitted   = { showRecordDialog = false },
        )
    }
}

@Composable
private fun ContributionRow(row: AdminContributionRow) {
    val currency = currentCurrency()
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.memberName ?: "Unnamed member", fontWeight = FontWeight.Medium)
                Text(row.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(.5f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(currency.format(row.amount), fontWeight = FontWeight.SemiBold, color = CoopGold)
                Spacer(Modifier.height(4.dp))
                Surface(color = statusColor(row.status).copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(row.status.replaceFirstChar { it.uppercase() }, color = statusColor(row.status),
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

private fun statusColor(status: String) = when (status.lowercase()) {
    "completed", "success" -> CoopTeal
    "failed", "cancelled"  -> CoopError
    else                    -> CoopGold
}

@Composable
private fun RecordAmountDialog(
    title: String,
    members: List<MemberDetails>,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (memberId: String, amount: Double) -> Unit,
    justSubmitted: Boolean,
    onSubmitted: () -> Unit,
) {
    var selectedMember by remember(members) { mutableStateOf(members.firstOrNull()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }
    val parsed = amount.toDoubleOrNull()

    LaunchedEffect(justSubmitted) { if (hasSubmitted && justSubmitted) onSubmitted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                if (members.isEmpty()) {
                    Text("No members found for your cooperative.", style = MaterialTheme.typography.bodySmall)
                } else {
                    ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
                        OutlinedTextField(
                            value = selectedMember?.fullName ?: "Select a member",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Member") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            members.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.fullName ?: "Unnamed member") },
                                    onClick = { selectedMember = m; menuExpanded = false },
                                )
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
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; selectedMember?.let { m -> parsed?.let { onSubmit(m.id, it) } } },
                enabled = selectedMember != null && parsed != null && parsed > 0 && !isSubmitting,
                colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
