package io.cooplink.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.ui.theme.*

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val loginMode by viewModel.loginMode.collectAsState()
    var identifier by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(CoopNavyDark, CoopNavyMid, CoopNavyDeep))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(60.dp))

            IconButton(onClick = onBack, Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }

            Spacer(Modifier.height(32.dp))

            Icon(Icons.Default.LockReset, null,
                tint = CoopGold, modifier = Modifier.size(64.dp))

            Spacer(Modifier.height(24.dp))

            Text("Reset Password", style = MaterialTheme.typography.headlineMedium,
                color = Color.White, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))

            Text(
                if (loginMode == LoginMode.MEMBER)
                    "Enter your Member ID to receive a reset link"
                else
                    "Enter your email address to receive a reset link",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(.55f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))

            if (!sent) {
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (loginMode == LoginMode.MEMBER) "Member ID" else "Email Address")
                    },
                    leadingIcon = {
                        Icon(
                            if (loginMode == LoginMode.MEMBER) Icons.Default.Badge
                            else Icons.Default.Email, null,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (loginMode == LoginMode.MEMBER)
                            KeyboardType.Text else KeyboardType.Email,
                    ),
                    singleLine = true,
                    colors = resetFieldColors(),
                )

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        loading = true
                        error = null
                        viewModel.resetPassword(identifier, isAdmin = loginMode != LoginMode.MEMBER) { result ->
                            loading = false
                            result.onSuccess { sent = true }
                                .onFailure { error = "Could not send reset link. Please try again." }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled  = identifier.isNotBlank() && !loading,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = CoopGold, contentColor = CoopNavyDeep),
                    shape    = MaterialTheme.shapes.medium,
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp),
                        strokeWidth = 2.5.dp, color = CoopNavyDeep)
                    else Text("Send Reset Link", fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Success state
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CoopGreen.copy(alpha = 0.15f)),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = CoopGreen, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Reset link sent!", style = MaterialTheme.typography.titleLarge,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Check your email or SMS for the password reset link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(.65f), textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(24.dp))

                TextButton(onClick = onBack) {
                    Text("Back to Login", color = CoopGold,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun resetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor    = CoopGold,
    unfocusedBorderColor  = Color.White.copy(.25f),
    focusedLabelColor     = CoopGold,
    unfocusedLabelColor   = Color.White.copy(.45f),
    focusedTextColor      = Color.White,
    unfocusedTextColor    = Color.White,
    cursorColor           = CoopGold,
    focusedLeadingIconColor    = CoopGold,
    unfocusedLeadingIconColor  = Color.White.copy(.4f),
)
