package com.toni.streaming.ui.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.toni.streaming.ui.theme.AccentPurple
import com.toni.streaming.ui.theme.DarkBackground
import com.toni.streaming.ui.theme.TextSecondary

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    message: String = "Caricamento…"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    Column(
        modifier = modifier.fillMaxSize().background(DarkBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                val barHeight by infiniteTransition.animateFloat(
                    initialValue = 12f,
                    targetValue = 36f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 500,
                            delayMillis = index * 120,
                            easing = LinearEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar$index"
                )

                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            AccentPurple.copy(
                                alpha = 0.5f + (index * 0.15f).coerceAtMost(0.5f)
                            )
                        )
                )
            }
        }

        androidx.tv.material3.Text(
            text = message,
            style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}
