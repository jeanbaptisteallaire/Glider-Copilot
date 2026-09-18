package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.safety.Wind
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Cœur de pompe estimé pendant une spirale : la trace des dernières secondes est **recalée de la dérive du vent**
 * (chaque point ramené à l'endroit où l'air se trouve maintenant). Sur le dernier tour, la montée de l'air suit
 * ln(w) = constante + (r·e/σ²)·cos(θ − φ) pour une pompe gaussienne : l'ajustement de cette sinusoïde donne la direction
 * du cœur (φ) et son décalage e, sous l'hypothèse d'une pompe de σ = 150 m. La consigne se donne en secondes avant
 * d'élargir, au taux de virage mesuré.
 *
 * Aide expérimentale : à valider avec un instructeur avant d'en faire une consigne.
 */
data class CoreEstimate(
    /** Première spirale en cours : pas encore d'estimation ([progress] de 0 à 1). */
    val analysing: Boolean,
    val progress: Double = 0.0,
    val core: LatLon? = null,
    /** Centre du cercle volé (moyenne des positions recalées des 26 dernières secondes). */
    val centre: LatLon? = null,
    val spreadM: Double = 0.0,
    val confidence: Double = 0.0,
    val offsetM: Double = 0.0,
    val bearingToCoreDeg: Double? = null,
    /** Secondes avant de passer au cap du cœur, au taux de virage mesuré ; 0 = élargir maintenant. */
    val secondsBeforeWidening: Double? = null,
    val turnRateDegS: Double = 0.0,
) {
    val centred: Boolean get() = !analysing && offsetM < CENTRED_M

    /** Consigne courte, en français, ou null si rien à dire. */
    fun cue(): String? = when {
        analysing -> "Analyse de la spirale · ${(progress * 100).roundToInt()} %"
        centred -> "Centré · cœur à ${offsetM.roundToInt()} m"
        secondsBeforeWidening == null -> null
        secondsBeforeWidening <= 1.0 -> "ÉLARGIS · décale ${(offsetM / 10).roundToInt() * 10} m"
        else -> "Élargis dans ${secondsBeforeWidening.roundToInt()} s · cœur à ${(offsetM / 10).roundToInt() * 10} m"
    }

    companion object {
        const val CENTRED_M = 35.0
    }
}

object CoreEstimator {
    /** Demi-largeur supposée de la pompe (m) : une seule spirale ne permet pas de la mesurer, elle est affichée comme hypothèse. */
    const val THERMAL_SIGMA_M = 150.0
    /** Chute propre du planeur en spirale (m/s), retirée pour retrouver la montée de l'air. */
    const val CIRCLING_SINK_MS = 1.0

    /**
     * [trace] : points 1 Hz (position, montée, heure). [since] : début de la spirale. [heading] : cap actuel.
     * [turnRateDegS] : taux de virage mesuré (positif à droite).
     */
    fun estimate(
        trace: List<TracePoint>,
        since: Instant?,
        now: Instant,
        wind: Wind?,
        heading: Double?,
        turnRateDegS: Double,
        windowS: Long = 100,
    ): CoreEstimate? {
        if (since == null) return null
        val turned = abs(turnRateDegS) * Duration.between(since, now).seconds
        val from = maxOf(since, now.minusSeconds(windowS))
        val pts = trace.filter { !it.time.isBefore(from) && !it.time.isAfter(now) }
        if (Duration.between(since, now).seconds < 25 || pts.size < 15) {
            return CoreEstimate(analysing = true, progress = (turned / 360.0).coerceIn(0.0, 0.95), turnRateDegS = turnRateDegS)
        }
        // recalage de la dérive : chaque point est ramené là où la masse d'air qui le portait se trouve maintenant
        val drifted = pts.map { p ->
            val dt = Duration.between(p.time, now).seconds
            val moved = if (wind == null || wind.speedKmh < 1 || dt <= 0) p.position else {
                val km = wind.speedKmh * dt / 3600.0
                Geo.destination(p.position, (wind.fromDeg + 180) % 360, km)
            }
            moved to p
        }
        val vmin = pts.minOf { it.climbMs }
        val recent = drifted.filter { Duration.between(it.second.time, now).seconds <= 26 }
        if (recent.size < 10) return CoreEstimate(analysing = true, progress = 0.95, turnRateDegS = turnRateDegS)
        // centre du cercle volé et rayon moyen, sur la dernière spirale recalée
        val centre = LatLon(recent.map { it.first.lat }.average(), recent.map { it.first.lon }.average())
        val radius = recent.map { Geo.distanceKm(centre, it.first) * 1000 }.average()
        if (radius < 15) return CoreEstimate(analysing = true, progress = 0.95, turnRateDegS = turnRateDegS)
        // Modèle de pompe gaussienne de demi-largeur THERMAL_SIGMA_M : le long d'un cercle de rayon r décalé de e du cœur,
        // ln(montée de l'air) est exactement une sinusoïde d'amplitude r·e/σ². On ajuste donc ln(v + chute du planeur).
        var u0 = 0.0; var uc = 0.0; var us = 0.0; var n = 0
        val samples = ArrayList<Pair<Double, Double>>(recent.size)
        for ((pos, p) in recent) {
            val th = Math.toRadians(Geo.bearingDeg(centre, pos))
            val u = kotlin.math.ln((p.climbMs + CIRCLING_SINK_MS).coerceAtLeast(0.05))
            samples += th to u
            u0 += u; uc += u * kotlin.math.cos(th); us += u * kotlin.math.sin(th); n++
        }
        u0 /= n; uc = 2 * uc / n; us = 2 * us / n
        val amplitude = hypot(uc, us)
        val phase = kotlin.math.atan2(us, uc)
        // résidu de l'ajustement : une pompe régulière donne un résidu faible, une pompe hachée un résidu fort
        var res = 0.0
        for ((th, u) in samples) {
            val fit = u0 + amplitude * kotlin.math.cos(th - phase)
            res += (u - fit) * (u - fit)
        }
        val noise = sqrt(res / n)
        val offset = (amplitude * THERMAL_SIGMA_M * THERMAL_SIGMA_M / radius).coerceIn(0.0, radius * 2)
        val brg = if (amplitude > 0.02) (Math.toDegrees(phase) + 360) % 360 else null
        val core = brg?.let { Geo.destination(centre, it, offset / 1000) }
        val spread = radius
        val confidence = (amplitude / (amplitude + noise + 0.05)).coerceIn(0.1, 1.0)
        val seconds = if (brg != null && heading != null && abs(turnRateDegS) > 1) {
            var diff = brg - heading
            while (diff < 0) diff += 360
            while (diff >= 360) diff -= 360
            val ahead = if (turnRateDegS >= 0) diff else 360 - diff       // sens du virage
            (ahead / abs(turnRateDegS)).coerceIn(0.0, 30.0)
        } else null
        return CoreEstimate(
            analysing = false,
            progress = 1.0,
            core = core ?: centre,
            centre = centre,
            spreadM = spread,
            confidence = confidence,
            offsetM = offset,
            bearingToCoreDeg = brg,
            secondsBeforeWidening = seconds,
            turnRateDegS = turnRateDegS,
        )
    }

    /** Taux de virage moyen (°/s, positif à droite) sur les [seconds] dernières secondes de trace. */
    fun turnRate(trace: List<TracePoint>, now: Instant, seconds: Long = 20): Double {
        val pts = trace.filter { Duration.between(it.time, now).seconds in 0..seconds }
        if (pts.size < 4) return 0.0
        var turn = 0.0
        val tracks = pts.zipWithNext().map { (a, b) -> Geo.bearingDeg(a.position, b.position) }
        tracks.zipWithNext().forEach { (a, b) ->
            var d = b - a
            if (d > 180) d -= 360
            if (d < -180) d += 360
            turn += d
        }
        val span = Duration.between(pts.first().time, pts.last().time).seconds
        return if (span <= 0) 0.0 else turn / span
    }
}

/** Zone décision de la maquette : rester ou partir, et fourchette de vitesse de transition. */
object Decision {
    enum class Gauge { UNKNOWN, WEAK, MEDIUM, GOOD }

    /** Compare la pompe en cours à la moyenne du jour ; il faut au moins [minSeconds] de spirale. */
    fun gauge(thermalMs: Double?, dayMs: Double?, circlingSeconds: Long, minSeconds: Long = 40): Gauge {
        if (thermalMs == null || dayMs == null || dayMs <= 0.1 || circlingSeconds < minSeconds) return Gauge.UNKNOWN
        val r = thermalMs / dayMs
        return when {
            r < 0.75 -> Gauge.WEAK
            r > 1.15 -> Gauge.GOOD
            else -> Gauge.MEDIUM
        }
    }

    fun gaugeLabel(g: Gauge): String = when (g) {
        Gauge.WEAK -> "Faible · cherche"
        Gauge.MEDIUM -> "Moyenne"
        Gauge.GOOD -> "Bonne · reste"
        Gauge.UNKNOWN -> "—"
    }

    /**
     * Pompe qui s'essouffle (moyenne 30 s retombée sous 60 % des 90 s précédentes) ou plafond prévu approché :
     * message court, ou null.
     */
    fun fading(last30Ms: Double?, previous90Ms: Double?, altitudeM: Double?, ceilingM: Double?, circlingSeconds: Long): String? {
        if (circlingSeconds < 60) return null
        if (altitudeM != null && ceilingM != null && altitudeM > ceilingM - 120) return "Proche du plafond prévu — envisage le départ"
        if (last30Ms != null && previous90Ms != null && previous90Ms > 0.4 && last30Ms < previous90Ms * 0.6) return "S'essouffle — envisage le départ"
        return null
    }

    /**
     * Fourchette de vitesse de transition : vitesse de finesse max de la machine majorée de 15 km/h par m/s
     * de moyenne du jour (MacCready approché, arrondi à 5 km/h). Indicative : la polaire réelle arrive en v1.1.
     */
    fun transitionBandKmh(dayMs: Double?, baseKmh: Double = 92.0): IntRange? {
        if (dayMs == null) return null
        val low = ((baseKmh + dayMs.coerceAtLeast(0.0) * 15) / 5).roundToInt() * 5
        return low..(low + 15)
    }
}

/** Pompe du réseau OGN la plus intéressante autour du planeur : cap, distance, montée. */
data class NetworkThermalCue(val climbMs: Double, val bearingDeg: Double, val distanceKm: Double, val ageMinutes: Long) {
    fun label(): String = "Réseau " + (if (climbMs >= 0) "+" else "−") + String.format(java.util.Locale.FRANCE, "%.1f m/s", abs(climbMs))
}

object NetworkThermals {
    /** Meilleure pompe à moins de [radiusKm], plus récente que [maxAgeMinutes], en préférant la montée et la proximité. */
    fun best(
        from: LatLon,
        thermals: List<Triple<LatLon, Double, Long>>,
        radiusKm: Double = 15.0,
        maxAgeMinutes: Long = 30,
        minClimbMs: Double = 0.8,
    ): NetworkThermalCue? = thermals
        .asSequence()
        .map { (p, climb, age) -> Triple(p, climb, age) to Geo.distanceKm(from, p) }
        .filter { (t, d) -> d in 0.3..radiusKm && t.third <= maxAgeMinutes && t.second >= minClimbMs }
        .maxByOrNull { (t, d) -> t.second - d / 12.0 - t.third / 60.0 }
        ?.let { (t, d) -> NetworkThermalCue(t.second, Geo.bearingDeg(from, t.first), d, t.third) }
}

/** Décalage d'un point de trace pour l'affichage « recalé du vent » : où se trouve maintenant l'air qui le portait. */
fun TracePoint.driftCorrected(now: Instant, wind: Wind?): LatLon {
    if (wind == null || wind.speedKmh < 1) return position
    val dt = Duration.between(time, now).seconds
    if (dt <= 0) return position
    return Geo.destination(position, (wind.fromDeg + 180) % 360, wind.speedKmh * dt / 3600.0)
}

/** Distance en mètres entre deux points, arrondie : utilitaire d'affichage. */
internal fun metresBetween(a: LatLon, b: LatLon): Double = hypot(
    (b.lat - a.lat) * 111_320.0,
    (b.lon - a.lon) * 111_320.0 * kotlin.math.cos(Math.toRadians((a.lat + b.lat) / 2)),
)
