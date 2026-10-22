package com.neutronstar.glidercopilot.domain.flight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PfdInstrumentsTest {
    @Test fun varioBornesEtSens() {
        assertEquals(0xFFFF2A2A, VarioPalette.flash(-5.0))
        assertEquals(0xFFFFE500, VarioPalette.flash(0.0))
        assertEquals(0xFF3D8BFF, VarioPalette.flash(9.0))
        assertEquals(0.0, VarioPalette.fraction(-3.0))
        assertEquals(1.0, VarioPalette.fraction(12.0))
        assertTrue(VarioPalette.overRange(4.5))
        assertEquals(listOf("+4", "+3", "+2", "+1", "0", "−1", "−2", "−3"), VarioPalette.ticks.map(VarioPalette::tickLabel))
    }

    @Test fun texteVarioLisibleSurBlanc() {
        var ms = -3.0
        while (ms <= 4.0) {
            assertTrue(VarioPalette.contrast(VarioPalette.ink(ms), 0xFFFFFFFF) >= 4.5, "contraste à $ms")
            ms += 0.25
        }
    }

    @Test fun traceRessortSurLeLisere() {
        VarioPalette.flashStops.forEach { (v, c) -> assertTrue(VarioPalette.contrast(c, VarioPalette.CASING) >= 4.5, "flash $v") }
    }

    @Test fun tambour() {
        val d = AltitudeTape.drum(1247.0)
        assertEquals("12", d.high)
        assertEquals(listOf("60", "50", "40", "30", "20"), d.rows)
        assertEquals(0.7, d.roll, 1e-9)
        assertEquals("", AltitudeTape.drum(85.0).high)
    }

    @Test fun altitudeAbsenteEnTirets() {
        val s = AltitudeTape.state(null, 900.0, true)
        assertFalse(s.valid); assertNull(s.drum)
    }

    @Test fun sousLeMinimum() {
        val s = AltitudeTape.state(820.0, 1000.0, true)
        assertTrue(s.belowMinimum)
        assertEquals(MarginLevel.ALARM, s.level)
        assertEquals("−180 m", s.gapText)
        assertEquals(-180.0 / -250.0, s.minimum!!.position, 1e-9)
    }

    @Test fun minimumHorsRubanEnButee() {
        val s = AltitudeTape.state(2000.0, 900.0, true)
        assertEquals(TapeMarker(-1.0, true), s.minimum)
        assertEquals("+1100 m", s.gapText)
        assertEquals(MarginLevel.OK, s.level)
    }

    @Test fun margeInconnueSansRepere() {
        val s = AltitudeTape.state(1500.0, 900.0, false)
        assertNull(s.minimum); assertEquals("", s.gapText); assertEquals(MarginLevel.UNKNOWN, s.level)
    }

    @Test fun ecartPrudent() {
        assertEquals("−10 m", AltitudeTape.gap(-3.0))
        assertEquals("+310 m", AltitudeTape.gap(318.0))
        assertEquals("0 m", AltitudeTape.gap(4.0))
        assertTrue(AltitudeTape.state(997.0, 1000.0, true).let { it.belowMinimum && it.gapText == "−10 m" })
    }

    @Test fun chiffreVario() {
        assertEquals("+1,8", VarioPalette.valueText(1.84))
        assertEquals("−2,4", VarioPalette.valueText(-2.36))
        assertEquals("0,0", VarioPalette.valueText(-0.02))
        assertEquals("+6,5", VarioPalette.valueText(6.5))
        assertEquals("–,–", VarioPalette.valueText(Double.NaN))
    }
}
