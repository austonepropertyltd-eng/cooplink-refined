package io.cooplink.app.feature.member.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.cooplink.app.core.domain.IdType
import io.cooplink.app.core.domain.KycStatus
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.feature.shell.CooperativeBrandingUiState
import io.cooplink.app.ui.theme.*

@Composable
fun KycScreen(
    onBack: () -> Unit,
    inactivityManager: InactivityManager,
    branding: CooperativeBrandingUiState = CooperativeBrandingUiState(),
    viewModel: KycViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isMicrofinance = branding.isMicrofinance

    var selectedIdType by remember { mutableStateOf<IdType?>(null) }
    var idNumber by remember { mutableStateOf("") }
    var expandedIdType by remember { mutableStateOf(false) }

    LaunchedEffect(state.kycData.idType) {
        if (selectedIdType == null) selectedIdType = state.kycData.idType
    }
    LaunchedEffect(state.kycData.idNumber) {
        if (idNumber.isBlank()) idNumber = state.kycData.idNumber ?: ""
    }
    // Microfinance institutions require BVN — lock the selection regardless
    // of whatever the member had previously chosen.
    LaunchedEffect(isMicrofinance) {
        if (isMicrofinance) selectedIdType = IdType.BVN
    }

    val idDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        inactivityManager.resumeTimer()
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null) viewModel.uploadIdDocument(bytes)
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KYC Verification") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoopNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = CoopNavyDeep,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // STATUS BANNER
            item {
                val kyc = state.kycData
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = kyc.status.color.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, kyc.status.color.copy(alpha = 0.4f)),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(kyc.status.icon, null, tint = kyc.status.color, modifier = Modifier.size(36.dp))
                        Column {
                            Text(kyc.status.label, style = MaterialTheme.typography.titleMedium,
                                color = kyc.status.color, fontWeight = FontWeight.Bold)
                            Text(
                                when (kyc.status) {
                                    KycStatus.NOT_SUBMITTED -> "Complete your KYC to access all features"
                                    KycStatus.PENDING       -> "Your documents are being reviewed (1-2 days)"
                                    KycStatus.VERIFIED      -> "Your identity has been verified ✓"
                                    KycStatus.REJECTED      -> "Reason: ${kyc.rejectionReason ?: "Contact admin"}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            if (state.kycData.status != KycStatus.VERIFIED) {
                item {
                    Text("Step 1 — Select ID Type", style = MaterialTheme.typography.titleMedium,
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (isMicrofinance) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CoopTeal.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, CoopTeal.copy(alpha = 0.3f)),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = CoopTeal, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("ID Type: BVN Required", style = MaterialTheme.typography.titleSmall,
                                        color = CoopTeal, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Microfinance institutions require Bank Verification Number (BVN)",
                                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    } else {
                        ExposedDropdownMenuBox(expanded = expandedIdType, onExpandedChange = { expandedIdType = it }) {
                            OutlinedTextField(
                                value = selectedIdType?.label ?: "Select ID type",
                                onValueChange = {}, readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedIdType) },
                                colors = kycFieldColors(),
                            )
                            ExposedDropdownMenu(expanded = expandedIdType, onDismissRequest = { expandedIdType = false }) {
                                IdType.entries.forEach { type ->
                                    DropdownMenuItem(text = { Text(type.label) }, onClick = { selectedIdType = type; expandedIdType = false })
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Step 2 — Enter ID Number", style = MaterialTheme.typography.titleMedium,
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = idNumber,
                        onValueChange = { if (!isMicrofinance || it.length <= 11) idNumber = it },
                        label = { Text(if (isMicrofinance) "BVN (Required)" else "ID Number") },
                        placeholder = { Text(if (isMicrofinance) "Enter your 11-digit BVN" else "Enter your ID number") },
                        leadingIcon = { Icon(Icons.Default.Badge, null) },
                        isError = isMicrofinance && idNumber.isNotEmpty() && idNumber.length != 11,
                        supportingText = if (isMicrofinance) {
                            { Text("11-digit BVN required for microfinance KYC") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = kycFieldColors(),
                    )
                }

                item {
                    Text("Step 3 — Upload ID Document", style = MaterialTheme.typography.titleMedium,
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Card(
                        onClick = {
                            inactivityManager.pauseTimer()
                            idDocLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
                        border = BorderStroke(2.dp, if (state.kycData.idDocumentUrl != null) CoopGreen else CoopDarkBorder),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        if (state.kycData.idDocumentUrl != null) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = state.kycData.idDocumentUrl, contentDescription = "ID Document",
                                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = CoopGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Document uploaded — tap to change", style = MaterialTheme.typography.labelMedium, color = CoopGreen)
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CloudUpload, null, tint = CoopTeal, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Tap to upload ID document", color = Color.White)
                                Text("JPG, PNG — max 5MB", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f))
                            }
                        }
                    }
                }

                if (state.isUploading) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopDarkSurface)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = CoopTeal)
                                Spacer(Modifier.width(12.dp))
                                Text("Uploading document...", color = Color.White)
                            }
                        }
                    }
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

                item {
                    val canSubmit = if (isMicrofinance) {
                        idNumber.length == 11 && !state.isLoading && !state.isUploading
                    } else {
                        selectedIdType != null && idNumber.isNotBlank() && !state.isLoading && !state.isUploading
                    }
                    Button(
                        onClick = { viewModel.submitKyc(selectedIdType!!, idNumber) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = CoopGreen),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Submit KYC for Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your documents are stored securely. Only your cooperative admin can view them.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun kycFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = CoopGold,   unfocusedBorderColor  = Color.White.copy(alpha = 0.25f),
    focusedLabelColor    = CoopGold,   unfocusedLabelColor   = Color.White.copy(alpha = 0.45f),
    focusedTextColor     = Color.White, unfocusedTextColor   = Color.White,
    cursorColor          = CoopGold,
)
