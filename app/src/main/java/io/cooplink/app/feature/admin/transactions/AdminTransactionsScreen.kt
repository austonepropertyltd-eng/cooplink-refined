package io.cooplink.app.feature.admin.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.feature.member.overview.toNaira
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopSuccess
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminTransactionsScreen(viewModel: AdminTransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var memberMenuExpanded by remember { mutableStateOf(false) }
    var dateMenuExpanded by remember { mutableStateOf(false) }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Transactions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.weight(1f), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Total In", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                            Text(state.totalIn.toNaira(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CoopSuccess)
                        }
                    }
                    Card(Modifier.weight(1f), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Total Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                            Text(state.totalOut.toNaira(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CoopError)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.query, onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by member or reference…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { dateMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(state.dateRangeFilter.label, maxLines = 1)
                        }
                        DropdownMenu(expanded = dateMenuExpanded, onDismissRequest = { dateMenuExpanded = false }) {
                            DateRangeFilter.entries.forEach { r ->
                                DropdownMenuItem(text = { Text(r.label) }, onClick = { viewModel.onDateRangeChange(r); dateMenuExpanded = false })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(state.typeFilter, maxLines = 1)
                        }
                        DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                            state.types.forEach { t ->
                                DropdownMenuItem(text = { Text(t) }, onClick = { viewModel.onTypeFilterChange(t); typeMenuExpanded = false })
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreditDebitFilter.entries.forEach { f ->
                        FilterChip(selected = state.creditDebitFilter == f, onClick = { viewModel.onCreditDebitChange(f) }, label = { Text(f.label) })
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { memberMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(state.members.firstOrNull { it.id == state.memberFilter }?.fullName ?: "All Members", maxLines = 1)
                        }
                        DropdownMenu(expanded = memberMenuExpanded, onDismissRequest = { memberMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("All Members") }, onClick = { viewModel.onMemberFilterChange(null); memberMenuExpanded = false })
                            state.members.forEach { m ->
                                DropdownMenuItem(text = { Text(m.fullName ?: "Unnamed member") },
                                    onClick = { viewModel.onMemberFilterChange(m.id); memberMenuExpanded = false })
                            }
                        }
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
                                Icon(Icons.Default.Receipt, null, tint = CoopGold, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No transactions yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f))
                            }
                        }
                    }
                }
            } else {
                items(state.filtered) { row ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.memberName ?: "Unnamed member", fontWeight = FontWeight.Medium)
                                Text(
                                    "${row.transaction.type.replaceFirstChar { it.uppercase() }} · ${row.transaction.createdAt.take(10)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f),
                                )
                                row.transaction.reference?.let {
                                    Text("Ref: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.4f))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (row.isCredit) "+" else "-"}${row.transaction.amount.toNaira()}",
                                    fontWeight = FontWeight.SemiBold, color = if (row.isCredit) CoopSuccess else CoopError,
                                )
                                Text("Bal: ${row.runningBalance.toNaira()}", style = MaterialTheme.typography.labelSmall,
                                    color = CoopTeal)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
