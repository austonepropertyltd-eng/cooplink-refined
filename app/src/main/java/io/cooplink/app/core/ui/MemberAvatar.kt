package io.cooplink.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopGreen
import io.cooplink.app.ui.theme.CoopTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private val AVATAR_PALETTE = listOf(
    CoopTeal, CoopGold, CoopGreen,
    Color(0xFF9B59B6), Color(0xFFE67E22), Color(0xFF3498DB),
)

/**
 * A circular member/user avatar backed by the `sign-urls` edge function.
 *
 * [avatarPath] is expected to be a raw storage path (e.g. "$userId/avatar.jpg"),
 * not a public URL — it gets resolved to a short-lived signed URL via
 * [signedUrlManager] before Coil ever sees it. Pass [resolvedUrlOverride] to
 * skip that per-instance network call when a caller (e.g. an admin list that
 * batch-signs every row up front) has already resolved the URL itself.
 */
@Composable
fun MemberAvatar(
    userId: String?,
    avatarPath: String?,
    fullName: String?,
    signedUrlManager: SignedUrlManager,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    resolvedUrlOverride: String? = null,
) {
    var resolvedUrl by remember(userId, avatarPath, resolvedUrlOverride) { mutableStateOf<String?>(resolvedUrlOverride) }
    var isLoading by remember(userId, avatarPath, resolvedUrlOverride) {
        mutableStateOf(resolvedUrlOverride == null && userId != null && !avatarPath.isNullOrBlank())
    }

    LaunchedEffect(userId, avatarPath, resolvedUrlOverride) {
        if (resolvedUrlOverride != null) {
            resolvedUrl = resolvedUrlOverride
            isLoading = false
            return@LaunchedEffect
        }
        if (userId == null || avatarPath.isNullOrBlank()) {
            resolvedUrl = null
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        resolvedUrl = withContext(Dispatchers.IO) { signedUrlManager.getAvatarUrl(userId, avatarPath) }
        isLoading = false
    }

    val bgColor = remember(userId, fullName) {
        val key = userId ?: fullName ?: "?"
        AVATAR_PALETTE[key.hashCode().absoluteValue % AVATAR_PALETTE.size]
    }
    val initials = remember(fullName) {
        fullName?.split(" ")?.filter { it.isNotBlank() }?.take(2)
            ?.joinToString("") { it.first().uppercaseChar().toString() }
            ?.ifBlank { "?" } ?: "?"
    }

    Box(modifier.size(size).clip(CircleShape)) {
        if (resolvedUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .memoryCacheKey(resolvedUrl)
                    .diskCacheKey(resolvedUrl)
                    .build(),
                contentDescription = fullName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Surface(Modifier.fillMaxSize(), CircleShape, color = bgColor) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(size * 0.4f), strokeWidth = 2.dp,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    } else {
                        Text(
                            initials,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.35).sp),
                            color = Color.White, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
