package com.neutronstar.glidercopilot.domain.flight

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Position du planeur à chaque image (30 à 60 Hz) entre deux positions GPS (1 Hz) : extrapolation en virage à taux
 * constant (arc de cercle), puis résorption douce de l'écart (τ = 0,35 s) à l'arrivée d'une nouvelle position.
 * Affichage de la carte seulement : marge, alertes et IGC restent calculés sur les vraies positions.
 * Port commun de `ios/.../Pilotage/OwnshipPredictor.swift` (iOS V3), même comportement. Temps en secondes.
 */
class OwnshipPredictor {
    data class State(val lat: Double, val lon: Double, val course: Double, val valid: Boolean)

    private var lat0 = 0.0
    private var lon0 = 0.0
    private var t0 = 0.0
    private var speed = 0.0
    private var course0 = 0.0
    private var gpsTurn = 0.0
    private var gyroTurn = 0.0
    private var gyroAt = -1e9
    private var hasFix = false
    private var errE = 0.0
    private var errN = 0.0
    private var errC = 0.0
    private var errAt = 0.0

    /** Taux de virage instantané (°/s, positif à droite) mesuré par le gyroscope à l'instant [t]. */
    fun gyro(turnDegS: Double, t: Double) {
        gyroTurn = gyroTurn * 0.6 + turnDegS * 0.4
        gyroAt = t
    }

    private fun turnRate(now: Double): Double =
        (if (now - gyroAt < 0.5) gyroTurn else gpsTurn).coerceIn(-40.0, 40.0)

    /** Nouvelle position. [course] < 0 ou vitesse ≤ 3 m/s : route inconnue (au sol), pas d'extrapolation. */
    fun fix(lat: Double, lon: Double, speedMs: Double, course: Double, t: Double) {
        if (hasFix) {
            val shown = predictRaw(t)
            val (e, n) = enu(lat, lon, shown.lat, shown.lon)
            if (hypot(e, n) < 300) {
                val decay = exp(-(t - errAt) / CORRECTION_TAU)
                errE = e + errE * decay
                errN = n + errN * decay
                val shownCourse = shown.course + errC * decay
                errC = if (course >= 0 && speedMs > 3) angleDiff(shownCourse, course) else errC * decay
            } else { errE = 0.0; errN = 0.0; errC = 0.0 }
            errAt = t
            if (course >= 0 && speedMs > 3 && t > t0) {
                val measured = angleDiff(course, course0) / maxOf(t - t0, 0.2)
                gpsTurn = gpsTurn * 0.5 + measured.coerceIn(-40.0, 40.0) * 0.5
            }
        }
        lat0 = lat; lon0 = lon; t0 = t
        speed = if (speedMs > 0) speedMs else 0.0
        if (course >= 0 && speedMs > 3) course0 = course else gpsTurn = 0.0
        hasFix = true
    }

    /** Position à afficher à l'instant [t] (écart résiduel compris). */
    fun state(t: Double): State {
        if (!hasFix) return State(0.0, 0.0, 0.0, false)
        val raw = predictRaw(t)
        val k = exp(-(t - errAt) / CORRECTION_TAU)
        val (lat, lon) = offset(raw.lat, raw.lon, errE * k, errN * k)
        return State(lat, lon, norm(raw.course + errC * k), true)
    }

    private fun predictRaw(t: Double): State {
        val dt = (t - t0).coerceIn(0.0, MAX_EXTRAPOLATION)
        if (speed <= 3) return State(lat0, lon0, course0, true)
        val rate = turnRate(t)
        val w = rate * PI / 180
        val c0 = course0 * PI / 180
        val e: Double
        val n: Double
        if (kotlin.math.abs(w) < 0.002) {
            e = speed * dt * sin(c0); n = speed * dt * cos(c0)
        } else {
            e = speed * (cos(c0) - cos(c0 + w * dt)) / w
            n = speed * (sin(c0 + w * dt) - sin(c0)) / w
        }
        val (lat, lon) = offset(lat0, lon0, e, n)
        return State(lat, lon, norm(course0 + rate * dt), true)
    }

    companion object {
        const val MAX_EXTRAPOLATION = 3.0
        const val CORRECTION_TAU = 0.35
        private const val R = 6_371_000.0

        fun offset(lat: Double, lon: Double, east: Double, north: Double): Pair<Double, Double> =
            (lat + north / R * 180 / PI) to (lon + east / (R * cos(lat * PI / 180)) * 180 / PI)

        /** Vecteur (est, nord) en mètres de (fromLat, fromLon) vers (toLat, toLon). */
        fun enu(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Pair<Double, Double> =
            ((toLon - fromLon) * PI / 180 * R * cos(fromLat * PI / 180)) to ((toLat - fromLat) * PI / 180 * R)

        fun norm(a: Double): Double { val x = a % 360; return if (x < 0) x + 360 else x }

        /** a − b ramené dans ]−180, 180]. */
        fun angleDiff(a: Double, b: Double): Double {
            var d = (a - b) % 360
            if (d > 180) d -= 360
            if (d <= -180) d += 360
            return d
        }
    }
}
