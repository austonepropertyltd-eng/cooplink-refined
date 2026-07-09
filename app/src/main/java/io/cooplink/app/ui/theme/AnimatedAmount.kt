package io.cooplink.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.text.NumberFormat
import java.util.Locale

/** Counts up/down to [amount] instead of snapping straight to the new value —
 * used for balances and KPI figures that change after a refresh. */
@Composable
fun AnimatedAmount(
    amount: Double,
    style: TextStyle,
    color: Color,
    prefix: String = "₦",
) {
    val animatedValue by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "balance_animation",
    )
    val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    Text(
        text = "$prefix${fmt.format(animatedValue.toDouble())}",
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}
