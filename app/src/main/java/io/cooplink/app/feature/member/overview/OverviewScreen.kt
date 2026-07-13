package io.cooplink.app.feature.member.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.MemberAvatar
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.core.util.formatIsoDate
import io.cooplink.app.feature.shell.CooperativeBrandingUiState
import io.cooplink.app.ui.theme.*

private fun timeOfDayGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else      -> "evening"
    }
}

private data class QuickAction(
    val icon: ImageVector, val label: String, val color: Color,
    val route: String, val autoOpen: Boolean,
)

@Composable
fun OverviewScreen(
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onNavigateToTab: (route: String, autoOpen: Boolean) -> Unit = { _, _ -> },
    onNavigateToKyc: () -> Unit = {},
    onNavigateToAirtime: () -> Unit = {},
    branding: CooperativeBrandingUiState = CooperativeBrandingUiState(),
    viewModel: MemberOverviewViewModel = hiltViewModel(),
    kycViewModel: io.cooplink.app.feature.member.profile.KycViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val kycState by kycViewModel.state.collectAsState()
    val currency = currentCurrency()

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            // Top bar
            item {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MemberAvatar(
                            userId = state.member?.userId,
                            avatarPath = state.member?.avatarUrl,
                            fullName = state.member?.fullName,
                            signedUrlManager = viewModel.signedUrlManager,
                            size = 48.dp,
                            modifier = Modifier.clickable { onNavigateToTab("profile", false) },
                        )
                        Column {
                            Text("Good ${timeOfDayGreeting()} 👋",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(.55f))
                            Text(
                                state.member?.fullName?.split(" ")?.firstOrNull() ?: "Member",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.onBackground.copy(.5f))
                        }
                    }
                }
            }

            if (!kycState.isLoading && kycState.kycData.status == io.cooplink.app.core.domain.KycStatus.NOT_SUBMITTED) {
                item {
                    Card(
                        onClick = onNavigateToKyc,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CoopGold.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CoopGold.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = CoopGold, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Complete Your KYC", style = MaterialTheme.typography.titleSmall,
                                    color = CoopGold, fontWeight = FontWeight.Bold)
                                Text("Verify your identity to unlock all features",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = CoopGold)
                        }
                    }
                }
            }

            // Error banner
            state.error?.let { err ->
                item {
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CoopError.copy(.15f)),
                        shape  = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = CoopError)
                            Spacer(Modifier.width(8.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }

            // Wallet card
            if (state.isLoading && state.member == null) {
                item { io.cooplink.app.ui.theme.ShimmerWalletCard() }
            } else {
                item {
                    Card(
                        Modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(20.dp)),
                        shape  = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    ) {
                        Box(
                            Modifier.fillMaxWidth()
                                .background(Brush.linearGradient(listOf(MemberGreen, CoopTeal)))
                                .padding(24.dp)
                        ) {
                            Column {
                                Text("Total Savings",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(.8f))
                                Spacer(Modifier.height(6.dp))
                                io.cooplink.app.ui.theme.AnimatedAmount(
                                    amount = state.member?.totalSavings ?: 0.0,
                                    style  = MaterialTheme.typography.displayMedium,
                                    color  = Color.White,
                                )
                                formatIsoDate(state.member?.createdAt)?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Member since $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(.65f))
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    WalletStat("Active Loans", "${state.activeLoansCount}")
                                    WalletStat("Status",
                                        state.member?.status?.replaceFirstChar { it.uppercase() } ?: "—")
                                }
                                Spacer(Modifier.height(12.dp))
                                // members.member_number is the real formatted ID column
                                // (e.g. "MEM-747074") — falls back to the row id if unset.
                                Text("Member ID", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.7f))
                                Text(
                                    state.member?.displayId ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // Quick actions
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    listOfNotNull(
                        QuickAction(Icons.Default.Payment,    "Pay",     MemberGold,          "contributions", autoOpen = true),
                        if (branding.hasLoansModule)
                            QuickAction(Icons.Default.CreditCard, "Loan", CoopTeal,            "loans",         autoOpen = true)
                        else null,
                        QuickAction(Icons.Default.Savings,    "Savings", Color(0xFF9B59B6),    "contributions", autoOpen = false),
                        QuickAction(Icons.Default.PhoneAndroid, "Airtime", Color(0xFFE67E22),  "airtime",       autoOpen = false),
                        QuickAction(Icons.Default.Notifications, "Alerts", CoopNavy,           "notifications", autoOpen = false),
                    ).forEach { qa ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = {
                                    when (qa.route) {
                                        "notifications" -> onOpenNotifications()
                                        "airtime" -> onNavigateToAirtime()
                                        else -> onNavigateToTab(qa.route, qa.autoOpen)
                                    }
                                },
                                Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = qa.color.copy(.15f))) {
                                Icon(qa.icon, qa.label, tint = qa.color)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(qa.label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(.65f))
                        }
                    }
                }
            }

            // KPI cards
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(Modifier.weight(1f), "Total Savings",
                        currency.format(state.member?.totalSavings ?: 0.0),
                        Icons.Default.Savings, MemberGold, state.isLoading, rawAmount = state.member?.totalSavings ?: 0.0)
                    KpiCard(Modifier.weight(1f), "Active Loans",
                        "${state.activeLoansCount}",
                        Icons.Default.CreditScore, CoopTeal, state.isLoading)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard(Modifier.weight(1f), "Share Capital",
                        currency.format(state.shareCapital),
                        Icons.Default.PieChart, Color(0xFF9B59B6), state.isLoading, rawAmount = state.shareCapital)
                }
            }

            // Recent transactions
            item {
                Text("Recent Activity",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
            }

            if (state.isLoading && state.recentTransactions.isEmpty()) {
                items(5) { io.cooplink.app.ui.theme.ShimmerListItem() }
            } else if (state.recentTransactions.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Box(Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(.18f), modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No transactions yet",
                                    color = MaterialTheme.colorScheme.onSurface.copy(.45f),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
                items(state.recentTransactions) { tx -> TransactionRow(tx) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun WalletStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.7f))
        Text(value, style = MaterialTheme.typography.titleMedium,
            color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KpiCard(modifier: Modifier, title: String, value: String,
                    icon: ImageVector, color: Color, loading: Boolean, rawAmount: Double? = null) {
    Card(modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(.55f))
            }
            Spacer(Modifier.height(8.dp))
            if (loading) {
                io.cooplink.app.ui.theme.ShimmerEffect(Modifier.fillMaxWidth(.6f).height(28.dp))
            } else if (rawAmount != null) {
                io.cooplink.app.ui.theme.AnimatedAmount(
                    amount = rawAmount, style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(value, style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun TransactionRow(tx: Transaction) {
    val currency = currentCurrency()
    val isCredit = tx.type in listOf("contribution", "deposit", "wallet_funding", "credit")
    val color    = if (isCredit) CoopGreen else CoopError
    val sign     = if (isCredit) "+" else "-"

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = MaterialTheme.shapes.medium,
                color = color.copy(.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        null, tint = color, modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tx.description ?: tx.type.replace("_", " ")
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium,
                )
                Text(
                    tx.createdAt.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(.45f),
                )
            }
            Text(
                "$sign${currency.format(tx.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = color, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
