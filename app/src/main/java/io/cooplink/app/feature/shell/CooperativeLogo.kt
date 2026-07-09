package io.cooplink.app.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.cooplink.app.R
import io.cooplink.app.ui.theme.CoopTeal

/** The cooperative's own logo, falling back to the CoopLink brand mark while
 * loading or if logo_url is missing/fails to load. */
@Composable
fun CooperativeLogoImage(logoUrl: String?, size: Dp = 40.dp) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(logoUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Cooperative Logo",
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.cooplink_icon_mark),
        placeholder = painterResource(R.drawable.cooplink_icon_mark),
    )
}

/** Cooperative logo, or a colored initial circle (using the cooperative's
 * primary_color) when no logo_url is set. */
@Composable
fun CooperativeLogo(
    name: String?,
    logoUrl: String?,
    primaryColor: String?,
    size: Dp = 28.dp,
) {
    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            model = logoUrl,
            contentDescription = name,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        val background = primaryColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: CoopTeal
        Box(
            Modifier.size(size).clip(CircleShape).background(background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name?.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
