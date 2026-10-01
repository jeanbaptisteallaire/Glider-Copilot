package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcColors
import com.neutronstar.glidercopilot.designsystem.GcFonts
import com.neutronstar.glidercopilot.designsystem.vario
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal object Fmt {
    val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE).withZone(zone)
    private val h = DateTimeFormatter.ofPattern("H'h'", Locale.FRANCE).withZone(zone)
    val day: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

    fun hm(i: Instant) = hm.format(i)
    fun hour(i: Instant) = h.format(i)
    fun m(v: Double) = grouped((v / 10).roundToInt() * 10) + " m"
    fun grouped(n: Int): String = "%,d".format(Locale.ROOT, n).replace(',', ' ')
    fun ms(v: Double) = String.format(Locale.FRANCE, "%.1f m/s", v)
    fun deg(v: Double) = "%03d°".format((v.roundToInt() % 360 + 360) % 360)
    fun kmh(v: Double) = "${v.roundToInt()} km/h"
}

/** Échelle de couleur de la montée : échelle vario de la charte v8. */
internal fun climbColor(c: GcColors, ms: Double): Color = c.vario(ms)

/**
 * V18.1 — couleur de la montée dans les graphiques et leur légende. Pages blanches : teinte « soleil » des
 * thermiques, d'autant plus soutenue que la montée est forte (même lecture faible → fort). Sombre : échelle vario v8.
 */
internal fun chartClimbColor(c: GcColors, ms: Double, social: Boolean): Color =
    if (social) c.sun.copy(alpha = (0.3 + ms / 2.5 * 0.7).coerceIn(0.3, 1.0).toFloat()) else climbColor(c, ms)

/** V18.1 — point « base des cumulus » : indigo « altitude » sur pages blanches, encre en sombre. */
internal fun cloudBaseColor(c: GcColors, social: Boolean): Color = if (social) c.altitude else c.ink

/** Rôle d'un texte de carte : échelle Apple sur pages blanches, tailles v8 en sombre. */
internal enum class TextRole { Strong, Body, Secondary, Fine }

/**
 * Style d'un texte de carte, en Inter dans les deux thèmes (charte : jamais de police codée dans un écran).
 * Pages blanches : Subheadline (Strong/Body), Footnote (Secondary), Caption 1 (Fine). Sombre : tailles v8 d'origine.
 */
@Composable
internal fun cardStyle(
    role: TextRole,
    darkSize: TextUnit,
    color: Color,
    weight: FontWeight? = null,
    darkLineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle {
    if (!Gc.social) return TextStyle(fontFamily = GcFonts.ui, fontSize = darkSize, lineHeight = darkLineHeight, fontWeight = weight, color = color)
    val base = when (role) {
        TextRole.Strong -> Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold)
        TextRole.Body -> Gc.type.subhead
        TextRole.Secondary -> Gc.type.footnote
        TextRole.Fine -> Gc.type.caption1
    }
    return base.copy(color = color, fontWeight = if (role == TextRole.Strong) FontWeight.SemiBold else weight ?: base.fontWeight)
}
