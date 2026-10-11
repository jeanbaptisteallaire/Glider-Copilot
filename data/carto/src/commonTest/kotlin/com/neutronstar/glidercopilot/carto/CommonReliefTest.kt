package com.neutronstar.glidercopilot.carto

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.safety.FieldOption
import com.neutronstar.glidercopilot.domain.safety.GlideComputer
import com.neutronstar.glidercopilot.domain.safety.SafetyConfig
import okio.Path.Companion.toPath
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** iOS / KMP : relief réel du pack (extrait z11 autour de LFNL) lu par Okio, même résultat sur JVM et iOS. */
class CommonReliefTest {
    private val fixture = "$FIXTURES_DIR/lfnl_relief_z11.pmtiles".toPath()

    @Test fun realTerrainAroundLfnl() {
        PmTiles(fixture).use { pm ->
            assertEquals(11, pm.maxZoom)
            val t = TerrariumTerrain(pm)
            assertEquals(183.0, t.elevationM(LatLon(43.80028, 3.78167))!!, 12.0)
            assertNull(t.elevationM(LatLon(45.0, 5.0)))
        }
    }

    @Test fun realRidgeBetweenGliderAndLfnl() {
        PmTiles(fixture).use { pm ->
            val t = TerrariumTerrain(pm)
            val lfnl = FieldOption("LFNL", "Saint-Martin-de-Londres", LatLon(43.80028, 3.78167), 183.0, isClub = true)
            val pic = LatLon(43.7765, 3.8085)
            val from = Geo.destination(pic, Geo.bearingDeg(lfnl.position, pic), 3.0)
            val low = GlideComputer.toField(from, 830.0, lfnl, SafetyConfig(finesse = 20.0), null, t)
            assertTrue(low.terrainKnown)
            assertNotNull(low.relief)
            assertTrue(low.requiredM > low.fieldRequiredM + 30)
        }
    }
}
