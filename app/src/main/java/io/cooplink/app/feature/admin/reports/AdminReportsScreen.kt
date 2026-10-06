package io.cooplink.app.feature.admin.reports

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopNavy
import io.cooplink.app.ui.theme.CoopSuccess
import io.cooplink.app.ui.theme.CoopTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminReportsScreen(viewModel: AdminReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val currency = currentCurrency()
    val context = LocalContext.current
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.inactivityManager.resumeTimer()
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Reports & Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        val reportText = buildString {
                            appendLine("CoopLink Report - ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())}")
                            appendLine("Cooperative: ${state.cooperativeName ?: "—"}")
                            appendLine("Generated: ${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date())}")
                            appendLine("---")
                            appendLine("Total Members: ${state.totalMembers}")
                            appendLine("Active Loans: ${state.activeLoans}")
                            appendLine("Total Disbursed: ${currency.format(state.totalDisbursed)}")
                        }
                        val intent = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reportText)
                                putExtra(Intent.EXTRA_SUBJECT, "CoopLink Report")
                            },
                            "Share Report",
                        )
                        viewModel.inactivityManager.pauseTimer()
                        shareLauncher.launch(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoopTeal),
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share Summary")
                }
            }

            state.error?.let { err ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f)),
                    shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = CoopError)
                        Spacer(Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.rows.forEach { row ->
                ReportCard(
                    title = row.title,
                    subtitle = row.summary,
                    onExportPdf = {
                        viewModel.inactivityManager.pauseTimer()
                        val uri = viewModel.exportManager.exportToPdf(
                            title = row.title,
                            cooperativeName = state.cooperativeName ?: "CoopLink",
                            headers = row.csvHeader,
                            rows = row.csvRows,
                        )
                        if (uri != null) viewModel.exportManager.shareUri(uri, "application/pdf", shareLauncher::launch)
                        else {
                            viewModel.inactivityManager.resumeTimer()
                            Toast.makeText(context, "Could not export PDF. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExportCsv = {
                        viewModel.inactivityManager.pauseTimer()
                        val uri = viewModel.exportManager.exportToCsv(row.title, row.csvHeader, row.csvRows)
                        if (uri != null) viewModel.exportManager.shareUri(uri, "text/csv", shareLauncher::launch)
                        else {
                            viewModel.inactivityManager.resumeTimer()
                            Toast.makeText(context, "Could not export CSV. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Loan Aging", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            var selectedBucket by remember { mutableStateOf<AgingBucket?>(null) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AgingBucket.entries.forEach { bucket ->
                    val count = state.agingLoans.count { it.agingBucket == bucket }
                    val bucketColor = agingBucketColor(bucket)
                    Card(
                        onClick = { selectedBucket = if (selectedBucket == bucket) null else bucket },
                        modifier = Modifier.weight(1f),
                        colors = if (selectedBucket == bucket)
                            CardDefaults.cardColors(containerColor = bucketColor.copy(alpha = 0.15f))
                        else CardDefaults.cardColors(),
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = bucketColor)
                            Text(bucket.label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            val visibleAgingLoans = selectedBucket?.let { b -> state.agingLoans.filter { it.agingBucket == b } } ?: state.agingLoans
            if (visibleAgingLoans.isEmpty()) {
                Text("No overdue loans in this range", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(vertical = 12.dp))
            } else {
                visibleAgingLoans.forEach { loan ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        ListItem(
                            headlineContent = { Text(loan.memberName) },
                            supportingContent = { Text("${loan.memberId} · ${loan.daysOverdue} days overdue") },
                            trailingContent = {
                                Text(currency.format(loan.outstandingBalance), fontWeight = FontWeight.Bold, color = agingBucketColor(loan.agingBucket))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun agingBucketColor(bucket: AgingBucket) = when (bucket) {
    AgingBucket.CURRENT      -> CoopSuccess
    AgingBucket.DAYS_30      -> CoopGold
    AgingBucket.DAYS_60      -> Color(0xFFED8936)
    AgingBucket.DAYS_90      -> CoopError
    AgingBucket.DAYS_90_PLUS -> Color(0xFF742A2A)
}

@Composable
private fun ReportCard(
    title: String,
    subtitle: String,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(48.dp), MaterialTheme.shapes.medium, color = CoopTeal.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Assessment, null, tint = CoopTeal, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onExportPdf,
                    colors  = ButtonDefaults.buttonColors(containerColor = CoopNavy),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onExportCsv,
                    border  = BorderStroke(1.dp, CoopTeal),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.TableChart, null, Modifier.size(14.dp), tint = CoopTeal)
                    Spacer(Modifier.width(4.dp))
                    Text("CSV", style = MaterialTheme.typography.labelMedium, color = CoopTeal)
                }
            }
        }
    }
}
