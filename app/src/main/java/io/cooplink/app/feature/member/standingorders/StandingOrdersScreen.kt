package io.cooplink.app.feature.member.standingorders

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.feature.member.overview.toNaira
import io.cooplink.app.ui.theme.*

@Composable
fun StandingOrdersScreen(
    onBack: () -> Unit,
    viewModel: StandingOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Standing Orders") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoopNavy, titleContentColor = Color.White, navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = CoopNavyDeep,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }, containerColor = MemberGreen) {
                Icon(Icons.Default.Add, "New standing order", tint = Color.White)
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
                        "Automate recurring contributions or repayments. Your cooperative admin can see these on their end.",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.5f),
                    )
                }
                state.error?.let { err ->
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = CoopError)
                                Spacer(Modifier.width(8.dp))
                                Text(err, color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (state.orders.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                            shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Autorenew, null, tint = MemberGold, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No standing orders yet. Tap + to set one up", color = Color.White.copy(.45f))
                                }
                            }
                        }
                    }
                } else {
                    items(state.orders) { order -> StandingOrderCard(order, onToggle = { viewModel.setActive(order, !order.active) }) }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showCreate) {
        CreateOrderDialog(
            isSubmitting = state.isSubmitting,
            error        = state.submitError,
            onDismiss    = { showCreate = false; viewModel.clearSubmitError() },
            onSubmit     = { amount, freq, day, purpose -> viewModel.createOrder(amount, freq, day, purpose) },
        )
        LaunchedEffect(state.isSubmitting, state.submitError) {
            if (!state.isSubmitting && state.submitError == null && showCreate) showCreate = false
        }
    }
}

@Composable
private fun StandingOrderCard(order: StandingOrder, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(order.amount.toNaira(), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        order.purpose?.takeIf { it.isNotBlank() } ?: "Standing order",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.55f),
                    )
                }
                Switch(
                    checked = order.active, onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedTrackColor = MemberGreen),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    order.frequency.replaceFirstChar { it.uppercase() } +
                        (order.day_of_month?.let { " · Day $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MemberGold,
                )
                Surface(
                    color = (if (order.active) CoopGreen else Color.White.copy(alpha = 0.15f)).copy(alpha = if (order.active) 0.15f else 1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        if (order.active) "Active" else "Paused",
                        color = if (order.active) CoopGreen else Color.White.copy(.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateOrderDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (amount: Double, frequency: String, dayOfMonth: Int?, purpose: String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(OrderFrequency.MONTHLY) }
    var freqMenuExpanded by remember { mutableStateOf(false) }
    var dayOfMonth by remember { mutableStateOf("1") }
    var purpose by remember { mutableStateOf("") }
    val amountValue = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Standing Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())

                ExposedDropdownMenuBox(expanded = freqMenuExpanded, onExpandedChange = { freqMenuExpanded = it }) {
                    OutlinedTextField(
                        value = frequency.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(freqMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = freqMenuExpanded, onDismissRequest = { freqMenuExpanded = false }) {
                        OrderFrequency.ALL.forEach { f ->
                            DropdownMenuItem(text = { Text(f.replaceFirstChar { it.uppercase() }) },
                                onClick = { frequency = f; freqMenuExpanded = false })
                        }
                    }
                }

                if (frequency == OrderFrequency.MONTHLY) {
                    OutlinedTextField(
                        value = dayOfMonth, onValueChange = { dayOfMonth = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Day of Month (1-28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(value = purpose, onValueChange = { purpose = it }, label = { Text("Purpose (optional)") },
                    placeholder = { Text("e.g. Monthly contribution") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    amountValue?.let {
                        val day = if (frequency == OrderFrequency.MONTHLY) dayOfMonth.toIntOrNull()?.coerceIn(1, 28) else null
                        onSubmit(it, frequency, day, purpose)
                    }
                },
                enabled = amountValue != null && amountValue > 0 && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MemberGreen),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
