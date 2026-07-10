package io.cooplink.app.feature.admin.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal

private enum class AccountingTab(val label: String) { LEDGER("Ledger"), TRIAL_BALANCE("Trial Balance"), PL("P&L") }

@Composable
fun AdminAccountingScreen(viewModel: AdminAccountingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(AccountingTab.LEDGER) }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Accounting", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    val (headers, rows) = when (tab) {
                        AccountingTab.LEDGER -> listOf("Date", "Description", "Debit", "Credit", "Balance") to
                            state.ledger.map { listOf(it.date, it.description, "${it.debit}", "${it.credit}", "${it.balance}") }
                        AccountingTab.TRIAL_BALANCE -> listOf("Account", "Debit", "Credit") to
                            state.trialBalance.map { listOf(it.account, "${it.debit}", "${it.credit}") }
                        AccountingTab.PL -> listOf("Item", "Amount") to
                            listOf(
                                listOf("Admin Fees Collected", "${state.adminFees}"),
                                listOf("Operating Costs", "${state.operatingCosts}"),
                                listOf("Net Surplus/Deficit", "${state.netSurplus}"),
                            )
                    }
                    viewModel.exportManager.exportToPdf(tab.label, "CoopLink", headers, rows)
                }) { Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = CoopTeal) }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountingTab.entries.forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
                }
            }
            Spacer(Modifier.height(12.dp))

            state.error?.let { err ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = CoopError)
                        Spacer(Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            when (tab) {
                AccountingTab.LEDGER -> LedgerTab(state.ledger)
                AccountingTab.TRIAL_BALANCE -> TrialBalanceTab(state.trialBalance)
                AccountingTab.PL -> ProfitLossTab(state)
            }
        }
    }
}

@Composable
private fun LedgerTab(ledger: List<LedgerEntry>) {
    val currency = currentCurrency()
    if (ledger.isEmpty()) {
        Text("No transactions recorded yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Date", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Description", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Debit", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Credit", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Balance", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
        }
        items(ledger) { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.date, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(entry.description, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(if (entry.debit > 0) currency.format(entry.debit) else "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = CoopError)
                Text(if (entry.credit > 0) currency.format(entry.credit) else "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = CoopGreen)
                Text(currency.format(entry.balance), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TrialBalanceTab(rows: List<TrialBalanceRow>) {
    val currency = currentCurrency()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Account", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Debit", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Credit", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
        }
        items(rows) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(row.account, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                Text(if (row.debit > 0) currency.format(row.debit) else "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(if (row.credit > 0) currency.format(row.credit) else "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
        }
        item {
            val totalDebit = rows.sumOf { it.debit }
            val totalCredit = rows.sumOf { it.credit }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Total", Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(currency.format(totalDebit), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(currency.format(totalCredit), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfitLossTab(state: AdminAccountingUiState) {
    val currency = currentCurrency()
    Column {
        Text("Income", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CoopGreen)
        Spacer(Modifier.height(8.dp))
        PlRow("Loan interest earned", state.interestEarned)
        PlRow("Admin fees collected", state.adminFees)
        PlRow("Late penalty fees", state.penaltyFees)
        Spacer(Modifier.height(16.dp))
        Text("Expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CoopError)
        Spacer(Modifier.height(8.dp))
        PlRow("Operating costs", state.operatingCosts)
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Net Surplus / Deficit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(currency.format(state.netSurplus), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = if (state.netSurplus >= 0) CoopGreen else CoopError)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Loan interest and penalty fees aren't tracked as separate accrual columns in the current schema, so those show as ₦0 until that data exists.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun PlRow(label: String, amount: Double) {
    val currency = currentCurrency()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(currency.format(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
