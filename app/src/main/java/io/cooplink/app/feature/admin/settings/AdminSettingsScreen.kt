package io.cooplink.app.feature.admin.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import io.cooplink.app.feature.shell.CooperativeLogo
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopTeal

private enum class SettingsDialog { NONE, COOP_PROFILE, ROLES, SECURITY, BANK_ACCOUNTS }

@Composable
fun AdminSettingsScreen(
    onLogout: () -> Unit,
    onOpenBranding: () -> Unit,
    viewModel: AdminSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(SettingsDialog.NONE) }
    var showSubscription by remember { mutableStateOf(false) }
    var showBankDetails by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    if (showSubscription) {
        io.cooplink.app.feature.admin.subscription.SubscriptionScreen(
            onBack            = { showSubscription = false },
            inactivityManager = viewModel.inactivityManager,
        )
        return
    }

    if (showBankDetails) {
        io.cooplink.app.feature.admin.paymentbankdetails.PaymentBankDetailsScreen(
            onBack = { showBankDetails = false },
        )
        return
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        data class SettingsItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val premium: Boolean, val action: () -> Unit)
        buildList {
            add(SettingsItem(Icons.Default.Business, "Cooperative Profile", false) { dialog = SettingsDialog.COOP_PROFILE })
            add(SettingsItem(Icons.Default.PeopleAlt, "Role Management", false) {
                viewModel.loadRoles(); dialog = SettingsDialog.ROLES
            })
            add(SettingsItem(Icons.Default.AccountBalance, "Bank & Cash", true) {
                viewModel.loadBankAccounts(); dialog = SettingsDialog.BANK_ACCOUNTS
            })
            add(SettingsItem(Icons.Default.Palette, "Branding", true, onOpenBranding))
            add(SettingsItem(Icons.Default.Subscriptions, "Subscription", false) { showSubscription = true })
            if (state.isSuperAdmin) {
                add(SettingsItem(Icons.Default.AccountBalanceWallet, "Payment Bank Details", false) { showBankDetails = true })
            }
            add(SettingsItem(Icons.Default.Security, "Security", false) { dialog = SettingsDialog.SECURITY })
            add(SettingsItem(Icons.Default.Logout, "Logout", false, onLogout))
        }.forEach { item ->
            Card(onClick = item.action, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                ListItem(
                    leadingContent = { Icon(item.icon, null, tint = if (item.label == "Logout") CoopError else CoopTeal) },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label)
                            if (item.premium) {
                                Spacer(Modifier.width(8.dp))
                                Surface(shape = MaterialTheme.shapes.extraSmall, color = CoopGold.copy(alpha = 0.18f)) {
                                    Text(
                                        "PRO", color = CoopGold, style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) })
            }
        }
    }
    }

    when (dialog) {
        SettingsDialog.COOP_PROFILE -> {
            val coop = state.cooperative
            var name by remember(coop) { mutableStateOf(coop?.name ?: "") }
            var address by remember(coop) { mutableStateOf(coop?.address ?: "") }
            var phone by remember(coop) { mutableStateOf(coop?.phone ?: "") }
            var email by remember(coop) { mutableStateOf(coop?.email ?: "") }
            var whatsapp by remember(coop) { mutableStateOf(coop?.whatsappNumber ?: "") }
            var hasSubmitted by remember { mutableStateOf(false) }
            val justSaved = !state.isSavingProfile && state.profileError == null
            LaunchedEffect(justSaved) { if (hasSubmitted && justSaved) dialog = SettingsDialog.NONE }

            AlertDialog(
                onDismissRequest = { dialog = SettingsDialog.NONE; viewModel.clearProfileError() },
                title = { Text("Cooperative Profile") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.profileError?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                        CooperativeLogo(coop?.name, coop?.logoUrl, coop?.primaryColor, size = 56.dp)
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = whatsapp, onValueChange = { whatsapp = it }, label = { Text("WhatsApp Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { hasSubmitted = true; viewModel.updateCooperativeProfile(name, address, phone, email, whatsapp, coop?.logoUrl ?: "") },
                        enabled = name.isNotBlank() && !state.isSavingProfile,
                    ) {
                        if (state.isSavingProfile) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text("Cancel") } },
            )
        }
        SettingsDialog.BANK_ACCOUNTS -> {
            var showAddAccount by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { dialog = SettingsDialog.NONE },
                title = { Text("Bank & Cash") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.bankAccountError?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                        if (state.isLoadingBankAccounts) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        } else if (state.bankAccounts.isEmpty()) {
                            Text("No bank accounts on file yet.")
                        } else {
                            state.bankAccounts.forEach { acc ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(acc.bank_name ?: "—", fontWeight = FontWeight.SemiBold)
                                    Text("${acc.account_name ?: "—"} · ${acc.account_number ?: "—"}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (showAddAccount) {
                            var bankName by remember { mutableStateOf("") }
                            var accNumber by remember { mutableStateOf("") }
                            var accName by remember { mutableStateOf("") }
                            val justSaved = !state.isSavingBankAccount && state.bankAccountError == null
                            var hasSubmitted by remember { mutableStateOf(false) }
                            LaunchedEffect(justSaved) { if (hasSubmitted && justSaved) showAddAccount = false }

                            HorizontalDivider()
                            OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = accNumber, onValueChange = { accNumber = it }, label = { Text("Account Number") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = accName, onValueChange = { accName = it }, label = { Text("Account Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = { hasSubmitted = true; viewModel.addBankAccount(bankName, accNumber, accName) },
                                enabled = bankName.isNotBlank() && accNumber.isNotBlank() && accName.isNotBlank() && !state.isSavingBankAccount,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isSavingBankAccount) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add Account")
                            }
                        } else {
                            TextButton(onClick = { showAddAccount = true }) { Text("+ Add Bank Account") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text("Close") } },
            )
        }
        SettingsDialog.ROLES -> AlertDialog(
            onDismissRequest = { dialog = SettingsDialog.NONE },
            title = { Text("Role Management") },
            text = {
                if (state.isLoadingRoles) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.roles.isEmpty()) {
                    Text("No role assignments found for your cooperative.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.roles.forEach { role ->
                            var menuExpanded by remember(role.user_id) { mutableStateOf(false) }
                            val userId = role.user_id
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text((userId ?: "—").take(12), style = MaterialTheme.typography.bodySmall)
                                Box {
                                    OutlinedButton(
                                        onClick = { menuExpanded = true },
                                        enabled = userId != null && state.updatingUserId != userId,
                                    ) {
                                        if (state.updatingUserId == userId) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        else Text(role.role ?: "—", style = MaterialTheme.typography.bodySmall)
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        ASSIGNABLE_ROLES.forEach { r ->
                                            DropdownMenuItem(text = { Text(r) }, onClick = {
                                                menuExpanded = false
                                                userId?.let { viewModel.changeRole(it, r) }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text("Close") } },
        )
        SettingsDialog.SECURITY -> {
            val autoLogoutEnabled by viewModel.autoLogoutEnabled.collectAsState()
            val requireBiometric by viewModel.requireBiometric.collectAsState()
            AlertDialog(
                onDismissRequest = { dialog = SettingsDialog.NONE },
                title = { Text("Security") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-logout after inactivity", style = MaterialTheme.typography.bodyMedium)
                                Text("Logout after 15 minutes of no activity", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(.55f))
                            }
                            Switch(checked = autoLogoutEnabled, onCheckedChange = { viewModel.setAutoLogout(it) })
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Require fingerprint on open", style = MaterialTheme.typography.bodyMedium)
                                Text("Show a fingerprint prompt when reopened from background",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.55f))
                            }
                            Switch(checked = requireBiometric, onCheckedChange = { viewModel.setRequireBiometric(it) })
                        }
                        HorizontalDivider()
                        Text("Send a password reset link to your registered admin email?",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.sendPasswordReset(); dialog = SettingsDialog.NONE }) {
                        Text("Send Reset Email")
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text("Close") } },
            )
        }
        SettingsDialog.NONE -> {}
    }
}

