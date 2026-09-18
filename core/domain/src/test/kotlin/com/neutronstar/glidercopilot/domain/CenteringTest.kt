package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidercopilot.domain.flight.CoreEstimate
import com.neutronstar.glidercopilot.domain.flight.CoreEstimator
import com.neutronstar.glidercopilot.domain.flight.Decision
import com.neutronstar.glidercopilot.domain.flight.NetworkThermals
import com.neutronstar.glidercopilot.domain.flight.TracePoint
import com.neutronstar.glidercopilot.domain.flight.driftCorrected
import com.neutronstar.glidercopilot.domain.safety.Wind
import com.neutronstar.glidercopilot.domain.safety.WindSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class CenteringTest {
    private val t0: Instant = Instant.parse("2026-07-14T12:00:00Z")
    private val start = LatLon(43.75, 3.85)

    /**
     * Spirale de rayon [radiusM] autour d'un centre qui dérive avec le vent, dans une pompe gaussienne dont le cœur
     * est décalé de [coreOffsetM] vers [coreBearing]. Renvoie la trace 1 Hz.
     */
    private fun spiral(
        turns: Double = 4.0,
        radiusM: Double = 110.0,
        periodS: Double = 26.0,
        coreOffsetM: Double = 90.0,
        coreBearing: Double = 45.0,
        wind: Wind? = null,
    ): List<TracePoint> {
        val out = ArrayList<TracePoint>()
        val seconds = (turns * periodS).toInt()
        var centre = start
        val core0 = Geo.destination(start, coreBearing, coreOffsetM / 1000)
        for (s in 0..seconds) {
            val now = t0.plusSeconds(s.toLong())
            // le centre du cercle et le cœur dérivent avec le vent
            val drift = wind?.let { w -> w.speedKmh * s / 3600.0 } ?: 0.0
            val downwind = wind?.let { (it.fromDeg + 180) % 360 } ?: 0.0
            centre = if (drift > 0) Geo.destination(start, downwind, drift) else start
            val core = if (drift > 0) Geo.destination(core0, downwind, drift) else core0
            val angle = 2 * PI * s / periodS
            val pos = LatLon(
                centre.lat + radiusM / 111_320.0 * cos(angle),
                centre.lon + radiusM / (111_320.0 * cos(Math.toRadians(centre.lat))) * sin(angle),
            )
            val d = Geo.distanceKm(pos, core) * 1000
            val climb = 3.0 * exp(-(d * d) / (2 * 140.0 * 140.0)) - 0.6
            out += TracePoint(pos, climb, now)
        }
        return out
    }

    @Test fun firstTurnIsAnalysing() {
        val trace = spiral(turns = 0.5)
        val now = trace.last().time
        val e = CoreEstimator.estimate(trace, t0, now, null, 0.0, 13.8)!!
        assertTrue(e.analysing)
        assertTrue("progression ${e.progress}", e.progress in 0.3..0.6)
        assertTrue(e.cue()!!.startsWith("Analyse"))
    }

    @Test fun findsTheOffCentreCoreWithoutWind() {
        val trace = spiral()
        val now = trace.last().time
        val e = CoreEstimator.estimate(trace, t0, now, null, 0.0, 13.8)!!
        assertFalse(e.analysing)
        val core = Geo.destination(start, 45.0, 0.090)
        assertEquals("cœur estimé à ${Geo.distanceKm(core, e.core!!) * 1000} m du vrai", 0.0, Geo.distanceKm(core, e.core!!) * 1000, 45.0)
        assertTrue("décalage ${e.offsetM}", e.offsetM in 45.0..140.0)
        assertTrue("confiance ${e.confidence}", e.confidence > 0.3)
        assertFalse(e.centred)
    }

    @Test fun windDriftIsRemovedBeforeEstimating() {
        val wind = Wind(300.0, 25.0, WindSource.CIRCLING, t0)
        val trace = spiral(wind = wind)
        val now = trace.last().time
        // sans recalage, la trace s'étire sous le vent : le cœur estimé part loin
        val naive = CoreEstimator.estimate(trace, t0, now, null, 0.0, 13.8)!!
        val fixed = CoreEstimator.estimate(trace, t0, now, wind, 0.0, 13.8)!!
        val trueCore = Geo.destination(
            Geo.destination(start, 45.0, 0.090),
            120.0, wind.speedKmh * Duration.between(t0, now).seconds / 3600.0,
        )
        val errNaive = Geo.distanceKm(trueCore, naive.core!!) * 1000
        val errFixed = Geo.distanceKm(trueCore, fixed.core!!) * 1000
        assertTrue("recalé $errFixed m, brut $errNaive m", errFixed < errNaive / 2)
        assertTrue("recalé $errFixed m", errFixed < 60)
    }

    @Test fun cueCountsSecondsToTheCore() {
        val trace = spiral()
        val now = trace.last().time
        // cap actuel 90° et virage à droite à 13,8 °/s : le cœur au 45° est atteint après presque un tour complet
        val e = CoreEstimator.estimate(trace, t0, now, null, heading = 90.0, turnRateDegS = 13.8)!!
        val brg = e.bearingToCoreDeg!!
        val expected = ((brg - 90 + 360) % 360) / 13.8
        assertEquals(expected, e.secondsBeforeWidening!!, 0.6)
        assertTrue(e.cue()!!.startsWith("Élargis dans") || e.cue()!!.startsWith("ÉLARGIS"))
        // virage à gauche : le cœur arrive dans l'autre sens
        val left = CoreEstimator.estimate(trace, t0, now, null, heading = 90.0, turnRateDegS = -13.8)!!
        assertEquals((360 - (brg - 90 + 360) % 360) / 13.8, left.secondsBeforeWidening!!, 0.6)
    }

    @Test fun centredWhenTheCoreIsUnderTheCircle() {
        val trace = spiral(coreOffsetM = 5.0)
        val e = CoreEstimator.estimate(trace, t0, trace.last().time, null, 0.0, 13.8)!!
        assertTrue("décalage ${e.offsetM}", e.centred)
        assertTrue(e.cue()!!.startsWith("Centré"))
    }

    @Test fun turnRateFromTrace() {
        val trace = spiral(periodS = 24.0)
        val rate = CoreEstimator.turnRate(trace, trace.last().time)
        assertEquals(360.0 / 24.0, rate, 1.5)
        assertNull(CoreEstimator.estimate(emptyList(), null, t0, null, null, 0.0))
    }

    @Test fun driftCorrectionMovesDownwind() {
        val wind = Wind(270.0, 36.0, WindSource.CIRCLING, t0)      // 10 m/s d'ouest
        val p = TracePoint(start, 1.0, t0)
        val moved = p.driftCorrected(t0.plusSeconds(60), wind)
        assertEquals(0.6, Geo.distanceKm(start, moved), 0.02)
        assertEquals(90.0, Geo.bearingDeg(start, moved), 1.0)
        assertEquals(start, p.driftCorrected(t0.plusSeconds(60), null))
    }

    /** Vol simulé du mode démo rejoué : le cœur est estimé dans chaque pompe et la jauge de décision se remplit. */
    @Test fun estimatesCoresAlongTheDemoFlight() {
        val field = LatLon(43.80028, 3.78167)
        val fixes = com.neutronstar.glidercopilot.domain.flight.DemoFlight.generate(field, start = t0)
        val replay = com.neutronstar.glidercopilot.domain.flight.SensorReplay(fixes)
        val core = com.neutronstar.glidercopilot.domain.flight.FlightEngineCore(igc = { null })
        val wind = Wind(300.0, 15.0, WindSource.CIRCLING, t0)
        var estimated = 0
        var confident = 0
        var maxOffset = 0.0
        val gauges = HashSet<Decision.Gauge>()
        for (s in replay.samples()) when (s) {
            is com.neutronstar.glidercopilot.domain.flight.SensorSample.Baro -> core.onBaro(s.hPa, s.timeNs)
            is com.neutronstar.glidercopilot.domain.flight.SensorSample.Accel -> core.onAcceleration(s.upMs2, s.timeNs)
            is com.neutronstar.glidercopilot.domain.flight.SensorSample.Gps -> {
                core.onGps(s.fix, s.timeNs)
                val snap = core.snapshot(s.timeNs, s.fix.time)
                if (snap.circling) {
                val rate = CoreEstimator.turnRate(snap.trace, s.fix.time)
                val e = CoreEstimator.estimate(snap.trace, snap.circlingSince, s.fix.time, wind, s.fix.trackDeg, rate)
                if (e != null && !e.analysing) {
                    estimated++
                    maxOffset = maxOf(maxOffset, e.offsetM)
                    if (e.confidence > 0.4) confident++
                    gauges += Decision.gauge(snap.avgThermalMs, snap.avgDayMs, 60)
                }
                }
            }
        }
        assertTrue("estimations $estimated", estimated > 200)
        assertTrue("estimations sûres $confident sur $estimated", confident > estimated / 3)
        assertTrue("décalage maximal $maxOffset m", maxOffset < 400)
        assertTrue("jauges $gauges", gauges.contains(Decision.Gauge.GOOD) || gauges.contains(Decision.Gauge.MEDIUM))
    }

    @Test fun decisionGaugeAndTransitionBand() {
        assertEquals(Decision.Gauge.UNKNOWN, Decision.gauge(2.0, 1.5, 20))
        assertEquals(Decision.Gauge.GOOD, Decision.gauge(2.0, 1.5, 60))
        assertEquals(Decision.Gauge.MEDIUM, Decision.gauge(1.6, 1.5, 60))
        assertEquals(Decision.Gauge.WEAK, Decision.gauge(1.0, 1.5, 60))
        assertEquals(Decision.Gauge.UNKNOWN, Decision.gauge(null, 1.5, 60))
        assertEquals("Bonne · reste", Decision.gaugeLabel(Decision.Gauge.GOOD))
        assertEquals(110..125, Decision.transitionBandKmh(1.2))
        assertEquals(90..105, Decision.transitionBandKmh(0.0))
        assertNull(Decision.transitionBandKmh(null))
    }

    @Test fun fadingThermalAndCeiling() {
        assertNull(Decision.fading(0.4, 1.8, 1200.0, 1900.0, 30))
        assertEquals("S'essouffle — envisage le départ", Decision.fading(0.4, 1.8, 1200.0, 1900.0, 120))
        assertEquals("Proche du plafond prévu — envisage le départ", Decision.fading(1.8, 1.8, 1850.0, 1900.0, 120))
        assertNull(Decision.fading(1.7, 1.8, 1200.0, 1900.0, 120))
    }

    @Test fun networkThermalPicksTheBestNearby() {
        val near = Geo.destination(start, 90.0, 4.0)
        val far = Geo.destination(start, 180.0, 25.0)
        val weak = Geo.destination(start, 0.0, 2.0)
        val cue = NetworkThermals.best(
            start,
            listOf(Triple(near, 2.4, 5L), Triple(far, 4.0, 2L), Triple(weak, 0.4, 1L)),
        )!!
        assertEquals(2.4, cue.climbMs, 1e-9)
        assertEquals(90.0, cue.bearingDeg, 1.0)
        assertEquals(4.0, cue.distanceKm, 0.1)
        assertTrue(cue.label().startsWith("Réseau +2,4"))
        assertNull(NetworkThermals.best(start, listOf(Triple(near, 2.4, 90L))))
    }
}
