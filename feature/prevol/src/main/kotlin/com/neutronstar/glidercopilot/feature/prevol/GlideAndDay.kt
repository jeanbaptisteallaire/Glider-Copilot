package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.domain.DayScoreResult
import com.neutronstar.glidercopilot.domain.ScoreLevel
import com.neutronstar.glidercopilot.domain.safety.SafetyConfig
import kotlinx.coroutines.flow.StateFlow

/** Réglage du plané fait en Prévol, lu par la marge de sécurité de Pilotage (V21, comme iOS). */
data class GlideSettings(val finesse: Int = 20, val arrivalMarginM: Int = 300)

interface GlideSettingsSource {
    val glide: StateFlow<GlideSettings>
    fun setFinesse(value: Int)
    fun setArrivalMargin(meters: Int)
}

/** Commande segmentée façon iOS : fond lavande, segment choisi en cyan. */
@Composable
internal fun Segmented(options: List<Pair<String, Int>>, selected: Int, description: String, onSelect: (Int) -> Unit) {
    val c = Gc.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(c.control).padding(2.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(7.dp)).background(if (on) c.accentFill else c.control)
                    .clickable(role = Role.Tab) { onSelect(value) }.semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Gc.type.subhead.copy(color = if (on) c.onAccentFill else c.ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1) }
        }
    }
}

/** Choix du jour de la météo : aujourd'hui, demain, après-demain (JB, 22/10/2026). */
@Composable
internal fun DayPicker(offset: Int, onSelect: (Int) -> Unit) =
    Segmented(listOf("Aujourd'hui" to 0, "Demain" to 1, "Après-demain" to 2), offset, "Jour de la météo", onSelect)

/** Score du jour sur 100 : petit anneau coloré, chiffre au centre (façon Apple Watch). Indicatif. */
@Composable
internal fun ScoreRing(result: DayScoreResult, size: Float = 56f) {
    val c = Gc.colors
    val col = when (result.level) { ScoreLevel.POOR -> c.bad; ScoreLevel.FAIR -> c.warn; ScoreLevel.GOOD -> c.ok; ScoreLevel.GREAT -> c.sky }
    Box(Modifier.size(size.dp).semantics(mergeDescendants = true) { contentDescription = "Score du jour ${result.score} sur 100, indicatif" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val w = 7.dp.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(col.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(col, -90f, 360f * (result.score / 100f).coerceAtLeast(0.001f), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${result.score}", style = Gc.type.metric.copy(fontSize = (size * 0.36f).sp, fontWeight = FontWeight.Bold))
    }
}

/** Réglage du plané : finesse 10 / 20 / 30 et marge d'arrivée au terrain (0, +300 m ou autre). */
@Composable
internal fun GlideSettingsCard(g: GlideSettings, source: GlideSettingsSource) {
    val c = Gc.colors
    val custom = g.arrivalMarginM !in SafetyConfig.ARRIVAL_PRESETS
    GcCard(title = "Plané", tint = c.ink) {
        Text("Finesse", style = Gc.type.subhead.copy(color = c.dim))
        Segmented(SafetyConfig.FINESSES.map { "F$it" to it }, g.finesse, "Finesse") { source.setFinesse(it) }
        Text("Marge d'arrivée au terrain", style = Gc.type.subhead.copy(color = c.dim), modifier = Modifier.padding(top = 4.dp))
        Segmented(listOf("0 m" to 0, "+300 m" to 300, "Autre" to -1), if (custom) -1 else g.arrivalMarginM, "Marge d'arrivée") { v ->
            source.setArrivalMargin(if (v == -1) 200 else v)
        }
        if (custom) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("+${g.arrivalMarginM} m", style = Gc.type.metric.copy(fontSize = 20.sp, color = c.sky), modifier = Modifier.weight(1f))
            StepButton("−", "Réduire la marge d'arrivée") { source.setArrivalMargin(g.arrivalMarginM - 50) }
            StepButton("+", "Augmenter la marge d'arrivée") { source.setArrivalMargin(g.arrivalMarginM + 50) }
        }
        Text(
            "Calcul indicatif de la marge de sécurité en Pilotage : finesse réduite par le vent, arrivée à la hauteur choisie au-dessus du terrain.",
            style = Gc.type.caption1,
        )
    }
}

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    val c = Gc.colors
    Box(
        Modifier.size(44.dp, 34.dp).clip(RoundedCornerShape(8.dp)).background(c.control)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Gc.type.headline.copy(color = c.sky)) }
}
