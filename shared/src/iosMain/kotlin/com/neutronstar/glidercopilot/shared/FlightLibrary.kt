package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.flight.Igc
import com.neutronstar.glidercopilot.domain.formatFixed
import com.neutronstar.glidercopilot.domain.secondsBetween
import com.neutronstar.glidercopilot.domain.zeroPad
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath

/** Vol du carnet : statistiques lues dans le fichier IGC fermé (même règle qu'Android : Mes vols ne reçoit que des IGC fermés). */
data class FlightEntry(
    val path: String,
    val fileName: String,
    val title: String,
    val detail: String,
    val durationText: String,
    val distanceText: String,
    val maxAltText: String,
    val gainText: String,
    val startEpochS: Double,
    val track: List<TraceDot>,
)

/** Carnet Mes vols de l'édition Lite iOS : fichiers IGC du dossier de l'app, du plus récent au plus ancien. */
class FlightLibrary(private val dir: String) {
    private val fs = FileSystem.SYSTEM

    fun list(): List<FlightEntry> {
        val d = dir.toPath()
        if (!fs.exists(d)) return emptyList()
        return fs.list(d).filter { it.name.endsWith(".igc") }
            .mapNotNull { p -> runCatching { read(p.toString()) }.getOrNull() }
            .sortedByDescending { it.startEpochS }
    }

    fun read(path: String): FlightEntry {
        val p = path.toPath()
        val fixes = Igc.parse(fs.read(p) { readUtf8() }).filter { it.valid || it.gnssAltM != null }
        val first = fixes.firstOrNull()
        val dur = if (fixes.size > 1) secondsBetween(fixes.first().time, fixes.last().time) else 0L
        // « 00000 » dans un B-record = altitude absente (GPS sans altitude, pas de baromètre)
        val alts = fixes.mapNotNull { it.pressureAltM?.takeIf { a -> a != 0 } ?: it.gnssAltM?.takeIf { a -> a != 0 } }
        var dist = 0.0
        fixes.zipWithNext { a, b -> dist += Geo.distanceKm(a.position, b.position) }
        var gain = 0
        alts.zipWithNext { a, b -> if (b > a) gain += b - a }
        val local = first?.time?.toLocalDateTime(TimeZone.currentSystemDefault())
        // trace allégée (1 point sur 4 au-delà de 2000) et montée estimée point à point pour la couleur
        val step = if (fixes.size > 2000) 4 else 1
        val track = fixes.filterIndexed { i, _ -> i % step == 0 }.zipWithNext { a, b ->
            val dt = secondsBetween(a.time, b.time).coerceAtLeast(1)
            val climb = ((b.pressureAltM ?: b.gnssAltM ?: 0) - (a.pressureAltM ?: a.gnssAltM ?: 0)).toDouble() / dt
            TraceDot(b.position.lat, b.position.lon, climb)
        }
        return FlightEntry(
            path = path,
            fileName = p.name,
            title = local?.let { "${it.dayOfMonth.zeroPad(2)}/${it.month.ordinal.plus(1).zeroPad(2)}/${it.year} · ${it.hour.zeroPad(2)}h${it.minute.zeroPad(2)}" } ?: p.name,
            detail = "${fixes.size} points" + if (p.name.contains("REJEU")) " · rejeu" else "",
            durationText = IosFlightEngine.chrono(dur),
            distanceText = formatFixed(dist, 1, decimalComma = true) + " km",
            maxAltText = alts.maxOrNull()?.let { IosFlightEngine.grouped(it) + " m" } ?: "—",
            gainText = IosFlightEngine.grouped(gain) + " m",
            startEpochS = first?.time?.toEpochMilliseconds()?.div(1000.0) ?: 0.0,
            track = track,
        )
    }

    fun delete(path: String) { runCatching { fs.delete(path.toPath()) } }
}
