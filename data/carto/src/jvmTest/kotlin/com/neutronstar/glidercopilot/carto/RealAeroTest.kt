package com.neutronstar.glidercopilot.carto

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.aero.AirspaceFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pack réel « occitanie-est » fabriqué par la CI le 16/09/2026 (openAIP). */
class RealAeroTest {
    private val aero = AeroGeoJson.parse(javaClass.classLoader!!.getResource("real_occitanie_est_aero_20260916.geojson")!!.readText())
    private val lfnl = LatLon(43.80028, 3.78167)

    @Test fun countsAndLfnl() {
        assertEquals(215, aero.airspaces.size)
        assertEquals(88, aero.airports.size)
        val field = aero.airports.first { it.icao == "LFNL" }
        assertEquals("SAINT MARTIN DE LONDRES", field.name)
        assertEquals(183, field.elevationM)
        assertEquals("122.505", field.frequency)
    }

    @Test fun airspacesAroundLfnl() {
        val around = aero.airspacesAround(lfnl, 15.0)
        assertTrue(around.isNotEmpty())
        val names = around.map { it.first.name }
        assertTrue(names.any { it.contains("PIC ST LOUP") })
        // tous les espaces renvoyés ont une limite basse et haute lisibles
        assertTrue(around.all { it.first.lower != null && it.first.upper != null })
        // le tri place d'abord les espaces qui contiennent le terrain
        val firstOutside = around.indexOfFirst { it.second > 0 }
        assertTrue(firstOutside == -1 || around.drop(firstOutside).all { it.second > 0 })
        val inside = around.filter { it.second == 0.0 }.map { it.first }
        assertTrue(inside.all { it.contains(lfnl) })
        assertTrue(aero.airspaces.any { it.family == AirspaceFamily.RESTRICTED })
    }

    @Test fun styleWithRealDataStaysValid() {
        val pack = PackInfo("occitanie-est", "Occitanie est", "1", null, doubleArrayOf(2.55, 42.95, 5.25, 44.75), null, "",
            listOf("fond", "relief", "courbes", "aero").map { PackFile(it, "occitanie-est-$it.x", "u", 1, "") })
        val json = MapStyle.build(pack, java.io.File("/p"), javaClass.classLoader!!.getResource("real_occitanie_est_aero_20260916.geojson")!!.readText())
        val parsed = com.neutronstar.glidercopilot.precog.Json.parse(json)
        assertEquals(389, parsed["sources"]!!["aero"]!!["data"]!!["features"]!!.arr.size)
        assertTrue(parsed["layers"]!!.arr.any { it["id"]!!.str == "hillshade" })
    }
}

/** Pistes dessinées sur la carte : orientation réelle des trois terrains proches du club. */
class RunwayGeoJsonTest {
    private val aero = AeroGeoJson.parse(javaClass.classLoader!!.getResource("real_occitanie_est_aero_20260916.geojson")!!.readText())

    @Test fun knownRunwaysAreDrawnWithTheirOrientation() {
        val json = RunwayGeoJson.build(aero.airports)
        // une piste par terrain connu présent dans le pack, pas une de plus
        assertEquals(3, Regex("\"type\":\"Feature\"").findAll(json).count())
        assertTrue(json.contains("\"hdg\":120.0"))   // LFMT 12L/30R et LFNL 12/30
        assertTrue(json.contains("\"hdg\":10.0"))    // LFMS 01/19
        assertTrue(json.contains("\"hard\":false"))  // LFNL en herbe
        // la piste est posée sur la position du terrain
        val lfnl = aero.airports.first { it.icao == "LFNL" }
        assertTrue(json.contains("[${lfnl.position.lon},${lfnl.position.lat}]"))
    }

    @Test fun airportsWithoutKnownRunwayAreSkipped() {
        val json = RunwayGeoJson.build(aero.airports.filter { it.icao == null || it.icao !in listOf("LFMT", "LFNL", "LFMS") })
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", json)
    }
}
