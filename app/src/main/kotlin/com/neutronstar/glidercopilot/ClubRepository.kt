package com.neutronstar.glidercopilot

import android.content.Context
import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.feature.prevol.ClubSource
import com.neutronstar.glidercopilot.precog.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Annuaire FFVP embarqué (assets/clubs_fr.json) et club choisi. Club par défaut : CVV Montpellier Pic Saint-Loup. */
class ClubRepository(context: Context, private val prefs: UserPreferences) : ClubSource {
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

    override val selectedClub: Flow<Club?> = prefs.selectedClubId.map { id ->
        clubs.firstOrNull { it.id == (id ?: DEFAULT_CLUB_ID) }
    }

    override suspend fun select(clubId: String) = prefs.setSelectedClub(clubId)

    companion object {
        const val DEFAULT_CLUB_ID = "centre-de-vol-a-voile-montpellier-pic-st-loup"
    }
}
