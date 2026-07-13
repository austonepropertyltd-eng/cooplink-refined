package io.cooplink.app.feature.admin.dashboard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import io.cooplink.app.feature.admin.contributions.AdminContributionsScreen
import io.cooplink.app.feature.admin.contributions.AdminSavingsScreen
import io.cooplink.app.feature.admin.loans.AdminLoansScreen
import io.cooplink.app.feature.admin.members.AdminMembersScreen
import io.cooplink.app.feature.admin.notifications.AdminNotificationsScreen
import io.cooplink.app.feature.admin.repayments.AdminRepaymentsScreen
import io.cooplink.app.feature.admin.reports.AdminReportsScreen
import io.cooplink.app.feature.admin.settings.AdminSettingsScreen
import io.cooplink.app.feature.admin.sharecapital.AdminShareCapitalScreen
import io.cooplink.app.feature.admin.sms.AdminSmsScreen
import io.cooplink.app.feature.admin.statements.AdminStatementsScreen
import io.cooplink.app.feature.admin.transactions.AdminTransactionsScreen
import io.cooplink.app.feature.shell.CooperativeBrandingViewModel
import io.cooplink.app.feature.shell.CooperativeLogoImage
import io.cooplink.app.ui.theme.*
import kotlinx.coroutines.launch

private data class AdminTab(val route: String, val icon: ImageVector, val label: String)
private val adminTabs = listOf(
    AdminTab("overview",  Icons.Default.Dashboard,     "Dashboard"),
    AdminTab("members",   Icons.Default.Group,         "Members"),
    AdminTab("loans",     Icons.Default.CreditScore,   "Finance"),
    AdminTab("reports",   Icons.Default.BarChart,      "Reports"),
    AdminTab("settings",  Icons.Default.Settings,      "Settings"),
)

// Drawer/tab routes the web platform hides for investment_club/thrift_ajo/
// savings_group cooperatives — see Cooperative.hasLoansModule.
private val LOANS_MODULE_ROUTES = setOf("loans", "repayments")

@Composable
fun AdminShell(
    onLogout: () -> Unit,
    inactivityManager: io.cooplink.app.core.security.InactivityManager = hiltViewModel<AdminShellViewModel>().inactivityManager,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope  = rememberCoroutineScope()
    val nav    = rememberNavController()
    val branding by hiltViewModel<CooperativeBrandingViewModel>().state.collectAsState()
    val showLoansModule = branding.hasLoansModule

    val expired by inactivityManager.sessionExpired.collectAsState()
    LaunchedEffect(expired) {
        if (expired) {
            inactivityManager.acknowledge()
            onLogout()
        }
    }

    val isPinSet by hiltViewModel<io.cooplink.app.feature.member.security.TransactionPinViewModel>().isPinSet.collectAsState()
    var pinJustSetUp by remember { mutableStateOf(false) }
    if (!isPinSet && !pinJustSetUp) {
        CoopLinkAdminTheme {
            io.cooplink.app.feature.member.security.SetupPinScreen(onDone = { pinJustSetUp = true })
        }
        return
    }

    CoopLinkAdminTheme(accentColor = io.cooplink.app.ui.theme.parseHexColorOrNull(branding.primaryColor)) {
        ModalNavigationDrawer(
            drawerState   = drawer,
            drawerContent = {
                AdminDrawer(
                    cooperativeName = branding.name,
                    logoUrl         = branding.logoUrl,
                    items           = drawerItems.filter { showLoansModule || it.route !in LOANS_MODULE_ROUTES },
                    onNavigate = { route ->
                        nav.navigate(route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                        scope.launch { drawer.close() }
                    },
                    onLogout = onLogout,
                )
            },
        ) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CooperativeLogoImage(branding.logoUrl, size = 40.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${branding.name ?: "CoopLink"} › Admin", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(8.dp))
                                    OrgTypeBadge(branding.orgType)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(Icons.Default.Menu, "Menu")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor             = CoopNavy,
                                titleContentColor          = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor     = Color.White,
                            ),
                        )
                        io.cooplink.app.feature.shell.OfflineBanner()
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = CoopNavy, tonalElevation = 0.dp) {
                        val back by nav.currentBackStackEntryAsState()
                        val current = back?.destination
                        adminTabs.filter { showLoansModule || it.route !in LOANS_MODULE_ROUTES }.forEach { tab ->
                            val sel = current?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = sel,
                                onClick = {
                                    inactivityManager.onUserInteraction()
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon  = { Icon(tab.icon, tab.label, tint = if (sel) CoopGold else Color.White.copy(.45f)) },
                                label = { Text(tab.label, color = if (sel) CoopGold else Color.White.copy(.45f),
                                    style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = CoopGold.copy(.15f)),
                            )
                        }
                    }
                },
            ) { padding ->
                val navigateToTab: (String) -> Unit = { route ->
                    inactivityManager.onUserInteraction()
                    nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                }
                NavHost(
                    nav, startDestination = "overview",
                    modifier = Modifier.padding(padding).pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                inactivityManager.onUserInteraction()
                            }
                        }
                    },
                ) {
                    composable("overview")      { AdminDashboardScreen(onLogout, onNavigateToTab = navigateToTab) }
                    composable("members")       { AdminMembersScreen(branding = branding) }
                    composable("loans")         { AdminLoansScreen() }
                    composable("reports")       { AdminReportsScreen() }
                    composable("settings")      { AdminSettingsScreen(onLogout, onOpenBranding = { nav.navigate("branding") }) }
                    composable("contributions") { AdminContributionsScreen() }
                    composable("share_capital") { AdminShareCapitalScreen() }
                    composable("savings")       { AdminSavingsScreen() }
                    composable("repayments")    { AdminRepaymentsScreen() }
                    composable("transactions")  { AdminTransactionsScreen() }
                    composable("statements")    { AdminStatementsScreen() }
                    composable("sms")           { AdminSmsScreen() }
                    composable("notifications") { AdminNotificationsScreen() }
                    composable("kyc_review")    { io.cooplink.app.feature.admin.kyc.AdminKycReviewScreen() }
                    composable("payment_history") { io.cooplink.app.feature.admin.paymenthistory.AdminPaymentHistoryScreen() }
                    composable("accounting")     { io.cooplink.app.feature.admin.accounting.AdminAccountingScreen() }
                    composable("dividends")      { io.cooplink.app.feature.admin.dividends.AdminDividendsScreen() }
                    composable("branding")       { io.cooplink.app.feature.admin.branding.AdminBrandingScreen() }
                    composable("resources")      {
                        io.cooplink.app.feature.admin.resources.MarketingResourcesScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

private data class DrawerItem(val icon: ImageVector, val label: String, val route: String?, val premium: Boolean = false)

// route = null means there's no screen for it yet — shows a "Coming soon" toast
// instead of navigating. Several items intentionally point at an existing
// screen where that feature already lives (e.g. Bank & Cash/Branding/Roles are
// dialogs inside Settings; Disbursements is a tab inside Loans; Directory is
// the members list) rather than duplicating a screen that already exists.
private val drawerItems = listOf(
    DrawerItem(Icons.Default.Dashboard,            "Dashboard",         "overview"),
    DrawerItem(Icons.Default.Group,                "Members",           "members"),
    DrawerItem(Icons.Default.VerifiedUser,          "KYC Review",        "kyc_review", premium = true),
    DrawerItem(Icons.Default.Savings,               "Contributions",     "contributions"),
    DrawerItem(Icons.Default.PieChart,              "Share Capital",     "share_capital"),
    DrawerItem(Icons.Default.AccountBalanceWallet,  "Savings",           "savings"),
    DrawerItem(Icons.Default.CreditScore,           "Loans",             "loans"),
    DrawerItem(Icons.Default.Payments,              "Repayments",        "repayments"),
    DrawerItem(Icons.Default.Receipt,               "Transactions",      "transactions"),
    DrawerItem(Icons.Default.History,                "Payment History",  "payment_history"),
    DrawerItem(Icons.Default.AccountBalance,        "Accounting",        "accounting"),
    DrawerItem(Icons.Default.BarChart,              "Reports",           "reports"),
    DrawerItem(Icons.Default.CalendarMonth,         "Monthly Statements","statements"),
    DrawerItem(Icons.Default.Percent,                "Dividends",        "dividends"),
    DrawerItem(Icons.Default.AccountBalanceWallet,  "Bank & Cash",       "settings"),
    DrawerItem(Icons.Default.Palette,               "Branding",          "branding"),
    DrawerItem(Icons.Default.Sms,                   "SMS",               "sms", premium = true),
    DrawerItem(Icons.Default.Warning,                "Fraud Alerts",     null),
    DrawerItem(Icons.Default.LocalAtm,              "Disbursements",     "loans"),
    DrawerItem(Icons.Default.NotificationsActive,   "Reminders",         "sms"),
    DrawerItem(Icons.Default.AdminPanelSettings,    "Roles",             "settings"),
    DrawerItem(Icons.Default.UploadFile,            "Excel Import",      null),
    DrawerItem(Icons.Default.Campaign,               "Campaigns",        null),
    DrawerItem(Icons.Default.ContactPage,           "Directory",         "members"),
    DrawerItem(Icons.Default.Notifications,         "Notifications",    "notifications"),
    DrawerItem(Icons.Default.Slideshow,              "Marketing Resources", "resources"),
    DrawerItem(Icons.Default.Settings,              "Settings",          "settings"),
)

@Composable
private fun OrgTypeBadge(orgType: io.cooplink.app.core.domain.OrganizationType) {
    val color = when (orgType) {
        io.cooplink.app.core.domain.OrganizationType.MICROFINANCE    -> CoopTeal
        io.cooplink.app.core.domain.OrganizationType.COOPERATIVE     -> CoopGreen
        io.cooplink.app.core.domain.OrganizationType.INVESTMENT_CLUB -> CoopGold
        io.cooplink.app.core.domain.OrganizationType.THRIFT_AJO      -> Color(0xFF9B59B6)
        io.cooplink.app.core.domain.OrganizationType.SAVINGS_GROUP   -> Color(0xFF3498DB)
        io.cooplink.app.core.domain.OrganizationType.SACCO           -> CoopGreen
        io.cooplink.app.core.domain.OrganizationType.NGO             -> Color(0xFFE67E22)
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.2f)) {
        Text(
            orgType.label,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun AdminDrawer(
    cooperativeName: String?,
    logoUrl: String?,
    items: List<DrawerItem>,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    ModalDrawerSheet(drawerContainerColor = CoopNavy) {
        // Fixed header
        Spacer(Modifier.height(24.dp))
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CooperativeLogoImage(logoUrl, size = 64.dp)
            Spacer(Modifier.height(10.dp))
            Text(cooperativeName ?: "CoopLink", style = MaterialTheme.typography.titleLarge,
                color = Color.White, fontWeight = FontWeight.Bold)
            Text("Admin Portal", style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(.45f))
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color.White.copy(.1f))

        // Scrollable menu items
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
        ) {
            items.forEach { item ->
                NavigationDrawerItem(
                    icon     = { Icon(item.icon, null, tint = CoopTeal) },
                    label    = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, color = Color.White)
                            if (item.premium) {
                                Spacer(Modifier.width(8.dp))
                                Surface(shape = MaterialTheme.shapes.extraSmall, color = CoopGold.copy(alpha = 0.18f)) {
                                    Text(
                                        "PRO", color = CoopGold, style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    },
                    selected = false,
                    onClick  = {
                        if (item.route != null) onNavigate(item.route)
                        else Toast.makeText(context, "${item.label} coming soon", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors   = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor   = CoopGold.copy(.15f)),
                )
            }
        }

        // Fixed footer
        HorizontalDivider(color = Color.White.copy(.1f))
        NavigationDrawerItem(
            icon     = { Icon(Icons.Default.Logout, null, tint = CoopError) },
            label    = { Text("Logout", color = CoopError) },
            selected = false,
            onClick  = onLogout,
            modifier = Modifier.padding(12.dp),
            colors   = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
        )
    }
}
