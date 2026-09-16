package com.neutronstar.glidercopilot.feature.flight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcFonts
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Écran de vol — squelette de la session 1 : les trois zones fixes de la spec, alimentées par un
 * signal de démonstration. Capteurs (S5), sécurité réelle (S6), carte et centrage (S3, S7) à venir.
 */
@Composable
fun FlightScreen(modifier: Modifier = Modifier) {
    val c = Gc.colors
    var t by remember { mutableDoubleStateOf(0.0) }
    var finesse by rememberSaveable { mutableIntStateOf(20) }
    var pendingRaise by remember { mutableStateOf<Int?>(null) }
    var varioVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            t += 0.1
        }
    }
    LaunchedEffect(pendingRaise) {
        if (pendingRaise != null) {
            delay(3000)
            pendingRaise = null
        }
    }

    // Signal de démonstration : spirale dans une pompe de ~1,5 m/s.
    val vario = 1.4 + 1.3 * sin(t * 2 * Math.PI / 26) + 0.3 * sin(t * 1.7)
    val alt = 1250.0 + 40 * sin(t / 30)
    val distanceKm = 7.4
    val need = 262 + 300 + distanceKm * 1000 / finesse
    val marge = alt - need

    Column(modifier.fillMaxSize().background(c.background)) {
        // ---- Zone haute : sécurité ----
        Row(
            Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MARGE SÉCURITÉ", style = Gc.type.eyebrow)
                val margeColor = when { marge < 0 -> c.bad; marge < 150 -> c.warn; else -> c.ok }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(signed(marge), style = Gc.type.giant.copy(color = margeColor, fontSize = 70.sp))
                    Text(" m", style = Gc.type.kpi.copy(color = c.dim, fontSize = 22.sp), modifier = Modifier.padding(bottom = 10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("ALT", grouped(alt.roundToInt()), c.ink, c.chip)
                    Chip("SÉCU", grouped(need.roundToInt()), c.magenta, c.magenta.copy(alpha = 0.18f))
                }
            }
            Column(Modifier.width(156.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FINESSE", style = Gc.type.eyebrow)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(20, 15, 10).forEach { f ->
                        val selected = f == finesse
                        val pending = pendingRaise == f
                        Box(
                            Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background(if (selected) c.magenta else c.panel2, RoundedCornerShape(10.dp))
                                .border(1.5.dp, if (pending) c.warn else if (selected) c.magenta else c.line, RoundedCornerShape(10.dp))
                                .clickable {
                                    when {
                                        f == finesse -> Unit
                                        f < finesse -> { finesse = f; pendingRaise = null }
                                        pendingRaise == f -> { finesse = f; pendingRaise = null }
                                        else -> pendingRaise = f
                                    }
                                }
                                .semantics { contentDescription = "Finesse de sécurité $f" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("F$f", style = Gc.type.kpi.copy(fontSize = 22.sp, color = if (selected) Color.White else if (pending) c.warn else c.dim))
                        }
                    }
                }
                Text(
                    if (pendingRaise != null) "Touchez encore pour relever à F$pendingRaise" else "LFNL · 7,4 km · +300 m",
                    style = Gc.type.monoSmall.copy(color = if (pendingRaise != null) c.warn else c.magenta),
                )
            }
        }

        // ---- Coupe du terrain (placeholder) ----
        Canvas(Modifier.fillMaxWidth().height(84.dp).background(Brush.verticalGradient(listOf(Color(0xFF13284A), c.background)))) {
            val ground = Path().apply {
                moveTo(0f, size.height)
                val n = 40
                for (i in 0..n) {
                    val x = size.width * i / n
                    val y = size.height * (0.78f - 0.18f * kotlin.math.sin(i / 5f).toFloat() * kotlin.math.exp(-((i - 22f) / 9f) * ((i - 22f) / 9f)))
                    lineTo(x, y)
                }
                lineTo(size.width, size.height); close()
            }
            drawPath(ground, Color(0xFF8A7650))
            drawLine(c.magenta, Offset(0f, size.height * 0.45f), Offset(size.width, size.height * 0.62f), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            drawLine(c.ink, Offset(0f, size.height * 0.28f), Offset(size.width, size.height * 0.5f), 2.dp.toPx())
        }

        // ---- Zone centrale : carte (session 3) ----
        Box(Modifier.weight(1f).fillMaxWidth().background(c.background)) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 40.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(c.line.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                var y = 0f
                while (y < size.height) { drawLine(c.line.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                val cx = size.width / 2
                val cy = size.height / 2
                drawCircle(Color(0xFFF2D24B), 56.dp.toPx(), Offset(cx, cy), style = Stroke(6.dp.toPx()))
                drawCircle(c.magenta, 14.dp.toPx(), Offset(cx - 22.dp.toPx(), cy - 30.dp.toPx()), style = Stroke(2.5.dp.toPx()))
                val a = t * 2 * Math.PI / 26
                val gx = cx + (56.dp.toPx() * kotlin.math.cos(a)).toFloat()
                val gy = cy + (56.dp.toPx() * kotlin.math.sin(a)).toFloat()
                drawLine(Color.White, Offset(gx - 9.dp.toPx(), gy), Offset(gx + 9.dp.toPx(), gy), 2.5.dp.toPx())
                drawLine(Color.White, Offset(gx, gy - 5.dp.toPx()), Offset(gx, gy + 7.dp.toPx()), 2.5.dp.toPx())
            }
            Text(
                "DÉMO · carte réelle en session 3, capteurs en session 5",
                style = Gc.type.monoSmall.copy(color = c.faint),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).background(c.panel, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            if (!varioVisible) {
                Text(
                    "VARIO",
                    style = Gc.type.eyebrow.copy(color = c.ink),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(c.panel, RoundedCornerShape(10.dp))
                        .border(1.dp, c.line, RoundedCornerShape(10.dp))
                        .clickable { varioVisible = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics { contentDescription = "Rouvrir le vario" },
                )
            }
        }

        // ---- Zone basse : vario (fermable) ----
        if (varioVisible) {
            Row(
                Modifier.fillMaxWidth().background(c.panel).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(96.dp)) {
                    Text("VARIO M/S", style = Gc.type.eyebrow)
                    Text(signed1(vario), style = Gc.type.giant.copy(fontSize = 44.sp, color = varioColor(vario)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VarioBar(vario)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Avg("SPIR.", "+1,4", Modifier.weight(1f))
                        Avg("POMPE", "+1,2", Modifier.weight(1f))
                        Avg("JOUR", "+1,0", Modifier.weight(1f))
                    }
                }
                IconButton(onClick = { varioVisible = false }, modifier = Modifier.semantics { contentDescription = "Fermer le vario et couper le son" }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = c.dim)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, value: String, color: Color, bg: Color) {
    Row(Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, style = Gc.type.eyebrow.copy(fontSize = 9.sp))
        Spacer(Modifier.width(5.dp))
        Text(value, style = Gc.type.kpi.copy(fontSize = 21.sp, color = color, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Avg(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Gc.colors
    Row(
        modifier.background(c.chip, RoundedCornerShape(7.dp)).padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Gc.type.eyebrow.copy(fontSize = 8.sp, letterSpacing = 0.4.sp), maxLines = 1, softWrap = false)
        Text(value, style = Gc.type.mono.copy(fontSize = 12.sp), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun VarioBar(v: Double) {
    val c = Gc.colors
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val colors = listOf(Color(0xFF2F6BFF), Color(0xFF4FB3E8), Color(0xFF96A3AC), Color(0xFFF2D24B), Color(0xFFFF8A2A), Color(0xFFFF2D3F))
        drawRoundRect(Brush.horizontalGradient(colors), topLeft = Offset(0f, size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
        val x = ((v.coerceIn(-5.0, 5.0) + 5) / 10 * size.width).toFloat()
        drawLine(c.ink, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
    }
}

private fun signed(v: Double): String = (if (v >= 0) "+" else "−") + grouped(kotlin.math.abs(v).roundToInt())
private fun signed1(v: Double): String = (if (v >= 0) "+" else "−") + String.format(Locale.FRANCE, "%.1f", kotlin.math.abs(v))

private fun varioColor(v: Double): Color = when {
    v < -1 -> Color(0xFF4FB3E8)
    v < 0.3 -> Color(0xFF96A3AC)
    v < 1.2 -> Color(0xFFF2D24B)
    v < 2.5 -> Color(0xFFFF8A2A)
    else -> Color(0xFFFF2D3F)
}

private fun grouped(n: Int): String = "%,d".format(Locale.ROOT, n).replace(',', ' ')
