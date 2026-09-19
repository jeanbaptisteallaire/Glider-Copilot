package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidercopilot.domain.flight.CalibrationLog
import com.neutronstar.glidercopilot.domain.flight.CalibrationRecorder
import com.neutronstar.glidercopilot.domain.flight.GpsFix
import com.neutronstar.glidercopilot.domain.flight.SensorSample
import com.neutronstar.glidercopilot.domain.flight.VarioFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/** Journal de calibration (V7.3) : le format doit survivre à un aller-retour écriture/lecture sans perte. */
class CalibrationLogTest {
    private val fixWithNulls = GpsFix(Instant.parse("2026-09-19T12:00:03Z"), LatLon(43.80028, 3.78167), null, null, null, null)
    private val fixFull = GpsFix(Instant.parse("2026-09-19T12:00:04Z"), LatLon(43.5, -1.25), 812.4, 91.3, 274.5, 4.0)

    @Test fun baroRoundTrip() {
        val s = SensorSample.Baro(123_456_789L, 987.65)
        val parsed = CalibrationLog.parseLine(CalibrationLog.line(s))
        assertEquals(s, parsed)
    }

    @Test fun accelRoundTrip() {
        val s = SensorSample.Accel(42_000_000L, -0.732)
        assertEquals(s, CalibrationLog.parseLine(CalibrationLog.line(s)))
    }

    @Test fun gpsRoundTripWithAllFields() {
        val s = SensorSample.Gps(9_000_000_000L, fixFull)
        val parsed = CalibrationLog.parseLine(CalibrationLog.line(s)) as SensorSample.Gps
        assertEquals(s.timeNs, parsed.timeNs)
        assertEquals(fixFull.time, parsed.fix.time)
        assertEquals(fixFull.position.lat, parsed.fix.position.lat, 1e-9)
        assertEquals(fixFull.position.lon, parsed.fix.position.lon, 1e-9)
        assertEquals(fixFull.altitudeM, parsed.fix.altitudeM!!, 1e-9)
        assertEquals(fixFull.groundSpeedKmh, parsed.fix.groundSpeedKmh!!, 1e-9)
        assertEquals(fixFull.trackDeg, parsed.fix.trackDeg!!, 1e-9)
        assertEquals(fixFull.accuracyM, parsed.fix.accuracyM!!, 1e-9)
    }

    @Test fun gpsRoundTripWithMissingOptionalFields() {
        val parsed = CalibrationLog.parseLine(CalibrationLog.line(SensorSample.Gps(1L, fixWithNulls))) as SensorSample.Gps
        assertNull(parsed.fix.altitudeM)
        assertNull(parsed.fix.groundSpeedKmh)
        assertNull(parsed.fix.trackDeg)
        assertNull(parsed.fix.accuracyM)
    }

    @Test fun headerAndBlankLinesAreIgnored() {
        assertNull(CalibrationLog.parseLine(CalibrationLog.HEADER))
        assertNull(CalibrationLog.parseLine(""))
        assertNull(CalibrationLog.parseLine("# un commentaire quelconque"))
    }

    @Test fun recorderWritesHeaderThenCountsSamples() {
        val lines = ArrayList<String>()
        val rec = CalibrationRecorder(lines::add)
        rec.onBaro(989.1, 0L)
        rec.onAccel(0.05, 20_000_000L)
        rec.onGps(fixWithNulls, 1_000_000_000L)
        assertEquals(3, rec.samples)
        assertEquals(4, lines.size) // en-tête + 3 échantillons
        assertTrue(lines[0].startsWith("#"))
        val parsed = CalibrationLog.parse(lines.joinToString("\n"))
        assertEquals(3, parsed.size)
    }

    /** Un journal relu doit rejouer [VarioFilter] à l'identique d'un enregistrement direct : c'est le but du format. */
    @Test fun replayedLogDrivesTheSameFilterAsLiveCalls() {
        val lines = ArrayList<String>()
        val rec = CalibrationRecorder(lines::add)
        val direct = VarioFilter()
        var t = 0L
        repeat(50) { i ->
            val hPa = 1000.0 - i * 0.02
            direct.onBaro(hPa, t); rec.onBaro(hPa, t)
            t += 40_000_000L
            val a = if (i % 2 == 0) 0.3 else -0.1
            direct.onAcceleration(a, t); rec.onAccel(a, t)
            t += 20_000_000L
        }
        val replay = VarioFilter()
        for (s in CalibrationLog.parse(lines.joinToString("\n"))) {
            when (s) {
                is SensorSample.Baro -> replay.onBaro(s.hPa, s.timeNs)
                is SensorSample.Accel -> replay.onAcceleration(s.upMs2, s.timeNs)
                is SensorSample.Gps -> Unit
            }
        }
        assertTrue(abs(direct.altitude - replay.altitude) < 1e-9)
        assertTrue(abs(direct.climb - replay.climb) < 1e-9)
    }
}
