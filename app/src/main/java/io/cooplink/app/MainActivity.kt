package io.cooplink.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.CompositionLocalProvider
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.AppReleaseConfig
import io.cooplink.app.core.data.AppUpdateRepository
import io.cooplink.app.core.data.CurrencyProvider
import io.cooplink.app.core.data.OnboardingPreferences
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.domain.AuthUser
import io.cooplink.app.core.domain.UserRole
import io.cooplink.app.core.payment.PaymentDeepLinkBus
import io.cooplink.app.core.security.BiometricHelper
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.core.ui.LocalCurrency
import io.cooplink.app.navigation.CoopLinkNavHost
import io.cooplink.app.navigation.Routes
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopLinkAdminTheme
import io.cooplink.app.ui.theme.CoopNavyDeep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val COLD_START_ROLE_MAX_ATTEMPTS = 3
private const val COLD_START_ROLE_RETRY_DELAY_MS = 1_000L

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var sessionPreferences: SessionPreferences
    @Inject lateinit var onboardingPreferences: OnboardingPreferences
    @Inject lateinit var paymentDeepLinkBus: PaymentDeepLinkBus
    @Inject lateinit var biometricHelper: BiometricHelper
    @Inject lateinit var inactivityManager: InactivityManager
    @Inject lateinit var currencyProvider: CurrencyProvider
    @Inject lateinit var appUpdateRepository: AppUpdateRepository

    private var isLocked by mutableStateOf(false)
    private var startDestination by mutableStateOf<String?>(null)

    // Non-null only once a fetched config confirms this build is actually
    // behind the minimum — checked in parallel with startDestination so a
    // slow/offline version check never delays the splash screen for the
    // common case (an up-to-date user).
    private var requiredUpdate by mutableStateOf<AppReleaseConfig?>(null)

    // Read synchronously from onResume (not a suspend context) — kept live
    // via the collector started below. Off by default: the user stays signed
    // in without a re-lock prompt unless they opt in from Security Settings.
    private var requireBiometric = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)

        splashScreen.setKeepOnScreenCondition { startDestination == null }

        lifecycleScope.launch {
            sessionPreferences.requireBiometricFlow.collect { requireBiometric = it }
        }

        lifecycleScope.launch {
            val config = appUpdateRepository.fetchConfig() ?: return@launch
            if (BuildConfig.VERSION_CODE < config.min_supported_version_code) {
                requiredUpdate = config
            }
        }

        // Resolve the actual role before deciding where to land — a session
        // existing locally doesn't tell us whether this user is an admin or
        // a member, and guessing wrong here previously sent admins to the
        // member shell after a cold start. If we already have the role from
        // the last successful login cached locally, route instantly from
        // that (no network wait) and reconcile with the server in the
        // background; only fall back to the network-gated retry loop when
        // there's no cached role yet (first launch after login on this
        // device, or the cache was cleared on logout).
        lifecycleScope.launch {
            startDestination = if (authRepository.isLoggedIn()) {
                val cachedRole = sessionPreferences.cachedRoleFlow.first()
                    ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }

                if (cachedRole != null) {
                    launch {
                        val user = runCatching { authRepository.fetchCurrentUser() }.getOrNull()
                        user?.cooperativeId?.let { loadAndSubscribeCurrency(it) }
                    }
                    if (cachedRole.isAdmin) Routes.ADMIN_SHELL else Routes.MEMBER_SHELL
                } else {
                    var user: AuthUser? = null
                    for (attempt in 1..COLD_START_ROLE_MAX_ATTEMPTS) {
                        user = runCatching { authRepository.fetchCurrentUser() }.getOrNull()
                        if (user != null || attempt == COLD_START_ROLE_MAX_ATTEMPTS) break
                        delay(COLD_START_ROLE_RETRY_DELAY_MS)
                    }
                    user?.cooperativeId?.let { loadAndSubscribeCurrency(it) }
                    when {
                        user == null        -> Routes.LOGIN
                        user.role.isAdmin   -> Routes.ADMIN_SHELL
                        else                 -> Routes.MEMBER_SHELL
                    }
                }
            } else if (onboardingPreferences.isDoneFlow.first()) {
                Routes.LOGIN
            } else {
                Routes.ONBOARDING
            }
        }

        setContent {
            CompositionLocalProvider(LocalCurrency provides currencyProvider) {
                CoopLinkAdminTheme {
                    Surface(Modifier.fillMaxSize()) {
                        val dest = startDestination
                        val blockingUpdate = requiredUpdate
                        if (blockingUpdate != null) {
                            UpdateRequiredScreen(blockingUpdate)
                        } else if (isLocked) {
                            LockScreen(onUnlock = ::promptBiometric)
                        } else if (dest != null) {
                            CoopLinkNavHost(startDestination = dest)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadAndSubscribeCurrency(cooperativeId: String) {
        currencyProvider.loadFromCooperative(cooperativeId)
        currencyProvider.subscribeToChanges(cooperativeId, lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        if (!requireBiometric) return
        // A picker/camera/share-sheet/download launched by this app also
        // triggers onPause→onResume — that's not a real "user left the app"
        // event, so skip the re-lock while one of those is in flight.
        if (inactivityManager.isPaused) return
        if (authRepository.isLoggedIn() && biometricHelper.isAvailable()) {
            isLocked = true
            promptBiometric()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun promptBiometric() {
        biometricHelper.authenticate(
            activity  = this,
            onSuccess = { isLocked = false },
            onError   = { /* leave locked; user can retry via the Unlock button */ },
        )
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "cooplink" && (uri.host == "payment-callback" || uri.host == "subscription-callback")) {
            paymentDeepLinkBus.emit(uri)
        }
    }
}

@Composable
private fun UpdateRequiredScreen(config: AppReleaseConfig) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(Modifier.fillMaxSize(), color = CoopNavyDeep) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.SystemUpdate, null, tint = CoopGold, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Update Required", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                config.update_message ?: "A new version of CoopLink is required to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val url = config.play_store_url ?: "https://play.google.com/store/apps/details?id=${context.packageName}"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoopGold),
            ) {
                Text("Update Now", color = CoopNavyDeep)
            }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = CoopNavyDeep) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Fingerprint, null, tint = CoopGold, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("CoopLink Locked", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Authenticate to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = CoopGold)) {
                Text("Unlock", color = CoopNavyDeep)
            }
        }
    }
}
