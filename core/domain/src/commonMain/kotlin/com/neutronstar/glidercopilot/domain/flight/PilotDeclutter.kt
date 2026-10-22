package com.neutronstar.glidercopilot.domain.flight

/** Niveau de la marge de sécurité : la couleur ne sert qu'à alerter (vert normal, orange attention, rouge alarme). */
enum class MarginLevel { UNKNOWN, OK, CAUTION, ALARM }

/**
 * Ce que l'écran Pilotage montre à cet instant (plan « Pilotage épuré », JB 19/10/2026).
 * Mode Essentiel (JB, 19/10/2026) : marge, distance et terrain seulement, ni coupe ni vario. « Dark cockpit » : un capteur n'apparaît que s'il est en
 * défaut, la coupe se déplie seule quand la marge devient faible. Les alertes restent visibles dans tous les modes.
 */
data class PilotDisplay(
    val essential: Boolean,
    val marginLevel: MarginLevel,
    /** Finesse, cap GPS, SÉCU et ALT. */
    val showHeaderDetails: Boolean,
    val showProfile: Boolean,
    /** Coupe affichée par la marge et non par le choix du pilote. */
    val profileForced: Boolean,
    val showVario: Boolean,
    /** Moyennes spirale / pompe / jour et bouton son. */
    val showVarioDetails: Boolean,
    /** Orientation, couches, zoom, vent, échelle vario, DÉMO, réglages. */
    val showMapTools: Boolean,
    val showOgn: Boolean,
    val gpsAlert: Boolean,
    val baroAlert: Boolean,
    val dataAlert: Boolean,
)

object PilotDeclutter {
    /** Sous ce seuil (m), la marge passe à l'orange et la coupe se déplie (JB, 23/10/2026 : 50 m, au lieu de 200). */
    const val CAUTION_M = 50.0

    fun marginLevel(known: Boolean, marginM: Double): MarginLevel = when {
        !known || marginM.isNaN() -> MarginLevel.UNKNOWN
        marginM < 0 -> MarginLevel.ALARM
        marginM < CAUTION_M -> MarginLevel.CAUTION
        else -> MarginLevel.OK
    }

    fun display(
        essential: Boolean,
        profileWanted: Boolean,
        varioWanted: Boolean,
        marginKnown: Boolean,
        marginM: Double,
        gpsOk: Boolean,
        baroOk: Boolean,
        dataOk: Boolean,
        ognConnected: Boolean,
    ): PilotDisplay {
        val level = marginLevel(marginKnown, marginM)
        val low = level == MarginLevel.CAUTION || level == MarginLevel.ALARM
        // Complet : le pilote garde la main (interrupteur « relief » toujours visible ; l'écran le rouvre seul quand la
        // marge passe sous le seuil). Essentiel : ni coupe ni vario ; la marge reste colorée (orange / rouge).
        return PilotDisplay(
            essential = essential,
            marginLevel = level,
            showHeaderDetails = !essential,
            showProfile = !essential && profileWanted,
            profileForced = false,
            showVario = varioWanted && !essential,
            showVarioDetails = !essential,
            showMapTools = !essential,
            showOgn = ognConnected && !essential,
            gpsAlert = !gpsOk,
            baroAlert = !baroOk,
            dataAlert = !dataOk,
        )
    }
}
