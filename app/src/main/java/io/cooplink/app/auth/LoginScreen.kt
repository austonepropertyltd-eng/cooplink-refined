package io.cooplink.app.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.R
import io.cooplink.app.ui.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess: (isAdmin: Boolean) -> Unit,
    onForgotPassword: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel(),
    themeViewModel: io.cooplink.app.ui.theme.ThemeViewModel = hiltViewModel(),
) {
    val state     by viewModel.state.collectAsState()
    val loginMode by viewModel.loginMode.collectAsState()
    val isDarkTheme by themeViewModel.isDark.collectAsState()
    val savedIdentifier by viewModel.savedIdentifier.collectAsState()
    val savedIsAdmin     by viewModel.savedIsAdmin.collectAsState()
    val focus     = LocalFocusManager.current
    val activity  = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity

    var identifier by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var showPass   by remember { mutableStateOf(false) }
    var showFullForm by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val showWelcomeBack = savedIdentifier != null && !showFullForm

    LaunchedEffect(loginMode) {
        identifier = ""
        password   = ""
        viewModel.clearError()
    }

    // Warms up the login edge function's cold-start container as soon as the
    // screen appears, so it's likely already awake by the time the user
    // finishes typing and submits — never shown to the user, best-effort only.
    LaunchedEffect(Unit) {
        viewModel.pingEdgeFunction()
    }

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) {
            onLoginSuccess((state as AuthUiState.Success).user.role.isAdmin)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                listOf(CoopNavyDark, CoopNavyMid, CoopNavyDeep)))
            .safeDrawingPadding(),
    ) {
        // This toggles the member app's saved theme preference — the login
        // background itself stays the fixed dark gradient above either way,
        // matching the standing "keep exactly" design rule for this screen.
        IconButton(
            onClick = { themeViewModel.toggle() },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).size(48.dp),
        ) {
            Surface(Modifier.size(36.dp), CircleShape, color = Color.White.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle member app theme",
                        tint = if (isDarkTheme) CoopGold else CoopNavy,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo
            Image(
                painter           = painterResource(R.drawable.cooplink_logo),
                contentDescription = "CoopLink",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.width(200.dp).height(80.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Africa's Cooperative Platform",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(.55f))

            Spacer(Modifier.height(40.dp))

            if (showWelcomeBack) {
                val sessionValid = viewModel.hasValidSession()
                val biometricAvailable = viewModel.isBiometricAvailable()

                Text("Welcome back", style = MaterialTheme.typography.headlineSmall,
                    color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(savedIdentifier.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = CoopGold)
                Spacer(Modifier.height(28.dp))

                if (sessionValid && biometricAvailable) {
                    Button(
                        onClick = {
                            biometricError = null
                            viewModel.authenticateBiometric(
                                activity,
                                onSuccess = { viewModel.loginWithSaved() },
                                onError   = { biometricError = it },
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled  = state !is AuthUiState.Loading,
                        colors   = ButtonDefaults.buttonColors(containerColor = CoopGold, contentColor = CoopNavyDeep),
                        shape    = MaterialTheme.shapes.medium,
                    ) {
                        if (state is AuthUiState.Loading) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = CoopNavyDeep)
                        } else {
                            Icon(Icons.Default.Fingerprint, null)
                            Spacer(Modifier.width(10.dp))
                            Text("Unlock with Biometrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    biometricError?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // Session expired (refresh token no longer valid) or no biometric
                    // enrolled on this device — a password is the only honest option;
                    // there is no locally cached credential to unlock with.
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; viewModel.clearError() },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("Password") },
                        leadingIcon   = { Icon(Icons.Default.Lock, null) },
                        trailingIcon  = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focus.clearFocus()
                            viewModel.setMode(if (savedIsAdmin) LoginMode.ADMIN else LoginMode.MEMBER)
                            viewModel.login(savedIdentifier.orEmpty(), password)
                        }),
                        singleLine = true, colors = fieldColors(),
                    )

                    val retrySavedLogin = {
                        focus.clearFocus()
                        viewModel.setMode(if (savedIsAdmin) LoginMode.ADMIN else LoginMode.MEMBER)
                        viewModel.login(savedIdentifier.orEmpty(), password)
                    }

                    AnimatedVisibility(state is AuthUiState.Error) {
                        val err = state as? AuthUiState.Error
                        LoginErrorCard(err?.message.orEmpty(), retryable = err?.retryable ?: true, onRetry = retrySavedLogin)
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick  = retrySavedLogin,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled  = password.isNotBlank() && state !is AuthUiState.Loading && state !is AuthUiState.Connecting,
                        colors   = ButtonDefaults.buttonColors(containerColor = CoopGold, contentColor = CoopNavyDeep),
                        shape    = MaterialTheme.shapes.medium,
                    ) { SignInButtonContent(state) }
                }

                Spacer(Modifier.height(20.dp))
                TextButton(
                    onClick = {
                        viewModel.forgetSavedAccount()
                        showFullForm = true
                        identifier = ""
                        password = ""
                        biometricError = null
                        viewModel.clearError()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Not you? Switch account", color = Color.White.copy(.6f))
                }
            } else {
            // Mode toggle
            Row(
                Modifier.background(Color.White.copy(.07f), MaterialTheme.shapes.extraLarge)
                    .padding(4.dp)
            ) {
                listOf(LoginMode.MEMBER to "Member", LoginMode.ADMIN to "Admin")
                    .forEach { (mode, label) ->
                        val sel = loginMode == mode
                        Button(
                            onClick    = { if (!sel) viewModel.toggleMode() },
                            modifier   = Modifier.weight(1f),
                            colors     = ButtonDefaults.buttonColors(
                                containerColor = if (sel) CoopGold else Color.Transparent,
                                contentColor   = if (sel) CoopNavyDeep else Color.White),
                            elevation  = ButtonDefaults.buttonElevation(0.dp),
                            shape      = MaterialTheme.shapes.extraLarge,
                        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                    }
            }

            Spacer(Modifier.height(32.dp))

            // Identifier field
            OutlinedTextField(
                value         = identifier,
                onValueChange = { identifier = it; viewModel.clearError() },
                modifier      = Modifier.fillMaxWidth(),
                label = { Text(if (loginMode == LoginMode.MEMBER) "Member ID" else "Email") },
                leadingIcon = {
                    Icon(if (loginMode == LoginMode.MEMBER) Icons.Default.Badge
                         else Icons.Default.Email, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (loginMode == LoginMode.MEMBER)
                        KeyboardType.Text else KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true, colors = fieldColors(),
            )

            Spacer(Modifier.height(14.dp))

            // Password field
            OutlinedTextField(
                value         = password,
                onValueChange = { password = it; viewModel.clearError() },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Password") },
                leadingIcon   = { Icon(Icons.Default.Lock, null) },
                trailingIcon  = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility, null,
                        )
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    viewModel.login(identifier, password)
                }),
                singleLine = true, colors = fieldColors(),
            )

            TextButton(onClick = onForgotPassword,
                modifier = Modifier.align(Alignment.End)) {
                Text("Forgot Password?", color = CoopGold)
            }

            // Error
            val retryFullLogin = { focus.clearFocus(); viewModel.login(identifier, password) }
            AnimatedVisibility(state is AuthUiState.Error) {
                val err = state as? AuthUiState.Error
                LoginErrorCard(err?.message.orEmpty(), retryable = err?.retryable ?: true, onRetry = retryFullLogin)
            }

            Button(
                onClick  = retryFullLogin,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled  = identifier.isNotBlank() && password.isNotBlank()
                        && state !is AuthUiState.Loading && state !is AuthUiState.Connecting,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = CoopGold, contentColor = CoopNavyDeep),
                shape    = MaterialTheme.shapes.medium,
            ) { SignInButtonContent(state) }
            }

            Spacer(Modifier.height(40.dp))
            Text("Powered by VFG Technology Ltd",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(.28f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor    = CoopGold,   unfocusedBorderColor  = Color.White.copy(.25f),
    focusedLabelColor     = CoopGold,   unfocusedLabelColor   = Color.White.copy(.45f),
    focusedTextColor      = Color.White,unfocusedTextColor    = Color.White,
    cursorColor           = CoopGold,
    focusedLeadingIconColor    = CoopGold, unfocusedLeadingIconColor  = Color.White.copy(.4f),
    focusedTrailingIconColor   = CoopGold, unfocusedTrailingIconColor = Color.White.copy(.4f),
)

@Composable
private fun LoginErrorCard(message: String, retryable: Boolean = true, onRetry: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = CoopError.copy(alpha = 0.12f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, null, tint = CoopError)
                Spacer(Modifier.width(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
            if (retryable) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = CoopError.copy(alpha = 0.25f), contentColor = Color.White),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

@Composable
private fun SignInButtonContent(state: AuthUiState) {
    when (state) {
        is AuthUiState.Connecting -> {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = CoopNavyDeep)
            Spacer(Modifier.width(10.dp))
            Text(
                if (state.maxAttempts > 1) "Retrying... (attempt ${state.attempt} of ${state.maxAttempts})" else "Connecting...",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
        }
        is AuthUiState.Loading -> {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = CoopNavyDeep)
            Spacer(Modifier.width(10.dp))
            Text(SigningInDots(state.message), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        else -> Text("Sign In", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SigningInDots(base: String): String {
    var dotsCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            dotsCount = (dotsCount % 3) + 1
        }
    }
    return "$base" + ".".repeat(dotsCount)
}
