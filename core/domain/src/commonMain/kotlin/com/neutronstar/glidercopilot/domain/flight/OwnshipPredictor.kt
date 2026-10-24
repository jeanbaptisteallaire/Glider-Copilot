package com.neutronstar.glidercopilot.domain.flight

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Position du planeur à chaque image entre deux positions GPS (1 Hz) : extrapolation en virage à taux constant, puis
 * résorption douce de l'écart à l'arrivée d'une nouvelle position. Affichage de la carte seulement : la sécurité
 * (marge, alertes, IGC) reste calculée sur les vraies positions. Portage commun d'OwnshipPredictor.swift (iOS).
 * Temps [t] en secondes, monotone, fourni par l'appelant.
 */
class OwnshipPredictor {
    data class State(val lat: Double, val lon: Double, val course: Double, val valid: Boolean)

    private var lat0 = 0.0; private var lon0 = 0.0; private var t0 = 0.0
    private var speed = 0.0
    private var course0 = 0.0
    private var gpsTurn = 0.0
    private var gyroTurn = 0.0
    private var gyroAt = -1e9
    private var hasFix = false
    private var errE = 0.0; private var errN = 0.0; private var errC = 0.0; private var errAt = 0.0

    /** Taux de virage instantané (°/s, positif à droite) mesuré par le gyroscope, s'il y en a un. */
    fun gyro(turnDegS: Double, t: Double) {
        gyroTurn = gyroTurn * 0.6 + turnDegS * 0.4
        gyroAt = t
    }

    private fun turnRate(t: Double): Double = (if (t - gyroAt < 0.5) gyroTurn else gpsTurn).coerceIn(-40.0, 40.0)

    /** Nouvelle position. [course] < 0 ou vitesse faible : route inconnue (au sol), pas d'extrapolation. */
    fun fix(lat: Double, lon: Double, speedMs: Double, course: Double, t: Double) {
        if (hasFix) {
            val shown = predictRaw(t)
            val (e, n) = enu(lat, lon, shown.first, shown.second)
            if (hypot(e, n) < 300) {
                val decay = exp(-(t - errAt) / CORRECTION_TAU)
                errE = e + errE * decay
                errN = n + errN * decay
                val shownCourse = shown.third + errC * decay
                errC = if (course >= 0 && speedMs > 3) angleDiff(shownCourse, course) else errC * decay
            } else { errE = 0.0; errN = 0.0; errC = 0.0 }   // saut (démo, perte GPS) : pas de glissade absurde
            errAt = t
            if (course >= 0 && speedMs > 3 && t > t0) {
                val measured = angleDiff(course, course0) / max(t - t0, 0.2)
                gpsTurn = gpsTurn * 0.5 + measured.coerceIn(-40.0, 40.0) * 0.5
            }
        } else errAt = t
        lat0 = lat; lon0 = lon; t0 = t
        speed = max(speedMs, 0.0)
        if (course >= 0 && speedMs > 3) course0 = course else gpsTurn = 0.0
        hasFix = true
    }

    /** Position à afficher maintenant (écart résiduel compris). */
    fun state(t: Double): State {
        if (!hasFix) return State(0.0, 0.0, 0.0, false)
        val raw = predictRaw(t)
        val k = exp(-(t - errAt) / CORRECTION_TAU)
        val (lat, lon) = offset(raw.first, raw.second, errE * k, errN * k)
        return State(lat, lon, norm(raw.third + errC * k), true)
    }

    fun reset() { hasFix = false; errE = 0.0; errN = 0.0; errC = 0.0; gpsTurn = 0.0 }

    private fun predictRaw(t: Double): Triple<Double, Double, Double> {
        val dt = min(max(t - t0, 0.0), MAX_EXTRAPOLATION_S)
        if (speed <= 3) return Triple(lat0, lon0, course0)
        val rate = turnRate(t)
        val w = rate * PI / 180
        val c0 = course0 * PI / 180
        val e: Double
        val n: Double
        if (abs(w) < 0.002) {
            e = speed * dt * sin(c0); n = speed * dt * cos(c0)
        } else {
            // arc de cercle : intégrale de (sin c, cos c) avec c = c0 + w·t
            e = speed * (cos(c0) - cos(c0 + w * dt)) / w
            n = speed * (sin(c0 + w * dt) - sin(c0)) / w
        }
        val (lat, lon) = offset(lat0, lon0, e, n)
        return Triple(lat, lon, norm(course0 + rate * dt))
    }

    companion object {
        const val MAX_EXTRAPOLATION_S = 3.0
        const val CORRECTION_TAU = 0.35
        private const val R = 6_371_000.0

        fun offset(lat: Double, lon: Double, east: Double, north: Double): Pair<Double, Double> =
            (lat + north / R * 180 / PI) to (lon + east / (R * cos(lat * PI / 180)) * 180 / PI)

        fun enu(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Pair<Double, Double> =
            ((toLon - fromLon) * PI / 180 * R * cos(fromLat * PI / 180)) to ((toLat - fromLat) * PI / 180 * R)

        fun norm(a: Double): Double { val x = a % 360; return if (x < 0) x + 360 else x }
        /** a − b ramené dans ]−180, 180]. */
        fun angleDiff(a: Double, b: Double): Double { var d = (a - b) % 360; if (d > 180) d -= 360; if (d <= -180) d += 360; return d }
    }
}
