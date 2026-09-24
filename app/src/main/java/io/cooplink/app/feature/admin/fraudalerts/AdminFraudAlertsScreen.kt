package io.cooplink.app.feature.admin.fraudalerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.SkeletonCard

@Composable
fun AdminFraudAlertsScreen(viewModel: AdminFraudAlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Fraud Alerts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Members who share a BVN, NIN, or phone number — the same identity registered more than once.",
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

            if (state.isLoading && state.signals.isEmpty()) {
                items(3) { SkeletonCard() }
            } else if (state.signals.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.GppGood, null, tint = CoopGreen, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No duplicate identities found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            } else {
                items(state.signals, key = { it.label + it.value }) { signal -> SignalCard(signal) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SignalCard(signal: DuplicateSignal) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = CoopGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(signal.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Shared value: ${signal.value}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            signal.members.forEach { member ->
                Text(
                    "• ${member.fullName ?: "Unnamed member"} (${member.displayId})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
