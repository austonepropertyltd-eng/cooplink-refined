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
import io.cooplink.app.core.domain.format
import io.cooplink.app.core.ui.currentCurrency

/** Counts up/down to [amount] instead of snapping straight to the new value —
 * used for balances and KPI figures that change after a refresh. */
@Composable
fun AnimatedAmount(
    amount: Double,
    style: TextStyle,
    color: Color,
) {
    val currency = currentCurrency()
    val animatedValue by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "balance_animation",
    )
    Text(
        text = currency.format(animatedValue.toDouble()),
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}
