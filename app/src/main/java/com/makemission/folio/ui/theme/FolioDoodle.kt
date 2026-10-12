package com.makemission.folio.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Small, imperfect pen marks used as accents across Folio's interface. */
enum class FolioDoodleKind { SPARK, UNDERLINE, CIRCLE, ARROW }

@Composable
fun FolioDoodle(
    kind: FolioDoodleKind,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        val line = Path()
        val w = size.width
        val h = size.height
        when (kind) {
            FolioDoodleKind.SPARK -> {
                line.moveTo(w * .5f, h * .06f)
                line.quadraticTo(w * .46f, h * .4f, w * .12f, h * .52f)
                line.quadraticTo(w * .48f, h * .55f, w * .51f, h * .94f)
                line.quadraticTo(w * .56f, h * .56f, w * .9f, h * .47f)
                line.quadraticTo(w * .56f, h * .44f, w * .5f, h * .06f)
                drawPath(line, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color.copy(alpha = .6f), Offset(w * .82f, h * .12f), Offset(w * .94f, h * .02f), 1.2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color.copy(alpha = .6f), Offset(w * .92f, h * .68f), Offset(w * .99f, h * .76f), 1.2.dp.toPx(), cap = StrokeCap.Round)
            }
            FolioDoodleKind.UNDERLINE -> {
                line.moveTo(w * .04f, h * .38f)
                line.quadraticTo(w * .24f, h * .1f, w * .48f, h * .35f)
                line.quadraticTo(w * .73f, h * .62f, w * .96f, h * .3f)
                drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            FolioDoodleKind.CIRCLE -> {
                line.moveTo(w * .12f, h * .54f)
                line.quadraticTo(w * .08f, h * .1f, w * .51f, h * .08f)
                line.quadraticTo(w * .94f, h * .06f, w * .9f, h * .52f)
                line.quadraticTo(w * .87f, h * .93f, w * .48f, h * .9f)
                line.quadraticTo(w * .13f, h * .87f, w * .12f, h * .54f)
                drawPath(line, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            FolioDoodleKind.ARROW -> {
                line.moveTo(w * .08f, h * .78f)
                line.quadraticTo(w * .46f, h * .85f, w * .82f, h * .2f)
                drawPath(line, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color, Offset(w * .58f, h * .2f), Offset(w * .84f, h * .16f), 1.8.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(w * .84f, h * .16f), Offset(w * .84f, h * .43f), 1.8.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}
