package io.cooplink.app.feature.admin.resources

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.core.util.MARKETING_RESOURCES
import io.cooplink.app.core.util.MarketingResource
import io.cooplink.app.core.util.MarketingResourceType
import io.cooplink.app.ui.theme.CoopDarkSurface
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopNavy
import io.cooplink.app.ui.theme.CoopTeal

private val SHARE_APP_TEXT = """
🏦 Discover CoopLink — Africa's smartest cooperative management platform!

✅ Manage members, savings & loans
✅ Mobile app for members & admins
✅ Real-time financial reporting
✅ Secure & NDPR compliant

🌐 Visit: https://cooplink.io
📞 Call VFG Technology: 07061365172
📧 info@vfgtechnology.com
""".trimIndent()

private const val SHARE_APP_SHORT_TEXT =
    "Download CoopLink — Africa's cooperative management platform: https://cooplink.io"

@Composable
fun MarketingResourcesScreen(
    onBack: () -> Unit,
    viewModel: AdminResourcesViewModel = hiltViewModel(),
) {
    val slides by viewModel.slides.collectAsState()
    val fullScreenBitmaps by viewModel.fullScreenBitmaps.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.inactivityManager.resumeTimer()
    }
    fun shareResource(resource: MarketingResource) {
        viewModel.inactivityManager.pauseTimer()
        shareLauncher.launch(viewModel.resourceManager.buildShareIntent(resource, "Share ${resource.title}"))
    }
    fun shareAppLink(intent: Intent) {
        viewModel.inactivityManager.pauseTimer()
        shareLauncher.launch(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marketing Resources") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { shareResource(MARKETING_RESOURCES.first { it.id == "presentation" }) }) {
                        Icon(Icons.Default.Share, "Share presentation")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CoopNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (slides.isNotEmpty()) {
                item {
                    SlideshowPager(
                        slides = slides,
                        onShareSlide = { index ->
                            val single = MARKETING_RESOURCES.first { it.id == "all_slides" }
                                .copy(assetPaths = listOf(io.cooplink.app.core.util.SLIDE_ASSET_PATHS[index]))
                            shareResource(single)
                        },
                        onTapSlide = { index ->
                            viewModel.showFullScreen(io.cooplink.app.core.util.SLIDE_ASSET_PATHS[index], MarketingResourceType.IMAGE)
                        },
                    )
                }
            }

            item {
                Text("Downloads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }

            item {
                ResourceCard(
                    icon = Icons.Default.PictureAsPdf, iconColor = CoopError,
                    title = "Full Presentation (PDF)",
                    description = "7-page professional presentation for government, MFBs and cooperatives",
                    fileSize = humanFileSize(viewModel.resourceManager, MARKETING_RESOURCES[0]),
                    onPreview = { viewModel.showFullScreen(MARKETING_RESOURCES[0].assetPaths.first(), MarketingResourceType.PDF) },
                    onShare = { shareResource(MARKETING_RESOURCES[0]) },
                )
            }
            item {
                ResourceCard(
                    icon = Icons.Default.AccountTree, iconColor = CoopGreen,
                    title = "Member Journey Infographic",
                    description = "Step-by-step visual showing how members join and use CoopLink",
                    fileSize = humanFileSize(viewModel.resourceManager, MARKETING_RESOURCES[1]),
                    onPreview = { viewModel.showFullScreen(MARKETING_RESOURCES[1].assetPaths.first(), MarketingResourceType.IMAGE) },
                    onShare = { shareResource(MARKETING_RESOURCES[1]) },
                )
            }
            item {
                ResourceCard(
                    icon = Icons.Default.BarChart, iconColor = CoopGold,
                    title = "Statistics Overview",
                    description = "CoopLink at a glance — key numbers and platform highlights",
                    fileSize = humanFileSize(viewModel.resourceManager, MARKETING_RESOURCES[2]),
                    onPreview = { viewModel.showFullScreen(MARKETING_RESOURCES[2].assetPaths.first(), MarketingResourceType.IMAGE) },
                    onShare = { shareResource(MARKETING_RESOURCES[2]) },
                )
            }
            item {
                ResourceCard(
                    icon = Icons.Default.Collections, iconColor = CoopTeal,
                    title = "Share All 10 Slides",
                    description = "Share the complete slide deck with any contact",
                    fileSize = "All slides",
                    onPreview = null,
                    onShare = { shareResource(MARKETING_RESOURCES[3]) },
                )
            }

            item {
                ShareAppCard(
                    onWhatsApp = {
                        val encoded = Uri.encode(SHARE_APP_TEXT)
                        shareAppLink(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded")))
                    },
                    onEmail = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "Discover CoopLink — Smart Cooperative Management")
                            putExtra(Intent.EXTRA_TEXT, SHARE_APP_TEXT)
                        }
                        shareAppLink(intent)
                    },
                    onCopyLink = {
                        clipboard.setText(AnnotatedString(SHARE_APP_SHORT_TEXT))
                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    fullScreenBitmaps?.let { bitmaps ->
        FullScreenImageViewer(bitmaps = bitmaps, onDismiss = viewModel::dismissFullScreen)
    }
}

// AssetManager.openFd() only reports an accurate length for assets stored
// uncompressed in the APK — .pdf isn't in the default noCompress list, so
// this reads the materialized cache copy's real File.length() instead.
private fun humanFileSize(resourceManager: io.cooplink.app.core.util.MarketingResourceManager, resource: MarketingResource): String {
    val bytes = runCatching { resourceManager.materialize(resource.assetPaths.first()).length() }.getOrDefault(0L)
    val kb = bytes / 1024.0
    return if (kb >= 1024.0) "%.1f MB".format(kb / 1024.0) else "%.0f KB".format(kb)
}

@Composable
private fun SlideshowPager(
    slides: List<android.graphics.Bitmap>,
    onShareSlide: (Int) -> Unit,
    onTapSlide: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large)
                .background(CoopDarkSurface),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Image(
                    bitmap = slides[page].asImageBitmap(),
                    contentDescription = "Slide ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { onTapSlide(page) },
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small, color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Text(
                    "Slide ${pagerState.currentPage + 1} of ${slides.size}",
                    style = MaterialTheme.typography.labelMedium, color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            IconButton(
                onClick = { onShareSlide(pagerState.currentPage) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) { Icon(Icons.Default.Share, "Share slide", tint = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(slides.size) { index ->
                Box(
                    Modifier.padding(3.dp).size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (index == pagerState.currentPage) CoopGold else Color.White.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

@Composable
private fun ResourceCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    fileSize: String,
    onPreview: (() -> Unit)?,
    onShare: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(52.dp), MaterialTheme.shapes.medium, color = iconColor.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                Text(fileSize, style = MaterialTheme.typography.labelSmall, color = CoopGold)
            }
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                onPreview?.let {
                    OutlinedButton(
                        onClick = it,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, CoopTeal),
                    ) {
                        Icon(Icons.Default.Visibility, null, Modifier.size(14.dp), tint = CoopTeal)
                        Spacer(Modifier.width(4.dp))
                        Text("Preview", color = CoopTeal, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Button(
                    onClick = onShare,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoopNavy),
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ShareAppCard(onWhatsApp: () -> Unit, onEmail: () -> Unit, onCopyLink: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CoopNavy),
        border = BorderStroke(1.dp, CoopGold.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, null, tint = CoopGold, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Share CoopLink", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Invite others to use CoopLink", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShareAppButton(
                    Modifier.weight(1f), icon = Icons.Default.Chat, label = "WhatsApp",
                    containerColor = Color(0xFF25D366), contentColor = Color.White, onClick = onWhatsApp,
                )
                ShareAppButton(
                    Modifier.weight(1f), icon = Icons.Default.Email, label = "Email",
                    containerColor = CoopTeal, contentColor = Color.White, onClick = onEmail,
                )
                ShareAppButton(
                    Modifier.weight(1f), icon = Icons.Default.ContentCopy, label = "Copy Link",
                    containerColor = Color.Transparent, contentColor = CoopGold,
                    border = BorderStroke(1.dp, CoopGold), onClick = onCopyLink,
                )
            }
        }
    }
}

@Composable
private fun ShareAppButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    border: BorderStroke? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = border,
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                label, color = contentColor, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, softWrap = false,
            )
        }
    }
}

@Composable
private fun FullScreenImageViewer(bitmaps: List<android.graphics.Bitmap>, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { bitmaps.size })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                Image(
                    bitmap = bitmaps[page].asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .scale(scale)
                        .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                        .transformable(
                            state = rememberTransformableState { zoomChange, offsetChange, _ ->
                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                offset += offsetChange
                            },
                        ),
                )
            }
            if (bitmaps.size > 1) {
                Surface(
                    shape = MaterialTheme.shapes.small, color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${bitmaps.size}",
                        style = MaterialTheme.typography.labelMedium, color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
}
