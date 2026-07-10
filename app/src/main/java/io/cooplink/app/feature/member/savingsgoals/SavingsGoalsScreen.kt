package io.cooplink.app.feature.member.savingsgoals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency
import io.cooplink.app.ui.theme.*

@Composable
fun SavingsGoalsScreen(
    onBack: () -> Unit,
    viewModel: SavingsGoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var contributingTo by remember { mutableStateOf<SavingsGoal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings Goals") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoopNavy, titleContentColor = Color.White, navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = CoopNavyDeep,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }, containerColor = MemberGreen) {
                Icon(Icons.Default.Add, "New goal", tint = Color.White)
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh    = viewModel::refresh,
            modifier     = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                if (state.goals.isEmpty() && !state.isLoading) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                            shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Savings, null, tint = MemberGold, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No savings goals yet. Tap + to set one", color = Color.White.copy(.45f))
                                }
                            }
                        }
                    }
                } else {
                    items(state.goals) { goal -> SavingsGoalCard(goal, onContribute = { contributingTo = goal }) }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showCreate) {
        var hasSubmitted by remember { mutableStateOf(false) }
        CreateGoalDialog(
            isSubmitting = state.isSubmitting,
            error        = state.submitError,
            onDismiss    = { showCreate = false; viewModel.clearSubmitError() },
            onSubmit     = { name, amount -> hasSubmitted = true; viewModel.createGoal(name, amount, null) },
        )
        LaunchedEffect(state.isSubmitting, state.submitError) {
            if (hasSubmitted && !state.isSubmitting && state.submitError == null) showCreate = false
        }
    }

    contributingTo?.let { goal ->
        var hasSubmitted by remember(goal.id) { mutableStateOf(false) }
        ContributeDialog(
            goal = goal,
            isSubmitting = state.isSubmitting,
            error = state.submitError,
            onDismiss = { contributingTo = null; viewModel.clearSubmitError() },
            onSubmit = { amount -> hasSubmitted = true; viewModel.addToGoal(goal, amount) },
        )
        LaunchedEffect(state.isSubmitting, state.submitError) {
            if (hasSubmitted && !state.isSubmitting && state.submitError == null && contributingTo != null) contributingTo = null
        }
    }
}

@Composable
private fun SavingsGoalCard(goal: SavingsGoal, onContribute: () -> Unit) {
    val currency = currentCurrency()
    val progress = if (goal.target_amount > 0) (goal.current_amount / goal.target_amount).toFloat().coerceIn(0f, 1f) else 0f
    val isCompleted = goal.status == SavingsGoalStatus.COMPLETED

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(goal.goal_name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                if (isCompleted) {
                    Surface(color = CoopGreen.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                        Text("Completed ✓", color = CoopGreen, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(currency.format(goal.current_amount), style = MaterialTheme.typography.bodyMedium, color = MemberGold, fontWeight = FontWeight.SemiBold)
                Text("of ${currency.format(goal.target_amount)}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(.5f))
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (isCompleted) CoopGreen else MemberGreen,
                trackColor = Color.White.copy(alpha = 0.1f),
            )
            if (!isCompleted) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onContribute, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Money")
                }
            }
        }
    }
}

@Composable
private fun CreateGoalDialog(isSubmitting: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (name: String, amount: Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val amountValue = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Savings Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal Name") },
                    placeholder = { Text("e.g. New Laptop") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Target Amount (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { amountValue?.let { onSubmit(name, it) } },
                enabled = name.isNotBlank() && amountValue != null && amountValue > 0 && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MemberGreen),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContributeDialog(goal: SavingsGoal, isSubmitting: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    val amountValue = amount.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to \"${goal.goal_name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₦)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { amountValue?.let { onSubmit(it) } },
                enabled = amountValue != null && amountValue > 0 && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MemberGreen),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
