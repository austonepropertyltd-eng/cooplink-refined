package io.cooplink.app.feature.admin.subscription

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.ui.theme.*

private const val UPGRADE_WHATSAPP_NUMBER = "2347061365172"

// Shown only until a super admin sets a real active row in Payment Bank
// Details (Admin -> Settings -> Payment Bank Details, backed by the
// payment_bank_details table) — SubscriptionViewModel prefers that row
// whenever one exists.
private const val FALLBACK_BANK_NAME = "VFG Technology Ltd Bank"
private const val FALLBACK_ACCOUNT_NAME = "VFG Technology Ltd"
private const val FALLBACK_ACCOUNT_NUMBER = "Contact 07061365172"

@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    inactivityManager: InactivityManager,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var selectedPlan by remember { mutableStateOf<PricingPlan?>(null) }
    var showBankTransferSheet by remember { mutableStateOf(false) }
    var showTransferConfirmation by remember { mutableStateOf(false) }
    var transferReference by remember { mutableStateOf("") }
    var showConfetti by remember { mutableStateOf(false) }

    val externalIntentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { inactivityManager.resumeTimer() }

    // React to Paystack payment state changes — open the Custom Tab as soon
    // as the edge function hands back an authorization URL, and celebrate on
    // confirmed success. There's no activity-result callback for a Custom Tab
    // (launchUrl isn't a startActivityForResult contract) — the deep link
    // callback that fires onPaystackCallback() is what resumes the timer.
    LaunchedEffect(state.payment) {
        when (val payment = state.payment) {
            is SubscriptionPaymentState.ReadyToOpen -> {
                inactivityManager.pauseTimer()
                viewModel.openPaystack(context, payment.url)
            }
            is SubscriptionPaymentState.Success -> {
                showConfetti = true
                Toast.makeText(context, "Upgraded to ${payment.planName}!", Toast.LENGTH_LONG).show()
                viewModel.resetPayment()
            }
            is SubscriptionPaymentState.Failed -> {
                Toast.makeText(context, payment.message, Toast.LENGTH_LONG).show()
                viewModel.resetPayment()
            }
            else -> {}
        }
    }

    PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Spacer(Modifier.width(4.dp))
                Text("Subscription", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }

            state.error?.let { err ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = CoopError)
                        Spacer(Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            CurrentPlanCard(state)

            Text("Available Plans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            state.plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    isProcessing = state.payment !is SubscriptionPaymentState.Idle && state.payment !is SubscriptionPaymentState.Failed,
                    onPayWithPaystack = { selectedPlan = plan; viewModel.startPaystackPayment(plan) },
                    onPayByBankTransfer = { selectedPlan = plan; showBankTransferSheet = true },
                )
            }

            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp)) {
                    Text("Need help choosing?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Contact VFG Technology Ltd", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                inactivityManager.pauseTimer()
                                externalIntentLauncher.launch(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://wa.me/$UPGRADE_WHATSAPP_NUMBER?text=${Uri.encode("I want to upgrade my CoopLink plan")}")))
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color(0xFF25D366)),
                        ) {
                            Icon(Icons.Default.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp", color = Color(0xFF25D366))
                        }
                        Button(
                            onClick = {
                                inactivityManager.pauseTimer()
                                externalIntentLauncher.launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$UPGRADE_WHATSAPP_NUMBER")))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CoopTeal),
                        ) {
                            Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Call Us")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    selectedPlan?.let { plan ->
        if (showBankTransferSheet) {
            BankTransferSheet(
                plan = plan,
                bankDetail = state.bankDetail,
                cooperativeId = state.cooperativeId,
                onDismiss = { showBankTransferSheet = false },
                onConfirmed = { reference ->
                    viewModel.recordBankTransferIntent(plan, reference)
                    transferReference = reference
                    showBankTransferSheet = false
                    showTransferConfirmation = true
                },
            )
        }
    }

    if (showTransferConfirmation) {
        TransferConfirmationDialog(
            planName = selectedPlan?.planName ?: "",
            reference = transferReference,
            onDismiss = { showTransferConfirmation = false },
        )
    }

    io.cooplink.app.ui.theme.ConfettiOverlay(visible = showConfetti, onComplete = { showConfetti = false })
}

@Composable
private fun CurrentPlanCard(state: SubscriptionUiState) {
    val usageFraction = if (state.maxMembers > 0)
        (state.membersUsed.toFloat() / state.maxMembers).coerceIn(0f, 1f) else 0f

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Current Plan", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Text(state.currentPlanName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Surface(color = CoopGold.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        state.subscriptionStatus.replaceFirstChar { it.uppercase() },
                        color = CoopGold, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                state.trialEndsAt?.let { "Trial ends $it" } ?: "No expiry date on file",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(.6f),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Members", style = MaterialTheme.typography.bodySmall)
                Text("${state.membersUsed}/${state.maxMembers}",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { usageFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = CoopGold,
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: PricingPlan,
    isProcessing: Boolean,
    onPayWithPaystack: () -> Unit,
    onPayByBankTransfer: () -> Unit,
) {
    val planColor = if (plan.isFeatured) CoopGold else CoopTeal
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = if (plan.isFeatured) CardDefaults.cardColors(containerColor = planColor.copy(alpha = 0.12f)) else CardDefaults.cardColors(),
        border = BorderStroke(if (plan.isFeatured) 2.dp else 1.dp, if (plan.isFeatured) planColor else planColor.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (plan.isFeatured) {
                Surface(shape = MaterialTheme.shapes.small, color = planColor, modifier = Modifier.align(Alignment.End)) {
                    Text("MOST POPULAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(plan.planName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = planColor)
            Spacer(Modifier.height(4.dp))
            PriceDisplay(plan)
            plan.maxMembers?.let {
                Text(
                    if (it >= 999_999) "unlimited members" else "up to $it members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(.6f),
                )
            }
            Spacer(Modifier.height(12.dp))
            plan.features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Icon(Icons.Default.Check, null, tint = planColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (plan.isPayable) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onPayWithPaystack,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoopGreen),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pay with Paystack", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPayByBankTransfer,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.dp, CoopGold),
                ) {
                    Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(18.dp), tint = CoopGold)
                    Spacer(Modifier.width(8.dp))
                    Text("Pay by Bank Transfer", color = CoopGold, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PriceDisplay(plan: PricingPlan) {
    Column {
        if (plan.hasDiscount) {
            Text(
                text = "₦${"%,.0f".format(plan.basePrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(.4f),
                textDecoration = TextDecoration.LineThrough,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(plan.displayPrice, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (plan.isPayable) {
                Spacer(Modifier.width(4.dp))
                Text("/month", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(.5f))
            }
        }
        if (plan.hasDiscount) {
            Surface(shape = MaterialTheme.shapes.small, color = CoopError, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    "-${plan.discountPercent.toInt()}% ${plan.discountLabel ?: "OFF"}",
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankTransferSheet(
    plan: PricingPlan,
    bankDetail: io.cooplink.app.feature.admin.paymentbankdetails.PaymentBankDetail?,
    cooperativeId: String?,
    onDismiss: () -> Unit,
    onConfirmed: (reference: String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val reference = remember(plan) { "COOP-${(cooperativeId ?: "SUB").take(8)}-${plan.planKey.uppercase()}" }
    val bankName = bankDetail?.bank_name ?: FALLBACK_BANK_NAME
    val accountName = bankDetail?.account_name ?: FALLBACK_ACCOUNT_NAME
    val accountNumber = bankDetail?.account_number ?: FALLBACK_ACCOUNT_NUMBER

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(24.dp)) {
            Text("Bank Transfer Details", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopNavy),
                border = BorderStroke(1.dp, CoopGold.copy(alpha = 0.4f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Amount to Transfer", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(.6f))
                    Text("₦${"%,.2f".format(plan.finalPrice)}", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, color = CoopGold)
                    Text("${plan.planName} Plan — 1 Month", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.5f))
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BankDetailRow("Bank Name", bankName)
                    BankDetailRow("Account Name", accountName)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Account Number", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.5f))
                            Text(accountNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(accountNumber))
                            Toast.makeText(context, "Account number copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, null, tint = CoopGold) }
                    }

                    HorizontalDivider(color = Color.White.copy(.08f))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Payment Reference", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.5f))
                            Text(reference, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CoopTeal)
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(reference))
                            Toast.makeText(context, "Reference copied!", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, null, tint = CoopTeal) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopGold.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, CoopGold.copy(alpha = 0.3f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = CoopGold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Use the reference above as your transfer narration. Your plan will be activated within 2-4 hours after payment is confirmed.",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.8f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onConfirmed(reference) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoopGreen),
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("I've Made the Transfer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = {
                    val msg = Uri.encode(
                        "Hello VFG Technology, I have made a bank transfer of ₦${"%,.2f".format(plan.finalPrice)} " +
                            "for CoopLink ${plan.planName} plan.\nReference: $reference\nPlease activate my subscription.",
                    )
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$UPGRADE_WHATSAPP_NUMBER?text=$msg")))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Confirm via WhatsApp", color = Color(0xFF25D366))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BankDetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.5f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TransferConfirmationDialog(planName: String, reference: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CoopDarkSurface,
        icon = { Icon(Icons.Default.CheckCircle, null, tint = CoopGreen, modifier = Modifier.size(64.dp)) },
        title = { Text("Transfer Submitted!", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Your transfer has been recorded.\nYour $planName plan will be activated within 2-4 hours after VFG Technology confirms your payment.",
                    textAlign = TextAlign.Center, color = Color.White.copy(.7f),
                )
                Spacer(Modifier.height(16.dp))
                Text("Reference: $reference", style = MaterialTheme.typography.labelLarge, color = CoopGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Screenshot this reference or send it on WhatsApp to expedite activation.",
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.5f), textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CoopGreen)) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$UPGRADE_WHATSAPP_NUMBER")))
            }) { Text("WhatsApp Us", color = Color(0xFF25D366)) }
        },
    )
}
