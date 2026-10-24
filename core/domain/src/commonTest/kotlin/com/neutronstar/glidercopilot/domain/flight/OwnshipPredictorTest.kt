package com.neutronstar.glidercopilot.domain.flight

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnshipPredictorTest {
    private fun dist(a: OwnshipPredictor.State, lat: Double, lon: Double): Double =
        OwnshipPredictor.enu(lat, lon, a.lat, a.lon).let { hypot(it.first, it.second) }

    @Test fun sansPositionInvalide() = assertFalse(OwnshipPredictor().state(0.0).valid)

    @Test fun ligneDroiteVersLeNord() {
        val p = OwnshipPredictor()
        p.fix(45.0, 5.0, 25.0, 0.0, 0.0)
        val s = p.state(0.5)
        val (e, n) = OwnshipPredictor.enu(45.0, 5.0, s.lat, s.lon)
        assertEquals(12.5, n, 0.1)
        assertEquals(0.0, e, 0.1)
    }

    @Test fun extrapolationBorneeATroisSecondes() {
        val p = OwnshipPredictor()
        p.fix(45.0, 5.0, 25.0, 90.0, 0.0)
        assertEquals(dist(p.state(3.0), 45.0, 5.0), dist(p.state(30.0), 45.0, 5.0), 0.01)
    }

    @Test fun virageSuitLeTauxMesure() {
        val p = OwnshipPredictor()
        p.fix(45.0, 5.0, 22.0, 0.0, 0.0)
        p.fix(45.0002, 5.0, 22.0, 14.0, 1.0)   // 14 °/s mesuré (lissé à moitié)
        val c = p.state(1.0 + 1.0).course
        assertTrue(abs(OwnshipPredictor.angleDiff(c, 21.0)) < 3.0, "cap $c")
    }

    @Test fun ecartResorbeEnDouceur() {
        val p = OwnshipPredictor()
        p.fix(45.0, 5.0, 25.0, 0.0, 0.0)
        p.fix(45.0, 5.0005, 25.0, 0.0, 1.0)    // vraie position ≈ 40 m à l'est de la prévision
        val justAfter = p.state(1.0)
        val later = p.state(3.0)
        // juste après la nouvelle position, on affiche encore près de l'ancienne prévision (pas de saut)
        assertTrue(dist(justAfter, 45.0 + 25.0 / 111_195, 5.0) < 2.0)
        // deux secondes plus tard, l'écart est résorbé : on est sur la trajectoire de la vraie position
        val (e, _) = OwnshipPredictor.enu(45.0, 5.0005, later.lat, later.lon)
        assertTrue(abs(e) < 1.0, "écart résiduel $e m")
    }

    @Test fun auSolPasDExtrapolation() {
        val p = OwnshipPredictor()
        p.fix(45.0, 5.0, 1.0, -1.0, 0.0)
        assertTrue(dist(p.state(2.0), 45.0, 5.0) < 0.01)
    }
}
