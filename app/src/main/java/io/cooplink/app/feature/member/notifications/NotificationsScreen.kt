package io.cooplink.app.feature.member.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.util.formatIsoDate
import io.cooplink.app.ui.theme.*

@Composable
fun NotificationsScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications", style = MaterialTheme.typography.headlineMedium,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Notification settings", tint = Color.White)
                    }
                }
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

            if (state.notifications.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                        shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Notifications, null, tint = MemberGold, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No notifications", color = Color.White.copy(.45f))
                            }
                        }
                    }
                }
            } else {
                items(state.notifications) { n -> NotificationRow(n, onClick = { viewModel.markAsRead(n.id) }) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (n.is_read) CoopDarkSurface else CoopDarkSurface.copy(alpha = 0.9f))) {
        Row(Modifier.padding(16.dp)) {
            if (!n.is_read) {
                Box(
                    Modifier.padding(top = 6.dp, end = 10.dp).size(8.dp)
                        .background(MemberGold, shape = androidx.compose.foundation.shape.CircleShape),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(n.title ?: "Notification", style = MaterialTheme.typography.bodyMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                n.message?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.7f))
                }
                Spacer(Modifier.height(6.dp))
                Text(formatIsoDate(n.created_at) ?: n.created_at.take(10), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.4f))
            }
        }
    }
}
