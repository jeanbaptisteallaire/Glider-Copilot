package com.neutronstar.glidy.replay3d

import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.IgcFileRef
import com.neutronstar.glidy.flightarchive.IgcTrackPoint
import com.neutronstar.glidy.flightarchive.LocalFileState
import java.time.Instant
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Replay3dTrackPayloadTest {
    private val flight = ArchivedFlight(
        id = FlightId(UUID.randomUUID().toString()),
        file = IgcFileRef("trace.igc", "vol.igc", 1024, "a".repeat(64)),
        summary = null, pilot = null, gliderType = null, gliderId = null,
        localState = LocalFileState.AVAILABLE,
    )

    /** Spirale de thermique : 1 point par seconde, 30 s par tour, +1,5 m/s. */
    private fun thermal(n: Int): List<IgcTrackPoint> {
        val t0 = Instant.parse("2026-09-19T10:00:00Z")
        return List(n) { i ->
            val a = i * 2 * Math.PI / 30
            IgcTrackPoint(t0.plusSeconds(i.toLong()), 43.8 + 0.0012 * sin(a), 3.7 + 0.0016 * cos(a), null, 800 + (i * 1.5).toInt())
        }
    }

    @Test fun keepsEveryPointBelowTheLimitWithRealTimes() {
        val p = Replay3dTrackPayload.from(flight, thermal(6_781))
        assertEquals(6_781, p.latitudes.size)
        assertEquals(0L, p.seconds.first())
        assertEquals(6_780L, p.seconds.last())
        assertEquals(800, p.altitudes.first())
    }

    @Test fun simplifiesLongTracksButKeepsTheTurns() {
        val track = thermal(30_000)
        val p = Replay3dTrackPayload.from(flight, track)
        assertTrue("trop de points : ${p.latitudes.size}", p.latitudes.size <= 30_000)
        // un tour de 30 s ne peut pas tenir en moins de ~8 points à 2,5 m près : les spirales restent rondes
        val perTurn = p.latitudes.size / (30_000 / 30.0)
        assertTrue("spirales aplaties : $perTurn points par tour", perTurn >= 8)
        assertEquals(track.last().timestamp.epochSecond - track.first().timestamp.epochSecond, p.seconds.last())
    }

    @Test fun straightLineCollapsesToItsEnds() {
        val t0 = Instant.parse("2026-09-19T10:00:00Z")
        val line = List(1_000) { i -> IgcTrackPoint(t0.plusSeconds(i.toLong()), 43.8 + i * 1e-5, 3.7, null, 1000) }
        assertEquals(2, Replay3dTrackPayload.simplify(line, 2.5).size)
    }
}
