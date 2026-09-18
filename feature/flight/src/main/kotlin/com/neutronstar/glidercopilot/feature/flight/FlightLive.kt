package com.neutronstar.glidercopilot.feature.flight

import com.neutronstar.glidercopilot.domain.flight.AltitudeRef
import com.neutronstar.glidercopilot.domain.flight.FlightSnapshot
import com.neutronstar.glidercopilot.domain.flight.VarioSource
import java.util.Locale

/** Terrain proposé dans le choix du terrain de référence : toujours disponible, même au sol sans position. */
data class FieldChoice(val id: String, val code: String, val name: String, val distanceKm: Double?, val marginM: Double?)

/** Origine du planeur affiché : téléphone, rejeu du vol de démonstration, mode démo simulé, planeur suivi par OGN. */
enum class OwnshipMode { PHONE, REPLAY, DEMO, FOLLOW }

/** Moteur de vol vu par l'écran Pilotage : image instantanée des capteurs, sécurité et réglages. */
data class FlightLive(
    val snapshot: FlightSnapshot? = null,
    /** Rejeu du vol de démonstration (capteurs simulés), signalé à l'écran. */
    val replay: Boolean = false,
    val soundOn: Boolean = false,
    val autoTakeoff: Boolean = true,
    val mode: OwnshipMode = OwnshipMode.PHONE,
    val demo: Boolean = false,
    val safety: com.neutronstar.glidercopilot.domain.safety.SafetyState? = null,
    val finesse: Int = 20,
    /** Immatriculation du planeur suivi (Suivi & debug). */
    val followLabel: String? = null,
    /** Relief du pack chargé : coupe et marge sur le terrain réel. */
    val reliefLoaded: Boolean = false,
    /** Terrains proposés au choix (les plus proches du pack), avec marge quand la sécurité est calculée. */
    val fieldChoices: List<FieldChoice> = emptyList(),
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
    fun setFinesse(value: Int)
    fun setDemo(on: Boolean)
    /** Terrain choisi à la main, null pour revenir au choix automatique. */
    fun selectField(id: String?)
}

fun varioSourceLabel(s: FlightSnapshot?, ognLatencyS: Double? = null): String = when (s?.source) {
    VarioSource.BARO_ACCEL -> "Vario baro+accél."
    VarioSource.BARO -> "Vario baro"
    VarioSource.OGN -> "Vario OGN" + (ognLatencyS?.let { String.format(Locale.FRANCE, " · %.0f s", it) } ?: "")
    VarioSource.NONE, null -> "Vario indisponible"
}

fun altitudeRefLabel(r: AltitudeRef): String = when (r) {
    AltitudeRef.BARO_FIELD -> "calé terrain"
    AltitudeRef.BARO_GPS -> "calé GPS"
    AltitudeRef.BARO_ISA -> "alt. pression"
    AltitudeRef.GPS -> "GPS"
    AltitudeRef.OGN -> "OGN"
    AltitudeRef.NONE -> "indisponible"
}
