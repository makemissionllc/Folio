package com.makemission.folio.ui.reader.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Subtle iOS-like top/bottom gradient fade for reading area — prevents hard cutoff.
 * Works for both phone (single column) and tablet (spread), respects immersive chrome
 * toggle by being drawn inside the text Box (not over TopAppBar). Structural inspiration
 * from book-story-master's fade/edge treatments only.
 */
@Composable
fun TopReadingFade(
    backgroundColor: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "topFadeAlpha",
    )
    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(28.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.96f),
                            backgroundColor.copy(alpha = 0f),
                        )
                    )
                )
        )
    }
}

@Composable
fun BottomReadingFade(
    backgroundColor: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "bottomFadeAlpha",
    )
    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(32.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0f),
                            backgroundColor.copy(alpha = 0.96f),
                        )
                    )
                )
        )
    }
}
