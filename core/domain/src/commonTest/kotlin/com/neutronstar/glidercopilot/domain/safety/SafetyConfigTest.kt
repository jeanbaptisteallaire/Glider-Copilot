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
