package io.cooplink.app.feature.member.contributions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.Contribution
import io.cooplink.app.feature.member.overview.toNaira
import io.cooplink.app.feature.member.security.PinEntryDialog
import io.cooplink.app.ui.theme.*

@Composable
fun ContributionsScreen(
    autoOpenDialog: Boolean = false,
    viewModel: ContributionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAmountDialog by remember { mutableStateOf(autoOpenDialog) }
    var pendingAmount by remember { mutableStateOf<Double?>(null) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(state.receiptUri) {
        if (state.receiptUri != null) showConfetti = true
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Savings & Contributions", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
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

            item {
                Button(
                    onClick  = { showAmountDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = MemberGold),
                ) {
                    Text("Make a Contribution", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Dedicated Virtual Account", style = MaterialTheme.typography.titleMedium,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        val account = state.virtualAccount
                        if (account != null) {
                            Text(account.bank_name ?: "—", color = Color.White.copy(.8f), style = MaterialTheme.typography.bodyMedium)
                            Text(account.account_number ?: "—", color = MemberGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(account.account_name ?: "—", color = Color.White.copy(.6f), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Get a dedicated bank account number for direct deposits.",
                                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.55f))
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = viewModel::generateVirtualAccount,
                                enabled = !state.isGeneratingAccount,
                                colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
                            ) {
                                if (state.isGeneratingAccount) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Generate account")
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Savings Goal", style = MaterialTheme.typography.titleMedium,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = viewModel::setGoalNotYetAvailable) { Text("Set Goal", color = MemberGold) }
                        }
                    }
                }
            }

            if (state.isLoading && state.contributions.isEmpty()) {
                items(3) { SkeletonCard() }
            } else if (state.contributions.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                        shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Savings, null, tint = MemberGold, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No contributions recorded yet", color = Color.White.copy(.45f))
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { showAmountDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MemberGold)) {
                                    Text("Make a Contribution", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                items(state.contributions) { c -> ContributionRow(c) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAmountDialog) {
        AmountDialog(
            isSubmitting = state.isSubmitting,
            error        = state.submitError,
            onDismiss    = { showAmountDialog = false; viewModel.clearSubmitError() },
            onConfirm    = { amount -> showAmountDialog = false; pendingAmount = amount },
            justSubmitted = false,
            onSubmitted  = {},
        )
    }

    pendingAmount?.let { amount ->
        PinEntryDialog(
            title     = "Confirm Payment",
            onSuccess = { pendingAmount = null; viewModel.addContribution(amount) },
            onDismiss = { pendingAmount = null },
        )
    }

    val receiptUri = state.receiptUri
    if (receiptUri != null) {
        io.cooplink.app.feature.member.security.ReceiptBottomSheet(
            receiptUri       = receiptUri,
            memberName       = "You",
            transactionType  = "Contribution",
            amount           = state.receiptAmount ?: "",
            receiptGenerator = viewModel.receiptGenerator,
            onDismiss        = viewModel::clearReceipt,
        )
    }

    ConfettiOverlay(visible = showConfetti, onComplete = { showConfetti = false })

    if (state.showContactAdminDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissContactAdminDialog,
            containerColor   = CoopDarkSurface,
            title = { Text("Virtual Account Unavailable", color = Color.White, fontWeight = FontWeight.SemiBold) },
            text = {
                Text("Contact your cooperative admin to set up your dedicated virtual account: +2347061365172",
                    color = Color.White.copy(.8f))
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissContactAdminDialog) { Text("OK", color = MemberGold) }
            },
        )
    }

    if (state.showSavingsGoalComingSoon) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSavingsGoalComingSoon,
            containerColor   = CoopDarkSurface,
            title = { Text("Coming Soon", color = Color.White, fontWeight = FontWeight.SemiBold) },
            text = {
                Text("Savings goals aren't available yet — this needs a bit more setup on your cooperative's side. Check back soon.",
                    color = Color.White.copy(.8f))
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSavingsGoalComingSoon) { Text("OK", color = MemberGold) }
            },
        )
    }
}

@Composable
private fun ContributionRow(c: Contribution) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.reference ?: "Contribution",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White, fontWeight = FontWeight.Medium)
                Text(c.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(.45f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(c.amount.toNaira(), style = MaterialTheme.typography.bodyMedium,
                    color = MemberGold, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                StatusChip(c.status)
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status.lowercase()) {
        "completed", "success" -> CoopSuccess
        "pending"               -> MemberGold
        "failed", "cancelled"  -> CoopError
        else                    -> Color.White.copy(.4f)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            status.replaceFirstChar { it.uppercase() },
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AmountDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    justSubmitted: Boolean,
    onSubmitted: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }
    val parsed = amount.toDoubleOrNull()

    LaunchedEffect(justSubmitted) {
        if (hasSubmitted && justSubmitted) onSubmitted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CoopDarkSurface,
        title = { Text("Make a Contribution", color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let {
                    Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₦)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MemberGold,   unfocusedBorderColor  = Color.White.copy(.22f),
                        focusedLabelColor    = MemberGold,   unfocusedLabelColor   = Color.White.copy(.4f),
                        focusedTextColor     = Color.White,  unfocusedTextColor    = Color.White,
                        cursorColor          = MemberGold,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; parsed?.let(onConfirm) },
                enabled = parsed != null && parsed > 0 && !isSubmitting,
                colors  = ButtonDefaults.buttonColors(containerColor = MemberGold),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CoopNavyDeep)
                else Text("Continue", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(.5f)) }
        },
    )
}
