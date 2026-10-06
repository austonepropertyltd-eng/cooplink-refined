package io.cooplink.app.feature.admin.subscription

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal
import io.cooplink.app.ui.theme.SkeletonCard

@Composable
fun AdminManageCooperativesScreen(
    onBack: () -> Unit,
    viewModel: AdminManageCooperativesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var changingPlanFor by remember { mutableStateOf<CooperativeManagementRow?>(null) }
    var confirmingSuspend by remember { mutableStateOf<CooperativeManagementRow?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Cooperatives") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading, onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                if (state.isLoading && state.rows.isEmpty()) {
                    items(3) { SkeletonCard() }
                } else if (state.rows.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No cooperatives found.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            }
                        }
                    }
                } else {
                    items(state.rows, key = { it.id }) { row ->
                        CooperativeRow(
                            row = row,
                            isProcessing = state.processingId == row.id,
                            onChangePlan = { changingPlanFor = row },
                            onToggleStatus = {
                                if (row.subscription_status == CooperativeStatus.SUSPENDED) {
                                    viewModel.setStatus(row, CooperativeStatus.ACTIVE)
                                } else {
                                    confirmingSuspend = row
                                }
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    changingPlanFor?.let { row ->
        AlertDialog(
            onDismissRequest = { changingPlanFor = null },
            title = { Text("Change plan for ${row.name ?: "cooperative"}") },
            text = {
                if (state.plans.isEmpty()) {
                    Text("No pricing plans available.")
                } else {
                    Column {
                        state.plans.forEach { plan ->
                            ListItem(
                                headlineContent = { Text(plan.plan_name) },
                                supportingContent = { Text("₦${"%,.0f".format(plan.base_price)}" + (plan.max_members?.let { " · up to $it members" } ?: "")) },
                                modifier = Modifier.clickable {
                                    viewModel.changePlan(row, plan)
                                    changingPlanFor = null
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { changingPlanFor = null }) { Text("Close") } },
        )
    }

    confirmingSuspend?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmingSuspend = null },
            title = { Text("Suspend ${row.name ?: "this cooperative"}?") },
            text = { Text("Its admins and members may lose access depending on backend enforcement — confirm this is intended.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.setStatus(row, CooperativeStatus.SUSPENDED); confirmingSuspend = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CoopError),
                ) { Text("Suspend") }
            },
            dismissButton = { TextButton(onClick = { confirmingSuspend = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CooperativeRow(
    row: CooperativeManagementRow,
    isProcessing: Boolean,
    onChangePlan: () -> Unit,
    onToggleStatus: () -> Unit,
) {
    val isSuspended = row.subscription_status == CooperativeStatus.SUSPENDED
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name ?: "Unnamed cooperative", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isSuspended) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = CoopError.copy(alpha = 0.15f)) {
                        Text("SUSPENDED", color = CoopError, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
            Text(
                "${row.plan_name ?: "Free Trial"}" + (row.max_members?.let { " · up to $it members" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChangePlan, enabled = !isProcessing) { Text("Change Plan") }
                OutlinedButton(
                    onClick = onToggleStatus, enabled = !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isSuspended) CoopGreen else CoopError),
                ) {
                    if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(if (isSuspended) "Reactivate" else "Suspend")
                }
            }
        }
    }
}
