package io.cooplink.app.feature.member.notifications

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.R
import io.cooplink.app.core.notifications.CoopLinkMessagingService
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopNavy
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoopNavy,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NotifCard {
                    NotifToggleRow(
                        icon = Icons.Default.Notifications, title = "All Notifications",
                        subtitle = "Enable or disable all alerts", checked = prefs.allEnabled, color = CoopTeal,
                        onToggle = viewModel::setAllEnabled,
                    )
                }
            }

            item {
                Text("Alert Types", style = MaterialTheme.typography.titleSmall, color = CoopGold,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))
                NotifCard {
                    NotifToggleRow(Icons.Default.Savings, "Contributions", "When a contribution is recorded",
                        prefs.contributions && prefs.allEnabled, CoopGreen, viewModel::setContributions)
                    NotifDivider()
                    NotifToggleRow(Icons.Default.CreditScore, "Loans", "Loan approval, rejection, disbursement",
                        prefs.loans && prefs.allEnabled, CoopGold, viewModel::setLoans)
                    NotifDivider()
                    NotifToggleRow(Icons.Default.Payment, "Repayments", "When repayment is due or recorded",
                        prefs.repayments && prefs.allEnabled, CoopTeal, viewModel::setRepayments)
                    NotifDivider()
                    NotifToggleRow(Icons.Default.Campaign, "Announcements", "Messages from your cooperative admin",
                        prefs.announcements && prefs.allEnabled, androidx.compose.ui.graphics.Color(0xFF9B59B6), viewModel::setAnnouncements)
                    NotifDivider()
                    NotifToggleRow(Icons.Default.Schedule, "Reminders", "Upcoming due dates and deadlines",
                        prefs.reminders && prefs.allEnabled, CoopError, viewModel::setReminders)
                }
            }

            item {
                Text("Sound & Vibration", style = MaterialTheme.typography.titleSmall, color = CoopGold,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))
                NotifCard {
                    NotifToggleRow(Icons.Default.VolumeUp, "Notification Sound", "Play CoopLink chime for alerts",
                        prefs.soundEnabled, CoopTeal, viewModel::setSoundEnabled)
                    NotifDivider()
                    NotifToggleRow(Icons.Default.Vibration, "Vibration", "Vibrate when notification arrives",
                        prefs.vibration, CoopTeal, viewModel::setVibration)
                }
            }

            item {
                OutlinedButton(
                    onClick = { showTestNotification(context) },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoopTeal),
                ) {
                    Icon(Icons.Default.NotificationsActive, null, tint = CoopTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Test Notification", color = CoopTeal)
                }
            }

            item {
                Card(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = CoopGold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Phone Notification Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Manage at the OS level too", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun showTestNotification(context: android.content.Context) {
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    CoopLinkMessagingService.createNotificationChannels(context, manager)
    manager.notify(
        System.currentTimeMillis().toInt(),
        NotificationCompat.Builder(context, "announcements")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("CoopLink")
            .setContentText("This is a test notification — sound and vibration should match your settings above.")
            .setAutoCancel(true)
            .build(),
    )
}

@Composable
private fun NotifCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 8.dp), content = content)
    }
}

@Composable
private fun NotifDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

@Composable
private fun NotifToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onToggle: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = color) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.labelSmall) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = CoopGold, checkedTrackColor = CoopGold.copy(alpha = 0.4f)))
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
