package com.neutronstar.glidercopilot.feature.flight

import com.neutronstar.glidercopilot.domain.flight.AltitudeRef
import com.neutronstar.glidercopilot.domain.flight.FlightSnapshot
import com.neutronstar.glidercopilot.domain.flight.VarioSource
import java.util.Locale

/** Moteur de vol vu par l'écran Pilotage : image instantanée des capteurs et réglages du son. */
data class FlightLive(
    val snapshot: FlightSnapshot? = null,
    /** Rejeu du vol de démonstration (capteurs simulés), signalé à l'écran. */
    val replay: Boolean = false,
    val soundOn: Boolean = false,
    val autoTakeoff: Boolean = true,
) {
    /** Vrai dès qu'une vraie source (baro, GPS, OGN) alimente l'écran : plus aucune valeur de démonstration. */
    val hasData: Boolean
        get() {
            val s = snapshot ?: return false
            return s.source != VarioSource.NONE || s.altitudeM != null || (s.gpsAgeS ?: 99.0) < 10
        }

    val gpsFresh: Boolean
        get() {
            val s = snapshot ?: return false
            return s.gps != null && (s.gpsAgeS ?: 99.0) < 5
        }
    val baroActive: Boolean get() = (snapshot?.baroHz ?: 0.0) > 3
}

/** Actions de l'écran Pilotage vers le moteur de vol. */
interface FlightControls {
    fun setSound(on: Boolean)
    fun startChrono()
    fun stopChrono()
}

fun varioSourceLabel(s: FlightSnapshot?, ognLatencyS: Double? = null): String = when (s?.source) {
    VarioSource.BARO_ACCEL -> "Vario baro+accél."
    VarioSource.BARO -> "Vario baro"
    VarioSource.OGN -> "Vario OGN" + (ognLatencyS?.let { String.format(Locale.FRANCE, " · %.0f s", it) } ?: "")
    VarioSource.NONE, null -> "Vario indisponible"
}

fun altitudeRefLabel(r: AltitudeRef): String = when (r) {
    AltitudeRef.BARO_FIELD -> "baro calé terrain"
    AltitudeRef.BARO_GPS -> "baro calé GPS"
    AltitudeRef.BARO_ISA -> "altitude pression 1013"
    AltitudeRef.GPS -> "GPS"
    AltitudeRef.OGN -> "OGN"
    AltitudeRef.NONE -> "indisponible"
}
