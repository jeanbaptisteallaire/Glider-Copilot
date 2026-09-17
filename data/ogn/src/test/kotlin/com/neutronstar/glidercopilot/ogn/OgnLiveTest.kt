package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

class OgnLiveTest {
    private val recv = Instant.parse("2026-09-17T12:00:05Z")

    @Test fun parsesWikiExample() {
        val l = OgnParser.parse("FLRDDE626>APRS,qAS,EGHL:/074548h5111.32N/00102.04W'086/007/A=000607 id0ADDE626 -019fpm +0.0rot 5.5dB 3e -4.3kHz",
            Instant.parse("2026-09-17T07:45:50Z")) as OgnLine.Aircraft
        val f = l.fix
        assertEquals("DDE626", f.address)
        assertEquals(AddressType.FLARM, f.addressType)
        assertEquals(2, f.aircraftType)   // remorqueur
        assertEquals(Instant.parse("2026-09-17T07:45:48Z"), f.time)
        assertEquals(51.18867, f.position.lat, 1e-4)
        assertEquals(-1.034, f.position.lon, 1e-4)
        assertEquals(185.0, f.altitudeM!!, 0.1)
        assertEquals(12.96, f.groundSpeedKmh!!, 0.01)
        assertEquals(-0.0965, f.climbMs!!, 1e-3)
        assertEquals(0.0, f.turnDegS!!, 1e-9)
        assertEquals("EGHL", f.receiver)
        assertEquals(2.0, f.latency.seconds.toDouble(), 0.0)
        assertFalse(f.hidden)
    }

    @Test fun precisionStealthAndNoTracking() {
        val base = "FLRDD1234>OGFLR,qAS,LFNL:/120001h4348.02N\\00347.00E^270/045/A=004000 !W52! id%s +198fpm +2.1rot 10.2dB"
        val glider = (OgnParser.parse(base.format("06DD1234"), recv) as OgnLine.Aircraft).fix
        assertEquals(43 + 48.025 / 60, glider.position.lat, 1e-6)
        assertEquals(3 + 47.002 / 60, glider.position.lon, 1e-6)
        assertEquals(1, glider.aircraftType)
        assertEquals(6.3, glider.turnDegS!!, 1e-9)
        assertEquals(1.006, glider.climbMs!!, 1e-3)
        assertTrue((OgnParser.parse(base.format("86DD1234"), recv) as OgnLine.Aircraft).fix.stealth)
        assertTrue((OgnParser.parse(base.format("46DD1234"), recv) as OgnLine.Aircraft).fix.noTracking)
    }

    @Test fun serverAndReceiverLines() {
        assertTrue(OgnParser.parse("# aprsc 2.1.19-g730c5c0 17 Sep 2026 12:00:00 GMT GLIDERN2 1.2.3.4:14580", recv) is OgnLine.Server)
        assertTrue(OgnParser.parse("LFNL>OGNSDR,TCPIP*,qAC,GLIDERN2:/115959h4348.00NI00347.00E&/A=000600", recv) is OgnLine.Other)
        assertTrue(OgnParser.parse("n'importe quoi", recv) is OgnLine.Other)
    }

    @Test fun midnightRollover() {
        val t = OgnParser.utcTime(23, 59, 58, Instant.parse("2026-09-17T00:00:03Z"))
        assertEquals(Instant.parse("2026-09-16T23:59:58Z"), t)
    }

    // ------------------------------------------------------------------ spirales

    /** Planeur simulé : spirale de rayon 150 m, un tour en 26 s, montée [climb] m/s, une trame toutes les [step] s. */
    private fun spiral(addr: String, start: Instant, seconds: Int, climb: Double, step: Int = 4, center: LatLon = LatLon(43.9, 3.9), right: Boolean = true): List<OgnFix> =
        (0..seconds step step).map { s ->
            val a = (if (right) 1 else -1) * 2 * Math.PI * s / 26.0
            val pos = LatLon(center.lat + 0.15 * cos(a) / 111.32, center.lon + 0.15 * sin(a) / (111.32 * cos(Math.toRadians(center.lat))))
            val track = ((Math.toDegrees(a) + (if (right) 90 else -90)) % 360 + 360) % 360
            OgnFix(addr, AddressType.FLARM, 1, "FLR$addr", start.plusSeconds(s.toLong()), start.plusSeconds(s + 3L), pos, 1000.0 + climb * s,
                track, 80.0, climb, if (right) 13.8 else -13.8, stealth = false, noTracking = false, receiver = "R")
        }

    private fun straight(addr: String, start: Instant, seconds: Int): List<OgnFix> = (0..seconds step 4).map { s ->
        OgnFix(addr, AddressType.FLARM, 1, "FLR$addr", start.plusSeconds(s.toLong()), start.plusSeconds(s + 2L),
            LatLon(43.8 + s * 0.0002, 3.8), 1500.0 - s * 0.8, 0.0, 110.0, -0.8, 0.0, false, false, "R")
    }

    @Test fun detectsSpiralAndIgnoresStraightFlight() {
        val t0 = Instant.parse("2026-09-17T12:00:00Z")
        val ep = ThermalDetector.episodes(spiral("AAAAAA", t0, 120, 1.5))
        assertEquals(1, ep.size)
        assertEquals(1.5, ep[0].climbMs, 0.2)
        assertTrue(ep[0].turnDeg > 1200)
        assertEquals(43.9, ep[0].center.lat, 0.002)
        assertTrue(ThermalDetector.episodes(straight("BBBBBB", t0, 300)).isEmpty())
        // spirale gauche détectée aussi
        assertEquals(1, ThermalDetector.episodes(spiral("CCCCCC", t0, 90, 1.0, right = false)).size)
        // trop courte : un seul tour
        assertTrue(ThermalDetector.episodes(spiral("DDDDDD", t0, 24, 2.0)).isEmpty())
    }

    @Test fun mergesSameThermalAcrossGliders() {
        val t0 = Instant.parse("2026-09-17T12:00:00Z")
        val eps = ThermalDetector.episodes(spiral("AAAAAA", t0, 120, 1.8)) +
            ThermalDetector.episodes(spiral("BBBBBB", t0.plusSeconds(200), 90, 1.2, center = LatLon(43.903, 3.901))) +
            ThermalDetector.episodes(spiral("CCCCCC", t0, 90, 2.0, center = LatLon(44.2, 4.2))) +
            ThermalDetector.episodes(spiral("EEEEEE", t0, 90, -0.8, center = LatLon(44.0, 3.5)))   // descente : pas une pompe
        val th = ThermalDetector.thermals(eps)
        assertEquals(2, th.size)
        val shared = th.first { it.aircraftCount == 2 }
        assertTrue(shared.climbMs in 1.2..1.8)
    }

    @Test fun storeRespectsPrivacyAndDeduplicates() {
        val t0 = Instant.parse("2026-09-17T12:00:00Z")
        val ddb = mapOf(
            "AAAAAA" to DdbDevice("F", "AAAAAA", "LS-4", "F-CAAA", "AA", tracked = true, identified = true),
            "BBBBBB" to DdbDevice("F", "BBBBBB", "ASK-21", "F-CBBB", null, tracked = false, identified = true),
            "CCCCCC" to DdbDevice("F", "CCCCCC", "Duo", "F-CCCC", "CC", tracked = true, identified = false),
        )
        val store = TrafficStore({ ddb[it] })
        spiral("AAAAAA", t0, 120, 1.5).forEach { store.add(it) }
        spiral("AAAAAA", t0, 120, 1.5).forEach { store.add(it) }   // même trames via un autre récepteur
        spiral("BBBBBB", t0, 60, 1.0).forEach { store.add(it) }
        spiral("CCCCCC", t0, 60, 1.0, center = LatLon(44.2, 4.2)).forEach { store.add(it) }
        store.add(spiral("DDDDDD", t0, 8, 1.0).first().copy(stealth = true))
        val now = t0.plusSeconds(125)
        store.analyse(now)
        val ac = store.aircraft(now).associateBy { it.address }
        assertEquals(setOf("AAAAAA", "CCCCCC"), ac.keys)
        assertEquals("AA", ac["AAAAAA"]!!.label)
        assertNull(ac["CCCCCC"]!!.label)             // identification refusée : anonyme
        assertTrue(ac["AAAAAA"]!!.circling)
        assertEquals(31, store.track("AAAAAA").size)
        assertEquals(2, store.thermals(now).size)
        // mon planeur exclu du trafic et des pompes
        assertEquals(1, store.thermals(now, exclude = setOf("AAAAAA")).size)
        // purge
        store.analyse(t0.plus(Duration.ofHours(2)))
        assertEquals(0, store.size())
        assertTrue(store.thermals(t0.plus(Duration.ofHours(2))).isEmpty())
    }

    @Test fun ownGliderCadenceLatencyAndFilter() {
        val t0 = Instant.parse("2026-09-17T12:00:00Z")
        val st = OwnGlider.status(spiral("004839", t0, 60, 1.0))!!
        assertEquals(4.0, st.medianIntervalS!!, 1e-9)
        assertEquals(3.0, st.medianLatencyS!!, 1e-9)
        assertTrue(st.isFresh(t0.plusSeconds(90)))
        assertFalse(st.isFresh(t0.plusSeconds(200)))
        assertEquals("r/43.8003/3.7817/100 b/FLR004839", OwnGlider.aprsFilter(LatLon(43.80028, 3.78167), 100, listOf(AddressType.FLARM to "004839")))
        assertEquals("r/43.8003/3.7817/100", OwnGlider.aprsFilter(LatLon(43.80028, 3.78167), 100, emptyList()))
    }

    // ------------------------------------------------------------------ client APRS-IS

    @Test fun clientLogsInAndStreamsLines() = runBlocking {
        val server = ServerSocket(0)
        var login: String? = null
        thread(isDaemon = true) {
            server.accept().use { s ->
                val r = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = s.getOutputStream()
                out.write("# aprsc 2.1.19 17 Sep 2026 12:00:00 GMT GLIDERN1 127.0.0.1:14580\r\n".toByteArray())
                login = r.readLine()
                out.write("# logresp GLIDY00042 unverified, server GLIDERN1\r\n".toByteArray())
                out.write("FLRDD1234>OGFLR,qAS,LFNL:/120001h4348.02N\\00347.00E^270/045/A=004000 id06DD1234 +198fpm +2.1rot\r\n".toByteArray())
                out.flush()
                Thread.sleep(500)
            }
        }
        val client = AprsClient(AprsClient.userFor(42), host = "127.0.0.1", port = server.localPort)
        val state = MutableStateFlow<AprsState>(AprsState.Idle)
        val got = withTimeout(10_000) { client.lines({ "r/43.8/3.78/100" }, state).take(3).toList() }
        assertEquals(3, got.size)
        assertEquals("user GLIDY00042 pass -1 vers GLIDY 0.4 filter r/43.8/3.78/100", login)
        assertTrue(state.value is AprsState.Idle || state.value is AprsState.Connected)
        val fix = got.map { OgnParser.parse(it.text, it.received) }.filterIsInstance<OgnLine.Aircraft>().single().fix
        assertEquals("DD1234", fix.address)
        server.close()
        assertNotNull(fix.turnDegS)
    }

    @Suppress("unused")
    private fun fmt(d: Double) = String.format(Locale.ROOT, "%.4f", d)
}

/**
 * Extrait réel du flux OGN (16/09/2026 22:16 UTC, planeurs uniquement), anonymisé par tools/ogn/record.py :
 * identifiants, récepteurs, heures et lieux modifiés (trajectoires déplacées autour de LFNL, formes conservées).
 */
class RealOgnTest {
    private val lines = javaClass.classLoader!!.getResource("real_ogn_gliders_anon_20260916.aprs")!!.readText().lines()
    private val recv = Instant.parse("2026-09-17T12:30:00Z")

    private fun fixes() = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        .map { OgnParser.parse(it, recv) }
        .filterIsInstance<OgnLine.Aircraft>().map { it.fix }

    @Test fun parsesEveryGliderFrame() {
        val f = fixes()
        assertEquals(lines.count { it.isNotBlank() && !it.startsWith("#") }, f.size)
        assertEquals(setOf("20AF56", "DC00FF", "179719", "9F4297"), f.map { it.address }.toSet())
        assertTrue(f.all { it.aircraftType == 1 && !it.hidden })
        assertTrue(f.count { it.climbMs != null } > f.size * 0.9)
    }

    @Test fun realCadenceIsAFewSeconds() {
        val st = OwnGlider.status(fixes().filter { it.address == "179719" })!!
        assertTrue(st.medianIntervalS!! in 1.0..5.0)
    }

    @Test fun findsRealThermalsOnly() {
        val f = fixes()
        val strong = ThermalDetector.episodes(f.filter { it.address == "179719" })
        assertEquals(2, strong.size)
        assertTrue(strong.all { it.climbMs > 1.8 })
        // l'autre planeur spirale sans monter : pas de pompe publiée
        val store = TrafficStore()
        f.sortedBy { it.time }.forEach { store.add(it) }
        val end = f.maxOf { it.time }
        store.analyse(end)
        val th = store.thermals(end)
        assertTrue(th.isNotEmpty())
        assertTrue(th.all { it.climbMs >= 0.3 })
        assertEquals(2, th.count { it.climbMs > 1.8 })
    }
}

/** Suivi & debug : la trace OGN réelle d'un planeur en vol alimente le moteur de vol et la sécurité comme le téléphone. */
class FollowPipelineTest {
    @Test fun realOgnTrackDrivesFlightAndSafety() {
        val lines = javaClass.classLoader!!.getResource("real_ogn_gliders_anon_20260916.aprs")!!.readText().lines()
        val recv = Instant.parse("2026-09-17T12:30:00Z")
        val track = lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { (OgnParser.parse(it, recv) as? OgnLine.Aircraft)?.fix }
            .filter { it.address == "179719" }.sortedBy { it.time }
        val core = com.neutronstar.glidercopilot.domain.flight.FlightEngineCore(igc = { null })
        val lfnl = com.neutronstar.glidercopilot.domain.safety.FieldOption("LFNL", "Saint-Martin-de-Londres", com.neutronstar.glidercopilot.domain.LatLon(43.80028, 3.78167), 183.0, isClub = true)
        val safety = com.neutronstar.glidercopilot.domain.safety.SafetyEngine(terrain = { com.neutronstar.glidercopilot.domain.Terrain { 250.0 } }, fields = { listOf(lfnl) }, alert = {})
        val t0 = track.first().time
        var circling = 0
        var last: com.neutronstar.glidercopilot.domain.safety.SafetyState? = null
        for (f in track) {
            val ns = java.time.Duration.between(t0, f.time).toNanos() + 1
            core.onOgn(f.climbMs, f.altitudeM, 1)
            val fix = com.neutronstar.glidercopilot.domain.flight.GpsFix(f.time, f.position, f.altitudeM, f.groundSpeedKmh, f.trackDeg, 15.0)
            core.onGps(fix, ns)
            safety.onFix(fix)
            val snap = core.snapshot(ns, f.time)
            if (snap.circling) circling++
            last = safety.update(fix, snap.altitudeM!!, snap.avgSpiralMs ?: 0.0, snap.circling, com.neutronstar.glidercopilot.domain.safety.SafetyConfig(20.0), f.time, armed = true)
            assertEquals(com.neutronstar.glidercopilot.domain.flight.VarioSource.OGN, snap.source)
        }
        val end = core.snapshot(java.time.Duration.between(t0, track.last().time).toNanos() + 1, track.last().time)
        assertTrue("spirales vues sur $circling trames", circling > 30)
        assertTrue("pompe ${end.avgThermalMs}", (end.avgThermalMs ?: 0.0) > 1.0)
        assertEquals(com.neutronstar.glidercopilot.domain.flight.FlightPhase.FLYING, end.phase)
        assertTrue("vent estimé", safety.wind.wind(track.last().time) != null)
        val r = last!!.choice.result
        assertEquals("LFNL", r.field.id)
        assertTrue("distance ${r.distanceKm}", r.distanceKm in 5.0..25.0)
        assertTrue("marge ${r.marginM}", r.marginM > 0)
    }
}
