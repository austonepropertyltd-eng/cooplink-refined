package io.cooplink.app.feature.admin.subscription

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal
import io.cooplink.app.ui.theme.SkeletonCard

@Composable
fun AdminSubscriptionOrdersScreen(
    onBack: () -> Unit,
    viewModel: AdminSubscriptionOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var rejectingRow by remember { mutableStateOf<SubscriptionOrderRow?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription Requests") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading, onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "Pending bank-transfer plan upgrades across every cooperative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
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

                if (state.isLoading && state.rows.isEmpty()) {
                    items(3) { SkeletonCard() }
                } else if (state.rows.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CheckCircle, null, tint = CoopGreen, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No pending subscription requests", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                } else {
                    items(state.rows, key = { it.order.id }) { row ->
                        OrderCard(
                            row = row,
                            isProcessing = state.processingOrderId == row.order.id,
                            onApprove = { viewModel.approve(row) },
                            onReject = { rejectingRow = row },
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    rejectingRow?.let { row ->
        AlertDialog(
            onDismissRequest = { rejectingRow = null },
            title = { Text("Reject this request?") },
            text = { Text("${row.cooperativeName ?: "This cooperative"}'s ${row.order.plan_name} upgrade (ref: ${row.order.bank_transfer_reference ?: "—"}) will be marked rejected.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.reject(row); rejectingRow = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CoopError),
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { rejectingRow = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OrderCard(row: SubscriptionOrderRow, isProcessing: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    val order = row.order
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(row.cooperativeName ?: "Unknown cooperative", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${order.plan_name} · ₦${"%,.0f".format(order.final_price)} · ${order.billing_months} month(s)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Reference: ${order.bank_transfer_reference ?: order.paystack_reference ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            order.created_at?.let {
                Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = CoopGreen)) {
                    if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Approve")
                }
                OutlinedButton(onClick = onReject, enabled = !isProcessing) { Text("Reject") }
            }
        }
    }
}
