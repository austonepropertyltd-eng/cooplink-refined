package io.cooplink.app.feature.admin.sms

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
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
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminSmsScreen(viewModel: AdminSmsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var memberMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("SMS & Reminders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

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

            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.sendError?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                        ExposedDropdownMenuBox(expanded = groupMenuExpanded, onExpandedChange = { groupMenuExpanded = it }) {
                            OutlinedTextField(
                                value = state.recipientGroup.label, onValueChange = {}, readOnly = true,
                                label = { Text("Recipients") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { groupMenuExpanded = false }) {
                                RecipientGroup.entries.forEach { g ->
                                    DropdownMenuItem(text = { Text(g.label) }, onClick = { viewModel.onGroupChange(g); groupMenuExpanded = false })
                                }
                            }
                        }

                        if (state.recipientGroup == RecipientGroup.SPECIFIC) {
                            ExposedDropdownMenuBox(expanded = memberMenuExpanded, onExpandedChange = { memberMenuExpanded = it }) {
                                OutlinedTextField(
                                    value = state.members.firstOrNull { it.id == state.specificMemberId }?.fullName ?: "Select a member",
                                    onValueChange = {}, readOnly = true, label = { Text("Member") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberMenuExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                )
                                ExposedDropdownMenu(expanded = memberMenuExpanded, onDismissRequest = { memberMenuExpanded = false }) {
                                    state.members.forEach { m ->
                                        DropdownMenuItem(text = { Text(m.fullName ?: "Unnamed member") },
                                            onClick = { viewModel.onSpecificMemberChange(m.id); memberMenuExpanded = false })
                                    }
                                }
                            }
                        }

                        Text("${state.recipients.size} recipient(s) will receive this message",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))

                        OutlinedTextField(
                            value = state.message, onValueChange = viewModel::onMessageChange,
                            label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("${state.message.length}/160") },
                        )

                        Button(
                            onClick = viewModel::send,
                            enabled = state.message.isNotBlank() && !state.isSending,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CoopTeal),
                        ) {
                            if (state.isSending) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Send")
                            }
                        }
                    }
                }
            }

            item { Text("Recently Sent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

            if (state.recentLogs.isEmpty() && !state.isLoading) {
                item { Text("No messages sent yet", color = MaterialTheme.colorScheme.onSurface.copy(.45f)) }
            } else {
                items(state.recentLogs) { log ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        ListItem(
                            headlineContent   = { Text(log.message, maxLines = 2) },
                            supportingContent = { Text("${log.status ?: "sent"} · ${log.created_at?.take(10) ?: ""}") },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
