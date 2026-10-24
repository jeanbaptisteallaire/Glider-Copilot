package com.neutronstar.glidercopilot.feature.flight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcFonts
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.panelBrush
import com.neutronstar.glidercopilot.domain.flight.AltitudeDrum
import com.neutronstar.glidercopilot.domain.flight.AltitudeTapeState
import com.neutronstar.glidercopilot.domain.flight.VarioPalette
import kotlin.math.abs
import kotlin.math.max

/*
 * V21 — langage visuel de LIGHT PFD (Godspeed) dans Pilotage, miroir de PfdInstrumentsView.swift (iOS) :
 * chaque information une seule fois, rien au centre de la carte. Calculs et textes : AltitudeTape / VarioPalette (communs).
 */

/** Couleur ARGB venue du Kotlin commun (VarioPalette). */
internal fun argb(v: Long): Color = Color(v.toInt())

/** Chiffres de vol : Inter, chiffres tabulaires (SF Pro sur iOS). */
internal fun pfd(size: Float, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = GcFonts.numbers, fontWeight = weight, fontSize = size.sp, fontFeatureSettings = "tnum")

/** Panneau de Pilotage : dégradé lavande Godspeed, coins arrondis, sans ombre noire ; [fill] = aplat d'alerte. */
@Composable
internal fun Modifier.pfdPanel(fill: Color? = null, radius: Dp = 14.dp): Modifier {
    val c = Gc.colors
    val shape = RoundedCornerShape(radius)
    return if (fill != null) background(fill, shape) else background(c.panelBrush, shape)
}

/**
 * Bandeau sécurité (haut gauche) : « Marge sécu. (m) » en petit au-dessus de la marge en très gros, l'altitude en tambour
 * à côté. [alert] : marge faible ou négative, toute la boîte en aplat orange ou rouge, textes noirs.
 */
@Composable
internal fun SafetyBand(
    marginText: String,
    marginColor: Color,
    tape: AltitudeTapeState,
    essential: Boolean,
    alert: Boolean,
    altRef: String,
    accessory: @Composable () -> Unit,
) {
    val c = Gc.colors
    val ink = if (alert) c.onAccent else c.ink
    val label = if (alert) c.onAccent else c.dim
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f, fill = false)) {
            Text("Marge sécu. (m)", style = TextStyle(fontFamily = GcFonts.ui, fontSize = 11.7.sp, fontWeight = FontWeight.Medium, color = label), maxLines = 1)
            Box(
                Modifier.then(if (essential) Modifier else Modifier.height(78.dp).offset(y = (-16).dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    marginText,
                    // réduit au besoin (minimumScaleFactor 0,6 d'iOS) : la marge n'est jamais tronquée
                    style = pfd((if (essential) 64f else 48f) * shrink(marginText.length), FontWeight.Bold).copy(color = if (alert) c.onAccent else marginColor),
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.semantics { contentDescription = "Marge $marginText mètres" },
                )
                if (!essential) Box(Modifier.align(Alignment.BottomStart).offset(y = 11.dp)) { accessory() }
            }
        }
        if (!essential) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (altRef.isEmpty()) "Alti." else "Alti. · $altRef", style = TextStyle(fontFamily = GcFonts.ui, fontSize = 11.7.sp, fontWeight = FontWeight.Medium, color = label), maxLines = 1)
            Box(Modifier.offset(y = (-16).dp)) { Drum(tape, ink, inAlertBox = alert) }
        }
    }
}

/** Tambour d'altitude : centaines fixes + rouleau des deux derniers chiffres qui s'estompe ; bloc rouge plein sous le minimum. */
@Composable
private fun Drum(tape: AltitudeTapeState, ink: Color, inAlertBox: Boolean) {
    val c = Gc.colors
    val red = tape.belowMinimum && !inAlertBox
    val fg = if (red) Color.White else ink
    val rowH = 24.dp
    val boxH = 78.dp
    val d = tape.drum
    Row(
        Modifier.height(boxH)
            .then(if (red) Modifier.background(c.alarmFill, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = if (tape.valid) "Altitude" else "Altitude indisponible" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (d == null) {
            Text("– – –", style = pfd(22f).copy(color = if (inAlertBox) c.onAccent else c.bad))
            return@Row
        }
        // hachures devant la lecture, filet vertical entre les deux séries de chiffres (référence Thales)
        Canvas(Modifier.padding(end = 4.dp).size(22.dp, 16.dp)) {
            val step = 6.dp.toPx()
            var k = -size.height
            while (k < size.width) {
                drawLine(fg.copy(alpha = 0.85f), Offset(max(k, 0f), size.height - max(0f, -k)), Offset(minOf(k + size.height, size.width), max(0f, k + size.height - size.width)), 2.5.dp.toPx())
                k += step
            }
        }
        Text(d.high, style = pfd(30f, FontWeight.Bold).copy(color = fg), maxLines = 1)
        Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(boxH - 8.dp).background(fg.copy(alpha = 0.5f)))
        DrumRoll(d, fg, rowH, boxH)
    }
}

@Composable
private fun DrumRoll(d: AltitudeDrum, fg: Color, rowH: Dp, boxH: Dp) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.size(44.dp, boxH).clipToBounds()) {
        val row = rowH.toPx()
        val half = size.height / 2
        d.rows.forEachIndexed { i, txt ->
            val off = ((i - AltitudeDrum.CENTER) + d.roll.toFloat()) * row
            val centre = abs(off) < row / 2
            val fade = max(0f, 1 - abs(off) / (half + row / 2))
            // estompé vers les bords de la fenêtre (masque en dégradé d'iOS)
            val edge = (1 - abs(off) / half).coerceIn(0f, 1f).let { if (it > 0.4f) 1f else it / 0.4f }
            val alpha = (if (centre) 1f else 0.25f + 0.5f * fade) * edge
            val layout = tm.measure(txt, pfd(if (centre) 26f else 21f, if (centre) FontWeight.Bold else FontWeight.SemiBold).copy(color = fg.copy(alpha = alpha)))
            drawText(layout, topLeft = Offset(0f, half + off - layout.size.height / 2f))
        }
    }
}

/**
 * Vario au bord droit : chiffre en tête, barre étroite −3 … +4 au dégradé de la trace, traits courts sans texte,
 * index creux de la moyenne spirale, bouton son en pied.
 */
@Composable
internal fun VarioRail(ms: Double?, spiralMs: Double?, soundOn: Boolean, onSound: () -> Unit) {
    val c = Gc.colors
    val valid = ms != null && ms.isFinite()
    val barH = 230.dp
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = if (valid) "Vario ${VarioPalette.valueText(ms!!)} mètres par seconde" else "Vario indisponible" },
    ) {
        Text(
            VarioPalette.valueText(if (valid) ms!! else Double.NaN),
            style = pfd(28f, FontWeight.Bold).copy(color = if (valid) argb(VarioPalette.flash(ms!!)) else c.dim),
            maxLines = 1, softWrap = false,
            modifier = Modifier.pfdPanel(radius = 8.dp).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        val casing = argb(VarioPalette.CASING)
        Canvas(Modifier.size(40.dp, barH)) {
            val h = size.height
            val barW = 11.dp.toPx()
            val x0 = size.width - barW - 6.dp.toPx()
            fun y(v: Double) = (h - VarioPalette.fraction(v) * h).toFloat()
            // barre fine terminée par des demi-cercles, dégradé « flash » de la trace
            val stops = VarioPalette.flashStops.map { (v, col) -> (1f - VarioPalette.fraction(v).toFloat()) to argb(col) }.sortedBy { it.first }.toTypedArray()
            drawRoundRect(Brush.verticalGradient(*stops, startY = 0f, endY = h), Offset(x0, 0f), Size(barW, h), CornerRadius(barW / 2))
            drawRoundRect(casing, Offset(x0, 0f), Size(barW, h), CornerRadius(barW / 2), style = Stroke(1.5.dp.toPx()))
            // traits courts +4 / +2 / 0 / −2, le zéro plus long et blanc
            listOf(4.0, 2.0, 0.0, -2.0).forEach { v ->
                val zero = v == 0.0
                val w = if (zero) barW else 5.dp.toPx()
                drawLine(if (zero) Color.White else casing, Offset(x0 + barW - w, y(v)), Offset(x0 + barW, y(v)), if (zero) 2.dp.toPx() else 1.5.dp.toPx())
            }
            // index creux de la moyenne spirale
            if (spiralMs != null && spiralMs.isFinite()) {
                val yy = y(spiralMs)
                val tip = x0 - 4.dp.toPx()
                val p = Path().apply { moveTo(tip, yy); lineTo(tip - 10.dp.toPx(), yy - 6.dp.toPx()); lineTo(tip - 10.dp.toPx(), yy + 6.dp.toPx()); close() }
                drawPath(p, c.ink, style = Stroke(2.dp.toPx()))
            }
            // curseur (sans animation : le vario du moteur est déjà amorti, τ ≈ 1 s)
            if (valid) {
                val yy = y(ms!!)
                val w = 23.dp.toPx(); val hh = 5.dp.toPx()
                val left = x0 + barW / 2 - w / 2 + 6.dp.toPx()
                drawRoundRect(c.ink, Offset(left, yy - hh / 2), Size(w, hh), CornerRadius(hh / 2))
                drawRoundRect(casing, Offset(left, yy - hh / 2), Size(w, hh), CornerRadius(hh / 2), style = Stroke(1.5.dp.toPx()))
            }
        }
        Box(
            Modifier.size(40.dp).background(c.panelBrush, CircleShape)
                .clickable(role = Role.Button, onClickLabel = if (soundOn) "Couper le son du vario" else "Activer le son du vario", onClick = onSound),
            contentAlignment = Alignment.Center,
        ) { Icon(if (soundOn) GcIcons.Sound else GcIcons.SoundOff, contentDescription = null, tint = if (soundOn) c.ink else c.dim, modifier = Modifier.size(18.dp)) }
    }
}

/** Petite pastille ronde ou arrondie des bords de Pilotage (44 dp, cible tactile minimale). */
@Composable
internal fun PfdTool(description: String, modifier: Modifier = Modifier, round: Boolean = false, height: Dp = 50.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = Gc.colors
    val shape = if (round) CircleShape else RoundedCornerShape(14.dp)
    Box(
        modifier.size(44.dp, if (round) 44.dp else height).background(c.panelBrush, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Finesse sous la marge : un toucher passe 10 → 20 → 30 (réglage détaillé en Prévol). */
@Composable
internal fun FinesseChip(finesse: Int, alert: Boolean, onClick: () -> Unit) {
    val c = Gc.colors
    val col = if (alert) c.onAccent else c.selected
    Text(
        "F$finesse",
        style = pfd(16.5f).copy(color = col),
        modifier = Modifier.border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "Finesse suivante", onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.5.dp)
            .semantics { contentDescription = "Finesse $finesse" },
    )
}

/** Facteur de taille d'un chiffre selon sa longueur (« +320 » plein, « −12 340 » réduit), jamais sous 0,6. */
internal fun shrink(length: Int): Float = when {
    length <= 4 -> 1f
    length == 5 -> 0.85f
    length == 6 -> 0.72f
    else -> 0.6f
}
