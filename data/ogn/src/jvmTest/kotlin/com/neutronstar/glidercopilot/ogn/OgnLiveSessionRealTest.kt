package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * De bout en bout, sur la session commune utilisée par iOS : 1 292 trames OGN réelles (anonymisées) passent par le
 * client APRS → aéronefs autour avec étiquettes, et pompes détectées en reconnaissant les spirales des autres planeurs.
 */
class OgnLiveSessionRealTest {
    @Test fun realFramesGiveAircraftAndThermals() = runBlocking {
        val lines = javaClass.classLoader!!.getResource("real_ogn_gliders_anon_20260916.aprs")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val now = Instant.parse("2026-09-17T12:30:00Z")
        val last = lines.mapNotNull { (OgnParser.parse(it, now) as? OgnLine.Aircraft)?.fix?.time }.max()
        val fake = object : AprsConnection {
            val q = ArrayDeque(listOf("# aprsc 2.1.15") + lines)
            override suspend fun readLine(): String? { if (q.isEmpty()) { delay(20); return null }; return q.removeFirst() }
            override suspend fun write(text: String) {}
            override fun close() {}
        }
        val clock = object : kotlinx.datetime.Clock { override fun now() = now }
        val session = OgnLiveSession(null, "GLIDY0042", "test", clock = { last }, client = AprsClient("GLIDY0042", clock = clock, connect = { _, _ -> fake }))
        val scope = CoroutineScope(Dispatchers.Default)
        session.start(scope, LatLon(43.80028, 3.78167))
        val snap = withTimeout(20_000) {
            var s = session.snapshot(null)
            while (s.pumps.size < 2) { delay(200); s = session.snapshot(null) } // analyse toutes les 5 s
            s
        }
        scope.cancel()
        assertEquals(setOf("20AF56", "DC00FF", "179719", "9F4297").filter { id -> snap.planes.any { it.id == id } }.size, snap.planes.size)
        assertTrue(snap.planes.isNotEmpty())
        assertEquals(2, snap.pumps.count { it.climbMs > 1.8 })
        assertTrue(snap.pill, snap.pill.endsWith("pompes"))
    }
}
