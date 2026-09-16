package com.neutronstar.glidercopilot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Préférences de l'utilisateur. Interface volontaire : l'implémentation locale (DataStore) sera
 * doublée d'une implémentation « compte » synchronisée quand le login arrivera, sans toucher aux écrans.
 */
interface UserPreferences {
    val acknowledgedDisclaimer: Flow<Int>
    val selectedClubId: Flow<String?>
    val recentRegistrations: Flow<List<String>>
    suspend fun acknowledgeDisclaimer(version: Int)
    suspend fun setSelectedClub(id: String)
    suspend fun pushRegistration(registration: String)
}

class LocalUserPreferences(private val store: DataStore<Preferences>) : UserPreferences {
    private val kAck = intPreferencesKey("disclaimer_version")
    private val kClub = stringPreferencesKey("club_id")
    private val kRegs = stringPreferencesKey("recent_registrations")

    override val acknowledgedDisclaimer: Flow<Int> = store.data.map { it[kAck] ?: 0 }
    override val selectedClubId: Flow<String?> = store.data.map { it[kClub] }
    override val recentRegistrations: Flow<List<String>> =
        store.data.map { p -> p[kRegs]?.split('|')?.filter { it.isNotBlank() } ?: emptyList() }

    override suspend fun acknowledgeDisclaimer(version: Int) { store.edit { it[kAck] = version } }
    override suspend fun setSelectedClub(id: String) { store.edit { it[kClub] = id } }

    /** Garde les trois dernières immatriculations, la plus récente en tête. */
    override suspend fun pushRegistration(registration: String) {
        val r = registration.trim().uppercase()
        if (r.isEmpty()) return
        store.edit { p ->
            val current = p[kRegs]?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
            p[kRegs] = (listOf(r) + current.filterNot { it == r }).take(3).joinToString("|")
        }
    }
}
