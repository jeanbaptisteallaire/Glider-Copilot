package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Session OGN commune (Android / iOS) : trames APRS d'un faux serveur → aéronefs, pastille et état de connexion. */
class OgnLiveSessionTest {
    private val now = Instant.parse("2026-09-17T12:53:40Z")
    private val frames = listOf(
        "# aprsc 2.1.15",
        "# logresp GLIDY0042 unverified, server GLIDERN3",
        "OGN20AF56>OGFLR,qAS,RCVR00:/125325h4347.13N/00345.42E'052/058/A=006248 id0720AF56 -217fpm -0.4rot",
        "OGN20AF56>OGFLR,qAS,RCVR00:/125327h4347.15N/00345.46E'050/059/A=006231 id0720AF56 -633fpm -0.2rot",
        "FLRDC00FF>OGFLR,qAS,RCVR04:/125329h4346.81N/00347.79E'049/063/A=006179 id06DC00FF -1009fpm -0.3rot",
    )

    private class Fake(lines: List<String>) : AprsConnection {
        val queue = ArrayDeque(lines)
        val written = ArrayList<String>()
        override suspend fun readLine(): String? { if (queue.isEmpty()) { delay(50); return null }; return queue.removeFirst() }
        override suspend fun write(text: String) { written += text }
        override fun close() {}
    }

    @Test fun framesBecomePlanesWithTheSameLabelsAsAndroid() = runBlocking {
        val fake = Fake(frames)
        val client = AprsClient("GLIDY0042", appVersion = "test", clock = object : kotlinx.datetime.Clock { override fun now() = this@OgnLiveSessionTest.now }, connect = { _, _ -> fake })
        val session = OgnLiveSession(ddb = null, user = "GLIDY0042", appVersion = "test", clock = { now }, client = client)
        val scope = CoroutineScope(Dispatchers.Default)
        session.start(scope, LatLon(43.80028, 3.78167))
        val snap = withTimeout(5_000) {
            var s = session.snapshot(1800.0)
            while (s.planes.size < 2) { delay(20); s = session.snapshot(1800.0) }
            s
        }
        scope.cancel()
        assertTrue(fake.written.first().startsWith("user GLIDY0042 pass -1 vers GLIDY test filter r/43.8003/3.7817/250"), fake.written.first())
        assertTrue(snap.connected, snap.statusLabel)
        assertEquals("2 aéronefs · 0 pompe", snap.pill)
        val p = snap.planes.first { it.id == "20AF56" }
        assertEquals("+99", p.relativeAltText) // 6231 ft = 1899 m, mon planeur à 1800 m
        assertTrue(snap.statusLabel.startsWith("Connecté · GLIDERN3"), snap.statusLabel)
    }

    @Test fun pillMatchesFlightScreenLegend() {
        assertEquals("1 aéronef (1 à 15 km) · 2 pompes", OgnLiveSession.pill(1, 1, 2))
        assertEquals("0 aéronef · 0 pompe", OgnLiveSession.pill(0, null, 0))
    }
}
