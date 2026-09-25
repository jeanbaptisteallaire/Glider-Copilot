package com.neutronstar.glidy.replay3d

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightSummary
import com.neutronstar.glidy.flightarchive.GeoPoint
import com.neutronstar.glidy.flightarchive.IgcFileRef
import com.neutronstar.glidy.flightarchive.LocalFileState
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Replay3dPayloadTest {
    @Test
    fun archivedTrackBecomesTimed3dPayload() {
        val flight = ArchivedFlight(
            id = FlightId(UUID.randomUUID().toString()),
            file = IgcFileRef("trace.igc", "Saint-Martin.igc", 1024, "a".repeat(64)),
            summary = FlightSummary(
                startedAt = Instant.parse("2026-09-19T09:00:00Z"),
                endedAt = Instant.parse("2026-09-19T10:53:00Z"),
                pointCount = 2,
                validPointCount = 2,
                distanceMeters = 30_000,
                minimumAltitudeMeters = 180,
                maximumAltitudeMeters = 1_430,
                positiveGainMeters = 2_100,
                bounds = null,
            ),
            pilot = null,
            gliderType = "Discus 2",
            gliderId = null,
            localState = LocalFileState.AVAILABLE,
            previewTrack = listOf(GeoPoint(43.80, 3.73), GeoPoint(43.82, 3.77)),
            altitudeProfileMeters = listOf(190, 1_430),
        )

        val payload = Replay3dPayload.from(flight)
        val json = payload.toJson()

        assertEquals(6_780L, payload.durationSeconds)
        assertEquals(2, payload.points.size)
        assertEquals(1_430, json.getJSONArray("points").getJSONObject(1).getInt("alt"))
        assertEquals("Saint-Martin.igc", json.getString("title"))
    }
}
