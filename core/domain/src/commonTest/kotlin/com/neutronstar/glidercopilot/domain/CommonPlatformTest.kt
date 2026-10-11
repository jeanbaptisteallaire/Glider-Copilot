package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidercopilot.domain.flight.Igc
import com.neutronstar.glidercopilot.domain.flight.IgcFix
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** iOS / KMP : mêmes résultats sur la JVM et sur iOS (lancé par jvmTest et iosSimulatorArm64Test). */
class CommonPlatformTest {
    @Test fun formatFixedMatchesJavaFormat() {
        assertEquals("1,3", formatFixed(1.25, 1, decimalComma = true))       // demi-haut, comme String.format
        assertEquals("-0,4", formatFixed(-0.35, 1, decimalComma = true))
        assertEquals("43.80028", formatFixed(43.800283, 5))
        assertEquals("0.00000", formatFixed(0.0, 5))
        assertEquals("1234567.0", formatFixed(1234567.0, 1))                 // jamais de séparateur de milliers
    }

    @Test fun secondsBetweenFloorsLikeJavaTime() {
        val a = Instant.parse("2026-07-14T12:00:00.600Z")
        val b = Instant.parse("2026-07-14T12:00:00.100Z")
        assertEquals(-1L, secondsBetween(a, b))
        assertEquals(0L, secondsBetween(b, a))
        assertEquals(3600L, secondsBetween(Instant.parse("2026-07-14T12:00:00Z"), Instant.parse("2026-07-14T13:00:00Z")))
    }

    @Test fun igcRecordsAreByteIdentical() {
        val b = Igc.bRecord(IgcFix(Instant.parse("2026-07-14T13:05:09Z"), LatLon(43 + 29.130 / 60, 4 + 22.650 / 60), true, 1234, 1301))
        assertEquals("B1305094329130N00422650EA0123401301", b)
        val s = Igc.bRecord(IgcFix(Instant.parse("2026-07-14T00:00:00Z"), LatLon(-33.5, -70.25), false, -12, null))
        assertEquals("B0000003330000S07015000WV-001200000", s)
        assertEquals("2026-07-14-XXX-GLY-03.igc", Igc.fileName(LocalDate(2026, 7, 14), 3))
        assertEquals("HFDTEDATE:140726,01", Igc.headerLines(Igc.Header(LocalDate(2026, 7, 14)))[1])
    }

    @Test fun igcHeaderNamesThePlatform() {
        val android = Igc.headerLines(Igc.Header(LocalDate(2026, 7, 14)))
        assertEquals("HFRHWHARDWAREVERSION:Android", android[8])   // Android : en-tête inchangé
        assertEquals("HFFTYFRTYPE:WINDGLIDER,Android", android[9])
        val ios = Igc.headerLines(Igc.Header(LocalDate(2026, 7, 14), platform = "iOS"))
        assertEquals("HFRHWHARDWAREVERSION:iOS", ios[8])
        assertEquals("HFGPSRECEIVER:iOS,telephone", ios[10])
    }

    @Test fun igcParseRoundTripsAndCrossesMidnight() {
        val text = "HFDTEDATE:310126,01\nB2359590000000N00000000EA0000000000\nB0000010000000N00000000EA0000000000\n"
        val fixes = Igc.parse(text)
        assertEquals(Instant.parse("2026-01-31T23:59:59Z"), fixes[0].time)
        assertEquals(Instant.parse("2026-02-01T00:00:01Z"), fixes[1].time)
        // résolution SMART de java.time : 31 février → dernier jour du mois
        assertEquals(Instant.parse("2026-02-28T12:00:00Z"), Igc.parse("HFDTE310226\nB1200000000000N00000000EA0000000000\n")[0].time)
    }

    @Test fun clubSearchIgnoresAccents() {
        val c = Club("x", "Aéroclub de Saint-Étienne", null, "Saint-Étienne", "42000", null, "LFMH", null, null)
        assertEquals(listOf(c), ClubSearch.search(listOf(c), "etienne"))
    }
}
