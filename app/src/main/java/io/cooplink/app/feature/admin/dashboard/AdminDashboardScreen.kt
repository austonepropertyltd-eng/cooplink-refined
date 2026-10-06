package io.cooplink.app.feature.admin.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.*

private data class Kpi(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
    val badge: Int? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onNavigateToTab: (String) -> Unit = {},
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currency = currentCurrency()
    var showHealthDetail by remember { mutableStateOf(false) }

    // healthLabel is only ever set alongside a real successful load — using
    // it as the gate (rather than always formatting healthScorePct) avoids
    // showing a fabricated-looking "100" next to an error banner if the
    // very first load fails before any real score has ever come back.
    val healthValue = state.healthLabel?.let { "%.0f".format(state.healthScorePct) + " · $it" } ?: "—"

    val kpis = listOf(
        Kpi("Total Members",    "${state.totalMembers}",                 Icons.Default.Group,          CoopTeal),
        Kpi("Active Members",   "${state.activeMembers}",                Icons.Default.CreditScore,    CoopGold),
        Kpi("Total Disbursed",  currency.format(state.totalDisbursed),   Icons.Default.AccountBalance, CoopGreen),
        Kpi("Repayment Rate",   "%.0f%%".format(state.repaymentRatePct), Icons.Default.Savings,        Color(0xFF9B59B6)),
        Kpi("Default Rate",     "%.1f%%".format(state.defaultRatePct),   Icons.Default.Warning,        CoopError),
        Kpi("Health Score",     healthValue,                             Icons.Default.Favorite,       CoopTeal),
    )

    val analytics = state.analytics
    val snapshotKpis = listOf(
        Kpi("Total Members",      "${analytics.totalMembers}",              Icons.Default.Group,               CoopTeal),
        Kpi("Active Loans",       "${analytics.activeLoans}",               Icons.Default.CreditScore,         CoopGold),
        Kpi("Total Disbursed",    currency.format(analytics.totalDisbursed),     Icons.Default.AccountBalance,       CoopGreen),
        Kpi("Outstanding",        currency.format(analytics.outstandingBalance), Icons.Default.CreditCard,           Color(0xFFE67E22)),
        Kpi("Total Repaid",       currency.format(analytics.totalRepaid),        Icons.Default.CheckCircle,          CoopGreen),
        Kpi("Monthly Collections", currency.format(analytics.monthlyCollections), Icons.Default.Savings,             CoopTeal),
        Kpi("Total Savings",      currency.format(analytics.totalSavings),       Icons.Default.AccountBalanceWallet, Color(0xFF9B59B6)),
        Kpi("Default Rate",      "%.1f%%".format(analytics.defaultRate),    Icons.Default.Warning,
            if (analytics.defaultRate > 5) CoopError else CoopGreen),
        Kpi("Pending KYC",       "${analytics.pendingKyc}",                 Icons.Default.VerifiedUser,
            if (analytics.pendingKyc > 0) CoopGold else CoopGreen,
            badge = analytics.pendingKyc.takeIf { it > 0 },
            onClick = { onNavigateToTab("kyc_review") }),
        Kpi("Pending Loans",     "${analytics.pendingLoans}",               Icons.Default.HourglassEmpty,
            if (analytics.pendingLoans > 0) CoopGold else CoopGreen,
            badge = analytics.pendingLoans.takeIf { it > 0 },
            onClick = { onNavigateToTab("loans") }),
    )

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Cooperative overview", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DashboardPeriod.entries.toList()) { period ->
                        FilterChip(
                            selected = state.selectedPeriod == period,
                            onClick  = { viewModel.setPeriod(period) },
                            label    = { Text(period.label) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoopGold,
                                selectedLabelColor     = Color.White,
                                containerColor          = MaterialTheme.colorScheme.surface,
                                labelColor              = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            ),
                        )
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
                            Text(err, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SkeletonCard(Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surface, barColor = MaterialTheme.colorScheme.onSurface)
                                SkeletonCard(Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surface, barColor = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        kpis.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { kpi ->
                                    Card(
                                        onClick = { if (kpi.label == "Health Score") showHealthDetail = true },
                                        modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large,
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Icon(kpi.icon, null, tint = kpi.color, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text(kpi.value, style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold)
                                            Text(kpi.label, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (!state.isLoading) {
                item {
                    Text("Cooperative Snapshot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        snapshotKpis.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { kpi -> KpiTile(kpi, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Invite Member" to "members",
                        "Disburse Loan" to "loans",
                        "Send SMS"      to "members",
                    ).forEach { (label, route) ->
                        OutlinedButton(onClick = { onNavigateToTab(route) }, Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                Text("Recent Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No recent activity", color = MaterialTheme.colorScheme.onSurface.copy(.35f))
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showHealthDetail) {
        HealthScoreDetailSheet(state = state, onDismiss = { showHealthDetail = false })
    }
}

@Composable
private fun KpiTile(kpi: Kpi, modifier: Modifier = Modifier) {
    Card(
        onClick = kpi.onClick ?: {},
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
    ) {
        Box {
            Column(Modifier.padding(16.dp)) {
                Icon(kpi.icon, null, tint = kpi.color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(8.dp))
                Text(kpi.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(kpi.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(.5f))
            }
            kpi.badge?.let { count ->
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = CoopError,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$count", color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthScoreDetailSheet(state: AdminDashboardUiState, onDismiss: () -> Unit) {
    // portfolio_quality isn't a field the cooperative-analytics function
    // returns — derived from the real default_rate instead of fabricated,
    // since a healthy portfolio is definitionally one with a low default rate.
    val portfolioQuality = (100.0 - state.defaultRatePct).coerceIn(0.0, 100.0)
    val healthScore = state.healthScorePct.toInt()

    val scoreColor = when {
        healthScore >= 80 -> CoopSuccess
        healthScore >= 60 -> CoopGold
        else -> CoopError
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(24.dp)) {
            Box(Modifier.size(120.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { healthScore / 100f },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 10.dp,
                    color = scoreColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$healthScore", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("/ 100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Score Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            listOf(
                Triple("Repayment Rate", state.repaymentRatePct, Color(0xFF9B59B6)),
                Triple("Member Activity", state.memberActivityPct, CoopTeal),
                Triple("Contribution Compliance", state.contributionCompliancePct, CoopGold),
                Triple("Loan Portfolio Quality", portfolioQuality, CoopTeal),
            ).forEach { (label, value, color) ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text("${value.toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (value / 100f).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CoopGold)
            Spacer(Modifier.height(12.dp))

            val recommendations = buildList {
                if (state.memberActivityPct < 50) add("⚠️ Over half of members are inactive. Send a reminder SMS to re-engage them.")
                if (state.repaymentRatePct < 90) add("⚠️ Repayment rate is below 90%. Follow up with overdue borrowers.")
                if (state.contributionCompliancePct < 80) add("⚠️ Some members missed contributions. Enable standing orders to automate.")
                if (healthScore >= 80) add("✅ Your cooperative is healthy! Keep up the good work.")
            }

            recommendations.forEach { rec ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(rec, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
