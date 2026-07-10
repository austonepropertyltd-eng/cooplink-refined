package io.cooplink.app.feature.admin.sharecapital

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminShareCapitalScreen(viewModel: AdminShareCapitalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val currency = currentCurrency()
    val context = LocalContext.current
    var showRecordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Share Capital", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = { showRecordDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Record")
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Total Share Capital", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Text(currency.format(state.totalValue), style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = CoopGold)
                    }
                }
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

            if (state.rows.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No share capital recorded yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f))
                        }
                    }
                }
            } else {
                items(state.rows) { row ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        ListItem(
                            headlineContent   = { Text(row.memberName ?: "Unnamed member") },
                            supportingContent = row.createdAt?.let { { Text(it.take(10)) } },
                            trailingContent   = { Text(currency.format(row.totalValue), fontWeight = FontWeight.SemiBold, color = CoopGold) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showRecordDialog) {
        var selectedMember by remember(state.members) { mutableStateOf(state.members.firstOrNull()) }
        var menuExpanded by remember { mutableStateOf(false) }
        var amount by remember { mutableStateOf("") }
        var hasSubmitted by remember { mutableStateOf(false) }
        val parsed = amount.toDoubleOrNull()
        val justSubmitted = !state.isSubmitting && state.submitError == null

        LaunchedEffect(justSubmitted) { if (hasSubmitted && justSubmitted) showRecordDialog = false }

        AlertDialog(
            onDismissRequest = { showRecordDialog = false; viewModel.clearSubmitError() },
            title = { Text("Record Share Purchase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.submitError?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                    if (state.members.isEmpty()) {
                        Text("No members found for your cooperative.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
                            OutlinedTextField(
                                value = selectedMember?.fullName ?: "Select a member",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Member") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                state.members.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.fullName ?: "Unnamed member") },
                                        onClick = { selectedMember = m; menuExpanded = false },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Amount (₦)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { hasSubmitted = true; selectedMember?.let { m -> parsed?.let { viewModel.recordSharePurchase(m.id, it) } } },
                    enabled = selectedMember != null && parsed != null && parsed > 0 && !state.isSubmitting,
                    colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
                ) {
                    if (state.isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showRecordDialog = false }) { Text("Cancel") } },
        )
    }
}
