package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcColors
import com.neutronstar.glidercopilot.designsystem.GcFonts
import com.neutronstar.glidercopilot.domain.LiftType
import com.neutronstar.glidercopilot.domain.WindLayer
import com.neutronstar.glidercopilot.precog.HourWeather
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Plafond des pompes heure par heure : barres = plafond (altitude QNH), couleur = montée estimée. */
@Composable
internal fun CeilingChart(hours: List<HourWeather>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Gc.colors
    val tm = rememberTextMeasurer()
    val label = TextStyle(fontFamily = GcFonts.mono, fontSize = 9.sp, color = c.dim)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(hours.size) {
                detectTapGestures { p ->
                    val left = 40.dp.toPx()
                    val w = (size.width - left) / max(1, hours.size)
                    onSelect(((p.x - left) / w).toInt().coerceIn(0, hours.size - 1))
                }
            },
    ) {
        if (hours.isEmpty()) return@Canvas
        val left = 40.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val ground = hours.minOf { it.analysis.groundAltitudeM }
        val top = max(ground + 1500.0, hours.maxOf { it.analysis.ceilingMslM } + 300.0)
        val step = if (top - ground > 2200) 1000.0 else 500.0
        fun y(alt: Double) = (bottom - (alt - ground) / (top - ground) * (bottom - 6.dp.toPx())).toFloat()
        var g = (kotlin.math.ceil(ground / step) * step)
        while (g < top) {
            val yy = y(g)
            drawLine(c.line, Offset(left, yy), Offset(size.width, yy), 1f)
            drawText(tm, Fmt.grouped(g.toInt()), Offset(0f, yy - 7.dp.toPx()), label)
            g += step
        }
        val w = (size.width - left) / hours.size
        hours.forEachIndexed { i, h ->
            val a = h.analysis
            val x = left + i * w
            if (i == selected) drawRect(c.route.copy(alpha = 0.16f), Offset(x, 0f), Size(w, bottom))
            val color = if (a.liftType == LiftType.NONE) c.faint else climbColor(c, a.climbMs)
            val barTop = y(a.ceilingMslM)
            drawRoundRect(color, Offset(x + w * 0.22f, barTop), Size(w * 0.56f, bottom - barTop), CornerRadius(3.dp.toPx()))
            a.cloudBaseMslM?.takeIf { a.liftType == LiftType.CUMULUS }?.let {
                drawCircle(c.ink, 3.dp.toPx(), Offset(x + w / 2, y(it) - 5.dp.toPx()))
            }
            if (i % 2 == 0 || hours.size <= 6) drawText(tm, Fmt.hour(a.validTime), Offset(x + w * 0.2f, bottom + 3.dp.toPx()), label)
        }
        drawRect(c.terrainBottom.copy(alpha = 0.8f), Offset(left, y(ground)), Size(size.width - left, bottom - y(ground) + 1f))
    }
}

/** Rose des vents : un anneau par tranche, le point marque d'où vient le vent, le trait va dans le sens du vent. */
@Composable
internal fun WindRose(layers: List<WindLayer>) {
    val c = Gc.colors
    val tm = rememberTextMeasurer()
    val label = TextStyle(fontFamily = GcFonts.ui, fontSize = 9.sp, color = c.faint)
    Canvas(Modifier.size(140.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val ring0 = 16.dp.toPx()
        val gap = (size.minDimension / 2 - ring0 - 8.dp.toPx()) / max(1, layers.size - 1)
        layers.indices.forEach { i -> drawCircle(c.line, ring0 + i * gap, Offset(cx, cy), style = Stroke(1f)) }
        drawText(tm, "N", Offset(cx - 3.dp.toPx(), 0f), label)
        drawText(tm, "S", Offset(cx - 3.dp.toPx(), size.height - 12.dp.toPx()), label)
        drawText(tm, "O", Offset(0f, cy - 6.dp.toPx()), label)
        drawText(tm, "E", Offset(size.width - 8.dp.toPx(), cy - 6.dp.toPx()), label)
        layers.forEachIndexed { i, l ->
            val r = ring0 + i * gap
            val a = Math.toRadians(l.fromDeg)
            val p = Offset(cx + (r * sin(a)).toFloat(), cy - (r * cos(a)).toFloat())
            val len = (6.dp.toPx() + l.speedKmh.toFloat() * 0.35f.dp.toPx()).coerceAtMost(r)
            val e = Offset(p.x - (len * sin(a)).toFloat(), p.y + (len * cos(a)).toFloat())
            val col = windLayerColor(c, i)
            drawLine(col, p, e, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(col, 3.5.dp.toPx(), p)
        }
        drawCircle(c.ink, 2.5.dp.toPx(), Offset(cx, cy))
    }
}

/** Tranches de vent : vert de la charte, du plus soutenu (sol) au plus léger (altitude). */
internal fun windLayerColor(c: GcColors, i: Int): Color = c.windLayer.copy(alpha = (1f - i * 0.14f).coerceAtLeast(0.4f))

@Suppress("unused")
private val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
