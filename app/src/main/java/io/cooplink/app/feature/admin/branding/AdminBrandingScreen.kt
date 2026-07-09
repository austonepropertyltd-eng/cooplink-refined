package io.cooplink.app.feature.admin.branding

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopNavy
import io.cooplink.app.ui.theme.CoopTeal

@Composable
fun AdminBrandingScreen(viewModel: AdminBrandingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coop = state.cooperative

    var coopName by remember(coop) { mutableStateOf(coop?.name ?: "") }
    var logoUrl by remember(coop) { mutableStateOf(coop?.logoUrl ?: "") }
    var primaryColor by remember(coop) { mutableStateOf(coop?.primaryColor ?: "#243C54") }
    var secondaryColor by remember(coop) { mutableStateOf(coop?.secondaryColor ?: "#FCB424") }
    var whatsapp by remember(coop) { mutableStateOf(coop?.whatsappNumber ?: "") }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSnackbar()
        }
    }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.inactivityManager.resumeTimer()
        uri?.let {
            val bytes = runCatching { context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } }.getOrNull()
            val mime = context.contentResolver.getType(it) ?: "image/jpeg"
            val ext = mime.substringAfterLast("/").ifBlank { "jpg" }
            if (bytes != null) viewModel.uploadLogo(bytes, ext) { url -> if (url != null) logoUrl = url }
        }
    }

    fun parseColor(hex: String, fallback: Color) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Cooperative Branding", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Customise how your cooperative appears", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }

        item {
            BrandingSection(title = "Logo") {
                Card(
                    Modifier.fillMaxWidth().height(140.dp),
                    colors = CardDefaults.cardColors(containerColor = parseColor(primaryColor, CoopNavy).copy(alpha = 0.3f)),
                    border = BorderStroke(2.dp, parseColor(primaryColor, CoopNavy)),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (logoUrl.isNotBlank()) {
                            AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.height(80.dp).fillMaxWidth(0.6f), contentScale = ContentScale.Fit)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                                Text("No logo set", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = logoUrl, onValueChange = { logoUrl = it },
                    label = { Text("Logo URL") }, placeholder = { Text("https://yoursite.com/logo.png") },
                    leadingIcon = { Icon(Icons.Default.Link, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.inactivityManager.pauseTimer(); logoLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, CoopTeal),
                    enabled = !state.isUploadingLogo,
                ) {
                    if (state.isUploadingLogo) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CoopTeal)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, tint = CoopTeal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Upload from Device", color = CoopTeal)
                    }
                }
            }
        }

        item {
            BrandingSection(title = "Cooperative Details") {
                OutlinedTextField(value = coopName, onValueChange = { coopName = it }, label = { Text("Cooperative Name") },
                    leadingIcon = { Icon(Icons.Default.Business, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = whatsapp, onValueChange = { whatsapp = it }, label = { Text("WhatsApp Number") },
                    leadingIcon = { Icon(Icons.Default.Chat, null) }, placeholder = { Text("2347061365172") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }

        item {
            BrandingSection(title = "Colour Scheme") {
                Text("Your cooperative's colours appear throughout the member app", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorSwatch(Modifier.weight(1f), "Primary Color", primaryColor, parseColor(primaryColor, CoopNavy))
                    ColorSwatch(Modifier.weight(1f), "Accent Color", secondaryColor, parseColor(secondaryColor, CoopGold))
                }
                Spacer(Modifier.height(12.dp))
                Text("Quick Presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                val presets = listOf(
                    "CoopLink Blue" to ("#243C54" to "#FCB424"),
                    "Forest Green" to ("#1A4731" to "#F6C90E"),
                    "Royal Purple" to ("#4A235A" to "#F0B429"),
                    "Deep Red" to ("#7B241C" to "#F4D03F"),
                    "Ocean Blue" to ("#1A5276" to "#1ABC9C"),
                    "Midnight" to ("#17202A" to "#2ECC71"),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (name, colors) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { primaryColor = colors.first; secondaryColor = colors.second },
                        ) {
                            Box(Modifier.size(48.dp)) {
                                Box(Modifier.fillMaxSize().clip(CircleShape).background(parseColor(colors.first, CoopNavy)))
                                Box(Modifier.size(24.dp).align(Alignment.BottomEnd).clip(CircleShape).background(parseColor(colors.second, CoopGold)))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                        }
                    }
                }
            }
        }

        item {
            BrandingSection(title = "Preview") {
                Text("How your member app will look:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth().height(180.dp).background(parseColor(primaryColor, CoopNavy)).padding(16.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(Modifier.size(36.dp), CircleShape, color = parseColor(secondaryColor, CoopGold)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(coopName.firstOrNull()?.toString() ?: "C", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    }
                                }
                                Text(coopName.ifEmpty { "Your Cooperative" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Wallet Balance", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            Text("₦24,500.00", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.save(coopName, logoUrl, primaryColor, secondaryColor, whatsapp) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = coopName.isNotBlank() && !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = io.cooplink.app.ui.theme.CoopGreen),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Branding Changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ColorSwatch(modifier: Modifier, label: String, hex: String, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(MaterialTheme.shapes.medium).background(color)
                .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text("Tap a preset below", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(hex, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BrandingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CoopGold, modifier = Modifier.padding(bottom = 8.dp))
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}
