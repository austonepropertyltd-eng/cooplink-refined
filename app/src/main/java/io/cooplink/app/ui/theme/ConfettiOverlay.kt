package io.cooplink.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.random.Random

private enum class ParticleShape { CIRCLE, RECT }

private data class ConfettiParticle(
    val x: Float, val color: Color,
    val size: Float, val speed: Float,
    val drift: Float, val shape: ParticleShape,
)

@Composable
fun ConfettiOverlay(
    visible: Boolean,
    onComplete: () -> Unit,
) {
    if (!visible) return

    val particles = remember(visible) {
        (1..40).map {
            ConfettiParticle(
                x = Random.nextFloat(),
                color = listOf(
                    Color(0xFFFCB424),
                    Color(0xFF249C84),
                    Color(0xFF0F8B8D),
                    Color(0xFFFFFFFF),
                    Color(0xFF1FA67A),
                ).random(),
                size = Random.nextFloat() * 12f + 6f,
                speed = Random.nextFloat() * 0.4f + 0.3f,
                drift = Random.nextFloat() * 0.4f - 0.2f,
                shape = if (Random.nextBoolean()) ParticleShape.CIRCLE else ParticleShape.RECT,
            )
        }
    }

    var progress by remember(visible) { mutableStateOf(0f) }

    LaunchedEffect(visible) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(2500, easing = LinearEasing),
        ) { value, _ -> progress = value }
        onComplete()
    }

    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} },
    ) {
        particles.forEach { p ->
            val x = (p.x + p.drift * progress) * size.width
            val y = progress * p.speed * size.height * 2.5f
            val alpha = (1f - progress).coerceIn(0f, 1f)

            if (y < size.height) {
                drawContext.canvas.nativeCanvas.apply {
                    when (p.shape) {
                        ParticleShape.CIRCLE ->
                            drawCircle(
                                x, y, p.size,
                                android.graphics.Paint().apply { color = p.color.copy(alpha = alpha).toArgb() },
                            )
                        ParticleShape.RECT ->
                            drawRect(
                                x - p.size / 2, y - p.size / 2,
                                x + p.size / 2, y + p.size / 2,
                                android.graphics.Paint().apply { color = p.color.copy(alpha = alpha).toArgb() },
                            )
                    }
                }
            }
        }
    }
}
