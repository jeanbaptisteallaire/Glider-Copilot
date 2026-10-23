package com.neutronstar.glidercopilot.domain.flight

/**
 * Chien de garde des capteurs en vol (revue résilience M10, 23/10/2026) : une perte du GPS ou du vario ne doit jamais
 * passer en silence. Annonce une seule fois à la perte (après un délai qui évite les fausses alertes sur un fix
 * manqué), puis une fois au rétablissement. Rien au sol ni en démo.
 */
class SensorWatchdog(
    private val gpsLostAfterS: Double = 10.0,
    private val varioLostAfterS: Double = 5.0,
) {
    enum class Event(val text: String, val level: Int) {
        GPS_LOST("GPS perdu, marge indisponible", 1),
        GPS_BACK("GPS retrouvé", 0),
        VARIO_LOST("Vario indisponible", 1),
        VARIO_BACK("Vario rétabli", 0),
    }

    private var gpsLost = false
    private var varioLost = false
    private var varioNoneSinceS: Double? = null

    /**
     * [nowS] : temps monotone (s). [gpsAgeS] : âge du dernier fix (null = jamais). [varioOk] : source vario présente.
     * Renvoie les annonces à faire maintenant.
     */
    fun update(nowS: Double, inFlight: Boolean, gpsAgeS: Double?, varioOk: Boolean): List<Event> {
        if (!inFlight) { gpsLost = false; varioLost = false; varioNoneSinceS = null; return emptyList() }
        val out = ArrayList<Event>(2)
        val gpsStale = gpsAgeS == null || !gpsAgeS.isFinite() || gpsAgeS > gpsLostAfterS
        if (gpsStale && !gpsLost) { gpsLost = true; out += Event.GPS_LOST }
        else if (!gpsStale && gpsLost && gpsAgeS!! < 2.0) { gpsLost = false; out += Event.GPS_BACK }
        if (varioOk) {
            varioNoneSinceS = null
            if (varioLost) { varioLost = false; out += Event.VARIO_BACK }
        } else {
            val since = varioNoneSinceS ?: nowS.also { varioNoneSinceS = it }
            if (!varioLost && nowS - since >= varioLostAfterS) { varioLost = true; out += Event.VARIO_LOST }
        }
        return out
    }
}
