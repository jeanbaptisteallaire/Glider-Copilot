package com.neutronstar.glidercopilot.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Vignette de vol « carte claire vue de dessus » (S16, partagée avec le fil en S17) : fond papier, quadrillage
 * léger façon carte, trace rouge [GcColors.trace], départ marqué. Dessinée sur le téléphone, sans réseau.
 * [route] : points normalisés dans le carré unité (x → est, y → sud).
 */
@Composable
fun GcTraceThumbnail(route: List<Pair<Float, Float>>, modifier: Modifier = Modifier) {
    val c = Gc.colors
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(c.sunken)) {
        val step = size.minDimension / 6f
        var x = step
        while (x < size.width) { drawLine(c.lineFaint, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = step
        while (y < size.height) { drawLine(c.lineFaint, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        if (route.size > 1) {
            val path = Path()
            route.forEachIndexed { i, p ->
                val px = p.first * size.width
                val py = p.second * size.height
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            val w = (size.minDimension / 55f).coerceIn(2f, 5f)
            drawPath(path, c.trace, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val s = route.first()
            drawCircle(c.panel, radius = w * 1.9f, center = Offset(s.first * size.width, s.second * size.height))
            drawCircle(c.trace, radius = w * 1.2f, center = Offset(s.first * size.width, s.second * size.height))
        }
    }
}
