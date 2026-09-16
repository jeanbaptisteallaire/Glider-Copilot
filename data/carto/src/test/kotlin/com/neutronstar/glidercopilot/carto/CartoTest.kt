package com.neutronstar.glidercopilot.carto

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.aero.AirspaceFamily
import com.neutronstar.glidercopilot.domain.aero.AltRef
import com.neutronstar.glidercopilot.domain.aero.AltUnit
import com.neutronstar.glidercopilot.domain.aero.AltitudeLimit
import com.neutronstar.glidercopilot.precog.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant

class CartoTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun altitudeLabelsAndConversion() {
        assertEquals("SFC", AltitudeLimit.fromOpenAip(0.0, 1, 0)!!.label)
        assertEquals("2 500 ft AMSL", AltitudeLimit.fromOpenAip(2500.0, 1, 1)!!.label)
        assertEquals("FL065", AltitudeLimit.fromOpenAip(65.0, 6, 2)!!.label)
        assertEquals("1 000 ft ASFC", AltitudeLimit.fromOpenAip(1000.0, 1, 0)!!.label)
        assertEquals(762.0, AltitudeLimit(2500.0, AltUnit.FEET, AltRef.MSL).approxMetersAmsl(), 0.01)
        assertEquals(504.8, AltitudeLimit(1000.0, AltUnit.FEET, AltRef.GND).approxMetersAmsl(200.0), 0.01)
        assertNull(AltitudeLimit.fromOpenAip(1.0, 9, 0))
    }

    @Test fun parsesAeroGeoJson() {
        val d = AeroGeoJson.parse(res("aero_synth.geojson"))
        assertEquals(2, d.airspaces.size)
        assertEquals(1, d.airports.size)
        assertEquals("LFMT", d.airports[0].icao)
        assertEquals("VOR-DME", d.navaids[0].typeLabel)
        assertEquals(1, d.reportingPoints.size)
        assertEquals(Instant.parse("2026-09-16T20:30:00Z"), d.fetched)
        val tma = d.airspaces.first { it.id == "a1" }
        assertEquals("TMA", tma.typeLabel)
        assertEquals("D", tma.classLetter)
        assertEquals(AirspaceFamily.CONTROLLED, tma.family)
        assertEquals("2 500 ft AMSL → FL145", tma.verticalLabel)
        assertTrue(d.airspaces.first { it.id == "a2" }.byNotam)
    }

    @Test fun pointInPolygonHonoursHoles() {
        val d = AeroGeoJson.parse(res("aero_synth.geojson"))
        val r = d.airspaces.first { it.id == "a2" }
        assertTrue(r.contains(LatLon(43.55, 3.95)))
        assertFalse(r.contains(LatLon(43.55, 4.05)))      // dans le trou
        assertEquals(0.0, r.distanceKm(LatLon(43.55, 3.95)), 1e-9)
        // à 0,1° au nord du bord supérieur (43,6) : ~11,1 km
        assertEquals(11.1, r.distanceKm(LatLon(43.7, 3.95)), 0.2)
    }

    @Test fun airspacesAroundLfnlSorted() {
        val d = AeroGeoJson.parse(res("aero_synth.geojson"))
        val lfnl = LatLon(43.80028, 3.78167)
        val around = d.airspacesAround(lfnl, 30.0)
        assertEquals("a1", around.first().first.id)
        assertEquals(0.0, around.first().second, 1e-9)
        assertEquals(1, d.airspacesAround(lfnl, 5.0).size)
    }

    private val catalog = """
        {"format":1,"packs":[
         {"id":"occitanie-est","name":"Occitanie est","version":"202609162100","created":"2026-09-16T21:00:00Z","bbox":[2.55,42.95,5.25,44.75],
          "aeroValidUntil":"2026-10-14T21:00:00Z","attribution":"© OSM","files":[{"role":"fond","name":"occitanie-est-fond.pmtiles","url":"https://x/occitanie-est-fond.pmtiles","size":10,"sha256":"ab"}]},
         {"id":"provence-alpes-sud","name":"Provence","version":"1","bbox":[4.55,43.35,7.15,45.15],"files":[]}
        ]}
    """.trimIndent()

    @Test fun catalogPicksPackForClub() {
        val packs = PackCatalog.parse(catalog)
        assertEquals(2, packs.size)
        assertEquals("occitanie-est", PackCatalog.forPosition(packs, LatLon(43.80028, 3.78167))!!.id)
        // Carpentras est dans les deux emprises : la région dont le centre est le plus proche gagne
        assertEquals("provence-alpes-sud", PackCatalog.forPosition(packs, LatLon(44.02, 5.09))!!.id)
        assertNull(PackCatalog.forPosition(packs, LatLon(48.85, 2.35)))
        val p = packs[0]
        assertFalse(p.aeroExpired(Instant.parse("2026-10-01T00:00:00Z")))
        assertTrue(p.aeroExpired(Instant.parse("2026-10-15T00:00:00Z")))
        assertTrue(packs[1].aeroExpired(Instant.parse("2026-01-01T00:00:00Z")))   // pas de date = à rafraîchir
    }

    @Test fun styleIsValidJsonWithAeroInlined() {
        val pack = PackCatalog.parse(catalog)[0]
        val style = MapStyle.build(pack, File("/data/packs/occitanie-est"), res("aero_synth.geojson"))
        val j = Json.parse(style)
        val sources = j["sources"]!!.obj!!.map
        assertEquals("pmtiles://file:///data/packs/occitanie-est/occitanie-est-fond.pmtiles", sources["fond"]!!["url"]!!.str)
        assertNull(sources["relief"])   // fichier absent du pack : couche omise
        assertEquals(5, sources["aero"]!!["data"]!!["features"]!!.arr.size)
        val ids = j["layers"]!!.arr.map { it["id"]!!.str }
        assertTrue(ids.containsAll(listOf("background", "earth", "airspace-line", "airport", "trace", "glider")))
        assertFalse("hillshade" in ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    // ------------------------------------------------------------------ téléchargement

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun downloaderVerifiesResumesAndInstallsManifestLast() {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val root = Files.createTempDirectory("packs").toFile()
        val pack = PackInfo("r", "R", "1", null, doubleArrayOf(0.0, 0.0, 1.0, 1.0), null, "",
            listOf(PackFile("fond", "r-fond.pmtiles", "u://fond", data.size.toLong(), sha(data))))
        // premier essai : coupure à mi-fichier ; le second reprend à l'octet atteint
        var calls = 0
        val offsets = ArrayList<Long>()
        val opener = StreamOpener { _, offset ->
            calls++; offsets += offset
            val slice = if (calls == 1) data.copyOfRange(0, 120_000) else data.copyOfRange(offset.toInt(), data.size)
            ByteArrayInputStream(slice) to slice.size.toLong()
        }
        PackDownloader(opener).install(pack, "{\"id\":\"r\",\"bbox\":[0,0,1,1],\"files\":[{\"role\":\"fond\",\"name\":\"r-fond.pmtiles\",\"url\":\"u://fond\",\"size\":300000,\"sha256\":\"${sha(data)}\"}]}", root)
        assertEquals(listOf(0L, 120_000L), offsets)
        val installed = PackDownloader.installed(root, "r")
        assertNotNull(installed)
        assertEquals(300_000L, File(root, "r/r-fond.pmtiles").length())
        assertFalse(File(root, "r/r-fond.pmtiles.part").exists())
    }

    @Test fun downloaderRejectsCorruptFile() {
        val data = ByteArray(1000) { 7 }
        val root = Files.createTempDirectory("packs").toFile()
        val pack = PackInfo("r", "R", "1", null, doubleArrayOf(0.0, 0.0, 1.0, 1.0), null, "",
            listOf(PackFile("fond", "r-fond.pmtiles", "u://fond", 1000, "00".repeat(32))))
        val opener = StreamOpener { _, _ -> ByteArrayInputStream(data) to 1000L }
        try {
            PackDownloader(opener).install(pack, "{}", root)
            error("aurait dû échouer")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("empreinte"))
        }
        assertNull(PackDownloader.installed(root, "r"))
    }
}
