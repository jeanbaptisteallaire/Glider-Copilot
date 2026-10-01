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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Plafond des pompes heure par heure : barres = plafond (altitude QNH), couleur = montée estimée.
 * V18.1 pages blanches (Apple Santé) : barres en gélule à la teinte « soleil », dégradé doux, grille en
 * pointillés très clairs, peu de libellés, valeur du créneau sélectionné affichée au-dessus de sa barre.
 */
@Composable
internal fun CeilingChart(hours: List<HourWeather>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    val tm = rememberTextMeasurer()
    val label = if (social) Gc.type.caption2.copy(color = c.faint, fontFeatureSettings = "tnum")
    else TextStyle(fontFamily = GcFonts.mono, fontSize = 9.sp, color = c.dim)
    val valueLabel = Gc.type.caption2.copy(color = c.ink, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val selectedLabel = label.copy(color = c.route, fontWeight = FontWeight.SemiBold)
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
            if (social) drawLine(c.lineSoft, Offset(left, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = dashed)
            else drawLine(c.line, Offset(left, yy), Offset(size.width, yy), 1f)
            drawText(tm, Fmt.grouped(g.toInt()), Offset(0f, yy - 7.dp.toPx()), label)
            g += step
        }
        val w = (size.width - left) / hours.size
        val labelEvery = if (social && hours.size > 8) 3 else 2
        hours.forEachIndexed { i, h ->
            val a = h.analysis
            val x = left + i * w
            val on = i == selected
            if (on) {
                if (social) drawRoundRect(c.route.copy(alpha = 0.08f), Offset(x + w * 0.06f, 0f), Size(w * 0.88f, bottom), CornerRadius(8.dp.toPx()))
                else drawRect(c.route.copy(alpha = 0.16f), Offset(x, 0f), Size(w, bottom))
            }
            val color = if (a.liftType == LiftType.NONE) (if (social) c.line else c.faint) else chartClimbColor(c, a.climbMs, social)
            val barTop = y(a.ceilingMslM)
            if (social) {
                val bw = w * 0.56f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(color, color.copy(alpha = color.alpha * 0.55f)), startY = barTop, endY = bottom),
                    topLeft = Offset(x + (w - bw) / 2, barTop),
                    size = Size(bw, bottom - barTop),
                    cornerRadius = CornerRadius(bw / 2),
                )
            } else {
                drawRoundRect(color, Offset(x + w * 0.22f, barTop), Size(w * 0.56f, bottom - barTop), CornerRadius(3.dp.toPx()))
            }
            a.cloudBaseMslM?.takeIf { a.liftType == LiftType.CUMULUS }?.let {
                drawCircle(cloudBaseColor(c, social), 3.dp.toPx(), Offset(x + w / 2, y(it) - 5.dp.toPx()))
            }
            if (social && on) {
                // valeur du créneau sélectionné au-dessus de sa barre, gardée dans le cadre
                val t = tm.measure(AnnotatedString(Fmt.m(a.ceilingMslM)), style = valueLabel)
                val tx = (x + w / 2 - t.size.width / 2f).coerceAtMost(size.width - t.size.width).coerceAtLeast(left)
                val ty = (barTop - t.size.height - 3.dp.toPx()).coerceAtLeast(0f)
                drawText(t, topLeft = Offset(tx, ty))
            }
            if (i % labelEvery == 0 || hours.size <= 6 || (social && on)) {
                drawText(tm, Fmt.hour(a.validTime), Offset(x + w * 0.2f, bottom + 3.dp.toPx()), if (social && on) selectedLabel else label)
            }
        }
        if (social) drawLine(c.line, Offset(left, y(ground)), Offset(size.width, y(ground)), 1.dp.toPx())
        else drawRect(c.terrainBottom.copy(alpha = 0.8f), Offset(left, y(ground)), Size(size.width - left, bottom - y(ground) + 1f))
    }
}

/**
 * Rose des vents : un anneau par tranche, le point marque d'où vient le vent, le trait va dans le sens du vent.
 * V18.1 pages blanches : anneaux en pointillés très clairs, flèches à la teinte « vent ».
 */
@Composable
internal fun WindRose(layers: List<WindLayer>) {
    val c = Gc.colors
    val social = Gc.social
    val tm = rememberTextMeasurer()
    val label = if (social) Gc.type.caption2.copy(color = c.faint, fontWeight = FontWeight.SemiBold)
    else TextStyle(fontFamily = GcFonts.ui, fontSize = 9.sp, color = c.faint)
    Canvas(Modifier.size(140.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val ring0 = 16.dp.toPx()
        val gap = (size.minDimension / 2 - ring0 - 8.dp.toPx()) / max(1, layers.size - 1)
        layers.indices.forEach { i ->
            if (social) drawCircle(c.lineSoft, ring0 + i * gap, Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = dashed))
            else drawCircle(c.line, ring0 + i * gap, Offset(cx, cy), style = Stroke(1f))
        }
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
            val col = windLayerColor(c, i, social)
            drawLine(col, p, e, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(col, 3.5.dp.toPx(), p)
        }
        drawCircle(c.ink, 2.5.dp.toPx(), Offset(cx, cy))
    }
}

/**
 * Tranches de vent, du plus soutenu (sol) au plus léger (altitude) : vert de la charte en sombre,
 * teinte « vent » (bleu-vert) sur les pages blanches (V18.1).
 */
internal fun windLayerColor(c: GcColors, i: Int, social: Boolean = false): Color =
    (if (social) c.wind else c.windLayer).copy(alpha = (1f - i * 0.14f).coerceAtLeast(0.4f))

/** Pointillés très légers des grilles (pages blanches). */
private val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
