package io.cooplink.app.feature.admin.subscription

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
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
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopTeal
import io.cooplink.app.ui.theme.SkeletonCard

@Composable
fun AdminPricingPlansScreen(
    onBack: () -> Unit,
    viewModel: AdminPricingPlansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<AdminPricingPlan?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pricing Plans") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }, containerColor = CoopTeal) {
                Icon(Icons.Default.Add, "New plan")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading, onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                if (state.isLoading && state.plans.isEmpty()) {
                    items(3) { SkeletonCard() }
                } else if (state.plans.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No pricing plans yet. Tap + to create one.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            }
                        }
                    }
                } else {
                    items(state.plans, key = { it.id }) { plan -> PlanCard(plan, onEdit = { editingPlan = plan }) }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showCreate) {
        PricingPlanDialog(
            title = "New Pricing Plan", plan = null,
            isSaving = state.isSaving, error = state.saveError,
            onDismiss = { showCreate = false; viewModel.clearSaveError() },
            onCreate = { key, name, price, maxMembers -> viewModel.createPlan(key, name, price, maxMembers) },
            onUpdate = null,
            justSaved = state.justSaved,
            onSaved = { showCreate = false },
        )
    }

    editingPlan?.let { plan ->
        PricingPlanDialog(
            title = "Edit ${plan.plan_name}", plan = plan,
            isSaving = state.isSaving, error = state.saveError,
            onDismiss = { editingPlan = null; viewModel.clearSaveError() },
            onCreate = null,
            onUpdate = { name, price, maxMembers, discountPct, discountLabel, discountActive, featured, active ->
                viewModel.updatePlan(plan.id, name, price, maxMembers, discountPct, discountLabel, discountActive, featured, active)
            },
            justSaved = state.justSaved,
            onSaved = { editingPlan = null },
        )
    }
}

@Composable
private fun PlanCard(plan: AdminPricingPlan, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.plan_name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!plan.is_active) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = CoopError.copy(alpha = 0.15f)) {
                            Text("INACTIVE", color = CoopError, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                    if (plan.discount_active && plan.discount_percent > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = CoopGold.copy(alpha = 0.18f)) {
                            Text("${"%.0f".format(plan.discount_percent)}% OFF", color = CoopGold, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(
                    "₦${"%,.0f".format(plan.base_price)}" + (plan.max_members?.let { " · up to $it members" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = CoopTeal) }
        }
    }
}

@Composable
private fun PricingPlanDialog(
    title: String,
    plan: AdminPricingPlan?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: ((planKey: String, planName: String, basePrice: Double, maxMembers: Int?) -> Unit)?,
    onUpdate: ((planName: String, basePrice: Double, maxMembers: Int?, discountPct: Double, discountLabel: String?, discountActive: Boolean, featured: Boolean, active: Boolean) -> Unit)?,
    justSaved: Boolean,
    onSaved: () -> Unit,
) {
    var planKey by remember { mutableStateOf(plan?.plan_key ?: "") }
    var planName by remember { mutableStateOf(plan?.plan_name ?: "") }
    var basePrice by remember { mutableStateOf(plan?.base_price?.toString() ?: "") }
    var maxMembers by remember { mutableStateOf(plan?.max_members?.toString() ?: "") }
    var discountPercent by remember { mutableStateOf(plan?.discount_percent?.toString() ?: "0") }
    var discountLabel by remember { mutableStateOf(plan?.discount_label ?: "") }
    var discountActive by remember { mutableStateOf(plan?.discount_active ?: false) }
    var isFeatured by remember { mutableStateOf(plan?.is_featured ?: false) }
    var isActive by remember { mutableStateOf(plan?.is_active ?: true) }
    var hasSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(justSaved) { if (hasSubmitted && justSaved) onSaved() }

    val price = basePrice.toDoubleOrNull()
    val members = maxMembers.toIntOrNull()
    val discount = discountPercent.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

                if (plan == null) {
                    OutlinedTextField(value = planKey, onValueChange = { planKey = it.filter { c -> c.isLetterOrDigit() || c == '_' }.lowercase() },
                        label = { Text("Plan Key (e.g. starter, pro)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = planName, onValueChange = { planName = it }, label = { Text("Plan Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = basePrice, onValueChange = { basePrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Base Price (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = maxMembers, onValueChange = { maxMembers = it.filter { c -> c.isDigit() } },
                    label = { Text("Max Members (blank = unlimited)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())

                if (plan != null) {
                    HorizontalDivider()
                    OutlinedTextField(value = discountPercent, onValueChange = { discountPercent = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Discount %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = discountLabel, onValueChange = { discountLabel = it }, label = { Text("Discount Label (e.g. \"Launch Offer\")") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = discountActive, onCheckedChange = { discountActive = it })
                        Text("Discount Active")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isFeatured, onCheckedChange = { isFeatured = it })
                        Text("Featured")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                        Text("Active (visible to cooperatives)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasSubmitted = true
                    val p = price ?: return@Button
                    if (plan == null) {
                        if (planKey.isNotBlank()) onCreate?.invoke(planKey, planName, p, members)
                    } else {
                        onUpdate?.invoke(planName, p, members, discount, discountLabel, discountActive, isFeatured, isActive)
                    }
                },
                enabled = planName.isNotBlank() && price != null && (plan != null || planKey.isNotBlank()) && !isSaving,
                colors  = ButtonDefaults.buttonColors(containerColor = CoopTeal),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
