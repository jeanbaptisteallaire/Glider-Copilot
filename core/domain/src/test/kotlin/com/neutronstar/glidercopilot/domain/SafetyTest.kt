package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidercopilot.domain.flight.GpsFix
import com.neutronstar.glidercopilot.domain.safety.FieldOption
import com.neutronstar.glidercopilot.domain.safety.FieldSelector
import com.neutronstar.glidercopilot.domain.safety.GlideComputer
import com.neutronstar.glidercopilot.domain.safety.SafetyAlert
import com.neutronstar.glidercopilot.domain.safety.SafetyConfig
import com.neutronstar.glidercopilot.domain.safety.SafetyEngine
import com.neutronstar.glidercopilot.domain.safety.Wind
import com.neutronstar.glidercopilot.domain.safety.WindEstimator
import com.neutronstar.glidercopilot.domain.safety.WindSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SafetyTest {
    private val t0 = Instant.parse("2026-07-14T12:00:00Z")
    private val lfnl = FieldOption("LFNL", "Saint-Martin-de-Londres", LatLon(43.80028, 3.78167), 183.0, isClub = true)
    private val flat = Terrain { 150.0 }
    private val cfg = SafetyConfig(finesse = 20.0)

    private fun wind(from: Double, kmh: Double) = Wind(from, kmh, WindSource.FORECAST, t0)

    @Test fun windVectorConvention() {
        val w = wind(270.0, 20.0)          // vent d'ouest : souffle vers l'est
        assertEquals(20.0, w.east, 1e-9); assertEquals(0.0, w.north, 1e-9)
        val back = Wind.fromVector(w.east, w.north, WindSource.FORECAST, t0)
        assertEquals(270.0, back.fromDeg, 1e-6); assertEquals(20.0, back.speedKmh, 1e-9)
    }

    @Test fun groundSpeedWithWind() {
        assertEquals(90.0, GlideComputer.groundSpeed(0.0, 90.0, null)!!, 1e-9)
        assertEquals(70.0, GlideComputer.groundSpeed(0.0, 90.0, wind(0.0, 20.0))!!, 1e-9)       // vent de face
        assertEquals(110.0, GlideComputer.groundSpeed(180.0, 90.0, wind(0.0, 20.0))!!, 1e-9)    // vent arrière
        assertEquals(Math.sqrt(90.0 * 90 - 400), GlideComputer.groundSpeed(90.0, 90.0, wind(0.0, 20.0))!!, 1e-9)
        assertNull(GlideComputer.groundSpeed(0.0, 90.0, wind(0.0, 85.0)))
    }

    @Test fun windFromCircling() {
        val est = WindEstimator()
        val w = wind(300.0, 22.0)
        var pos = LatLon(43.7, 3.8)
        for (s in 0 until 180) {
            val heading = (s * 360.0 / 26) % 360                 // tour de 26 s en masse d'air
            val he = 85 * sin(Math.toRadians(heading)); val hn = 85 * cos(Math.toRadians(heading))
            val ge = he + w.east; val gn = hn + w.north
            val gs = Math.hypot(ge, gn); val trk = (Math.toDegrees(Math.atan2(ge, gn)) + 360) % 360
            pos = Geo.destination(pos, trk, gs / 3600)
            est.onFix(GpsFix(t0.plusSeconds(s.toLong()), pos, 1000.0, gs, trk, 5.0))
        }
        val got = est.wind(t0.plusSeconds(180))!!
        assertTrue("tours ${est.turns}", est.turns >= 4)
        assertEquals(22.0, got.speedKmh, 2.5)
        assertEquals(300.0, got.fromDeg, 6.0)
        assertNull(est.wind(t0.plusSeconds(3600)))
    }

    @Test fun glideToFieldFlatTerrain() {
        val from = Geo.destination(lfnl.position, 90.0, 10.0)
        val r = GlideComputer.toField(from, 1000.0, lfnl, cfg, null, flat)
        assertEquals(10.0, r.distanceKm, 0.01)
        assertEquals(270.0, r.bearingDeg, 0.2)
        assertEquals(183.0 + 300 + 500, r.requiredM, 1.0)
        assertEquals(17.0, r.marginM, 1.0)
        assertNull(r.relief)
        assertTrue(r.terrainKnown)
        // vent de face de 30 km/h : finesse sol 20 × 60/90
        val head = GlideComputer.toField(from, 1000.0, lfnl, cfg, wind(270.0, 30.0), flat)
        assertEquals(20.0 * 60 / 90, head.effectiveFinesse, 1e-3)
        assertEquals(183.0 + 300 + 750, head.requiredM, 1.0)
        assertFalse(head.reachable)
    }

    @Test fun ridgeOnTheRouteRaisesRequirement() {
        val from = Geo.destination(lfnl.position, 90.0, 10.0)
        val ridgeAt = Geo.destination(lfnl.position, 90.0, 5.0)
        val ridge = Terrain { p -> if (abs(p.lon - ridgeAt.lon) < 0.004) 800.0 else 150.0 }
        val r = GlideComputer.toField(from, 1000.0, lfnl, cfg, null, ridge)
        assertEquals(800.0 + 100 + 250, r.requiredM, 20.0)
        assertTrue(r.marginM < -100)
        assertNotNull(r.relief)
        assertEquals(5.0, r.relief!!.sKm, 0.4)
        val high = GlideComputer.toField(from, 1300.0, lfnl, cfg, null, ridge)
        assertNull(high.relief)
        assertTrue(high.reachable)
    }

    @Test fun projectionStraightAndCircling() {
        val fix = GpsFix(t0, lfnl.position, 1000.0, 90.0, 90.0, 5.0)
        val (p, a) = GlideComputer.project(fix, 1000.0, -1.0, false, null)
        assertEquals(3.0, Geo.distanceKm(lfnl.position, p), 0.01)
        assertEquals(880.0, a, 1e-9)
        val (pc, ac) = GlideComputer.project(fix, 1000.0, 2.0, true, wind(270.0, 18.0))
        assertEquals(0.6, Geo.distanceKm(lfnl.position, pc), 0.01)
        assertEquals(90.0, Geo.bearingDeg(lfnl.position, pc), 1.0)
        assertEquals(1240.0, ac, 1e-9)
    }

    @Test fun fieldSelectionWithHysteresis() {
        val other = FieldOption("LFMT", "Montpellier", LatLon(43.576, 3.963), 5.0)
        val sel = FieldSelector()
        fun at(p: LatLon, alt: Double) = listOf(lfnl, other).map { GlideComputer.toField(p, alt, it, cfg, null, Terrain.NONE) }
        // près de LFNL : le club
        val c1 = sel.choose(at(Geo.destination(lfnl.position, 150.0, 5.0), 900.0))!!
        assertEquals("LFNL", c1.field.id); assertFalse(c1.changed)
        // loin au sud-est, bas : le club n'est plus rejoignable, Montpellier oui
        val south = Geo.destination(other.position, 0.0, 6.0)
        val c2 = sel.choose(at(south, 700.0))!!
        assertEquals("LFMT", c2.field.id); assertTrue(c2.changed)
        // le club redevient juste rejoignable (moins de 80 m) : on garde Montpellier
        val dClub = Geo.distanceKm(south, lfnl.position)
        val c3 = sel.choose(at(south, 183 + 300 + dClub * 50 + 30))!!
        assertEquals("LFMT", c3.field.id); assertFalse(c3.changed)
        val c4 = sel.choose(at(south, 183 + 300 + dClub * 50 + 120))!!
        assertEquals("LFNL", c4.field.id); assertTrue(c4.changed)
        sel.manual = other
        assertEquals("LFMT", sel.choose(at(south, 2000.0))!!.field.id)
    }

    /**
     * Scénario rejoué : planeur à 1 200 m, à 8 km à l'est de LFNL, s'éloigne vers l'est à 90 km/h en perdant 1 m/s,
     * puis fait demi-tour et spirale à +2 m/s. Chaque alerte doit tomber au bon moment.
     */
    @Test fun replayedScenarioAlertsAtTheRightTime() {
        val heard = ArrayList<Pair<Long, SafetyAlert>>()
        var now = t0
        val engine = SafetyEngine(terrain = { flat }, fields = { listOf(lfnl) }, alert = { heard += Duration(now, t0) to it })
        var pos = Geo.destination(lfnl.position, 90.0, 8.0)
        var alt = 1200.0
        var s = 0L
        fun step(track: Double, kmh: Double, climb: Double, circling: Boolean) {
            pos = if (kmh > 0) Geo.destination(pos, track, kmh / 3600) else pos
            alt += climb; s++; now = t0.plusSeconds(s)
            val fix = GpsFix(now, pos, alt, if (circling) 70.0 else kmh, track, 5.0)
            engine.onFix(fix)
            engine.update(fix, alt, climb, circling, cfg, now, armed = true)
        }
        // marge initiale : 1200 − (183 + 300 + 8 km × 50) = 317 m ; elle baisse de 1 + 25/20·... ≈ 2,25 m/s
        while (s < 400) step(90.0, 90.0, -1.0, false)
        // demi-tour et spirale sur place
        while (s < 1000) step(270.0, 0.0, 2.0, true)
        val kinds = heard.map { it.second.kind }
        val first = { k: SafetyAlert.Kind -> heard.first { it.second.kind == k }.first }
        // marge(t) = 317 − 2,25 t : projection < 0 à t ≈ 141 − 120 = 21 s ; faible (< 150) à t ≈ 74 s ; sous la sécurité à t ≈ 141 s
        assertEquals(SafetyAlert.Kind.PROJECTED_BELOW, kinds.first())
        assertEquals(21.0, first(SafetyAlert.Kind.PROJECTED_BELOW).toDouble(), 3.0)
        assertEquals(74.0, first(SafetyAlert.Kind.MARGIN_LOW).toDouble(), 3.0)
        assertEquals(141.0, first(SafetyAlert.Kind.MARGIN_BELOW).toDouble(), 3.0)
        // répétée toutes les 30 s tant que sous la sécurité (jusqu'à la remontée)
        val belows = heard.filter { it.second.kind == SafetyAlert.Kind.MARGIN_BELOW }.map { it.first }
        assertTrue("répétitions $belows", belows.size >= 10 && belows.zipWithNext().all { (a, b) -> b - a in 29..31 })
        assertTrue(heard.first { it.second.kind == SafetyAlert.Kind.MARGIN_BELOW }.second.text.startsWith("Sous la sécurité. Cap Saint-Martin"))
        // en spirale à +2 m/s la marge (−583 m à 400 s) remonte : « rétablie » au-dessus de 200 m (≈ 792 s), jamais de « faible » en remontant
        val rec = first(SafetyAlert.Kind.MARGIN_RECOVERED)
        assertTrue("rétablie à $rec s", rec in 785L..800L)
        assertTrue("dernière $belows", belows.last() in 690L..720L)
        assertEquals(1, heard.count { it.second.kind == SafetyAlert.Kind.MARGIN_LOW })
        assertEquals(1, heard.count { it.second.kind == SafetyAlert.Kind.MARGIN_RECOVERED })
        assertNotNull(engine.state)
    }

    @Suppress("FunctionName")
    private fun Duration(now: Instant, start: Instant) = java.time.Duration.between(start, now).seconds
}

class DemoFlightTest {
    @Test fun demoLoopIsClosedAndNearTheField() {
        val field = LatLon(43.80028, 3.78167)
        val f = com.neutronstar.glidercopilot.domain.flight.DemoFlight.generate(field)
        assertTrue("durée ${f.size} s", f.size in 600..2400)
        val alts = f.map { it.gnssAltM!! }
        assertTrue("altitudes ${alts.min()}–${alts.max()}", alts.min() > 1350 && alts.max() < 1700)
        assertTrue("éloignement max ${f.maxOf { Geo.distanceKm(field, it.position) }}", f.all { Geo.distanceKm(field, it.position) < 10.0 })
        assertTrue("fermeture ${Geo.distanceKm(f.first().position, f.last().position)}", Geo.distanceKm(f.first().position, f.last().position) < 0.2)
        assertEquals(f.first().gnssAltM!!.toDouble(), f.last().gnssAltM!!.toDouble(), 3.0)
        // le banc de rejeu en tire des vitesses sol de planeur et des spirales visibles par le moteur de vol
        val replay = com.neutronstar.glidercopilot.domain.flight.SensorReplay(f)
        val gps = replay.samples().filterIsInstance<com.neutronstar.glidercopilot.domain.flight.SensorSample.Gps>().toList()
        val slow = gps.drop(5).count { (it.fix.groundSpeedKmh ?: 0.0) < 50 }
        assertTrue("sous 50 km/h : $slow", slow <= 8)   // sorties de spirale : demi-tour vers la pompe suivante
        val core = com.neutronstar.glidercopilot.domain.flight.FlightEngineCore(igc = { null })
        var circling = 0
        for (s in replay.samples()) when (s) {
            is com.neutronstar.glidercopilot.domain.flight.SensorSample.Baro -> core.onBaro(s.hPa, s.timeNs)
            is com.neutronstar.glidercopilot.domain.flight.SensorSample.Accel -> core.onAcceleration(s.upMs2, s.timeNs)
            is com.neutronstar.glidercopilot.domain.flight.SensorSample.Gps -> { core.onGps(s.fix, s.timeNs); if (core.snapshot(s.timeNs, s.fix.time).circling) circling++ }
        }
        assertTrue("spirales $circling s", circling > f.size / 5)
    }
}
