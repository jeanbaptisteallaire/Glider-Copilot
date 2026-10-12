package com.neutronstar.glidercopilot.domain.flight

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OwnshipPredictorTest {
    @Test fun straightLineExtrapolatesAtGroundSpeed() {
        val p = OwnshipPredictor()
        p.fix(43.8, 3.78, 30.0, 90.0, 0.0)
        val s = p.state(1.0)
        val (e, n) = OwnshipPredictor.enu(43.8, 3.78, s.lat, s.lon)
        assertEquals(30.0, e, 0.5)
        assertEquals(0.0, n, 0.5)
        assertEquals(90.0, s.course, 0.01)
    }

    @Test fun extrapolationStopsAfterThreeSeconds() {
        val p = OwnshipPredictor()
        p.fix(43.8, 3.78, 30.0, 0.0, 0.0)
        val a = p.state(3.0); val b = p.state(10.0)
        assertEquals(a.lat, b.lat, 1e-9)
    }

    @Test fun circlingFollowsAnArcAndCorrectionIsSmooth() {
        val p = OwnshipPredictor()
        val v = 22.0; val rate = 360.0 / 28   // tour de 28 s
        val r = v / (rate * PI / 180)
        fun truth(t: Double): Pair<Double, Double> {
            val a = rate * t * PI / 180
            return OwnshipPredictor.offset(43.8, 3.78, r * (1 - cos(a)), r * sin(a))
        }
        for (k in 0..10) { val (la, lo) = truth(k.toDouble()); p.fix(la, lo, v, OwnshipPredictor.norm(rate * k), k.toDouble()) }
        // entre deux positions : sur l'arc, à quelques mètres près
        val s = p.state(10.5); val (la, lo) = truth(10.5)
        val (e, n) = OwnshipPredictor.enu(la, lo, s.lat, s.lon)
        assertTrue(hypot(e, n) < 3.0, "écart ${hypot(e, n)} m")
        // pas de saut à l'arrivée d'une position : l'affichage juste avant et juste après est continu
        val before = p.state(11.0)
        val (la2, lo2) = truth(11.0); p.fix(la2, lo2, v, OwnshipPredictor.norm(rate * 11), 11.0)
        val after = p.state(11.0)
        val (je, jn) = OwnshipPredictor.enu(before.lat, before.lon, after.lat, after.lon)
        assertTrue(hypot(je, jn) < 0.5, "saut ${hypot(je, jn)} m")
    }

    @Test fun traceFadeOnlyOnOverlap() {
        // 4 tours au même endroit puis une ligne droite
        val lat = ArrayList<Double>(); val lon = ArrayList<Double>()
        for (t in 0 until 112) { val a = 2 * PI * t / 28; val (la, lo) = OwnshipPredictor.offset(43.8, 3.78, 100 * cos(a), 100 * sin(a)); lat += la; lon += lo }
        for (t in 1..60) { val (la, lo) = OwnshipPredictor.offset(43.8, 3.78, 100.0 + 30 * t, 0.0); lat += la; lon += lo }
        val f = TraceFade.levels(lat.toDoubleArray(), lon.toDoubleArray())
        assertTrue(f[0] >= 3, "premier tour estompé : ${f[0]}")
        assertEquals(0, f[100], "dernier tour net")
        assertEquals(0, f[150], "ligne droite nette")
    }
}
