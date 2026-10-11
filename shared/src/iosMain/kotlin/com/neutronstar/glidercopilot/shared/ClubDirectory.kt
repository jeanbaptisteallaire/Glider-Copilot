package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.domain.ClubLocator
import com.neutronstar.glidercopilot.domain.ClubSearch
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.precog.Json
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Annuaire FFVP embarqué (même fichier que l'app Android : app/src/main/assets/clubs_fr.json, copié dans le bundle iOS)
 * et choix du club du jour : club choisi à la main, sinon le plus proche du téléphone, sinon CVV Montpellier Pic Saint-Loup.
 */
class ClubDirectory(jsonPath: String) {
    val clubs: List<Club> = runCatching {
        val text = FileSystem.SYSTEM.read(jsonPath.toPath()) { readUtf8() }
        Json.parse(text)["clubs"]?.arr.orEmpty().mapNotNull { j ->
            val id = j["id"]?.str ?: return@mapNotNull null
            val lat = j["lat"]?.num
            val lon = j["lon"]?.num
            Club(
                id = id,
                name = j["name"]?.str ?: id,
                shortName = j["short"]?.str,
                city = j["city"]?.str ?: "",
                postcode = j["postcode"]?.str ?: "",
                phone = j["phone"]?.str,
                airfieldIcao = j["icao"]?.str,
                airfieldName = j["airfield"]?.str,
                position = if (lat != null && lon != null) LatLon(lat, lon) else null,
                isFederation = (j["federation"] as? Json.Bool)?.value == true,
            )
        }
    }.getOrDefault(emptyList())

    fun search(query: String): List<Club> = ClubSearch.search(clubs.filter { !it.isFederation }, query)

    fun byId(id: String?): Club? = id?.let { i -> clubs.firstOrNull { it.id == i } }

    /** Club du jour : [chosenId], sinon le plus proche de ([lat], [lon]) si la position est connue, sinon le club par défaut. */
    fun current(chosenId: String?, lat: Double, lon: Double, hasPosition: Boolean): Club? =
        byId(chosenId)
            ?: (if (hasPosition) ClubLocator.byDistance(clubs, LatLon(lat, lon)).firstOrNull()?.first else null)
            ?: byId(DEFAULT_CLUB_ID)

    companion object {
        const val DEFAULT_CLUB_ID = "centre-de-vol-a-voile-montpellier-pic-st-loup"
    }
}
