package io.cooplink.app.feature.admin.paymenthistory

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminPaymentHistoryScreen(viewModel: AdminPaymentHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val currency = currentCurrency()
    val context = LocalContext.current
    var selectedPayment by remember { mutableStateOf<PaymentRow?>(null) }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.inactivityManager.resumeTimer()
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Payment History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = {
                            viewModel.inactivityManager.pauseTimer()
                            val uri = viewModel.exportManager.exportToPdf(
                                "Payment History", "CoopLink", listOf("Member", "Type", "Amount", "Date"),
                                state.visiblePayments.map { listOf(it.memberName, it.transaction.type, "${it.transaction.amount}", it.transaction.createdAt.take(10)) },
                            )
                            if (uri != null) viewModel.exportManager.shareUri(uri, "application/pdf", shareLauncher::launch) else viewModel.inactivityManager.resumeTimer()
                        }) { Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = CoopTeal) }
                        IconButton(onClick = {
                            viewModel.inactivityManager.pauseTimer()
                            val uri = viewModel.exportManager.exportToCsv(
                                "Payment History", listOf("Member", "Type", "Amount", "Date"),
                                state.visiblePayments.map { listOf(it.memberName, it.transaction.type, "${it.transaction.amount}", it.transaction.createdAt.take(10)) },
                            )
                            if (uri != null) viewModel.exportManager.shareUri(uri, "text/csv", shareLauncher::launch) else viewModel.inactivityManager.resumeTimer()
                        }) { Icon(Icons.Default.TableChart, "Export CSV", tint = CoopTeal) }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard(Modifier.weight(1f), "Total In", currency.format(state.totalIn), CoopGreen)
                    SummaryCard(Modifier.weight(1f), "Total Out", currency.format(state.totalOut), CoopError)
                    SummaryCard(Modifier.weight(1f), "Net", currency.format(state.totalIn - state.totalOut), CoopGold)
                }
            }

            item {
                OutlinedTextField(
                    value = state.query, onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by member name or ID…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PaymentTypeFilter.entries.toList()) { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick  = { viewModel.onFilterChange(f) },
                            label    = { Text(f.label) },
                            colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = CoopGold, selectedLabelColor = androidx.compose.ui.graphics.Color.White),
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

            if (state.visiblePayments.isEmpty() && !state.isLoading) {
                item {
                    Text("No payments found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 24.dp))
                }
            } else {
                items(state.visiblePayments, key = { it.transaction.id }) { row ->
                    PaymentRowCard(row, onClick = { selectedPayment = row })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    selectedPayment?.let { row ->
        PaymentDetailSheet(row = row, onDismiss = { selectedPayment = null })
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Card(modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun PaymentRowCard(row: PaymentRow, onClick: () -> Unit) {
    val currency = currentCurrency()
    val isCredit = row.transaction.type.lowercase() in setOf("contribution", "deposit", "wallet_funding", "repayment", "loan_repayment")
    val color = if (isCredit) CoopGreen else CoopError
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        ListItem(
            leadingContent = {
                Surface(Modifier.size(40.dp), MaterialTheme.shapes.medium, color = color.copy(alpha = 0.15f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = color, modifier = Modifier.size(18.dp))
                    }
                }
            },
            headlineContent = { Text(row.memberName) },
            supportingContent = { Text("${row.transaction.type.replaceFirstChar { it.uppercase() }} · ${row.transaction.createdAt.take(10)}") },
            trailingContent = {
                Text("${if (isCredit) "+" else "-"}${currency.format(row.transaction.amount)}", fontWeight = FontWeight.Bold, color = color)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDetailSheet(row: PaymentRow, onDismiss: () -> Unit) {
    val currency = currentCurrency()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(24.dp)) {
            Text("Payment Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            DetailRow("Member", row.memberName)
            DetailRow("Member ID", row.memberDisplayId)
            DetailRow("Type", row.transaction.type.replaceFirstChar { it.uppercase() })
            DetailRow("Amount", currency.format(row.transaction.amount))
            DetailRow("Date", row.transaction.createdAt.take(10))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Reference", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.transaction.reference ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (row.transaction.reference != null) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(row.transaction.reference!!))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy reference", modifier = Modifier.size(16.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
