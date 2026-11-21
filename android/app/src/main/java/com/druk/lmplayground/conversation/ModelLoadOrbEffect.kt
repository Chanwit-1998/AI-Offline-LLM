package com.druk.lmplayground.conversation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ModelLoadOrbEffect(
    progress: Float,
    modelName: String?,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0.001f || clamped >= 0.999f) return

    val inf = rememberInfiniteTransition(label = "orb_load")
    val angleDeg by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_angle"
    )
    val pulse by inf.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val pct = (clamped * 100f).toInt().coerceIn(1, 99)
    val rad = angleDeg * (PI / 180.0)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseR = size.minDimension * 0.24f
                    val orbitR = size.minDimension * (0.28f + 0.14f * pulse)

                    val d1x = center.x + (cos(rad) * orbitR).toFloat()
                    val d1y = center.y + (sin(rad) * orbitR).toFloat()
                    val d2Angle = rad + PI * 0.85
                    val d2x = center.x + (cos(d2Angle) * (orbitR * 0.85f)).toFloat()
                    val d2y = center.y + (sin(d2Angle) * (orbitR * 0.85f)).toFloat()

                    // เส้นเชื่อมหยดน้ำจางๆ เมื่อจุดเข้าใกล้ตัวหลัก
                    drawLine(
                        color = Color(0xFF141518).copy(alpha = 0.22f * pulse),
                        start = center,
                        end = Offset(d1x, d1y),
                        strokeWidth = baseR * 0.32f
                    )
                    drawCircle(
                        color = Color(0xFF141518),
                        radius = baseR * (0.22f + 0.08f * pulse),
                        center = Offset(d1x, d1y)
                    )
                    drawCircle(
                        color = Color(0xFF141518).copy(alpha = 0.85f),
                        radius = baseR * 0.16f,
                        center = Offset(d2x, d2y)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF22252B), Color(0xFF0B0C0E))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$pct",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!modelName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
