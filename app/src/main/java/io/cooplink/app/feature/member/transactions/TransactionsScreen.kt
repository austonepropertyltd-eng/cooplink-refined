package io.cooplink.app.feature.member.transactions

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.feature.member.overview.TransactionRow
import io.cooplink.app.ui.theme.*

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel(),
    // Creating a standing order/dispute is handled entirely by the dedicated
    // screens (feature/member/standingorders, feature/member/disputes) — this
    // screen used to have its own local copies of both dialogs, but they sent
    // field shapes the DB rejects (free-text `purpose` instead of the
    // contribution/repayment enum the standing_orders_purpose_check
    // constraint requires; no `subject` for disputes, which is NOT NULL), so
    // every submission through here silently failed. Routing to the working
    // screens instead of maintaining a second, divergent implementation.
    onNavigateToStandingOrders: () -> Unit = {},
    onNavigateToDisputes: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val currency = currentCurrency()
    val context = LocalContext.current

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
                            TextButton(onClick = onNavigateToStandingOrders) { Text("+ New", color = MemberGold) }
                        }
                        if (state.isLoading) {
                            Spacer(Modifier.height(4.dp))
                            Text("Loading…", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.35f))
                        } else if (state.standingOrdersLoadFailed) {
                            Spacer(Modifier.height(4.dp))
                            Text("Couldn't load standing orders. Pull down to retry.", style = MaterialTheme.typography.bodySmall, color = CoopError)
                        } else if (state.standingOrders.isEmpty()) {
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
                                    Text(currency.format(order.amount), color = MemberGold, fontWeight = FontWeight.SemiBold)
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
                            TextButton(onClick = onNavigateToDisputes) { Text("Raise dispute", color = MemberGold) }
                        }
                        if (state.isLoading) {
                            Spacer(Modifier.height(4.dp))
                            Text("Loading…", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.35f))
                        } else if (state.disputesLoadFailed) {
                            Spacer(Modifier.height(4.dp))
                            Text("Couldn't load disputes. Pull down to retry.", style = MaterialTheme.typography.bodySmall, color = CoopError)
                        } else if (state.disputes.isEmpty()) {
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

            if (state.isLoading && state.transactions.isEmpty()) {
                items(3) { SkeletonCard() }
            } else if (state.transactions.isEmpty() && !state.isLoading) {
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
}
