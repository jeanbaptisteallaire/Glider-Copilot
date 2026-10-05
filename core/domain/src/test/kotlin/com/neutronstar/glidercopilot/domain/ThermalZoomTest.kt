package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidercopilot.domain.flight.ThermalZoom
import com.neutronstar.glidercopilot.domain.flight.TracePoint
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalZoomTest {
    private val t0 = Instant.parse("2026-10-06T12:00:00Z")
    private val c = LatLon(43.80, 3.90)

    /** Cercle de [radiusM] parcouru en [periodS] s, [seconds] points à 1 Hz, montée [climb]. */
    private fun circle(seconds: Int, radiusM: Double = 120.0, periodS: Double = 24.0, climb: Double = 1.5) = (0 until seconds).map { s ->
        val a = 2 * PI * s / periodS
        val dLat = radiusM * cos(a) / 111_320.0
        val dLon = radiusM * sin(a) / (111_320.0 * cos(c.lat * PI / 180))
        TracePoint(LatLon(c.lat + dLat, c.lon + dLon), climb, t0.plusSeconds(s.toLong()))
    }

    @Test fun noViewBeforeOneFullTurn() {
        val pts = circle(15)
        assertNull(ThermalZoom.view(pts, circling = true, since = t0, now = t0.plusSeconds(14)))
    }

    @Test fun zoomsOnTheLastCircleOnceClimbingAndTurnComplete() {
        val pts = circle(40)
        val v = ThermalZoom.view(pts, circling = true, since = t0, now = t0.plusSeconds(39))
        assertNotNull(v)
        v!!
        assertTrue("centre proche du centre du cercle", Geo.distanceKm(v.centre, c) * 1000 < 25)
        assertEquals(120.0, v.radiusM, 20.0)
        assertTrue(v.climbMs > 1.0)
    }

    @Test fun noViewWhenSinkingOrNotCircling() {
        val sinking = circle(40, climb = -0.8)
        assertNull(ThermalZoom.view(sinking, circling = true, since = t0, now = t0.plusSeconds(39)))
        assertNull(ThermalZoom.view(circle(40), circling = false, since = t0, now = t0.plusSeconds(39)))
    }

    @Test fun circleFillsTwoThirdsOfTheMap() {
        val v = ThermalZoom.view(circle(40, radiusM = 150.0), true, t0, t0.plusSeconds(39))!!
        val side = 400.0 // points d'écran
        val z = ThermalZoom.zoomFor(v, side)
        val metersPerPoint = 40_075_016.686 * cos(v.centre.lat * PI / 180) / (512.0 * Math.pow(2.0, z))
        assertEquals(2.0 / 3.0, 2 * v.radiusM / metersPerPoint / side, 0.02)
    }
}
