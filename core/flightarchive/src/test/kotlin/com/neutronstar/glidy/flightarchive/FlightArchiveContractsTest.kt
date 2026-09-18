package com.neutronstar.glidy.flightarchive

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FlightArchiveContractsTest {
    @Test
    fun flightIdRejectsNonUuidValue() {
        assertThrows(IllegalArgumentException::class.java) { FlightId("not-a-uuid") }
    }

    @Test
    fun fileReferenceRequiresIgcAndSha256() {
        val ref = IgcFileRef(
            relativePath = "igc/flight.igc",
            fileName = "flight.igc",
            sizeBytes = 42,
            sha256 = "a".repeat(64),
        )
        assertEquals("flight.igc", ref.fileName)
        assertThrows(IllegalArgumentException::class.java) {
            ref.copy(fileName = "flight.gpx")
        }
    }

    @Test
    fun summaryRejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException::class.java) {
            FlightSummary(
                startedAt = Instant.parse("2026-09-18T12:00:00Z"),
                endedAt = Instant.parse("2026-09-18T11:59:59Z"),
                pointCount = 1,
                validPointCount = 1,
                distanceMeters = null,
                minimumAltitudeMeters = null,
                maximumAltitudeMeters = null,
                positiveGainMeters = null,
                bounds = null,
            )
        }
    }
}

