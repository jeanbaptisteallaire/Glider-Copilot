package com.neutronstar.glidercopilot

import android.content.Context
import com.neutronstar.glidy.social.ExperienceLevel
import com.neutronstar.glidy.social.PilotProfile
import com.neutronstar.glidy.social.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S16 — profil pilote stocké sur ce téléphone (préférences privées « glidy_pilot_profile »).
 * S18 : la même donnée sera synchronisée avec la table Supabase `profiles` (pseudo unique côté serveur).
 */
class ProfileHost(context: Context) : ProfileStore {
    private val prefs = context.applicationContext.getSharedPreferences("glidy_pilot_profile", Context.MODE_PRIVATE)

    override suspend fun load(): PilotProfile = withContext(Dispatchers.IO) {
        PilotProfile(
            displayName = prefs.getString(K_NAME, "").orEmpty(),
            username = prefs.getString(K_USERNAME, "").orEmpty(),
            bio = prefs.getString(K_BIO, "").orEmpty(),
            club = prefs.getString(K_CLUB, "").orEmpty(),
            experience = prefs.getString(K_LEVEL, null)?.let { runCatching { ExperienceLevel.valueOf(it) }.getOrNull() },
            minutesBeforeApp = prefs.getLong(K_MINUTES, 0L),
        )
    }

    override suspend fun save(profile: PilotProfile) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(K_NAME, profile.displayName)
            .putString(K_USERNAME, profile.username)
            .putString(K_BIO, profile.bio)
            .putString(K_CLUB, profile.club)
            .putString(K_LEVEL, profile.experience?.name)
            .putLong(K_MINUTES, profile.minutesBeforeApp)
            .apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { prefs.edit().clear().apply() }

    private companion object {
        const val K_NAME = "display_name"
        const val K_USERNAME = "username"
        const val K_BIO = "bio"
        const val K_CLUB = "club"
        const val K_LEVEL = "experience"
        const val K_MINUTES = "minutes_before_app"
    }
}
