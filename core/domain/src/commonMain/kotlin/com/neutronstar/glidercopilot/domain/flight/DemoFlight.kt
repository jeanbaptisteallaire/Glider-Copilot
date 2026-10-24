package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.toRadians
import com.neutronstar.glidercopilot.domain.toDegrees
import com.neutronstar.glidercopilot.domain.EPOCH
import kotlin.time.Duration.Companion.seconds
import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import kotlinx.datetime.Instant
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
     * [spiralWanderMs] > 0 (iOS) : en spirale, le cercle dérive lentement au hasard (quelques mètres par tour) et
     * n'est pas parfaitement rond ; [spiralGainM] : gain de chaque pompe (iOS : plus de tours superposés), pour que les tours ne se superposent pas exactement. 0 = trace inchangée (Android).
     */
    /**
     * Même boucle, commencée [leadS] secondes avant l'entrée dans la première pompe (JB, 24/10/2026 : en démo, le
     * planeur part en spirale après 5 s de vol). La boucle étant fermée, la rotation ne crée aucune rupture ;
     * les heures sont recalées sur [start], à 1 Hz.
     */
    fun startingNearFirstSpiral(loop: List<IgcFix>, field: LatLon, leadS: Int = 5, start: Instant = EPOCH): List<IgcFix> {
        if (loop.isEmpty()) return loop
        val core = Geo.destination(field, LEGS.first().bearingFromField, LEGS.first().distanceKm)
        val entry = loop.indexOfFirst { Geo.distanceKm(it.position, core) <= 0.15 }
        if (entry < 0) return loop
        val from = (entry - leadS).coerceAtLeast(0)
        return (loop.drop(from) + loop.take(from)).mapIndexed { i, f -> f.copy(time = start + i.toLong().seconds) }
    }

    fun generate(
        field: LatLon,
        start: Instant = EPOCH,
        baseAltM: Double = 1550.0,
        windFromDeg: Double = 300.0,
        windKmh: Double = 15.0,
        glideKmh: Double = 100.0,
        sinkMs: Double = 1.1,
        seed: Int = 3,
        spiralWanderMs: Double = 0.0,
        spiralGainM: Double = 60.0,
    ): List<IgcFix> {
        val rnd = Random(seed)
        val wander = Random(seed + 101)   // tirages à part : la trace par défaut reste identique
        var dE = 0.0
        var dN = 0.0
        val wE = -windKmh * sin(toRadians(windFromDeg)) / 3.6
        val wN = -windKmh * cos(toRadians(windFromDeg)) / 3.6
        val out = ArrayList<IgcFix>()
        var pos = Geo.destination(field, LEGS.last().bearingFromField, LEGS.last().distanceKm)
        var alt = baseAltM
        var t = 0L
        fun emit() {
            out += IgcFix(start + t.seconds, pos, true, alt.roundToInt(), alt.roundToInt())   // journée standard : QNH 1013
            t++
        }
        fun move(eastMs: Double, northMs: Double) {
            val gs = kotlin.math.hypot(eastMs, northMs)
            if (gs > 0.01) pos = Geo.destination(pos, (toDegrees(kotlin.math.atan2(eastMs, northMs)) + 360) % 360, gs / 1000)
        }
        val startPos = pos
        var heading = 0.0
        for ((i, leg) in LEGS.withIndex()) {
            val core = Geo.destination(field, leg.bearingFromField, leg.distanceKm)
            // transition : cap corrigé de la dérive, jusqu'à 150 m du cœur
            var guard = 0
            while (Geo.distanceKm(pos, core) > 0.15 && guard++ < 2000) {
                val brg = toRadians(Geo.bearingDeg(pos, core))
                heading = brg
                val tas = glideKmh / 3.6
                move(tas * sin(brg) + wE, tas * cos(brg) + wN)
                alt -= sinkMs + 0.4 * sin(t / 9.0) * rnd.nextDouble()
                emit()
            }
            // spirale jusqu'à retrouver l'altitude de base + un peu (dernière pompe : exactement la base)
            val target = baseAltM + if (i == LEGS.lastIndex) 0.0 else spiralGainM + 30.0 * rnd.nextDouble()
            val period = 26.0 + 6 * rnd.nextDouble()
            var phase = heading   // continuité du cap à l'entrée en spirale
            val tas = 80 / 3.6
            while (alt < target) {
                phase += 2 * PI / period
                heading = phase
                if (spiralWanderMs > 0) {
                    // marche aléatoire lente du centre du cercle + rayon légèrement irrégulier
                    dE = (dE + (wander.nextDouble() - 0.5) * spiralWanderMs * 0.5).coerceIn(-spiralWanderMs, spiralWanderMs)
                    dN = (dN + (wander.nextDouble() - 0.5) * spiralWanderMs * 0.5).coerceIn(-spiralWanderMs, spiralWanderMs)
                    val v = tas * (1 + 0.07 * (wander.nextDouble() - 0.5))
                    move(v * sin(phase) + wE + dE, v * cos(phase) + wN + dN)
                } else {
                    move(tas * sin(phase) + wE, tas * cos(phase) + wN)
                }
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
