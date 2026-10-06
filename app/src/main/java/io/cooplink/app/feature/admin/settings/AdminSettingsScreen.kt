package io.cooplink.app.feature.admin.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import io.cooplink.app.core.domain.SavingsInterestMethod
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal

private enum class SettingsDialog { NONE, ROLES, SECURITY, BANK_ACCOUNTS, SAVINGS_INTEREST, LATE_FEES }

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
    var showSubscriptionOrders by remember { mutableStateOf(false) }
    var showPricingPlans by remember { mutableStateOf(false) }
    var showManageCooperatives by remember { mutableStateOf(false) }
    var showAppUpdate by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    val currentCurrency by viewModel.currencyProvider.currency.collectAsState()

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

    if (showSubscriptionOrders) {
        io.cooplink.app.feature.admin.subscription.AdminSubscriptionOrdersScreen(
            onBack = { showSubscriptionOrders = false },
        )
        return
    }

    if (showPricingPlans) {
        io.cooplink.app.feature.admin.subscription.AdminPricingPlansScreen(
            onBack = { showPricingPlans = false },
        )
        return
    }

    if (showManageCooperatives) {
        io.cooplink.app.feature.admin.subscription.AdminManageCooperativesScreen(
            onBack = { showManageCooperatives = false },
        )
        return
    }

    if (showAppUpdate) {
        io.cooplink.app.feature.admin.appupdate.AdminAppUpdateScreen(
            onBack = { showAppUpdate = false },
        )
        return
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        data class SettingsItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val premium: Boolean, val action: () -> Unit)
        buildList {
            add(SettingsItem(Icons.Default.Palette, "Cooperative Profile", false, onOpenBranding))
            if (state.isSuperAdmin) {
                add(SettingsItem(Icons.Default.PeopleAlt, "Role Management", false) {
                    viewModel.loadRoles(); dialog = SettingsDialog.ROLES
                })
            }
            add(SettingsItem(Icons.Default.AccountBalance, "Bank & Cash", true) {
                viewModel.loadBankAccounts(); dialog = SettingsDialog.BANK_ACCOUNTS
            })
            add(SettingsItem(Icons.Default.Percent, "Savings Interest", false) { dialog = SettingsDialog.SAVINGS_INTEREST })
            add(SettingsItem(Icons.Default.MoneyOff, "Late Fees", false) { dialog = SettingsDialog.LATE_FEES })
            add(SettingsItem(Icons.Default.Subscriptions, "Subscription", false) { showSubscription = true })
            add(SettingsItem(Icons.Default.CurrencyExchange, "Currency (${currentCurrency.flag} ${currentCurrency.code})", false) { showCurrencyPicker = true })
            if (state.isSuperAdmin) {
                add(SettingsItem(Icons.Default.AccountBalanceWallet, "Payment Bank Details", false) { showBankDetails = true })
                add(SettingsItem(Icons.Default.Receipt, "Subscription Requests", false) { showSubscriptionOrders = true })
                add(SettingsItem(Icons.Default.Sell, "Pricing Plans", false) { showPricingPlans = true })
                add(SettingsItem(Icons.Default.Business, "Manage Cooperatives", false) { showManageCooperatives = true })
                add(SettingsItem(Icons.Default.SystemUpdate, "App Updates", false) { showAppUpdate = true })
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
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.bankAccounts.forEach { acc -> BankAccountCard(acc) }
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
                            OutlinedTextField(
                                value = accNumber,
                                onValueChange = { accNumber = it.filter { c -> c.isDigit() }.take(10) },
                                label = { Text("Account Number") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(value = accName, onValueChange = { accName = it }, label = { Text("Account Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = { hasSubmitted = true; viewModel.addBankAccount(bankName, accNumber, accName) },
                                enabled = bankName.isNotBlank() && accNumber.length == 10 && accName.isNotBlank() && !state.isSavingBankAccount,
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
                            var menuExpanded by remember(role.userId) { mutableStateOf(false) }
                            val userId = role.userId
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(role.memberName ?: "Unknown member", style = MaterialTheme.typography.bodySmall)
                                Box {
                                    OutlinedButton(
                                        onClick = { menuExpanded = true },
                                        enabled = state.updatingUserId != userId,
                                    ) {
                                        if (state.updatingUserId == userId) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        else Text(roleDisplayName(role.role), style = MaterialTheme.typography.bodySmall)
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        ASSIGNABLE_ROLES.forEach { r ->
                                            DropdownMenuItem(text = { Text(roleDisplayName(r)) }, onClick = {
                                                menuExpanded = false
                                                viewModel.changeRole(userId, r)
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
        SettingsDialog.SAVINGS_INTEREST -> {
            val coop = state.cooperative
            var rate by remember(coop) { mutableStateOf(coop?.savingsInterestRate?.toString() ?: "0") }
            var method by remember(coop) { mutableStateOf(coop?.savingsInterestMethod ?: SavingsInterestMethod.SIMPLE_ESTIMATE) }

            AlertDialog(
                onDismissRequest = { dialog = SettingsDialog.NONE; viewModel.clearInterestError() },
                title = { Text("Savings Interest") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.interestError?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "Interest paid to members on their savings/contribution balance.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        OutlinedTextField(
                            value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Annual Rate (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Method", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { method = SavingsInterestMethod.SIMPLE_ESTIMATE }) {
                            RadioButton(selected = method == SavingsInterestMethod.SIMPLE_ESTIMATE, onClick = { method = SavingsInterestMethod.SIMPLE_ESTIMATE })
                            Column {
                                Text("Simple Estimate", style = MaterialTheme.typography.bodyMedium)
                                Text("Shown to members as an estimate — no money moves.", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { method = SavingsInterestMethod.PERIODIC_CREDIT }) {
                            RadioButton(selected = method == SavingsInterestMethod.PERIODIC_CREDIT, onClick = { method = SavingsInterestMethod.PERIODIC_CREDIT })
                            Column {
                                Text("Periodic Credit", style = MaterialTheme.typography.bodyMedium)
                                Text("You manually credit accrued interest as a real transaction whenever you choose.", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }

                        if (coop?.savingsInterestMethod == SavingsInterestMethod.PERIODIC_CREDIT) {
                            HorizontalDivider()
                            Text(
                                "Last credited: ${coop.savingsInterestLastCreditedAt?.take(10) ?: "never"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { viewModel.applyInterestNow() },
                                enabled = !state.isApplyingInterest,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CoopGreen),
                            ) {
                                if (state.isApplyingInterest) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Apply Interest Now")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { rate.toDoubleOrNull()?.let { viewModel.updateSavingsInterestSettings(it, method) } },
                        enabled = rate.toDoubleOrNull() != null && !state.isSavingInterestSettings,
                    ) {
                        if (state.isSavingInterestSettings) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text("Close") } },
            )
        }
        SettingsDialog.LATE_FEES -> {
            val coop = state.cooperative
            var rate by remember(coop) { mutableStateOf(coop?.lateFeeRate?.toString() ?: "0") }
            var graceDays by remember(coop) { mutableStateOf(coop?.lateFeeGraceDays?.toString() ?: "0") }

            AlertDialog(
                onDismissRequest = { dialog = SettingsDialog.NONE; viewModel.clearLateFeeError() },
                title = { Text("Late Fees") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.lateFeeError?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "A penalty charged on overdue loans, as a percentage of the outstanding balance.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        OutlinedTextField(
                            value = rate, onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Fee Rate (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = graceDays, onValueChange = { graceDays = it.filter { c -> c.isDigit() } },
                            label = { Text("Grace Period (days)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )

                        HorizontalDivider()
                        Text(
                            "Scans every overdue loan past its due date + grace period and charges each one once.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Button(
                            onClick = { viewModel.applyLateFeesNow() },
                            enabled = !state.isApplyingLateFees,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CoopGreen),
                        ) {
                            if (state.isApplyingLateFees) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Apply Late Fees Now")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { rate.toDoubleOrNull()?.let { r -> viewModel.updateLateFeeSettings(r, graceDays.toIntOrNull() ?: 0) } },
                        enabled = rate.toDoubleOrNull() != null && !state.isSavingLateFeeSettings,
                    ) {
                        if (state.isSavingLateFeeSettings) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = SettingsDialog.NONE }) { Text("Close") } },
            )
        }
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

    if (showCurrencyPicker) {
        CurrencyPickerSheet(
            current = currentCurrency,
            onSelect = { config -> viewModel.changeCurrency(config); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false },
        )
    }
}

@Composable
private fun BankAccountCard(account: BankAccountRow) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Text(account.bank_name ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(account.account_name ?: "—", style = MaterialTheme.typography.bodyMedium)
            Text(account.account_number ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = CoopGold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPickerSheet(
    current: io.cooplink.app.core.domain.CurrencyConfig,
    onSelect: (io.cooplink.app.core.domain.CurrencyConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Select Currency", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "This updates immediately across all member and admin apps for your cooperative",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            io.cooplink.app.core.domain.SupportedCurrencies.all.forEach { c ->
                val isSelected = c.code == current.code
                Card(
                    onClick = { onSelect(c) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = if (isSelected) CardDefaults.cardColors(containerColor = CoopTeal.copy(alpha = 0.12f)) else CardDefaults.cardColors(),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CoopTeal) else null,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(c.flag, style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${c.code} • ${c.symbol}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = CoopTeal, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

