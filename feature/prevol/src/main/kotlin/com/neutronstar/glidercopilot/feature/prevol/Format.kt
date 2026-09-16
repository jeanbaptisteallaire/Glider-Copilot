package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.ui.graphics.Color
import com.neutronstar.glidercopilot.designsystem.GcColors
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
