package io.cooplink.app.feature.member.overview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.feature.member.contributions.ContributionsScreen
import io.cooplink.app.feature.member.loans.LoansScreen
import io.cooplink.app.feature.member.notifications.NotificationsScreen
import io.cooplink.app.feature.member.profile.KycScreen
import io.cooplink.app.feature.member.profile.ProfileScreen
import io.cooplink.app.feature.member.transactions.TransactionsScreen
import io.cooplink.app.feature.shell.CooperativeBrandingViewModel
import io.cooplink.app.feature.shell.CooperativeLogoImage
import io.cooplink.app.ui.theme.*

private data class MemberTab(val route: String, val icon: ImageVector, val label: String)
private val memberTabs = listOf(
    MemberTab("overview",      Icons.Default.Home,        "Home"),
    MemberTab("contributions", Icons.Default.Savings,     "Savings"),
    MemberTab("loans",         Icons.Default.CreditScore, "Loans"),
    MemberTab("transactions",  Icons.Default.Receipt,     "History"),
    MemberTab("profile",       Icons.Default.Person,      "Profile"),
)

@Composable
fun MemberShell(
    onLogout: () -> Unit,
    inactivityManager: InactivityManager = hiltViewModel<MemberShellViewModel>().inactivityManager,
) {
    val nav     = rememberNavController()
    val context = LocalContext.current
    val branding by hiltViewModel<CooperativeBrandingViewModel>().state.collectAsState()

    val notificationSettingsViewModel: io.cooplink.app.feature.member.notifications.NotificationSettingsViewModel = hiltViewModel()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> notificationSettingsViewModel.saveDeviceToken(token) }
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token -> notificationSettingsViewModel.saveDeviceToken(token) }
            }
        } else {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> notificationSettingsViewModel.saveDeviceToken(token) }
        }
    }

    // StartActivityForResult (rather than a plain startActivity) gives a real
    // "we've returned" callback even for a fire-and-forget intent like this,
    // so resumeTimer() has somewhere precise to be called from.
    val whatsAppLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        inactivityManager.resumeTimer()
    }

    // ── Auto-logout on session expiry ────────────────────────────────────────
    val expired by inactivityManager.sessionExpired.collectAsState()
    LaunchedEffect(expired) {
        if (expired) {
            inactivityManager.acknowledge()
            onLogout()
        }
    }

    // Deliberately NOT using a blanket ON_PAUSE/ON_RESUME lifecycle observer
    // here: Android dispatches the identical onPause→onResume sequence
    // whether MainActivity is backgrounded by a picker/camera/share sheet we
    // launched ourselves, or by the user pressing Home and walking away. A
    // lifecycle observer can't tell those apart, so pausing/resuming the
    // timer there would suppress the inactivity timeout (and MainActivity's
    // biometric re-lock, which also reads isPaused) for real backgrounding
    // too — not just system pickers. Only explicit pauseTimer()/resumeTimer()
    // calls right around each actual picker/share Intent (see ProfileScreen,
    // AdminReportsScreen, etc.) can precisely capture "we triggered this
    // ourselves", so that's the only mechanism used.

    val isDarkTheme by hiltViewModel<io.cooplink.app.ui.theme.ThemeViewModel>().isDark.collectAsState()

    val isPinSet by hiltViewModel<io.cooplink.app.feature.member.security.TransactionPinViewModel>().isPinSet.collectAsState()
    var pinJustSetUp by remember { mutableStateOf(false) }

    if (!isPinSet && !pinJustSetUp) {
        CoopLinkMemberTheme(darkTheme = isDarkTheme) {
            io.cooplink.app.feature.member.security.SetupPinScreen(onDone = { pinJustSetUp = true })
        }
        return
    }

    CoopLinkMemberTheme(darkTheme = isDarkTheme, accentColor = io.cooplink.app.ui.theme.parseHexColorOrNull(branding.primaryColor)) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CooperativeLogoImage(branding.logoUrl, size = 40.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("${branding.name ?: "CoopLink"} › Member", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor    = CoopNavy,
                            titleContentColor = Color.White,
                        ),
                    )
                    io.cooplink.app.feature.shell.OfflineBanner()
                }
            },
            bottomBar = {
                NavigationBar(containerColor = CoopDarkSurface, tonalElevation = 0.dp) {
                    val back    by nav.currentBackStackEntryAsState()
                    val current = back?.destination
                    memberTabs.filter { branding.hasLoansModule || it.route != "loans" }.forEach { tab ->
                        val sel = current?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = sel,
                            onClick  = {
                                inactivityManager.onUserInteraction()
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon  = { Icon(tab.icon, tab.label,
                                tint = if (sel) MemberGold else Color.White.copy(.4f)) },
                            label = { Text(tab.label,
                                color = if (sel) MemberGold else Color.White.copy(.4f),
                                style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MemberGreen.copy(.2f)),
                        )
                    }
                }
            },
        ) { padding ->
            // Detect any touch to reset inactivity timer
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        if (isDarkTheme) Brush.verticalGradient(listOf(CoopNavyDark, CoopNavyMid, CoopNavyDeep))
                        else Brush.verticalGradient(listOf(CoopBackground, CoopBackground)),
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                inactivityManager.onUserInteraction()
                            }
                        }
                    }
            ) {
                val navigateToTab: (String, Boolean) -> Unit = { route, autoOpen ->
                    nav.navigate(if (autoOpen) "$route?autoOpen=true" else route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                }
                NavHost(nav, startDestination = "overview") {
                    composable("overview") {
                        OverviewScreen(
                            onLogout,
                            onOpenNotifications = { nav.navigate("notifications") },
                            onNavigateToTab      = navigateToTab,
                            onNavigateToKyc      = { nav.navigate("kyc") },
                            onNavigateToAirtime  = { nav.navigate("airtime") },
                            branding             = branding,
                        )
                    }
                    composable(
                        "contributions?autoOpen={autoOpen}",
                        arguments = listOf(navArgument("autoOpen") { type = NavType.BoolType; defaultValue = false }),
                    ) { backStackEntry ->
                        ContributionsScreen(autoOpenDialog = backStackEntry.arguments?.getBoolean("autoOpen") == true)
                    }
                    composable(
                        "loans?autoOpen={autoOpen}",
                        arguments = listOf(navArgument("autoOpen") { type = NavType.BoolType; defaultValue = false }),
                    ) { backStackEntry ->
                        LoansScreen(autoOpenDialog = backStackEntry.arguments?.getBoolean("autoOpen") == true)
                    }
                    composable("transactions")   { TransactionsScreen() }
                    composable("profile")        {
                        ProfileScreen(
                            onLogout,
                            onOpenNotifications = { nav.navigate("notifications") },
                            onNavigateToKyc     = { nav.navigate("kyc") },
                            onNavigateToSavingsGoals   = { nav.navigate("savings_goals") },
                            onNavigateToStandingOrders = { nav.navigate("standing_orders") },
                            onNavigateToDisputes       = { nav.navigate("disputes") },
                            branding            = branding,
                        )
                    }
                    composable("notifications")  { NotificationsScreen(onOpenSettings = { nav.navigate("notification_settings") }) }
                    composable("notification_settings") {
                        io.cooplink.app.feature.member.notifications.NotificationSettingsScreen(onBack = { nav.popBackStack() })
                    }
                    composable("kyc")            {
                        KycScreen(onBack = { nav.popBackStack() }, inactivityManager = inactivityManager, branding = branding)
                    }
                    composable("airtime")        { io.cooplink.app.feature.member.airtime.AirtimeScreen(onBack = { nav.popBackStack() }) }
                    composable("savings_goals")  {
                        io.cooplink.app.feature.member.savingsgoals.SavingsGoalsScreen(onBack = { nav.popBackStack() })
                    }
                    composable("standing_orders") {
                        io.cooplink.app.feature.member.standingorders.StandingOrdersScreen(onBack = { nav.popBackStack() })
                    }
                    composable("disputes")       {
                        io.cooplink.app.feature.member.disputes.DisputesScreen(onBack = { nav.popBackStack() })
                    }
                }

                DraggableFab(onClick = {
                    inactivityManager.onUserInteraction()
                    inactivityManager.pauseTimer()
                    whatsAppLauncher.launch(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/2347061365172?text=Hello, I need support.")))
                })
            }
        }
    }
}

/** Draggable WhatsApp chat button — members can move it out of the way of
 * content underneath, and it snaps to the nearest horizontal edge on release. */
@Composable
private fun BoxScope.DraggableFab(onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth  = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    var offsetX by remember { mutableStateOf(screenWidth - 80.dp) }
    var offsetY by remember { mutableStateOf(screenHeight - 200.dp) }
    var isDragging by remember { mutableStateOf(false) }

    FloatingActionButton(
        onClick = { if (!isDragging) onClick() },
        containerColor = Color(0xFF25D366),
        contentColor   = Color.White,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(with(density) { offsetX.roundToPx() }, with(density) { offsetY.roundToPx() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        isDragging = true
                        with(density) {
                            offsetX = (offsetX + dragAmount.x.toDp()).coerceIn(0.dp, screenWidth - 64.dp)
                            offsetY = (offsetY + dragAmount.y.toDp()).coerceIn(0.dp, screenHeight - 64.dp)
                        }
                    },
                    onDragEnd = {
                        val centerX = screenWidth / 2
                        offsetX = if (offsetX < centerX) 16.dp else screenWidth - 80.dp
                        isDragging = false
                    },
                )
            },
    ) {
        Icon(Icons.Default.Chat, "Chat Admin on WhatsApp", modifier = Modifier.size(28.dp))
    }
}
