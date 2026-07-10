package io.cooplink.app.feature.admin.dividends

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
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
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal

private enum class DividendTab { OVERVIEW, HISTORY }

@Composable
fun AdminDividendsScreen(viewModel: AdminDividendsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val currency = currentCurrency()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(DividendTab.OVERVIEW) }
    var showDeclareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSuccessMessage()
        }
    }
    LaunchedEffect(state.declareError) {
        state.declareError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearDeclareError()
        }
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dividends", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { showDeclareDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopGold)) {
                        Text("Declare Dividend", color = androidx.compose.ui.graphics.Color.Black)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DividendTab.OVERVIEW to "Overview", DividendTab.HISTORY to "History").forEach { (t, label) ->
                        FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(label) })
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

            if (tab == DividendTab.OVERVIEW) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Total Distributed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(currency.format(state.totalDistributedAllTime), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = CoopGreen)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Last Distribution", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    Text(state.lastDistributionDate ?: "Never", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Column {
                                    Text("Eligible Members", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    Text("${state.eligibleMemberCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            } else {
                if (state.history.isEmpty() && !state.isLoading) {
                    item {
                        Text("No dividend distributions yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(vertical = 24.dp))
                    }
                } else {
                    items(state.history) { batch ->
                        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            ListItem(
                                headlineContent = { Text(batch.date) },
                                supportingContent = { Text("${batch.memberCount} member(s)") },
                                trailingContent = { Text(currency.format(batch.totalAmount), fontWeight = FontWeight.Bold, color = CoopGreen) },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showDeclareDialog) {
        DeclareDividendDialog(
            viewModel = viewModel,
            isDeclaring = state.isDeclaring,
            onDismiss = { showDeclareDialog = false },
            onDeclared = { showDeclareDialog = false },
        )
    }
}

@Composable
private fun DeclareDividendDialog(
    viewModel: AdminDividendsViewModel,
    isDeclaring: Boolean,
    onDismiss: () -> Unit,
    onDeclared: () -> Unit,
) {
    val currency = currentCurrency()
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(DistributionMethod.EQUAL_SHARE) }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<DividendPreviewRow>?>(null) }
    var hasSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(isDeclaring) { if (hasSubmitted && !isDeclaring) onDeclared() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preview == null) "Declare Dividend" else "Preview Distribution") },
        text = {
            if (preview == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("Total Amount to Distribute (₦)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(expanded = methodMenuExpanded, onExpandedChange = { methodMenuExpanded = it }) {
                        OutlinedTextField(
                            value = method.label, onValueChange = {}, readOnly = true,
                            label = { Text("Distribution Method") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodMenuExpanded) },
                        )
                        ExposedDropdownMenu(expanded = methodMenuExpanded, onDismissRequest = { methodMenuExpanded = false }) {
                            DistributionMethod.entries.forEach { m ->
                                DropdownMenuItem(text = { Text(m.label) }, onClick = { method = m; methodMenuExpanded = false })
                            }
                        }
                    }
                    Text(
                        "Effective date and notes aren't stored — the backend's dividend_allocations table only tracks cooperative, member, amount, and timestamp.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    preview!!.forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.member.fullName ?: "Unnamed member", style = MaterialTheme.typography.bodySmall)
                            Text(currency.format(row.share), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = CoopGreen)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (preview == null) {
                Button(
                    onClick = { preview = viewModel.buildPreview(amount.toDoubleOrNull() ?: 0.0, method) },
                    enabled = (amount.toDoubleOrNull() ?: 0.0) > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = CoopTeal),
                ) { Text("Preview") }
            } else {
                Button(
                    onClick = { hasSubmitted = true; viewModel.declareDividend(preview!!) },
                    enabled = !isDeclaring,
                    colors = ButtonDefaults.buttonColors(containerColor = CoopGold),
                ) {
                    if (isDeclaring) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Confirm & Distribute", color = androidx.compose.ui.graphics.Color.Black)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (preview != null) preview = null else onDismiss() }) {
                Text(if (preview != null) "Back" else "Cancel")
            }
        },
    )
}
