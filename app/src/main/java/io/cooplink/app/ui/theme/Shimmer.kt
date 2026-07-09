package io.cooplink.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerEffect(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.08f),
        ),
        start = Offset(translateAnim - 500, 0f),
        end = Offset(translateAnim, 0f),
    )
    Box(modifier.background(brush, RoundedCornerShape(8.dp))) {}
}

@Composable
fun ShimmerWalletCard() {
    Card(
        Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerEffect(Modifier.width(120.dp).height(16.dp))
            ShimmerEffect(Modifier.width(200.dp).height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShimmerEffect(Modifier.width(60.dp).height(12.dp))
                        ShimmerEffect(Modifier.width(80.dp).height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerListItem() {
    Card(
        Modifier.fillMaxWidth().height(80.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxSize(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerEffect(Modifier.width(44.dp).height(44.dp).clip(CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerEffect(Modifier.fillMaxWidth(.6f).height(14.dp))
                ShimmerEffect(Modifier.fillMaxWidth(.4f).height(12.dp))
            }
            ShimmerEffect(Modifier.width(60.dp).height(18.dp))
        }
    }
}

@Composable
fun ShimmerKpiCard(modifier: Modifier) {
    Card(
        modifier.height(100.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CoopDarkSurface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShimmerEffect(Modifier.width(20.dp).height(20.dp).clip(CircleShape))
            ShimmerEffect(Modifier.width(80.dp).height(24.dp))
            ShimmerEffect(Modifier.width(100.dp).height(12.dp))
        }
    }
}
