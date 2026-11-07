package com.neutronstar.glidercopilot.domain.aero

/**
 * Orientation des pistes des terrains connus, pour les dessiner à la bonne inclinaison sur la carte.
 * openAIP ne donne pas l'orientation dans le pack : la table est tenue à la main, terrain par terrain.
 * (Les pistes sont à double sens : 120° suffit à décrire 12/30.)
 */
object Runways {
    data class Runway(val icao: String, val headingDeg: Double, val lengthM: Int, val hard: Boolean)

    val KNOWN = listOf(
        Runway("LFMT", 120.0, 2600, hard = true),   // Montpellier Méditerranée · 12L/30R
        Runway("LFNL", 120.0, 900, hard = false),   // Saint-Martin-de-Londres · 12/30
        Runway("LFMS", 10.0, 1000, hard = true),    // Alès-Cévennes · 01/19
    )

    fun forIcao(icao: String?): Runway? = icao?.uppercase()?.let { code -> KNOWN.firstOrNull { it.icao == code } }
}
