package io.cooplink.app.feature.member.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.ui.MemberAvatar
import io.cooplink.app.core.util.generateAndOpenStatementPdf
import io.cooplink.app.feature.shell.CooperativeBrandingUiState
import io.cooplink.app.feature.shell.CooperativeLogoImage
import io.cooplink.app.ui.theme.*

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onNavigateToKyc: () -> Unit = {},
    onNavigateToSavingsGoals: () -> Unit = {},
    onNavigateToStandingOrders: () -> Unit = {},
    onNavigateToDisputes: () -> Unit = {},
    branding: CooperativeBrandingUiState = CooperativeBrandingUiState(),
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val member = state.member
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showPersonalInfo by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.inactivityManager.resumeTimer()
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = mime.substringAfterLast("/").ifBlank { "jpg" }
            viewModel.uploadAvatar(bytes, extension)
        }
    }
    val statementViewerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.inactivityManager.resumeTimer()
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh    = viewModel::refresh,
        modifier     = Modifier.fillMaxSize(),
    ) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

        state.error?.let { err ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f)),
                shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = CoopError)
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Avatar card
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    if (member?.avatarUrl.isNullOrBlank()) {
                        Surface(
                            Modifier.size(100.dp).border(3.dp, CoopTeal, CircleShape),
                            shape = MaterialTheme.shapes.extraLarge, color = MemberGreen,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    member?.fullName?.firstOrNull()?.uppercase() ?: "M",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        MemberAvatar(
                            userId = member?.userId,
                            avatarPath = member?.avatarUrl,
                            fullName = member?.fullName,
                            signedUrlManager = viewModel.signedUrlManager,
                            resolvedUrlOverride = state.justUploadedAvatarUrl,
                            size = 100.dp,
                            modifier = Modifier.border(3.dp, CoopTeal, CircleShape),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.inactivityManager.pauseTimer(); pickPhotoLauncher.launch("image/*") },
                        modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).background(CoopTeal, CircleShape),
                    ) {
                        if (state.isUploadingAvatar) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.CameraAlt, "Change photo", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(member?.fullName ?: "My Account", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CooperativeLogoImage(branding.logoUrl, size = 20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(branding.name ?: "CoopLink", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                }
                Spacer(Modifier.height(10.dp))
                StatusBadge(member?.status ?: "active")
                Spacer(Modifier.height(14.dp))
                ProfileDetailRow(Icons.Default.Email, member?.email ?: "—")
                Spacer(Modifier.height(6.dp))
                ProfileDetailRow(Icons.Default.Phone, member?.phone ?: "—")

                // Copyable Member ID — there's no separate business "member_id"
                // code in the schema, so this is the member row's own id.
                Spacer(Modifier.height(14.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Member ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                            Text(member?.displayId ?: "—", style = MaterialTheme.typography.titleMedium, color = MemberGold, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            member?.displayId?.let {
                                clipboard.setText(AnnotatedString(it))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy Member ID", tint = MemberGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        val themeViewModel: io.cooplink.app.ui.theme.ThemeViewModel = hiltViewModel()
        val isDarkTheme by themeViewModel.isDark.collectAsState()
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            ListItem(
                leadingContent = { Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = CoopTeal, modifier = Modifier.size(20.dp)) },
                headlineContent = { Text("Dark Mode", color = MaterialTheme.colorScheme.onSurface) },
                trailingContent = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { themeViewModel.toggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = MemberGold, checkedTrackColor = MemberGold.copy(alpha = 0.4f)),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        val autoLogoutEnabled by viewModel.autoLogoutEnabled.collectAsState()
        val requireBiometric by viewModel.requireBiometric.collectAsState()
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Security Settings", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-logout after inactivity", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Logout after 15 minutes of no activity", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    }
                    Switch(
                        checked = autoLogoutEnabled,
                        onCheckedChange = { viewModel.setAutoLogout(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MemberGold, checkedTrackColor = MemberGold.copy(alpha = 0.4f)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(.08f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Require fingerprint on open", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Show a fingerprint prompt when the app is reopened from background",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    }
                    Switch(
                        checked = requireBiometric,
                        onCheckedChange = { viewModel.setRequireBiometric(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MemberGold, checkedTrackColor = MemberGold.copy(alpha = 0.4f)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(.08f))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { showChangePin = true },
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Change Transaction PIN", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.4f))
                }
            }
        }

        val items: List<Triple<ImageVector, String, () -> Unit>> = listOf(
            Triple(Icons.Default.Person, "Personal Information", { showPersonalInfo = true }),
            Triple(Icons.Default.Lock,   "Change Password",      { showChangePassword = true }),
            Triple(Icons.Default.Badge,  "KYC Documents",        onNavigateToKyc),
            Triple(Icons.Default.Download, "Download Statement", {
                viewModel.inactivityManager.pauseTimer()
                viewModel.downloadStatement { name, rows ->
                    generateAndOpenStatementPdf(context, name, rows, launch = statementViewerLauncher::launch)
                }
            }),
            Triple(Icons.Default.Notifications, "Notifications", onOpenNotifications),
            Triple(Icons.Default.Savings,    "Savings Goals",        onNavigateToSavingsGoals),
            Triple(Icons.Default.Autorenew,  "Standing Orders",      onNavigateToStandingOrders),
            Triple(Icons.Default.ReportProblem, "Disputes",          onNavigateToDisputes),
            Triple(Icons.Default.Logout,     "Logout",               onLogout),
        )

        items.forEach { (icon, label, action) ->
            Card(onClick = action, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ListItem(
                    leadingContent = {
                        Icon(icon, null, tint = if (label == "Logout") CoopError else CoopTeal,
                            modifier = Modifier.size(20.dp))
                    },
                    headlineContent = {
                        Text(label, color = if (label == "Logout") CoopError else MaterialTheme.colorScheme.onSurface)
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(.25f),
                            modifier = Modifier.size(16.dp))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
    }

    if (showPersonalInfo) {
        PersonalInfoDialog(
            fullName      = member?.fullName ?: "",
            phone         = member?.phone ?: "",
            isSaving      = state.isSavingInfo,
            error         = state.saveInfoError,
            onDismiss     = { showPersonalInfo = false; viewModel.clearSaveInfoError() },
            onSave        = { name, phone, dob, address -> viewModel.updatePersonalInfo(name, phone, dob, address) },
            justSaved     = !state.isSavingInfo && state.saveInfoError == null,
            onSaved       = { showPersonalInfo = false },
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            isSaving  = state.isChangingPassword,
            error     = state.passwordError,
            onDismiss = { showChangePassword = false; viewModel.clearPasswordError() },
            onSave    = { newPassword -> viewModel.changePassword(newPassword) },
            justSaved = !state.isChangingPassword && state.passwordError == null,
            onSaved   = { showChangePassword = false },
        )
    }

    if (showChangePin) {
        io.cooplink.app.feature.member.security.SetupPinScreen(onDone = { showChangePin = false })
    }
}

@Composable
private fun PersonalInfoDialog(
    fullName: String,
    phone: String,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, dob: String, address: String) -> Unit,
    justSaved: Boolean,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf(fullName) }
    var phoneValue by remember { mutableStateOf(phone) }
    var dob by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(justSaved) { if (hasSubmitted && justSaved) onSaved() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        title = { Text("Personal Information", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(value = phoneValue, onValueChange = { phoneValue = it },
                    label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(value = dob, onValueChange = { dob = it },
                    label = { Text("Date of Birth (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(value = address, onValueChange = { address = it },
                    label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; onSave(name, phoneValue, dob, address) },
                enabled = name.isNotBlank() && !isSaving,
                colors  = ButtonDefaults.buttonColors(containerColor = MemberGold),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CoopNavyDeep)
                else Text("Save", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(.5f)) } },
    )
}

@Composable
private fun ChangePasswordDialog(
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (newPassword: String) -> Unit,
    justSaved: Boolean,
    onSaved: () -> Unit,
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(justSaved) { if (hasSubmitted && justSaved) onSaved() }

    val mismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword
    val tooShort = newPassword.isNotEmpty() && newPassword.length < 6

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        title = { Text("Change Password", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(
                    value = newPassword, onValueChange = { newPassword = it },
                    label = { Text("New Password") }, singleLine = true,
                    isError = tooShort,
                    supportingText = { if (tooShort) Text("At least 6 characters") },
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                OutlinedTextField(
                    value = confirmPassword, onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") }, singleLine = true,
                    isError = mismatch,
                    supportingText = { if (mismatch) Text("Passwords don't match") },
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { hasSubmitted = true; onSave(newPassword) },
                enabled = newPassword.length >= 6 && newPassword == confirmPassword && !isSaving,
                colors  = ButtonDefaults.buttonColors(containerColor = MemberGold),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CoopNavyDeep)
                else Text("Save", color = CoopNavyDeep, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(.5f)) } },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = MemberGold,   unfocusedBorderColor  = MaterialTheme.colorScheme.onSurface.copy(.22f),
    focusedLabelColor    = MemberGold,   unfocusedLabelColor   = MaterialTheme.colorScheme.onSurface.copy(.4f),
    focusedTextColor     = MaterialTheme.colorScheme.onSurface,  unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor          = MemberGold,
)

@Composable
private fun StatusBadge(status: String) {
    val color = when (status.lowercase()) {
        "active"                -> CoopSuccess
        "suspended", "inactive" -> CoopError
        else                     -> MemberGold
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            status.replaceFirstChar { it.uppercase() },
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ProfileDetailRow(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(.4f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.7f))
    }
}
