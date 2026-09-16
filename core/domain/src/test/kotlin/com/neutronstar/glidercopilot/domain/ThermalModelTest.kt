package com.neutronstar.glidercopilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ThermalModelTest {
    private val ground = 200.0

    /** Profil synthétique : couche mélangée jusqu'à [mixedTop] puis inversion. */
    private fun profile(t2m: Double, mixedTop: Double): List<ProfileLevel> {
        val heights = listOf(20, 50, 100, 250, 500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500, 2750, 3000)
        val pSurf = 98_000.0
        return heights.map { h ->
            val tAdiab = t2m - 0.0098 * h
            val t = if (h <= mixedTop) tAdiab - 0.3 else (t2m - 0.0098 * mixedTop) - 0.004 * (h - mixedTop) + 2.0
            val p = pSurf * kotlin.math.exp(-Atmosphere.G * h / (Atmosphere.R_DRY * t))
            ProfileLevel(h.toDouble(), t, t - 10, p, 5.0, 300.0, (ground + h) * Atmosphere.G, 0.0)
        }
    }

    private fun surface(t2m: Double, td: Double?, sw: Double?) = SurfaceConditions(
        Instant.parse("2026-09-16T13:00:00Z"), t2m, td, 98_000.0, sw, 20.0, 10.0, 200.0, 3.0, 290.0,
    )

    @Test fun groundAltitudeFromGeopotential() {
        assertEquals(ground, ThermalModel.groundAltitude(profile(300.0, 1500.0))!!, 0.01)
    }

    @Test fun dryTopFindsInversion() {
        val a = ThermalModel.analyse(surface(300.0, null, 600.0), profile(300.0, 1500.0))
        assertTrue("sommet ${a.dryTopAglM}", a.dryTopAglM in 1400.0..1800.0)
        assertEquals(LiftType.BLUE, a.liftType)
        assertTrue(a.climbMs > 0.5)
    }

    @Test fun cumulusBaseCapsCeiling() {
        val a = ThermalModel.analyse(surface(300.0, 292.0, 600.0), profile(300.0, 2000.0))
        assertEquals(1000.0, a.cloudBaseAglM!!, 1.0)
        assertEquals(LiftType.CUMULUS, a.liftType)
        assertEquals(1000.0, a.ceilingAglM, 1.0)
        assertEquals(1200.0, a.ceilingMslM, 1.0)
    }

    @Test fun noSunNoClimb() {
        val a = ThermalModel.analyse(surface(300.0, null, 0.0), profile(300.0, 1500.0))
        assertEquals(0.0, a.wStarMs, 1e-9)
        assertEquals(0.0, a.climbMs, 1e-9)
    }

    @Test fun stableProfileGivesNoLift() {
        val stable = profile(290.0, 0.0)
        val a = ThermalModel.analyse(surface(290.0, null, 500.0), stable)
        assertEquals(LiftType.NONE, a.liftType)
    }

    @Test fun daySummary() {
        val hrs = (9..18).map { h ->
            val sw = if (h in 11..17) 500.0 else 50.0
            ThermalModel.analyse(surface(300.0, null, sw).copy(validTime = Instant.parse("2026-09-16T%02d:00:00Z".format(h))), profile(300.0, 1500.0))
        }
        val s = DayPlanner.summarize(hrs)
        assertEquals(Instant.parse("2026-09-16T11:00:00Z"), s.triggerTime)
        assertEquals(Instant.parse("2026-09-16T17:00:00Z"), s.endTime)
        assertTrue(s.quality != DayQuality.NONE)
    }
}
