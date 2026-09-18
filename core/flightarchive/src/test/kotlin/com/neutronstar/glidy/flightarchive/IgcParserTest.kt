package com.neutronstar.glidy.flightarchive

import java.io.File
import java.io.StringReader
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcParserTest {
    private val parser = StreamingIgcFlightParser()

    @Test
    fun `un vol valide produit metadonnees points et resume stables`() {
        val result = parser.parse(
            StringReader(
                """
                AXXXGLYGLIDY
                HFDTEDATE:170926,01
                HFPLTPILOTINCHARGE:Jean Dupont
                HFGTYGLIDERTYPE:ASW 28
                HFGIDGLIDERID:F-CODE
                B0816494348085N00346845EA0021800183
                B0816504348094N00346838EA0022000188
                B0816514348104N00346830EA0022100195
                """.trimIndent(),
            ),
        ) as IgcParseResult.Success

        with(result.flight) {
            assertEquals("Jean Dupont", metadata.pilot)
            assertEquals("ASW 28", metadata.gliderType)
            assertEquals("F-CODE", metadata.gliderId)
            assertEquals(3, points.size)
            assertEquals(3, summary.pointCount)
            assertEquals(3, summary.validPointCount)
            assertEquals(Duration.ofSeconds(2), Duration.between(summary.startedAt, summary.endedAt))
            assertEquals(183, summary.minimumAltitudeMeters)
            assertEquals(195, summary.maximumAltitudeMeters)
            assertEquals(12, summary.positiveGainMeters)
            assertTrue(summary.distanceMeters in 40L..60L)
            assertTrue(warnings.isEmpty())
        }
    }

    @Test
    fun `le passage de minuit avance la date`() {
        val result = parse(
            """
            HFDTE310126
            B2359594348085N00346845EA0010000100
            B0000014348094N00346838EA0010200102
            """.trimIndent(),
        )

        assertEquals(2, Duration.between(result.summary.startedAt, result.summary.endedAt).seconds)
        assertEquals("2026-02-01T00:00:01Z", result.summary.endedAt.toString())
    }

    @Test
    fun `un point V est compte mais exclu des points valides`() {
        val result = parse(
            """
            HFDTE170926
            B0816494348085N00346845EA0021800183
            B0816504348094N00346838EV0021800184
            B0816514348104N00346830EA0021800185
            """.trimIndent(),
        )

        assertEquals(3, result.summary.pointCount)
        assertEquals(2, result.summary.validPointCount)
        assertEquals(IgcWarningCode.INVALID_FIX, result.warnings.single().code)
    }

    @Test
    fun `une ligne tronquee devient un avertissement sans annuler le vol`() {
        val result = parse(
            """
            HFDTE170926
            B0816494348085N00346845EA0021800183
            B0816
            B0816514348104N00346830EA0021800185
            """.trimIndent(),
        )

        assertEquals(3, result.summary.pointCount)
        assertEquals(2, result.summary.validPointCount)
        assertEquals(IgcWarningCode.MALFORMED_B_RECORD, result.warnings.single().code)
    }

    @Test
    fun `un recul horaire court est ignore et signale`() {
        val result = parse(
            """
            HFDTE170926
            B0816494348085N00346845EA0021800183
            B0816484348094N00346838EA0021800184
            B0816514348104N00346830EA0021800185
            """.trimIndent(),
        )

        assertEquals(2, result.summary.validPointCount)
        assertEquals(IgcWarningCode.NON_MONOTONIC_TIME, result.warnings.single().code)
    }

    @Test
    fun `un fichier vide retourne une erreur typee`() {
        assertEquals(
            IgcParseResult.Failure(IgcParseError.EmptyFile),
            parser.parse(StringReader("\n  \n")),
        )
    }

    @Test
    fun `une date absente retourne une erreur typee`() {
        assertEquals(
            IgcParseResult.Failure(IgcParseError.MissingDateHeader),
            parser.parse(StringReader("AXXX\nB0816494348085N00346845EA0021800183")),
        )
    }

    @Test
    fun `une date impossible retourne son numero de ligne`() {
        assertEquals(
            IgcParseResult.Failure(IgcParseError.InvalidDateHeader(2)),
            parser.parse(StringReader("AXXX\nHFDTE320926\nB0816494348085N00346845EA0021800183")),
        )
    }

    @Test
    fun `des coordonnees invalides ne produisent aucun point`() {
        assertEquals(
            IgcParseResult.Failure(IgcParseError.NoValidFix),
            parser.parse(StringReader("HFDTE170926\nB0816499960085N00346845EA0021800183")),
        )
    }

    @Test
    fun `une altitude absente conserve la trace sans inventer de valeur`() {
        val result = parse("HFDTE170926\nB0816494348085N00346845EA----------")

        assertEquals(null, result.summary.minimumAltitudeMeters)
        assertEquals(null, result.summary.maximumAltitudeMeters)
        assertEquals(null, result.summary.positiveGainMeters)
    }

    @Test
    fun `une altitude negative est acceptee`() {
        val result = parse(
            """
            HFDTE170926
            B0816494348085N00346845EA-0123-0123
            B0816504348094N00346838EA-0100-0100
            """.trimIndent(),
        )

        assertEquals(-123, result.summary.minimumAltitudeMeters)
        assertEquals(-100, result.summary.maximumAltitudeMeters)
        assertEquals(23, result.summary.positiveGainMeters)
    }

    @Test
    fun `un echantillon externe peut etre controle sans entrer dans le depot`() {
        val samplePath = System.getenv("GLIDY_IGC_SAMPLE") ?: return
        val result = File(samplePath).reader().use(parser::parse)
        assertTrue(result is IgcParseResult.Success)
        val flight = (result as IgcParseResult.Success).flight
        assertTrue(flight.points.size > 10)
        assertTrue(flight.summary.distanceMeters != null)
    }

    private fun parse(text: String): ParsedIgcFlight =
        (parser.parse(StringReader(text)) as IgcParseResult.Success).flight
}
