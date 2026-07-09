package io.cooplink.app.feature.admin.paymentbankdetails

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun PaymentBankDetailsScreen(
    onBack: () -> Unit,
    viewModel: PaymentBankDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingDetail by remember { mutableStateOf<PaymentBankDetail?>(null) }
    var deletingDetail by remember { mutableStateOf<PaymentBankDetail?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Bank Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = CoopTeal) {
                Icon(Icons.Default.Add, "Add bank account")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh    = viewModel::refresh,
            modifier     = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "This is the account shown to every cooperative admin on the bank-transfer subscription payment screen. Only one account can be active at a time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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

                if (state.details.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AccountBalance, null, tint = CoopTeal, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No bank accounts on file yet. Tap + to add one",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                } else {
                    items(state.details) { detail ->
                        BankDetailCard(
                            detail = detail,
                            onSetActive = { viewModel.setActive(detail) },
                            onEdit = { editingDetail = detail },
                            onDelete = { deletingDetail = detail },
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddDialog) {
        BankDetailDialog(
            title = "Add Bank Account",
            detail = null,
            isSaving = state.isSaving,
            error = state.saveError,
            onDismiss = { showAddDialog = false; viewModel.clearSaveError() },
            onSave = { bankName, accountName, accountNumber -> viewModel.addDetail(bankName, accountName, accountNumber) },
        )
        LaunchedEffect(state.isSaving, state.saveError) {
            if (!state.isSaving && state.saveError == null && showAddDialog) showAddDialog = false
        }
    }

    editingDetail?.let { detail ->
        BankDetailDialog(
            title = "Edit Bank Account",
            detail = detail,
            isSaving = state.isSaving,
            error = state.saveError,
            onDismiss = { editingDetail = null; viewModel.clearSaveError() },
            onSave = { bankName, accountName, accountNumber -> viewModel.updateDetail(detail, bankName, accountName, accountNumber) },
        )
        LaunchedEffect(state.isSaving, state.saveError) {
            if (!state.isSaving && state.saveError == null && editingDetail != null) editingDetail = null
        }
    }

    deletingDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { deletingDetail = null },
            title = { Text("Remove this account?") },
            text = { Text("${detail.bank_name} — ${detail.account_number} will be removed. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteDetail(detail); deletingDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CoopError),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deletingDetail = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BankDetailCard(
    detail: PaymentBankDetail,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        border = if (detail.is_active) BorderStroke(2.dp, CoopGreen) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(detail.bank_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (detail.is_active) {
                    Surface(color = CoopGreen.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                        Text("Active", color = CoopGreen, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(detail.account_name, style = MaterialTheme.typography.bodyMedium)
            Text(detail.account_number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = CoopGold)

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!detail.is_active) {
                    Button(onClick = onSetActive, colors = ButtonDefaults.buttonColors(containerColor = CoopGreen)) {
                        Text("Set Active")
                    }
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoopError),
                    border = BorderStroke(1.dp, CoopError.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun BankDetailDialog(
    title: String,
    detail: PaymentBankDetail?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (bankName: String, accountName: String, accountNumber: String) -> Unit,
) {
    var bankName by remember { mutableStateOf(detail?.bank_name ?: "") }
    var accountName by remember { mutableStateOf(detail?.account_name ?: "") }
    var accountNumber by remember { mutableStateOf(detail?.account_number ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = accountName, onValueChange = { accountName = it }, label = { Text("Account Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = accountNumber, onValueChange = { accountNumber = it }, label = { Text("Account Number") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(bankName, accountName, accountNumber) },
                enabled = bankName.isNotBlank() && accountName.isNotBlank() && accountNumber.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = CoopTeal),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
