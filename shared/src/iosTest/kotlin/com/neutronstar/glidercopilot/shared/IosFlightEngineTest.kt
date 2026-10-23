package com.neutronstar.glidercopilot.shared

import platform.Foundation.NSDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.timeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Moteur de vol iOS (revue résilience : « aucun test pour IosFlightEngine ») : pas d'exception, tirets plutôt que des valeurs figées. */
class IosFlightEngineTest {
    private fun engine() = IosFlightEngine(NSTemporaryDirectory() + "igc-test-${NSDate().timeIntervalSince1970}", "test")
    private val now get() = (NSDate().timeIntervalSince1970 * 1000).toLong()

    @Test fun arreteSansCapteursToutEnTirets() {
        val ui = engine().ui()
        assertEquals("—", ui.marginText)
        assertFalse(ui.marginKnown)
        assertFalse(ui.gpsOk)
        assertEquals("—", ui.varioText)
    }

    @Test fun demarreSansCapteursPasDeMarge() {
        val e = engine(); e.start()
        val ui = e.ui()
        assertFalse(ui.marginKnown); assertFalse(ui.gpsOk); assertFalse(ui.recording)
        e.stop()
    }

    @Test fun positionsSansAltitudeCaleePasDeMarge() {
        val e = engine(); e.start()
        // GPS seul (pas de baro) : altitude GPS ; position fraîche
        repeat(3) { e.onLocation(43.8, 3.78, 1500.0, 1550.0, true, 25.0, 90.0, 5.0, now) }
        val ui = e.ui()
        assertTrue(ui.gpsOk)
        assertFalse(ui.marginText.contains("NaN"))
        e.stop()
    }

    @Test fun valeursAberrantesSansException() {
        val e = engine(); e.start()
        e.onLocation(Double.NaN, Double.NaN, Double.NaN, Double.NaN, true, Double.NaN, Double.NaN, Double.NaN, now)
        e.onPressure(Double.NaN, 1.0)
        e.onMotion(Double.NaN, 0.0, 0.0, 0.0, 0.0, -1.0, 1.0)
        val ui = e.ui()   // ne doit jamais lever (Swift planterait)
        assertFalse(ui.altText.contains("NaN"))
        e.stop()
    }

    @Test fun stopSansEnregistrementSansException() {
        val e = engine(); e.start()
        e.stopChrono()
        e.resumeInFlight()   // sans vitesse : rien ne démarre
        assertFalse(e.ui().recording)
        e.stop()
    }

    @Test fun traceVersionSuitLeDernierPoint() {
        val e = engine(); e.start()
        assertEquals(0L, e.ui().traceVersion)
        e.stop()
    }
}
