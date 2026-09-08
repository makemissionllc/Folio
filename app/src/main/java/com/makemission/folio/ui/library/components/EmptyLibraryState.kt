package com.makemission.folio.ui.library.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Empty-state for the library — a cohesive flat illustration in the Folio
 * palette (deep green / burgundy / amber on the current surface), with a
 * short editorial message. Mirrors the reference app's illustration-based
 * empty placeholder without copying its drawables.
 */
@Composable
fun EmptyLibraryState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FlatLibraryIllustration(
            modifier = Modifier.size(width = 200.dp, height = 148.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Import an EPUB to start your\ncurated Folio collection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FlatLibraryIllustration(
    modifier: Modifier = Modifier,
) {
    val amber = Color(0xFFF7B538)
    val burgundy = Color(0xFF780116)
    val burgundyContainer = Color(0xFF9A1B26)
    val offWhite = Color(0xFFFFF8E7)
    val paperMid = Color(0xFFF1E8D2)
    val deepGreen = Color(0xFF004F39)
    val deepGreenContainer = Color(0xFF0B5C45)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cr = CornerRadius(10f, 10f)
        val smallCr = CornerRadius(7f, 7f)

        // Shelf / base.
        drawRoundRect(
            color = deepGreenContainer.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.10f, h * 0.82f),
            size = Size(w * 0.80f, h * 0.08f),
            cornerRadius = smallCr,
        )

        // Sun — warm focal accent.
        drawCircle(
            color = amber,
            radius = w * 0.13f,
            center = Offset(w * 0.72f, h * 0.18f),
        )
        // Sun inner highlight (paper).
        drawCircle(
            color = offWhite.copy(alpha = 0.92f),
            radius = w * 0.045f,
            center = Offset(w * 0.70f, h * 0.14f),
        )

        // Left standing book — burgundy.
        drawRoundRect(
            color = burgundy,
            topLeft = Offset(w * 0.26f, h * 0.30f),
            size = Size(w * 0.20f, h * 0.52f),
            cornerRadius = cr,
        )
        // Spine highlight.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(w * 0.26f, h * 0.30f),
            size = Size(w * 0.04f, h * 0.52f),
            cornerRadius = CornerRadius(10f, 10f),
        )
        // Label band on book.
        drawRoundRect(
            color = offWhite.copy(alpha = 0.95f),
            topLeft = Offset(w * 0.29f, h * 0.52f),
            size = Size(w * 0.14f, h * 0.10f),
            cornerRadius = CornerRadius(5f, 5f),
        )

        // Right standing book — paper/off-white with burgundy accent.
        drawRoundRect(
            color = paperMid,
            topLeft = Offset(w * 0.48f, h * 0.36f),
            size = Size(w * 0.19f, h * 0.46f),
            cornerRadius = cr,
        )
        drawRoundRect(
            color = burgundyContainer.copy(alpha = 0.95f),
            topLeft = Offset(w * 0.495f, h * 0.50f),
            size = Size(w * 0.16f, h * 0.12f),
            cornerRadius = CornerRadius(5f, 5f),
        )

        // Top lying book — deep green with amber rule.
        drawRoundRect(
            color = deepGreen,
            topLeft = Offset(w * 0.33f, h * 0.20f),
            size = Size(w * 0.30f, h * 0.14f),
            cornerRadius = CornerRadius(7f, 7f),
        )
        drawRoundRect(
            color = amber,
            topLeft = Offset(w * 0.36f, h * 0.26f),
            size = Size(w * 0.24f, h * 0.03f),
            cornerRadius = CornerRadius(2f, 2f),
        )

        // Tiny burgundy dot — tag/active state.
        drawCircle(
            color = burgundy,
            radius = w * 0.018f,
            center = Offset(w * 0.58f, h * 0.56f),
        )
    }
}
