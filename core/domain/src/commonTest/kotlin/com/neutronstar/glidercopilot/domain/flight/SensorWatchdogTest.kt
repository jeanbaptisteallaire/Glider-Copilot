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
