package io.cooplink.app.feature.member.airtime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.ui.theme.*

private val QUICK_AMOUNTS = listOf(100.0, 200.0, 500.0, 1000.0, 2000.0)

@Composable
fun AirtimeScreen(
    onBack: () -> Unit,
    viewModel: AirtimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedNetwork by remember { mutableStateOf(Network.MTN) }
    var phone by remember(state.memberPhone) { mutableStateOf(state.memberPhone ?: "") }
    var selectedAmount by remember { mutableStateOf<Double?>(null) }
    var customAmount by remember { mutableStateOf("") }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) showConfetti = true
    }

    val amount = selectedAmount ?: customAmount.toDoubleOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buy Airtime") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CoopNavy, titleContentColor = Color.White, navigationIconContentColor = Color.White),
            )
        },
        containerColor = CoopNavyDeep,
    ) { padding ->
        if (state.successMessage != null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = CoopGreen, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                Text(state.successMessage ?: "", style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CoopGold)) {
                    Text("Done", color = CoopNavyDeep)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Select Network", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Network.entries.forEach { network ->
                        val selected = selectedNetwork == network
                        FilterChip(
                            selected = selected,
                            onClick = { selectedNetwork = network },
                            label = { Text(network.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(network.color),
                                selectedLabelColor = Color.White,
                                containerColor = CoopDarkSurface,
                                labelColor = Color.White.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Phone Number", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoopGold, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    ),
                )

                Spacer(Modifier.height(24.dp))
                Text("Amount", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_AMOUNTS.forEach { value ->
                        val selected = selectedAmount == value
                        FilterChip(
                            selected = selected,
                            onClick = { selectedAmount = value; customAmount = "" },
                            label = { Text("₦${value.toInt()}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoopGold, selectedLabelColor = CoopNavyDeep,
                                containerColor = CoopDarkSurface, labelColor = Color.White.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it; selectedAmount = null },
                    label = { Text("Or enter custom amount") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoopGold, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    ),
                )

                state.error?.let { err ->
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = CoopError)
                            Spacer(Modifier.width(8.dp))
                            Text(err, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { amount?.let { viewModel.buyAirtime(selectedNetwork, phone, it) } },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = phone.isNotBlank() && (amount ?: 0.0) > 0 && !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = CoopGold),
                ) {
                    if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CoopNavyDeep)
                    else Text("Buy Airtime", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    ConfettiOverlay(visible = showConfetti, onComplete = { showConfetti = false })
}
