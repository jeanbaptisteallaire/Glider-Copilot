package com.neutronstar.glidercopilot.domain.safety

import kotlin.test.Test
import kotlin.test.assertEquals

class SafetyConfigTest {
    @Test fun finesseEtMargeIndependantes() {
        assertEquals(30.0, SafetyConfig.of(30, 0).finesse)
        assertEquals(0.0, SafetyConfig.of(30, 0).arrivalMarginM)
        assertEquals(300.0, SafetyConfig.of(10, 300).arrivalMarginM)
    }

    @Test fun margeBornee() {
        assertEquals(0.0, SafetyConfig.of(20, -50).arrivalMarginM)
        assertEquals(1000.0, SafetyConfig.of(20, 5000).arrivalMarginM)
        assertEquals(listOf(10, 20, 30), SafetyConfig.FINESSES)
    }

    @Test fun regleHistoriqueInchangee() {
        assertEquals(300.0, SafetyConfig.forFinesse(20).arrivalMarginM)
        assertEquals(0.0, SafetyConfig.forFinesse(10).arrivalMarginM)
    }
}

class OfficialAerodromeTest {
    @Test fun seulementIndicatifOaci() {
        kotlin.test.assertTrue(FieldOption.isOfficialAerodrome("LFNL"))
        kotlin.test.assertTrue(FieldOption.isOfficialAerodrome("lfmt"))
        kotlin.test.assertFalse(FieldOption.isOfficialAerodrome(null))
        kotlin.test.assertFalse(FieldOption.isOfficialAerodrome("LF34"))
        kotlin.test.assertFalse(FieldOption.isOfficialAerodrome("ULM-12"))
        kotlin.test.assertFalse(FieldOption.isOfficialAerodrome("LFN"))
    }
}

class OnGroundTest {
    private fun t(s: Long) = kotlinx.datetime.Instant.fromEpochSeconds(1_800_000_000 + s)
    private fun engine() = SafetyEngine({ com.neutronstar.glidercopilot.domain.Terrain.NONE }, { emptyList() }, {})

    @Test fun lentPendant30sEgalSol() {
        val e = engine()
        kotlin.test.assertFalse(e.onGround(20.0, false, t(0)))
        kotlin.test.assertFalse(e.onGround(20.0, false, t(29)))
        kotlin.test.assertTrue(e.onGround(20.0, false, t(30)))
    }

    @Test fun spiraleFaceAuVentPasAuSol() {
        val e = engine()
        kotlin.test.assertFalse(e.onGround(15.0, true, t(0)))
        kotlin.test.assertFalse(e.onGround(15.0, true, t(60)))
        kotlin.test.assertFalse(e.onGround(25.0, false, t(61)))
        kotlin.test.assertFalse(e.onGround(80.0, false, t(80)))
        kotlin.test.assertFalse(e.onGround(25.0, false, t(100)))
    }
}
