package io.cooplink.app.feature.member.disputes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.*

@Composable
fun DisputesScreen(
    onBack: () -> Unit,
    viewModel: DisputesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showFile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Disputes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoopNavy, titleContentColor = Color.White, navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = CoopNavyDeep,
        floatingActionButton = {
            FloatingActionButton(onClick = { showFile = true }, containerColor = MemberGreen) {
                Icon(Icons.Default.Add, "File a dispute", tint = Color.White)
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh    = viewModel::refresh,
            modifier     = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { err ->
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = CoopError)
                                Spacer(Modifier.width(8.dp))
                                Text(err, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (state.disputes.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                            shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ReportProblem, null, tint = MemberGold, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No disputes filed. Tap + if something looks wrong", color = Color.White.copy(.45f))
                                }
                            }
                        }
                    }
                } else {
                    items(state.disputes) { dispute -> DisputeCard(dispute) }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showFile) {
        var hasSubmitted by remember { mutableStateOf(false) }
        FileDisputeDialog(
            recentTransactions = state.recentTransactions,
            isSubmitting = state.isSubmitting,
            error        = state.submitError,
            onDismiss    = { showFile = false; viewModel.clearSubmitError() },
            onSubmit     = { subject, desc, category, txId -> hasSubmitted = true; viewModel.fileDispute(subject, desc, category, txId) },
        )
        LaunchedEffect(state.isSubmitting, state.submitError) {
            if (hasSubmitted && !state.isSubmitting && state.submitError == null) showFile = false
        }
    }
}

@Composable
private fun DisputeCard(dispute: Dispute) {
    val statusColor = when (dispute.status) {
        DisputeStatus.RESOLVED      -> CoopGreen
        DisputeStatus.REJECTED      -> CoopError
        DisputeStatus.INVESTIGATING -> MemberGold
        else                        -> Color(0xFF718096)
    }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dispute.subject?.takeIf { it.isNotBlank() }
                        ?: dispute.category?.replaceFirstChar { it.uppercase() } ?: "Dispute",
                    style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold,
                )
                Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        dispute.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                        color = statusColor, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(dispute.description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.75f))
            dispute.resolution_notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(.08f))
                Spacer(Modifier.height(8.dp))
                Text("Response", style = MaterialTheme.typography.labelSmall, color = MemberGold, fontWeight = FontWeight.SemiBold)
                Text(notes, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.75f))
            }
            dispute.created_at?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.4f))
            }
        }
    }
}

@Composable
private fun FileDisputeDialog(
    recentTransactions: List<Transaction>,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (subject: String, description: String, category: String, transactionId: String?) -> Unit,
) {
    val currency = currentCurrency()
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DisputeCategory.TRANSACTION) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var txMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File a Dispute") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = category.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        DisputeCategory.ALL.forEach { c ->
                            DropdownMenuItem(text = { Text(c.replaceFirstChar { it.uppercase() }) },
                                onClick = { category = c; categoryMenuExpanded = false })
                        }
                    }
                }

                if (recentTransactions.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = txMenuExpanded, onExpandedChange = { txMenuExpanded = it }) {
                        OutlinedTextField(
                            value = selectedTransaction?.let { "${it.type} · ${currency.format(it.amount)} · ${it.createdAt.take(10)}" }
                                ?: "None (general dispute)",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Related Transaction (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(txMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = txMenuExpanded, onDismissRequest = { txMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("None (general dispute)") },
                                onClick = { selectedTransaction = null; txMenuExpanded = false })
                            recentTransactions.forEach { tx ->
                                DropdownMenuItem(
                                    text = { Text("${tx.type} · ${currency.format(tx.amount)} · ${tx.createdAt.take(10)}") },
                                    onClick = { selectedTransaction = tx; txMenuExpanded = false },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = subject, onValueChange = { subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("Short summary, e.g. Missing payment") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("What happened?") },
                    placeholder = { Text("Describe the issue in detail") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(subject, description, category, selectedTransaction?.id) },
                enabled = subject.isNotBlank() && description.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = CoopError),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
