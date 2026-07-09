package io.cooplink.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A shimmering placeholder card shown in place of real content while a
 * screen's first load is in flight, instead of blank space. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    containerColor: Color = CoopDarkSurface,
    barColor: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.15f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.fillMaxWidth(0.4f).height(14.dp)
                    .background(barColor.copy(alpha = alpha), MaterialTheme.shapes.small),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth(0.7f).height(20.dp)
                    .background(barColor.copy(alpha = alpha), MaterialTheme.shapes.small),
            )
        }
    }
}
