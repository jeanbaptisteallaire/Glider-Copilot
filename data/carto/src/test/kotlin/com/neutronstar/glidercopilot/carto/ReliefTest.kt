package com.neutronstar.glidercopilot.carto

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Extrait réel du pack occitanie-est (4 tuiles z11 Copernicus GLO-30 autour de LFNL). */
class ReliefTest {
    private fun fixture(): File {
        val url = javaClass.classLoader!!.getResource("lfnl_relief_z11.pmtiles")!!
        return File(url.toURI())
    }

    @Test fun hilbertTileIds() {
        assertEquals(0L, PmTiles.tileId(0, 0, 0))
        assertEquals(1L, PmTiles.tileId(1, 0, 0))
        assertEquals(2L, PmTiles.tileId(1, 0, 1))
        assertEquals(3L, PmTiles.tileId(1, 1, 1))
        assertEquals(4L, PmTiles.tileId(1, 1, 0))
        assertEquals(4959436L, PmTiles.tileId(11, 1045, 746))
    }

    @Test fun realTerrainAroundLfnl() {
        PmTiles(fixture()).use { pm ->
            assertEquals(11, pm.maxZoom)
            assertNotNull(pm.tile(11, 1045, 746))
            assertNull(pm.tile(11, 1044, 746))
            val t = TerrariumTerrain(pm)
            val field = t.elevationM(LatLon(43.80028, 3.78167))!!
            assertEquals("LFNL (openAIP 183 m)", 183.0, field, 12.0)
            // Pic Saint-Loup (658 m) et Hortus (512 m) dans l'extrait
            val pic = (0..20).flatMap { i -> (0..20).map { j -> LatLon(43.770 + i * 0.001, 3.800 + j * 0.001) } }.mapNotNull { t.elevationM(it) }.max()
            assertTrue("sommet du Pic Saint-Loup $pic", pic in 600.0..680.0)
            assertNull(t.elevationM(LatLon(45.0, 5.0)))
        }
    }

    /** Planeur à l'est du Pic Saint-Loup, route vers LFNL par-dessus le massif : le relief impose plus que l'arrivée. */
    @Test fun realRidgeBetweenGliderAndLfnl() {
        PmTiles(fixture()).use { pm ->
            val t = TerrariumTerrain(pm)
            val lfnl = com.neutronstar.glidercopilot.domain.safety.FieldOption("LFNL", "Saint-Martin-de-Londres", LatLon(43.80028, 3.78167), 183.0, isClub = true)
            val cfg = com.neutronstar.glidercopilot.domain.safety.SafetyConfig(finesse = 20.0)
            // point placé pour que la route passe par le sommet du Pic (≈ 43.776 N 3.808 E)
            val pic = LatLon(43.7765, 3.8085)
            val from = Geo.destination(pic, Geo.bearingDeg(lfnl.position, pic), 3.0)
            val low = com.neutronstar.glidercopilot.domain.safety.GlideComputer.toField(from, 830.0, lfnl, cfg, null, t)
            assertTrue("terrain connu", low.terrainKnown)
            assertTrue("relief ${low.requiredM} > arrivée ${low.fieldRequiredM}", low.requiredM > low.fieldRequiredM + 30 && 830.0 > low.fieldRequiredM)
            assertNotNull(low.relief)
            assertEquals(3.0, low.relief!!.sKm, 0.8)
            val high = com.neutronstar.glidercopilot.domain.safety.GlideComputer.toField(from, 900.0, lfnl, cfg, null, t)
            assertNull(high.relief)
            assertTrue(high.reachable)
            assertTrue(high.profile.size > 50 && high.profile.all { it.terrainM != null })
        }
    }

    @Test fun pngFiltersAllTypes() {
        // image 3×2 RVB, une ligne par filtre Sub puis Paeth, écrite à la main
        val raw = byteArrayOf(
            1, 10, 20, 30, 5, 5, 5, 5, 5, 5,
            4, 1, 1, 1, 0, 0, 0, 0, 0, 0,
        )
        val z = java.io.ByteArrayOutputStream().also { o -> java.util.zip.DeflaterOutputStream(o).use { it.write(raw) } }.toByteArray()
        fun chunk(type: String, data: ByteArray): ByteArray {
            val crc = java.util.zip.CRC32().apply { update(type.toByteArray()); update(data) }.value.toInt()
            return java.nio.ByteBuffer.allocate(12 + data.size).putInt(data.size).put(type.toByteArray()).put(data).putInt(crc).array()
        }
        val ihdr = java.nio.ByteBuffer.allocate(13).putInt(3).putInt(2).put(8).put(2).put(0).put(0).put(0).array()
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) + chunk("IHDR", ihdr) + chunk("IDAT", z) + chunk("IEND", ByteArray(0))
        val img = Png.decode(png)
        assertEquals(0x0A141E, img.rgb[0]); assertEquals(0x0F1923, img.rgb[1]); assertEquals(0x141E28, img.rgb[2])
        assertEquals(0x0B151F, img.rgb[3]); assertEquals(0x0F1923, img.rgb[4])
    }
}
