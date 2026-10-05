package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.PI

/** Cadrage de la carte sur la spirale en cours : centre du dernier tour et rayon englobant (m). */
data class ThermalView(val centre: LatLon, val radiusM: Double, val climbMs: Double)

/**
 * V20.1 — zoom automatique sur la spirale (demande JB). La vue se resserre sur le dernier tour dès que le planeur
 * **spirale** (détection du moteur de vol), qu'il a **bouclé un tour complet** et que la **montée moyenne de ce tour
 * est positive** ; elle revient au zoom du pilote dès qu'il sort de la spirale. Fonction pure, testée hors appareil.
 */
object ThermalZoom {
    /** Tour complet : ≥ 330° de virage cumulé (tolère le premier demi-segment). */
    const val FULL_TURN_DEG = 330.0
    /** Montée moyenne minimale sur le dernier tour pour parler de pompe (m/s). */
    const val MIN_CLIMB_MS = 0.1
    /** Les cercles occupent les deux tiers de la plus petite dimension de la carte. */
    const val SCREEN_FRACTION = 2.0 / 3.0
    private const val MAX_WINDOW_S = 90L

    /**
     * [trace] : points ~1 Hz. [circling] / [since] : état spirale du moteur. null = pas de vue spirale.
     */
    fun view(trace: List<TracePoint>, circling: Boolean, since: Instant?, now: Instant): ThermalView? {
        if (!circling || since == null) return null
        val from = maxOf(since, now.minusSeconds(MAX_WINDOW_S))
        val pts = trace.filter { !it.time.isBefore(from) }
        if (pts.size < 6) return null
        // remonte depuis le point le plus récent jusqu'à avoir couvert un tour complet
        var turned = 0.0
        var start = -1
        for (i in pts.size - 1 downTo 2) {
            val h1 = bearing(pts[i - 1].position, pts[i].position) ?: continue
            val h0 = bearing(pts[i - 2].position, pts[i - 1].position) ?: continue
            turned += wrap(h1 - h0)
            if (abs(turned) >= FULL_TURN_DEG) { start = i - 2; break }
        }
        if (start < 0) return null
        val circle = pts.subList(start, pts.size)
        val climb = circle.map { it.climbMs }.average()
        if (climb < MIN_CLIMB_MS) return null
        val lat = circle.map { it.position.lat }.average()
        val lon = circle.map { it.position.lon }.average()
        val centre = LatLon(lat, lon)
        val radius = circle.maxOf { Geo.distanceKm(centre, it.position) * 1000.0 }
        return ThermalView(centre, max(radius, 60.0), climb)
    }

    /**
     * Zoom MapLibre (tuiles de 512 points) pour que le diamètre du cercle couvre [SCREEN_FRACTION] de [minSidePts]
     * (plus petit côté de la carte, en points d'écran).
     */
    fun zoomFor(view: ThermalView, minSidePts: Double): Double {
        val metersPerPoint = (2 * view.radiusM) / (SCREEN_FRACTION * minSidePts)
        val z = ln(EARTH_CIRCUMFERENCE_M * cos(view.centre.lat * PI / 180.0) / (512.0 * metersPerPoint)) / ln(2.0)
        return z.coerceIn(11.0, 18.0)
    }

    private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

    private fun bearing(a: LatLon, b: LatLon): Double? {
        val dy = (b.lat - a.lat)
        val dx = (b.lon - a.lon) * cos(a.lat * PI / 180.0)
        if (abs(dx) < 1e-9 && abs(dy) < 1e-9) return null
        return atan2(dx, dy) * 180.0 / PI
    }

    private fun wrap(d: Double): Double {
        var x = d
        while (x > 180) x -= 360
        while (x < -180) x += 360
        return x
    }
}
