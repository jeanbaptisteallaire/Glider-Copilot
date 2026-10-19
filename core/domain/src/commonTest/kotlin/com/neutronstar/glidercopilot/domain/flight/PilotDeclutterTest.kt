package com.neutronstar.glidercopilot.domain.flight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PilotDeclutterTest {
    private fun d(
        essential: Boolean = false, profile: Boolean = true, vario: Boolean = true,
        known: Boolean = true, margin: Double = 800.0, gps: Boolean = true, baro: Boolean = true, data: Boolean = true, ogn: Boolean = true,
    ) = PilotDeclutter.display(essential, profile, vario, known, margin, gps, baro, data, ogn)

    @Test fun marginLevels() {
        assertEquals(MarginLevel.UNKNOWN, PilotDeclutter.marginLevel(false, 500.0))
        assertEquals(MarginLevel.UNKNOWN, PilotDeclutter.marginLevel(true, Double.NaN))
        assertEquals(MarginLevel.OK, PilotDeclutter.marginLevel(true, 200.0))
        assertEquals(MarginLevel.CAUTION, PilotDeclutter.marginLevel(true, 199.0))
        assertEquals(MarginLevel.CAUTION, PilotDeclutter.marginLevel(true, 0.0))
        assertEquals(MarginLevel.ALARM, PilotDeclutter.marginLevel(true, -1.0))
    }

    @Test fun completeModeShowsEverythingButHealthySensors() {
        val x = d()
        assertTrue(x.showHeaderDetails && x.showProfile && x.showVario && x.showVarioDetails && x.showMapTools && x.showOgn)
        assertFalse(x.profileForced)
        assertFalse(x.gpsAlert || x.baroAlert || x.dataAlert)
    }

    @Test fun essentialKeepsOnlyMarginDistanceAndField() {
        val x = d(essential = true)
        assertFalse(x.showHeaderDetails || x.showProfile || x.showVario || x.showVarioDetails || x.showMapTools || x.showOgn)
    }

    @Test fun lowMarginColoursMarginWithoutProfileInEssential() {
        val essential = d(essential = true, margin = 120.0)
        assertFalse(essential.showProfile || essential.profileForced)
        assertEquals(MarginLevel.CAUTION, essential.marginLevel)
        // Complet : le choix du pilote prime (l'écran rouvre la coupe au passage du seuil, côté interface)
        val folded = d(profile = false, margin = -50.0)
        assertFalse(folded.showProfile || folded.profileForced)
        assertEquals(MarginLevel.ALARM, folded.marginLevel)
        val opened = d(profile = true, margin = 50.0)
        assertTrue(opened.showProfile)
        assertFalse(opened.profileForced)
    }

    @Test fun sensorAlertsShowEvenInEssential() {
        val x = d(essential = true, gps = false, data = false)
        assertTrue(x.gpsAlert && x.dataAlert)
        assertFalse(x.baroAlert)
    }

    @Test fun ognHiddenWhenDisconnected() {
        assertFalse(d(ogn = false).showOgn)
    }
}
