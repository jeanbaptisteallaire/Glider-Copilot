package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

/** Échelle de couleur de la montée, identique à la trace vario de la maquette. */
internal fun climbColor(ms: Double): Color {
    val stops = listOf(
        0.0 to Color(0xFF96A3AC),
        0.6 to Color(0xFFF2D24B),
        1.8 to Color(0xFFFF8A2A),
        3.5 to Color(0xFFFF2D3F),
    )
    if (ms <= stops.first().first) return stops.first().second
    for (k in 1 until stops.size) {
        val (v1, c1) = stops[k]
        val (v0, c0) = stops[k - 1]
        if (ms <= v1) return lerp(c0, c1, ((ms - v0) / (v1 - v0)).toFloat())
    }
    return stops.last().second
}
