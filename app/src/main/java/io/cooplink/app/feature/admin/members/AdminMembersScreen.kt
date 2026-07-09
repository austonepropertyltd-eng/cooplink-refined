package io.cooplink.app.feature.admin.members

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.core.domain.KycBadge
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.ui.MemberAvatar
import io.cooplink.app.feature.member.overview.toNaira
import io.cooplink.app.feature.shell.CooperativeBrandingUiState
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopTeal
import kotlin.random.Random

@Composable
fun AdminMembersScreen(
    branding: CooperativeBrandingUiState = CooperativeBrandingUiState(),
    viewModel: AdminMembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val avatarUrls by viewModel.avatarUrls.collectAsState()
    val context = LocalContext.current
    var showInviteDialog by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<MemberDetails?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkSmsDialog by remember { mutableStateOf(false) }
    var showBulkContributionDialog by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.inactivityManager.resumeTimer()
        uri?.let {
            val text = runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
            }.getOrNull()
            if (text != null) viewModel.previewImport(text)
            else Toast.makeText(context, "Could not read that file", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }
    LaunchedEffect(state.bulkError) {
        state.bulkError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearBulkError()
        }
    }

    Box(Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Text("${selectedIds.size} selected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { selectedIds = emptySet() }) { Text("Cancel") }
                    } else {
                        Text("Members", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.inactivityManager.pauseTimer(); importLauncher.launch("text/*") }) {
                                Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(4.dp)); Text("Import")
                            }
                            Button(onClick = { showInviteDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                                Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(4.dp)); Text("Invite")
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.query, onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by name or member ID…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                )
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

            if (state.filtered.isEmpty() && !state.isLoading) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Group, null, tint = CoopTeal, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (state.query.isBlank()) "No members yet. Tap Invite to add members" else "No members match your search",
                                    color = MaterialTheme.colorScheme.onSurface.copy(.45f),
                                )
                                if (state.query.isBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { showInviteDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                                        Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(4.dp)); Text("Invite")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(state.filtered) { member ->
                    MemberRow(
                        member = member,
                        signedUrlManager = viewModel.signedUrlManager,
                        resolvedAvatarUrl = avatarUrls[member.id],
                        selectionMode = selectionMode,
                        selected = member.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (member.id in selectedIds) selectedIds - member.id else selectedIds + member.id
                            } else {
                                selectedMember = member
                            }
                        },
                        onLongClick = { if (!selectionMode) selectedIds = setOf(member.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (selectionMode) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(io.cooplink.app.ui.theme.CoopNavy).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${selectedIds.size} selected", color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { showBulkSmsDialog = true }) { Icon(Icons.Default.Sms, "Send SMS", tint = CoopGold) }
            IconButton(onClick = { showBulkContributionDialog = true }) { Icon(Icons.Default.Savings, "Record contribution", tint = CoopTeal) }
            IconButton(onClick = {
                io.cooplink.app.core.util.shareCsv(
                    context, "selected_members.csv",
                    listOf("Name", "Member ID", "Phone", "Status"),
                    state.members.filter { it.id in selectedIds }
                        .map { listOf(it.fullName ?: "", it.displayId, it.phone ?: "", it.status) },
                )
            }) { Icon(Icons.Default.Download, "Export CSV", tint = io.cooplink.app.ui.theme.CoopSuccess) }
            IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, "Cancel selection", tint = Color.White) }
        }
    }
    }

    if (showInviteDialog) {
        InviteMemberDialog(
            prefix       = state.memberIdPrefix,
            isSubmitting = state.isInviting,
            error        = state.inviteError,
            onDismiss    = { showInviteDialog = false; viewModel.clearInviteError() },
            onSubmit     = { name, phone, email -> viewModel.inviteMember(name, phone, email) },
            justAdded    = !state.isInviting && state.inviteError == null,
            onAdded      = { showInviteDialog = false },
        )
    }

    selectedMember?.let { member ->
        MemberDetailSheet(
            member          = member,
            cooperativeName = branding.name,
            signedUrlManager = viewModel.signedUrlManager,
            resolvedAvatarUrl = avatarUrls[member.id],
            onDismiss       = { selectedMember = null },
            onEdit          = { Toast.makeText(context, "Edit Member coming soon", Toast.LENGTH_SHORT).show() },
            onSendSms       = { Toast.makeText(context, "Send SMS coming soon", Toast.LENGTH_SHORT).show() },
        )
    }

    if (showBulkSmsDialog) {
        BulkSmsDialog(
            isSending = state.isBulkProcessing,
            onDismiss = { showBulkSmsDialog = false },
            onSend    = { message -> viewModel.sendBulkSms(selectedIds, message); showBulkSmsDialog = false; selectedIds = emptySet() },
        )
    }

    if (showBulkContributionDialog) {
        BulkContributionDialog(
            memberCount = selectedIds.size,
            isSaving    = state.isBulkProcessing,
            onDismiss   = { showBulkContributionDialog = false },
            onConfirm   = { amount -> viewModel.recordBulkContribution(selectedIds, amount); showBulkContributionDialog = false; selectedIds = emptySet() },
        )
    }

    if (state.importPreview.isNotEmpty() || state.isBulkProcessing || state.importDone) {
        ImportMembersDialog(
            preview     = state.importPreview,
            progress    = state.importProgress,
            total       = state.importTotal,
            isImporting = state.isBulkProcessing,
            isDone      = state.importDone,
            onDismiss   = { viewModel.clearImportPreview() },
            onConfirm   = viewModel::confirmImport,
        )
    }
}

@Composable
private fun BulkSmsDialog(isSending: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Bulk SMS") },
        text = {
            OutlinedTextField(
                value = message, onValueChange = { message = it },
                label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSend(message) }, enabled = message.isNotBlank() && !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                if (isSending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Send")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BulkContributionDialog(memberCount: Int, isSaving: Boolean, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Contribution for $memberCount Member(s)") },
        text = {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount per member (₦)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { amount.toDoubleOrNull()?.let(onConfirm) },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = CoopTeal),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MemberDetailSheet(
    member: MemberDetails,
    cooperativeName: String?,
    signedUrlManager: SignedUrlManager,
    resolvedAvatarUrl: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSendSms: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MemberAvatar(
                userId = member.userId,
                avatarPath = member.avatarUrl,
                fullName = member.fullName,
                signedUrlManager = signedUrlManager,
                size = 80.dp,
                resolvedUrlOverride = resolvedAvatarUrl,
            )
            Spacer(Modifier.height(12.dp))
            Text(member.fullName ?: "Unnamed member", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(member.displayId, color = CoopTeal, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = statusColor(member.status).copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(member.status.replaceFirstChar { it.uppercase() }, color = statusColor(member.status),
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                KycBadge(member.kycStatus)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Email", member.email ?: "Not set")
                DetailRow("Phone", member.phone ?: "Not set")
                DetailRow("Member Since", member.createdAt?.take(10) ?: "Not set")
                DetailRow("Total Savings", member.totalSavings.toNaira())
                DetailRow("Cooperative", cooperativeName ?: "Not set")
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit Member") }
                Button(onClick = onSendSms, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) { Text("Send SMS") }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(.55f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InviteMemberDialog(
    prefix: String,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, phone: String, email: String) -> Unit,
    justAdded: Boolean,
    onAdded: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }
    val previewSuffix = remember { Random.nextInt(0, 999_999).toString().padStart(6, '0') }

    LaunchedEffect(justAdded) { if (hasSubmitted && justAdded) onAdded() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                Surface(shape = MaterialTheme.shapes.small, color = CoopTeal.copy(alpha = 0.12f)) {
                    Text(
                        "Member ID preview: $prefix-$previewSuffix",
                        color = CoopTeal,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }

                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Full Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone Number *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = email, onValueChange = { email = it },
                    label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; onSubmit(name, phone, email) },
                enabled = name.isNotBlank() && phone.isNotBlank() && !isSubmitting,
                colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Add Member")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(
    member: MemberDetails,
    signedUrlManager: SignedUrlManager,
    resolvedAvatarUrl: String?,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick, onLongClick = onLongClick,
        ),
        shape = MaterialTheme.shapes.medium,
        colors = if (selected) CardDefaults.cardColors(containerColor = CoopTeal.copy(alpha = 0.12f)) else CardDefaults.cardColors(),
    ) {
        ListItem(
            leadingContent = {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() })
                } else {
                    MemberAvatar(
                        userId = member.userId,
                        avatarPath = member.avatarUrl,
                        fullName = member.fullName,
                        signedUrlManager = signedUrlManager,
                        size = 40.dp,
                        resolvedUrlOverride = resolvedAvatarUrl,
                    )
                }
            },
            headlineContent = { Text(member.fullName ?: "Unnamed member") },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(member.displayId, style = MaterialTheme.typography.labelMedium, color = CoopGold)
                    KycBadge(member.kycStatus)
                }
            },
            trailingContent = {
                Surface(color = statusColor(member.status).copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        member.status.replaceFirstChar { it.uppercase() },
                        color = statusColor(member.status),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

private fun statusColor(status: String) = when (status.lowercase()) {
    "active"                -> CoopTeal
    "suspended", "inactive" -> CoopError
    else                     -> CoopTeal
}

@Composable
private fun ImportMembersDialog(
    preview: List<ImportRow>,
    progress: Int,
    total: Int,
    isImporting: Boolean,
    isDone: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isDone -> "Import Complete"
                    isImporting -> "Importing Members..."
                    else -> "Import ${preview.size} Member(s)?"
                },
            )
        },
        text = {
            Column {
                if (isImporting) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) progress / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("$progress of $total", style = MaterialTheme.typography.bodySmall)
                } else if (isDone) {
                    Text("Check the Members list for results.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        preview.forEach { row ->
                            Text("${row.fullName} · ${row.phone}${row.email?.let { " · $it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isImporting && !isDone) {
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) {
                    Text("Import ${preview.size}")
                }
            } else if (isDone) {
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CoopTeal)) { Text("Done") }
            }
        },
        dismissButton = {
            if (!isImporting) TextButton(onClick = onDismiss) { Text(if (isDone) "Close" else "Cancel") }
        },
    )
}
