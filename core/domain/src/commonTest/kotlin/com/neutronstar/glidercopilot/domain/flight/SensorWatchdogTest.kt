package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.flight.SensorWatchdog.Event
import kotlin.test.Test
import kotlin.test.assertEquals

class SensorWatchdogTest {
    @Test fun gpsPerduPuisRetrouveUneSeuleFois() {
        val w = SensorWatchdog()
        assertEquals(emptyList(), w.update(0.0, true, 1.0, true))
        assertEquals(emptyList(), w.update(1.0, true, 9.0, true))
        assertEquals(listOf(Event.GPS_LOST), w.update(2.0, true, 11.0, true))
        assertEquals(emptyList(), w.update(3.0, true, 30.0, true))
        assertEquals(emptyList(), w.update(4.0, true, 5.0, true))   // pas encore stable
        assertEquals(listOf(Event.GPS_BACK), w.update(5.0, true, 0.5, true))
    }

    @Test fun varioAbsentCinqSecondes() {
        val w = SensorWatchdog()
        assertEquals(emptyList(), w.update(0.0, true, 1.0, false))
        assertEquals(emptyList(), w.update(4.9, true, 1.0, false))
        assertEquals(listOf(Event.VARIO_LOST), w.update(5.0, true, 1.0, false))
        assertEquals(emptyList(), w.update(6.0, true, 1.0, false))
        assertEquals(listOf(Event.VARIO_BACK), w.update(7.0, true, 1.0, true))
    }

    @Test fun rienAuSol() {
        val w = SensorWatchdog()
        assertEquals(emptyList(), w.update(0.0, false, null, false))
        assertEquals(emptyList(), w.update(100.0, false, null, false))
        // décollage sans GPS : annonce immédiate
        assertEquals(listOf(Event.GPS_LOST), w.update(101.0, true, null, true))
    }
}

class DemoStartTest {
    @Test fun spiraleApresSixSecondes() {
        val field = com.neutronstar.glidercopilot.domain.LatLon(43.8, 3.78)
        val raw = DemoFlight.generate(field, windKmh = 0.0)
        val loop = DemoFlight.startingNearFirstSpiral(raw, field)
        assertEquals(raw.size, loop.size)
        val core = com.neutronstar.glidercopilot.domain.Geo.destination(field, DemoFlight.LEGS.first().bearingFromField, DemoFlight.LEGS.first().distanceKm)
        val entry = loop.indexOfFirst { com.neutronstar.glidercopilot.domain.Geo.distanceKm(it.position, core) <= 0.15 }
        assertEquals(DemoFlight.LEAD_S, entry)
        assertEquals(6, entry)
        // heures à 1 Hz depuis le début
        assertEquals(1L, (loop[1].time - loop[0].time).inWholeSeconds)
    }

    /** JB (24/10/2026) : réglages de la démo (sans vent) → pompe ronde et centrée, pas de spirale qui dérive. */
    @Test fun pompeRondeEtCentree() {
        val field = com.neutronstar.glidercopilot.domain.LatLon(43.8, 3.78)
        val loop = DemoFlight.startingNearFirstSpiral(DemoFlight.generate(field, windKmh = 0.0, spiralWanderMs = 0.35, spiralGainM = 220.0, centeredSpirals = true), field)
        val core = com.neutronstar.glidercopilot.domain.Geo.destination(field, DemoFlight.LEGS.first().bearingFromField, DemoFlight.LEGS.first().distanceKm)
        val circling = loop.drop(DemoFlight.LEAD_S + 10).take(120)
        val d = circling.map { com.neutronstar.glidercopilot.domain.Geo.distanceKm(it.position, core) }
        kotlin.test.assertTrue(d.max() < 0.25, "cercle décentré : ${d.max()} km")
        kotlin.test.assertTrue(d.max() - d.min() < 0.08, "cercle irrégulier ou qui dérive : ${d.min()}…${d.max()} km")
    }
}
