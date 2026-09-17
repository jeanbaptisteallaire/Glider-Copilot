package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Vol simulé du mode démo : boucle fermée de pompes et de transitions autour du terrain, altitude autour de [baseAltM].
 * Chaque tour de boucle revient au point et à l'altitude de départ, pour rejouer sans rupture.
 */
object DemoFlight {
    data class Leg(val bearingFromField: Double, val distanceKm: Double, val climbMs: Double)

    val LEGS = listOf(
        Leg(40.0, 4.0, 2.1), Leg(100.0, 5.0, 1.6), Leg(160.0, 3.5, 2.6),
        Leg(220.0, 4.5, 1.4), Leg(285.0, 3.5, 2.0), Leg(340.0, 5.0, 1.8),
    )

    /**
     * Trace 1 Hz commençant en transition vers la première pompe. Vent [windFromDeg]/[windKmh] : les spirales dérivent,
     * les transitions sont corrigées (vitesse sol différente selon le cap).
     */
    fun generate(
        field: LatLon,
        start: Instant = Instant.EPOCH,
        baseAltM: Double = 1550.0,
        windFromDeg: Double = 300.0,
        windKmh: Double = 15.0,
        glideKmh: Double = 100.0,
        sinkMs: Double = 1.1,
        seed: Int = 3,
    ): List<IgcFix> {
        val rnd = Random(seed)
        val wE = -windKmh * sin(Math.toRadians(windFromDeg)) / 3.6
        val wN = -windKmh * cos(Math.toRadians(windFromDeg)) / 3.6
        val out = ArrayList<IgcFix>()
        var pos = Geo.destination(field, LEGS.last().bearingFromField, LEGS.last().distanceKm)
        var alt = baseAltM
        var t = 0L
        fun emit() {
            out += IgcFix(start.plusSeconds(t), pos, true, alt.roundToInt(), alt.roundToInt())   // journée standard : QNH 1013
            t++
        }
        fun move(eastMs: Double, northMs: Double) {
            val gs = Math.hypot(eastMs, northMs)
            if (gs > 0.01) pos = Geo.destination(pos, (Math.toDegrees(Math.atan2(eastMs, northMs)) + 360) % 360, gs / 1000)
        }
        val startPos = pos
        var heading = 0.0
        for ((i, leg) in LEGS.withIndex()) {
            val core = Geo.destination(field, leg.bearingFromField, leg.distanceKm)
            // transition : cap corrigé de la dérive, jusqu'à 150 m du cœur
            var guard = 0
            while (Geo.distanceKm(pos, core) > 0.15 && guard++ < 2000) {
                val brg = Math.toRadians(Geo.bearingDeg(pos, core))
                heading = brg
                val tas = glideKmh / 3.6
                move(tas * sin(brg) + wE, tas * cos(brg) + wN)
                alt -= sinkMs + 0.4 * sin(t / 9.0) * rnd.nextDouble()
                emit()
            }
            // spirale jusqu'à retrouver l'altitude de base + un peu (dernière pompe : exactement la base)
            val target = baseAltM + if (i == LEGS.lastIndex) 0.0 else 60.0 + 30.0 * rnd.nextDouble()
            val period = 26.0 + 6 * rnd.nextDouble()
            var phase = heading   // continuité du cap à l'entrée en spirale
            val tas = 80 / 3.6
            while (alt < target) {
                phase += 2 * PI / period
                heading = phase
                move(tas * sin(phase) + wE, tas * cos(phase) + wN)
                // montée plus forte d'un côté du cercle (cœur décalé)
                alt += leg.climbMs + 0.9 * sin(phase - 1.0)
                emit()
            }
        }
        // retour au point de départ de la boucle
        val back = Geo.distanceKm(pos, startPos)
        if (back < 0.03) return out
        val steps = (back * 1000 / 27.0).toInt().coerceAtLeast(1)
        val brg = Geo.bearingDeg(pos, startPos)
        val drop = (alt - baseAltM) / steps
        repeat(steps) {
            pos = Geo.destination(pos, brg, back / steps)
            alt -= drop
            emit()
        }
        return out
    }
}
