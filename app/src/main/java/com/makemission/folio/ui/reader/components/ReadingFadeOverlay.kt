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
 *
 * Fixed: previously used a 2-stop 0.96→0 gradient over 28/32dp which rendered as a
 * faint hard band rather than a smooth iOS-style feather. Now uses a taller 36/40dp
 * box with 3-stop opaque→mid→transparent stops so text feathers smoothly into the
 * background without a visible line.
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
                .height(36.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to backgroundColor,
                            0.55f to backgroundColor.copy(alpha = 0.55f),
                            1.0f to backgroundColor.copy(alpha = 0f),
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
                .height(40.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to backgroundColor.copy(alpha = 0f),
                            0.45f to backgroundColor.copy(alpha = 0.55f),
                            1.0f to backgroundColor,
                        )
                    )
                )
        )
    }
}
