package io.cooplink.app.feature.admin.kyc

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminKycReviewScreen(viewModel: AdminKycReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedRow by remember { mutableStateOf<KycReviewRow?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSnackbar()
            selectedRow = null
        }
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("KYC Review", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

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

            if (state.rows.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.VerifiedUser, null, tint = CoopTeal, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No pending KYC submissions", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            } else {
                items(state.rows, key = { it.member.id }) { row ->
                    Card(onClick = { selectedRow = row }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        ListItem(
                            headlineContent = { Text(row.member.fullName ?: "Unnamed member") },
                            supportingContent = { Text(row.member.displayId) },
                            trailingContent = {
                                Surface(color = CoopGold.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                    Text("Review", color = CoopGold, style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    selectedRow?.let { row ->
        MemberKycDetailSheet(
            row = row,
            isProcessing = state.processingMemberId == row.member.id,
            onDismiss = { selectedRow = null },
            onApprove = { viewModel.approve(row) },
            onReject  = { reason -> viewModel.reject(row, reason) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberKycDetailSheet(
    row: KycReviewRow,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: (reason: String) -> Unit,
) {
    var showRejectDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(24.dp)) {
            Text(row.member.fullName ?: "Unnamed member", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(row.member.displayId, color = CoopGold, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            DetailRow("Phone", row.member.phone ?: "Not set")
            DetailRow("Email", row.member.email ?: "Not set")
            DetailRow("ID Type", row.kyc.idType?.label ?: "Not set")
            DetailRow("ID Number", row.kyc.idNumber ?: "Not set")

            row.kyc.idDocumentUrl?.let { url ->
                Spacer(Modifier.height(12.dp))
                Text("ID Document", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model = url, contentDescription = "ID Document",
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showRejectDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoopError),
                    border = BorderStroke(1.dp, CoopError),
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                ) {
                    Icon(Icons.Default.Cancel, null); Spacer(Modifier.width(4.dp)); Text("Reject")
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = CoopGreen),
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                ) {
                    if (isProcessing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.VerifiedUser, null); Spacer(Modifier.width(4.dp)); Text("Approve") }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showRejectDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject KYC Submission") },
            text = {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Reason") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { showRejectDialog = false; onReject(reason) },
                    enabled = reason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CoopError),
                ) { Text("Reject") }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
