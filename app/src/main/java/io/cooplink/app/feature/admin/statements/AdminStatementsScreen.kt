package io.cooplink.app.feature.admin.statements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopSuccess
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminStatementsScreen(viewModel: AdminStatementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Monthly Statements", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

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

            if (state.statements.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarMonth, null, tint = CoopGold, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No activity recorded yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f))
                            }
                        }
                    }
                }
            } else {
                items(state.statements) { stmt ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stmt.month, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            StatementLine("Contributions Collected", stmt.contributionsCollected, CoopSuccess)
                            StatementLine("Loan Disbursements", stmt.loanDisbursements, CoopError)
                            StatementLine("Repayments Received", stmt.repaymentsReceived, CoopSuccess)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            StatementLine("Net Position", stmt.netPosition, CoopTeal, bold = true)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StatementLine(label: String, amount: Double, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    val currency = currentCurrency()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        Text(currency.format(amount), style = MaterialTheme.typography.bodyMedium, color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}
