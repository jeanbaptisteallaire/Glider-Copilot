package com.neutronstar.glidercopilot

import android.content.Context
import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.domain.ClubLocator
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.feature.prevol.ClubSource
import com.neutronstar.glidercopilot.precog.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Annuaire FFVP embarqué (assets/clubs_fr.json) et club du jour : le club choisi à la main,
 * sinon le club géolocalisé le plus proche du téléphone, sinon CVV Montpellier Pic Saint-Loup.
 */
class ClubRepository(context: Context, private val prefs: UserPreferences, private val location: LocationSource) : ClubSource {
    override val clubs: List<Club> by lazy {
        val text = context.assets.open("clubs_fr.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
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
    }

    override val selectedClub: Flow<Club?> = combine(prefs.selectedClubId, location.position) { id, pos ->
        id?.let { chosen -> clubs.firstOrNull { it.id == chosen } }
            ?: pos?.let { ClubLocator.nearest(clubs, it) }
            ?: clubs.firstOrNull { it.id == DEFAULT_CLUB_ID }
    }

    override suspend fun select(clubId: String) = prefs.setSelectedClub(clubId)

    companion object {
        const val DEFAULT_CLUB_ID = "centre-de-vol-a-voile-montpellier-pic-st-loup"
    }
}
