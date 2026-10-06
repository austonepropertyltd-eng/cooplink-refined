package io.cooplink.app.feature.admin.appupdate

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.BuildConfig
import io.cooplink.app.ui.theme.CoopError

@Composable
fun AdminAppUpdateScreen(
    onBack: () -> Unit,
    viewModel: AdminAppUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var minVersionCode by remember(state.config) { mutableStateOf(state.config?.min_supported_version_code?.toString() ?: "1") }
    var latestVersionCode by remember(state.config) { mutableStateOf(state.config?.latest_version_code?.toString() ?: "") }
    var latestVersionName by remember(state.config) { mutableStateOf(state.config?.latest_version_name ?: "") }
    var updateMessage by remember(state.config) { mutableStateOf(state.config?.update_message ?: "") }
    var playStoreUrl by remember(state.config) { mutableStateOf(state.config?.play_store_url ?: "") }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Updates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { Text(it, color = CoopError, style = MaterialTheme.typography.bodySmall) }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Text(
                    "This device is running version code ${BuildConfig.VERSION_CODE} (${BuildConfig.VERSION_NAME}).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    "Anyone on a version code below the minimum is blocked on launch with an update screen until they install a newer version.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                OutlinedTextField(
                    value = minVersionCode, onValueChange = { minVersionCode = it.filter(Char::isDigit) },
                    label = { Text("Minimum supported version code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = latestVersionCode, onValueChange = { latestVersionCode = it.filter(Char::isDigit) },
                    label = { Text("Latest version code (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = latestVersionName, onValueChange = { latestVersionName = it },
                    label = { Text("Latest version name (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = updateMessage, onValueChange = { updateMessage = it },
                    label = { Text("Message shown to blocked users") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = playStoreUrl, onValueChange = { playStoreUrl = it },
                    label = { Text("Play Store URL (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        viewModel.save(
                            minSupportedVersionCode = minVersionCode.toIntOrNull() ?: 1,
                            latestVersionCode       = latestVersionCode.toIntOrNull(),
                            latestVersionName       = latestVersionName,
                            updateMessage           = updateMessage,
                            playStoreUrl            = playStoreUrl,
                        )
                    },
                    enabled = !state.isSaving && minVersionCode.toIntOrNull() != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                }
            }
        }
    }
}
