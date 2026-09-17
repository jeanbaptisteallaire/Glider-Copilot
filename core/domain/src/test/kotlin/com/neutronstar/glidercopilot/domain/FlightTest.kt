package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidercopilot.domain.flight.AltitudeRef
import com.neutronstar.glidercopilot.domain.flight.AnnouncementPolicy
import com.neutronstar.glidercopilot.domain.flight.FlightEngineCore
import com.neutronstar.glidercopilot.domain.flight.FlightEvent
import com.neutronstar.glidercopilot.domain.flight.FlightPhase
import com.neutronstar.glidercopilot.domain.flight.GpsFix
import com.neutronstar.glidercopilot.domain.flight.Igc
import com.neutronstar.glidercopilot.domain.flight.IgcFix
import com.neutronstar.glidercopilot.domain.flight.IgcRecorder
import com.neutronstar.glidercopilot.domain.flight.Isa
import com.neutronstar.glidercopilot.domain.flight.SensorReplay
import com.neutronstar.glidercopilot.domain.flight.SensorSample
import com.neutronstar.glidercopilot.domain.flight.SensorStats
import com.neutronstar.glidercopilot.domain.flight.TakeoffDetector
import com.neutronstar.glidercopilot.domain.flight.VarioFilter
import com.neutronstar.glidercopilot.domain.flight.VarioSource
import com.neutronstar.glidercopilot.domain.flight.VerticalAcceleration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

class FlightTest {
    private val t0 = Instant.parse("2026-07-14T12:00:00Z")
    private val field = LatLon(43.4855, 4.3775)

    // --- atmosphère et accélération -------------------------------------------------------------------

    @Test fun isaRoundTrip() {
        assertEquals(0.0, Isa.pressureAltitude(1013.25), 1e-6)
        assertEquals(1000.0, Isa.pressureAltitude(Isa.pressureAt(1000.0)), 1e-6)
        assertEquals(898.75, Isa.pressureAt(1000.0), 0.1)   // table OACI : 898,76 hPa à 1 000 m
    }

    @Test fun verticalAccelerationSign() {
        // téléphone à plat : gravité sur +Z, l'accéléromètre lit g + a
        assertEquals(0.0, VerticalAcceleration.compute(0.0, 0.0, 9.80665, 0.0, 0.0, 9.80665)!!, 1e-9)
        assertEquals(2.0, VerticalAcceleration.compute(0.0, 0.0, 11.80665, 0.0, 0.0, 9.80665)!!, 1e-9)
        // téléphone incliné à 45°
        val g = 9.80665 / Math.sqrt(2.0)
        assertEquals(1.0, VerticalAcceleration.compute(g + 0.7071068, 0.0, g + 0.7071068, g, 0.0, g)!!, 1e-5)
        assertNull(VerticalAcceleration.compute(0.0, 0.0, 0.0, 0.0, 0.0, 0.1))
    }

    // --- filtre de Kalman -------------------------------------------------------------------------------

    /** Simule une montée [climb](t) avec baro 25 Hz et accéléromètre 50 Hz bruités ; renvoie (t, vario estimé). */
    private fun simulate(seconds: Double, withAccel: Boolean, climb: (Double) -> Double, seed: Int = 1): List<Pair<Double, Double>> {
        val rnd = Random(seed)
        fun g() = rnd.nextDouble().let { u -> Math.sqrt(-2 * Math.log(1 - u)) * Math.cos(2 * Math.PI * rnd.nextDouble()) }
        val f = VarioFilter()
        val out = ArrayList<Pair<Double, Double>>()
        var h = 500.0
        val dt = 0.001
        var t = 0.0
        var step = 0
        while (t < seconds) {
            val v0 = climb(t); val v1 = climb(t + dt)
            h += (v0 + v1) / 2 * dt
            val a = (v1 - v0) / dt
            t += dt; step++
            val ns = (t * 1e9).toLong()
            if (withAccel && step % 20 == 0) f.onAcceleration(a + 0.2 + g() * 0.5, ns)
            if (step % 40 == 0) {
                f.onBaroAltitude(h + g() * 0.35, ns)
                out += t to f.climb
            }
        }
        return out
    }

    @Test fun kalmanConvergesOnSteadyClimb() {
        val r = simulate(20.0, withAccel = true, climb = { 2.0 })
        val late = r.filter { it.first > 5 }.map { it.second }
        assertTrue("vario ${late.average()}", abs(late.average() - 2.0) < 0.1)
        assertTrue("écart max ${late.maxOf { abs(it - 2.0) }}", late.all { abs(it - 2.0) < 0.35 })
    }

    @Test fun accelerometerReactsFasterThanBaroOnly() {
        // entrée en thermique : 0 → 3 m/s en 1 s (3 m/s², une bonne rafale) ; la vraie valeur passe 1,5 m/s à t = 10,5 s
        val ramp = { t: Double -> (3.0 * (t - 10)).coerceIn(0.0, 3.0) }
        fun reach(r: List<Pair<Double, Double>>) = r.first { it.first > 10 && it.second > 1.5 }.first - 10.5
        val fused = (1..5).map { reach(simulate(20.0, true, ramp, seed = it)) }.average()
        val baro = (1..5).map { reach(simulate(20.0, false, ramp, seed = it)) }.average()
        assertTrue("fusion ${fused}s, baro ${baro}s", fused < baro)
        assertTrue("retard fusion ${fused}s", fused < 0.35)
        assertTrue("retard baro seul ${baro}s", baro < 1.2)
    }

    @Test fun stationaryNoiseIsLow() {
        for (accel in listOf(true, false)) {
            val r = simulate(40.0, accel, climb = { 0.0 }, seed = 3).filter { it.first > 10 }.map { it.second }
            val rms = Math.sqrt(r.sumOf { it * it } / r.size)
            assertTrue("bruit au repos accel=$accel : $rms", rms < if (accel) 0.12 else 0.25)
        }
    }

    @Test fun biasIsLearned() {
        val f = VarioFilter()
        var ns = 0L
        repeat(60 * 50) { i ->
            ns += 20_000_000L
            f.onAcceleration(0.3, ns)
            if (i % 2 == 1) f.onBaroAltitude(200.0, ns)
        }
        assertEquals(0.3, f.bias, 0.05)
        assertEquals(0.0, f.climb, 0.05)
        assertTrue(f.usesAccelerometer(ns))
        assertFalse(f.usesAccelerometer(ns + 1_000_000_000L))
    }

    @Test fun sensorStatsMeasuresRateAndNoise() {
        val s = SensorStats(10.0)
        val rnd = Random(5)
        for (i in 0..250) s.add(100 + i * 0.04 * 2 + (rnd.nextDouble() - 0.5), i * 40_000_000L)  // 25 Hz, montée 2 m/s
        assertEquals(25.0, s.rateHz, 0.5)
        assertEquals(1 / Math.sqrt(12.0), s.noise, 0.05)   // bruit uniforme ±0,5 m, tendance retirée
    }

    // --- décollage, atterrissage ------------------------------------------------------------------------

    private fun fix(sec: Long, kmh: Double) = GpsFix(t0.plusSeconds(sec), field, 200.0, kmh, 0.0, 5.0)

    @Test fun takeoffAndLandingDetected() {
        val d = TakeoffDetector()
        val events = ArrayList<FlightEvent>()
        var s = 0L
        repeat(60) { d.onFix(fix(s++, 0.0))?.let(events::add) }
        // à-coup au roulage (2 s au-dessus de 50 km/h) : pas de décollage
        repeat(2) { d.onFix(fix(s++, 55.0))?.let(events::add) }
        repeat(5) { d.onFix(fix(s++, 20.0))?.let(events::add) }
        assertEquals(FlightPhase.GROUND, d.phase)
        val takeoffAt = s
        repeat(600) { d.onFix(fix(s++, 95.0))?.let(events::add) }
        // arrêt de 20 s (thermique serré à faible vitesse sol : impossible, mais on vérifie la tenue)
        repeat(20) { d.onFix(fix(s++, 5.0))?.let(events::add) }
        repeat(600) { d.onFix(fix(s++, 90.0))?.let(events::add) }
        val landAt = s
        repeat(40) { d.onFix(fix(s++, 2.0))?.let(events::add) }
        assertEquals(2, events.size)
        assertEquals(FlightEvent.Takeoff(t0.plusSeconds(takeoffAt)), events[0])
        val landing = events[1] as FlightEvent.Landing
        assertEquals(t0.plusSeconds(landAt), landing.time)
        assertEquals(Duration.ofSeconds(landAt - takeoffAt), landing.duration)
        assertEquals(FlightPhase.LANDED, d.phase)
        assertEquals(Duration.ofSeconds(landAt - takeoffAt), d.flightDuration(t0.plusSeconds(99999)))
    }

    // --- IGC ------------------------------------------------------------------------------------------

    @Test fun igcRecordFormat() {
        val b = Igc.bRecord(IgcFix(Instant.parse("2026-07-14T13:05:09Z"), LatLon(43 + 29.130 / 60, 4 + 22.650 / 60), true, 1234, 1301))
        assertEquals("B1305094329130N00422650EA0123401301", b)
        assertEquals(35, b.length)
        val s = Igc.bRecord(IgcFix(Instant.parse("2026-07-14T00:00:00Z"), LatLon(-33.5, -70.25), false, -12, null))
        assertEquals("B0000003330000S07015000WV-001200000", s)
        assertEquals("2026-07-14-XXX-GLY-03.igc", Igc.fileName(LocalDate.of(2026, 7, 14), 3))
    }

    @Test fun igcWriteParseRoundTrip() {
        val lines = ArrayList<String>()
        val rec = IgcRecorder(Igc.Header(LocalDate.of(2026, 7, 14), "JB", "ASK 21", "F-CPHI", "0.5.0"), lines::add)
        val fixes = (0 until 120).map { i ->
            IgcFix(t0.plusMillis(i * 500L), Geo.destination(field, 45.0, i * 0.01), true, 200 + i, 250 + i)
        }
        fixes.forEach(rec::record)
        assertEquals(60, rec.fixes)   // 2 Hz en entrée, 1 Hz écrit
        assertTrue(lines.first().startsWith("AXXX"))
        assertTrue(lines.any { it == "HFDTEDATE:140726,01" })
        assertTrue(lines.all { l -> l.all { it.code in 32..126 } })
        val parsed = Igc.parse(lines.joinToString("\r\n"))
        assertEquals(60, parsed.size)
        parsed.forEachIndexed { i, p ->
            val src = fixes[i * 2]
            assertEquals(src.time, p.time)
            assertTrue(Geo.distanceKm(src.position, p.position) < 0.002)
            assertEquals(src.pressureAltM, p.pressureAltM)
        }
    }

    // --- annonces -------------------------------------------------------------------------------------

    @Test fun announcementsHaveHysteresisAndCooldown() {
        val a = AnnouncementPolicy()
        assertEquals("Décollage détecté, chrono lancé", a.onEvent(FlightEvent.Takeoff(t0), t0))
        assertEquals("Atterrissage. Vol de 1 heure 5 minutes enregistré", a.onEvent(FlightEvent.Landing(t0, Duration.ofMinutes(65)), t0))
        assertNull(a.onMargin(400.0, t0))
        assertEquals("Marge faible", a.onMargin(140.0, t0.plusSeconds(1)))
        // oscillation autour du seuil : silence
        assertNull(a.onMargin(160.0, t0.plusSeconds(2)))
        assertNull(a.onMargin(145.0, t0.plusSeconds(3)))
        assertNull(a.onMargin(190.0, t0.plusSeconds(4)))
        assertEquals("Sous la sécurité. Cap terrain", a.onMargin(-10.0, t0.plusSeconds(5)))
        // rétablie mais trop tôt après la dernière annonce de marge
        assertNull(a.onMargin(260.0, t0.plusSeconds(20)))
        assertEquals("Marge faible", a.onMargin(100.0, t0.plusSeconds(80)))

        assertNull(a.onSource(VarioSource.BARO_ACCEL, t0))
        assertEquals("Baromètre indisponible, vario OGN en secours", a.onSource(VarioSource.OGN, t0.plusSeconds(1)))
        assertNull(a.onSource(VarioSource.OGN, t0.plusSeconds(2)))
        assertNull(a.onSource(VarioSource.BARO, t0.plusSeconds(10)))   // délai
        assertNull(a.onSource(VarioSource.BARO_ACCEL, t0.plusSeconds(100)))  // baro → baro+accel : rien à dire
    }

    // --- banc de rejeu --------------------------------------------------------------------------------

    private fun syntheticFlight(): List<IgcFix> {
        // 60 s sol, 5 min montée à 2 m/s, 5 min descente à -1 m/s
        val out = ArrayList<IgcFix>()
        var h = 183.0
        var pos = field
        for (s in 0 until 660) {
            val climb = when { s < 60 -> 0.0; s < 360 -> 2.0; else -> -1.0 }
            h += climb
            if (s >= 60) pos = Geo.destination(pos, 90.0, 25.0 / 1000)
            out += IgcFix(t0.plusSeconds(s.toLong()), pos, true, h.toInt(), h.toInt() + 40)
        }
        return out
    }

    @Test fun replayProducesSensorStreams() {
        val r = SensorReplay(syntheticFlight())
        val samples = r.samples().toList()
        val baro = samples.filterIsInstance<SensorSample.Baro>()
        val acc = samples.filterIsInstance<SensorSample.Accel>()
        val gps = samples.filterIsInstance<SensorSample.Gps>()
        assertEquals(659.0, r.durationS, 1e-9)
        assertEquals(659 * 25 + 1, baro.size)
        assertEquals(659 * 50 + 1, acc.size)
        assertEquals(660, gps.size)
        assertTrue(samples.zipWithNext().all { (a, b) -> a.timeNs <= b.timeNs })
        assertEquals(90.0, gps[200].fix.groundSpeedKmh!!, 1.0)
        assertEquals(90.0, gps[200].fix.trackDeg!!, 1.0)
    }

    @Test fun filterOnReplayTracksClimbAndDetectsFlight() {
        for ((baro, accel) in listOf(true to true, true to false)) {
            val r = SensorReplay(syntheticFlight(), withBaro = baro, withAccel = accel)
            val f = VarioFilter()
            val d = TakeoffDetector()
            var err = 0.0; var n = 0
            var takeoff: FlightEvent? = null
            for (s in r.samples()) when (s) {
                is SensorSample.Baro -> {
                    f.onBaroAltitude(Isa.pressureAltitude(s.hPa), s.timeNs)
                    val t = s.timeNs / 1e9
                    if (t in 100.0..340.0 || t in 400.0..650.0) { err += abs(f.climb - r.climbAt(t)); n++ }
                }
                is SensorSample.Accel -> f.onAcceleration(s.upMs2, s.timeNs)
                is SensorSample.Gps -> d.onFix(s.fix)?.let { takeoff = it }
            }
            assertTrue("erreur moyenne baro=$baro accel=$accel : ${err / n}", err / n < if (accel) 0.12 else 0.2)
            assertNotNull(takeoff)
            assertEquals(FlightPhase.FLYING, d.phase)
        }
    }
}

class FlightEngineTest {
    private val field = LatLon(43.80028, 3.78167)

    private fun demoIgc(): String {
        val candidates = listOf("../../app/src/main/assets/flight/demo.igc", "app/src/main/assets/flight/demo.igc")
        return candidates.map { java.io.File(it) }.first { it.exists() }.readText()
    }

    @Test fun demoFlightEndToEnd() {
        val fixes = Igc.parse(demoIgc())
        assertTrue(fixes.size > 1500)
        val replay = SensorReplay(fixes)
        val start = Instant.parse("2026-07-14T11:00:00Z")
        val igcLines = ArrayList<String>()
        val spoken = ArrayList<String>()
        val engine = FlightEngineCore(
            igc = { igcLines::add },
            announce = { spoken += it },
            fieldElevationM = { 183.0 },
            field = { field },
        )
        var maxAltErr = 0.0
        var circlingSeen = 0
        var bestThermal = 0.0
        var sourceSeen = VarioSource.NONE
        var flyingAt = -1.0
        for (s in replay.samples()) {
            val t = s.timeNs / 1e9
            val now = start.plusNanos(s.timeNs)
            when (s) {
                is SensorSample.Baro -> engine.onBaro(s.hPa, s.timeNs)
                is SensorSample.Accel -> engine.onAcceleration(s.upMs2, s.timeNs)
                is SensorSample.Gps -> {
                    engine.onGps(s.fix.copy(time = now), s.timeNs)
                    val snap = engine.snapshot(s.timeNs, now)
                    if (snap.phase == FlightPhase.FLYING && flyingAt < 0) flyingAt = t
                    if (t > 120 && snap.phase == FlightPhase.FLYING) {
                        // vérité : altitude GNSS de la trace (le calage terrain retire l'écart ISA)
                        val truth = replay.altitudeAt(t) - 35.0
                        maxAltErr = maxOf(maxAltErr, abs(snap.altitudeM!! - truth))
                        assertEquals(AltitudeRef.BARO_FIELD, snap.altitudeRef)
                    }
                    if (snap.circling) circlingSeen++
                    bestThermal = maxOf(bestThermal, snap.avgThermalMs ?: 0.0)
                    sourceSeen = snap.source
                }
            }
        }
        val end = engine.snapshot((replay.durationS * 1e9).toLong(), start.plusSeconds(replay.durationS.toLong()))
        assertEquals(VarioSource.BARO_ACCEL, sourceSeen)
        assertTrue("décollage à $flyingAt s", flyingAt in 60.0..75.0)
        assertEquals(FlightPhase.LANDED, end.phase)
        assertTrue("écart altitude $maxAltErr m", maxAltErr < 6.0)
        assertTrue("spirale vue $circlingSeen s", circlingSeen > 150)
        assertTrue("meilleure pompe $bestThermal", bestThermal > 0.8)
        assertNotNull(end.avgDayMs)
        assertTrue("vol ${end.flightSeconds} s", end.flightSeconds in 1850L..1950L)
        assertEquals("Décollage détecté, chrono lancé", spoken.first())
        assertTrue(spoken.last().startsWith("Atterrissage. Vol de 3"))
        val parsed = Igc.parse(igcLines.joinToString("\n"))
        assertTrue("IGC ${parsed.size}", parsed.size in 1850..1950)
        assertTrue(parsed.zipWithNext().all { (a, b) -> b.time.isAfter(a.time) })
        assertTrue(igcLines.filter { it.startsWith("B") }.all { it.length == 35 })
    }

    /** Vol de démonstration rejoué avec la sécurité : silence au remorqué, dans le circuit et au sol. */
    @Test fun demoFlightSafetyAlertsAreQuietAtTakeoffAndLanding() {
        val fixes = Igc.parse(demoIgc())
        val replay = SensorReplay(fixes)
        val start = Instant.parse("2026-07-14T11:00:00Z")
        val lfnl = com.neutronstar.glidercopilot.domain.safety.FieldOption("LFNL", "Saint-Martin-de-Londres", field, 183.0, isClub = true, icao = "LFNL")
        val core = FlightEngineCore(igc = { null }, fieldElevationM = { 183.0 }, field = { field })
        val heard = ArrayList<Pair<Long, com.neutronstar.glidercopilot.domain.safety.SafetyAlert>>()
        var t = 0L
        val safety = com.neutronstar.glidercopilot.domain.safety.SafetyEngine(terrain = { Terrain { 150.0 } }, fields = { listOf(lfnl) }, alert = { heard += t to it })
        for (s in replay.samples()) when (s) {
            is SensorSample.Baro -> core.onBaro(s.hPa, s.timeNs)
            is SensorSample.Accel -> core.onAcceleration(s.upMs2, s.timeNs)
            is SensorSample.Gps -> {
                val fix = s.fix.copy(time = start.plusNanos(s.timeNs))
                core.onGps(fix, s.timeNs); safety.onFix(fix)
                t = s.timeNs / 1_000_000_000
                val snap = core.snapshot(s.timeNs, fix.time)
                snap.altitudeM?.let { alt -> safety.update(fix, alt, snap.avgSpiralMs ?: 0.0, snap.circling, com.neutronstar.glidercopilot.domain.safety.SafetyConfig(20.0), fix.time, snap.phase == FlightPhase.FLYING) }
            }
        }
        val margin = heard.filter { it.second.kind == com.neutronstar.glidercopilot.domain.safety.SafetyAlert.Kind.MARGIN_BELOW || it.second.kind == com.neutronstar.glidercopilot.domain.safety.SafetyAlert.Kind.MARGIN_LOW }
        assertTrue("alertes au remorqué : $heard", heard.none { it.first < 330 })
        assertTrue("alertes en finale : $heard", heard.none { it.first > 1700 })
        assertTrue("alertes de marge : $margin", margin.isEmpty())
        assertTrue("vent estimé", safety.state?.wind != null)
    }

    @Test fun ognFallbackWithoutBarometer() {
        val engine = FlightEngineCore(igc = { null })
        val t = Instant.parse("2026-07-14T11:00:00Z")
        assertEquals(VarioSource.NONE, engine.snapshot(1, t).source)
        engine.onOgn(1.8, 1450.0, 4)
        val s = engine.snapshot(2, t)
        assertEquals(VarioSource.OGN, s.source)
        assertEquals(1.8, s.climbMs!!, 1e-9)
        assertEquals(AltitudeRef.OGN, s.altitudeRef)
        engine.onBaro(1000.0, 3)
        assertEquals(VarioSource.BARO, engine.snapshot(4, t).source)
        assertEquals(VarioSource.OGN, engine.snapshot(3 + 2_000_000_000L, t).source)
        engine.onOgn(1.8, 1450.0, 90)
        assertEquals(VarioSource.NONE, engine.snapshot(3 + 2_000_000_000L, t).source)
    }

    @Test fun manualChronoWhenAutoDisabled() {
        val lines = ArrayList<String>()
        val engine = FlightEngineCore(igc = { lines::add }, autoTakeoff = { false })
        val t = Instant.parse("2026-07-14T11:00:00Z")
        for (i in 0 until 10) engine.onGps(GpsFix(t.plusSeconds(i.toLong()), field, 200.0, 90.0, 0.0, 5.0), i * 1_000_000_000L)
        assertFalse(engine.recording)
        assertEquals(0L, engine.snapshot(10_000_000_000L, t.plusSeconds(10)).flightSeconds)
        engine.startManual(t.plusSeconds(10))
        for (i in 10 until 70) engine.onGps(GpsFix(t.plusSeconds(i.toLong()), field, 200.0, 90.0, 0.0, 5.0), i * 1_000_000_000L)
        assertEquals(60L, engine.snapshot(70_000_000_000L, t.plusSeconds(70)).flightSeconds)
        engine.stopManual(t.plusSeconds(70))
        assertEquals(60L, engine.snapshot(90_000_000_000L, t.plusSeconds(90)).flightSeconds)
        assertEquals(60, lines.count { it.startsWith("B") })
    }
}
