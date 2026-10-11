package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.flight.Igc
import com.neutronstar.glidercopilot.domain.flight.IgcFix
import com.neutronstar.glidercopilot.domain.roundHalfUp
import com.neutronstar.glidercopilot.domain.toRadians
import kotlin.math.cos
import com.neutronstar.glidercopilot.domain.formatFixed
import com.neutronstar.glidercopilot.domain.secondsBetween
import com.neutronstar.glidercopilot.domain.zeroPad
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath

/** Point du profil d'altitude : minutes depuis le début, altitude (m). */
data class AltDot(val minutes: Double, val altM: Double)

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
    val durationS: Long,
    val distanceKm: Double,
    /** Vignette de la grille : « 31 min · 55,9 km » (comme Mes vols Android). */
    val gridLine: String,
    /** Profil d'altitude allégé (~300 points), source « GPS » ou « Baro », altitudes min et max. */
    val altProfile: List<AltDot>,
    val altSource: String,
    val minAltM: Double,
    val maxAltM: Double,
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
            durationS = dur,
            distanceKm = dist,
            altProfile = profileOf(fixes),
            altSource = if (fixes.any { (it.gnssAltM ?: 0) != 0 }) "GPS" else "Baro",
            minAltM = alts.minOrNull()?.toDouble() ?: 0.0,
            maxAltM = alts.maxOrNull()?.toDouble() ?: 0.0,
            gridLine = (if (dur >= 3600) "${dur / 3600} h ${(dur % 3600 / 60).toString().padStart(2, '0')}" else "${dur / 60} min") +
                " · " + formatFixed(dist, 1, decimalComma = true) + " km",
        )
    }

    private fun profileOf(fixes: List<com.neutronstar.glidercopilot.domain.flight.IgcFix>): List<AltDot> {
        val withAlt = fixes.filter { (it.gnssAltM ?: 0) != 0 || (it.pressureAltM ?: 0) != 0 }
        if (withAlt.isEmpty()) return emptyList()
        val t0 = withAlt.first().time
        val step = maxOf(1, withAlt.size / 300)
        return withAlt.filterIndexed { i, _ -> i % step == 0 || i == withAlt.lastIndex }.map {
            AltDot(secondsBetween(t0, it.time) / 60.0, (it.gnssAltM?.takeIf { a -> a != 0 } ?: it.pressureAltM ?: 0).toDouble())
        }
    }

    fun delete(path: String) { runCatching { fs.delete(path.toPath()) } }

    /**
     * Import d'un IGC choisi par le pilote (Fichiers) : copié dans le carnet s'il contient au moins un point B lisible.
     * Renvoie le chemin créé, ou null si le fichier n'est pas un IGC exploitable.
     */
    fun importIgc(sourcePath: String, name: String): String? = runCatching {
        val text = fs.read(sourcePath.toPath()) { readUtf8() }
        if (Igc.parse(text).isEmpty()) return null
        val d = dir.toPath()
        fs.createDirectories(d)
        val base = name.substringBeforeLast('.').filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "import" }
        var target = d / "$base.igc"
        var i = 2
        while (fs.exists(target)) target = d / "$base-${i++}.igc"
        fs.write(target) { writeUtf8(text) }
        target.toString()
    }.getOrNull()

    /**
     * Données du rejeu 3D (page replay3d d'Android, identique) : même format que Replay3dTrackPayload —
     * titre, lat/lon arrondis au millionième, altitude GNSS (baro en repli), secondes depuis le début ;
     * au-delà de 20 000 points, Douglas-Peucker à 2,5 m. Null si le fichier est illisible ou sans altitude.
     */
    fun replayJson(path: String): String? = runCatching {
        val p = path.toPath()
        val usable = Igc.parse(fs.read(p) { readUtf8() }).filter { (it.gnssAltM ?: 0) != 0 || (it.pressureAltM ?: 0) != 0 }
        if (usable.size < 2) return null
        val kept = if (usable.size > 20_000) simplify(usable, 2.5) else usable
        val t0 = kept.first().time.epochSeconds
        fun alt(f: IgcFix) = f.gnssAltM?.takeIf { it != 0 } ?: f.pressureAltM ?: 0
        fun r6(v: Double) = roundHalfUp(v * 1e6) / 1e6
        buildString {
            append("{\"title\":\"").append(p.name).append("\",")
            append("\"lat\":[").append(kept.joinToString(",") { r6(it.position.lat).toString() }).append("],")
            append("\"lon\":[").append(kept.joinToString(",") { r6(it.position.lon).toString() }).append("],")
            append("\"alt\":[").append(kept.joinToString(",") { alt(it).toString() }).append("],")
            append("\"t\":[").append(kept.joinToString(",") { (it.time.epochSeconds - t0).toString() }).append("]}")
        }
    }.getOrNull()

    /** Douglas-Peucker itératif en mètres (projection locale + altitude), comme Replay3dTrackPayload.simplify. */
    private fun simplify(points: List<IgcFix>, toleranceM: Double): List<IgcFix> {
        if (points.size < 3) return points
        val lat0 = toRadians(points.first().position.lat)
        val x = DoubleArray(points.size) { toRadians(points[it].position.lon) * 6_371_000.0 * cos(lat0) }
        val y = DoubleArray(points.size) { toRadians(points[it].position.lat) * 6_371_000.0 }
        val z = DoubleArray(points.size) { (points[it].gnssAltM?.takeIf { a -> a != 0 } ?: points[it].pressureAltM ?: 0).toDouble() }
        val keep = BooleanArray(points.size).also { it[0] = true; it[points.size - 1] = true }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.size - 1) }
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            val dx = x[b] - x[a]; val dy = y[b] - y[a]; val dz = z[b] - z[a]
            val len2 = dx * dx + dy * dy + dz * dz
            var worst = -1.0; var index = -1
            for (i in a + 1 until b) {
                val px = x[i] - x[a]; val py = y[i] - y[a]; val pz = z[i] - z[a]
                val u = if (len2 > 0) ((px * dx + py * dy + pz * dz) / len2).coerceIn(0.0, 1.0) else 0.0
                val ex = px - u * dx; val ey = py - u * dy; val ez = pz - u * dz
                val d = ex * ex + ey * ey + ez * ez
                if (d > worst) { worst = d; index = i }
            }
            if (index >= 0 && worst > toleranceM * toleranceM) {
                keep[index] = true
                stack.add(a to index); stack.add(index to b)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }
}
