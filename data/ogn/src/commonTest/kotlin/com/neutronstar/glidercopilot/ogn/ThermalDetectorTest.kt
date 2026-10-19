package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.LatLon
import kotlinx.datetime.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Pompes partagées : spirales d'autres planeurs (trajectoires OGN synthétiques), JVM et simulateur iOS. */
class ThermalDetectorTest {
    private val t0 = Instant.parse("2026-09-17T12:00:00Z")

    /** Spirale de rayon 150 m, un tour en 26 s, montée [climb] m/s, une trame toutes les [step] s. */
    private fun spiral(addr: String, seconds: Int, climb: Double, step: Int = 4, center: LatLon = LatLon(43.9, 3.9),
                       right: Boolean = true, start: Instant = t0): List<OgnFix> =
        (0..seconds step step).map { s ->
            val a = (if (right) 1 else -1) * 2 * PI * s / 26.0
            val pos = LatLon(center.lat + 0.15 * cos(a) / 111.32, center.lon + 0.15 * sin(a) / (111.32 * cos(center.lat * PI / 180)))
            val track = ((a * 180 / PI + (if (right) 90 else -90)) % 360 + 360) % 360
            OgnFix(addr, AddressType.FLARM, 1, "FLR$addr", start.plus(s.seconds), start.plus((s + 3).seconds), pos, 1000.0 + climb * s,
                track, 80.0, climb, if (right) 13.8 else -13.8, stealth = false, noTracking = false, receiver = "R")
        }

    private fun straight(addr: String, seconds: Int): List<OgnFix> = (0..seconds step 4).map { s ->
        OgnFix(addr, AddressType.FLARM, 1, "FLR$addr", t0.plus(s.seconds), t0.plus((s + 2).seconds),
            LatLon(43.8 + s * 0.0002, 3.8), 1500.0 - s * 0.8, 0.0, 110.0, -0.8, 0.0, false, false, "R")
    }

    @Test fun circlingTrackGivesOneClimbingEpisode() {
        val ep = ThermalDetector.episodes(spiral("AAAAAA", 120, 1.5))
        assertEquals(1, ep.size)
        assertTrue(abs(ep[0].climbMs - 1.5) < 0.2, "montée ${ep[0].climbMs}")
        assertTrue(ep[0].turnDeg > 1200)
        assertTrue(abs(ep[0].center.lat - 43.9) < 0.002)
        assertEquals(1, ThermalDetector.episodes(spiral("CCCCCC", 90, 1.0, right = false)).size)
        assertTrue(ThermalDetector.episodes(straight("BBBBBB", 300)).isEmpty())
        assertTrue(ThermalDetector.episodes(spiral("DDDDDD", 24, 2.0)).isEmpty())   // un seul tour
    }

    @Test fun sinkingSpiralIsNotAPump() {
        val eps = ThermalDetector.episodes(spiral("EEEEEE", 90, -0.8))
        assertEquals(1, eps.size)
        assertTrue(ThermalDetector.thermals(eps).isEmpty())
    }

    @Test fun storeTurnsOtherGlidersSpiralsIntoPumpsAndForgetsThem() {
        val store = TrafficStore()
        (spiral("AAAAAA", 120, 1.8) + spiral("BBBBBB", 90, 1.2, center = LatLon(43.903, 3.901), start = t0.plus(30.seconds)))
            .forEach { store.add(it) }
        val now = t0.plus(130.seconds)
        store.analyse(now)
        val pumps = store.thermals(now)
        assertEquals(1, pumps.size)
        assertEquals(2, pumps[0].aircraftCount)
        assertTrue(store.aircraft(now).all { it.circling })
        // mon propre planeur n'est jamais compté comme une pompe partagée
        assertEquals(1, store.thermals(now, exclude = setOf("AAAAAA"))[0].aircraftCount)
        // au-delà de 45 min, la pompe disparaît
        val later = now.plus(50.minutes)
        store.analyse(later)
        assertTrue(store.thermals(later).isEmpty())
    }
}
