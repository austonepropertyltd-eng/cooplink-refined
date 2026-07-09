package io.cooplink.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val CoopLinkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ── Admin: Light navy/teal theme ─────────────────────────────────────────────
private val AdminLightColors = lightColorScheme(
    primary          = CoopNavy,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    secondary        = CoopTeal,
    onSecondary      = Color.White,
    tertiary         = CoopGold,
    background       = CoopBackground,
    surface          = CoopSurface,
    surfaceVariant   = CoopSurfaceVariant,
    onBackground     = CoopForeground,
    onSurface        = CoopForeground,
    outline          = CoopBorder,
    error            = CoopError,
)

// ── Member: Dark navy theme (default, unchanged) ─────────────────────────────
private val MemberDarkColors = darkColorScheme(
    primary          = MemberGreen,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFF0D3B20),
    secondary        = MemberGold,
    onSecondary      = CoopNavyDeep,
    tertiary         = CoopTeal,
    background       = CoopDarkBackground,
    surface          = CoopDarkSurface,
    surfaceVariant   = Color(0xFF0F2038),
    onBackground     = Color.White,
    onSurface        = Color.White,
    outline          = CoopDarkBorder,
    error            = CoopError,
)

// ── Member: Light variant (optional, user-toggled) ───────────────────────────
// Same teal/gold accents and navy top bar as the dark theme, white background
// and navy text instead of the dark navy gradient.
private val MemberLightColors = lightColorScheme(
    primary          = MemberGreen,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD8F0E4),
    secondary        = MemberGold,
    onSecondary      = CoopNavyDeep,
    tertiary         = CoopTeal,
    background       = CoopBackground,
    surface          = CoopSurface,
    surfaceVariant   = CoopSurfaceVariant,
    onBackground     = CoopForeground,
    onSurface        = CoopForeground,
    outline          = CoopBorder,
    error            = CoopError,
)

/** Parses a "#RRGGBB"/"#AARRGGBB" hex string (as stored on
 * cooperatives.primary_color) into a Compose [Color], or null if [hex] is
 * blank/malformed — callers fall back to the fixed brand color in that case. */
fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}

@Composable
fun CoopLinkAdminTheme(accentColor: Color? = null, content: @Composable () -> Unit) {
    SetStatusBarColor(accentColor ?: CoopNavy, darkIcons = false)
    MaterialTheme(
        colorScheme = if (accentColor != null) AdminLightColors.copy(primary = accentColor) else AdminLightColors,
        typography  = CoopLinkTypography,
        shapes      = CoopLinkShapes,
        content     = content,
    )
}

@Composable
fun CoopLinkMemberTheme(darkTheme: Boolean = true, accentColor: Color? = null, content: @Composable () -> Unit) {
    SetStatusBarColor(if (darkTheme) CoopDarkBackground else (accentColor ?: CoopNavy), darkIcons = false)
    val base = if (darkTheme) MemberDarkColors else MemberLightColors
    MaterialTheme(
        colorScheme = if (accentColor != null) base.copy(primary = accentColor) else base,
        typography  = CoopLinkTypography,
        shapes      = CoopLinkShapes,
        content     = content,
    )
}

@Composable
private fun SetStatusBarColor(color: Color, darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = color.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = darkIcons
        }
    }
}
