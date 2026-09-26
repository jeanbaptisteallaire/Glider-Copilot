package com.neutronstar.glidercopilot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.neutronstar.glidercopilot.domain.checklist.CableBriefInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Préférences de l'utilisateur. Interface volontaire : l'implémentation locale (DataStore) sera
 * doublée d'une implémentation « compte » synchronisée quand le login arrivera, sans toucher aux écrans.
 */
interface UserPreferences {
    val acknowledgedDisclaimer: Flow<Int>
    /** Club choisi à la main ; null tant que l'app choisit le club le plus proche. */
    val selectedClubId: Flow<String?>
    val recentRegistrations: Flow<List<String>>
    val pairedRegistration: Flow<String?>
    val autoTakeoff: Flow<Boolean>
    val checklistChecked: Flow<Set<String>>
    val checklistBrief: Flow<CableBriefInput>
    val locationAsked: Flow<Boolean>
    val varioSound: Flow<Boolean>
    val voiceAnnouncements: Flow<Boolean>
    val followEnabled: Flow<Boolean>
    val followRegistration: Flow<String?>
    /** Mode clair (V7.2) : s'applique à Prévol, Check-lists et Carte. Pilotage reste noir, toujours. */
    val lightMode: Flow<Boolean>
    suspend fun acknowledgeDisclaimer(version: Int)
    suspend fun setSelectedClub(id: String)
    suspend fun pushRegistration(registration: String)
    suspend fun setPairedRegistration(registration: String)
    suspend fun setAutoTakeoff(on: Boolean)
    suspend fun setChecklistItem(id: String, checked: Boolean)
    suspend fun clearChecklist()
    suspend fun setChecklistBrief(brief: CableBriefInput)
    suspend fun setLocationAsked()
    suspend fun setVarioSound(on: Boolean)
    suspend fun setVoiceAnnouncements(on: Boolean)
    suspend fun setFollowEnabled(on: Boolean)
    suspend fun setFollowRegistration(registration: String)
    suspend fun setLightMode(on: Boolean)
}

class LocalUserPreferences(private val store: DataStore<Preferences>) : UserPreferences {
    private val kAck = intPreferencesKey("disclaimer_version")
    private val kClub = stringPreferencesKey("club_id")
    private val kRegs = stringPreferencesKey("recent_registrations")
    private val kPaired = stringPreferencesKey("paired_registration")
    private val kAutoTakeoff = booleanPreferencesKey("auto_takeoff")
    private val kChecked = stringSetPreferencesKey("checklist_checked")
    private val kQfu = stringPreferencesKey("brief_qfu")
    private val kTurn = stringPreferencesKey("brief_turn")
    private val kAhead = stringPreferencesKey("brief_ahead")
    private val kField = stringPreferencesKey("brief_field")
    private val kThreat = stringPreferencesKey("brief_threat")
    private val kLocationAsked = booleanPreferencesKey("location_asked")
    private val kSound = booleanPreferencesKey("vario_sound")
    private val kVoice = booleanPreferencesKey("voice_announcements")
    private val kFollow = booleanPreferencesKey("follow_enabled")
    private val kFollowReg = stringPreferencesKey("follow_registration")
    // S15 : thème clair « social » par défaut pour tous (nouvelle clé : l'ancien choix V7.2 n'est pas repris)
    private val kLightMode = booleanPreferencesKey("light_mode_social")

    override val acknowledgedDisclaimer: Flow<Int> = store.data.map { it[kAck] ?: 0 }
    override val selectedClubId: Flow<String?> = store.data.map { it[kClub] }
    override val recentRegistrations: Flow<List<String>> =
        store.data.map { p -> p[kRegs]?.split('|')?.filter { it.isNotBlank() } ?: emptyList() }
    override val pairedRegistration: Flow<String?> = store.data.map { it[kPaired] }
    override val autoTakeoff: Flow<Boolean> = store.data.map { it[kAutoTakeoff] ?: true }
    override val checklistChecked: Flow<Set<String>> = store.data.map { it[kChecked] ?: emptySet() }
    override val checklistBrief: Flow<CableBriefInput> = store.data.map { p ->
        val d = CableBriefInput()
        CableBriefInput(p[kQfu] ?: d.qfu, p[kTurn] ?: d.turn, p[kAhead] ?: d.ahead, p[kField] ?: d.field, p[kThreat] ?: d.threat)
    }
    override val locationAsked: Flow<Boolean> = store.data.map { it[kLocationAsked] ?: false }
    override val varioSound: Flow<Boolean> = store.data.map { it[kSound] ?: false }
    override val voiceAnnouncements: Flow<Boolean> = store.data.map { it[kVoice] ?: true }
    override val followEnabled: Flow<Boolean> = store.data.map { it[kFollow] ?: false }
    override val followRegistration: Flow<String?> = store.data.map { it[kFollowReg] }
    override val lightMode: Flow<Boolean> = store.data.map { it[kLightMode] ?: true }

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

    override suspend fun setPairedRegistration(registration: String) { store.edit { it[kPaired] = registration } }
    override suspend fun setAutoTakeoff(on: Boolean) { store.edit { it[kAutoTakeoff] = on } }

    override suspend fun setChecklistItem(id: String, checked: Boolean) {
        store.edit { p ->
            val cur = p[kChecked] ?: emptySet()
            p[kChecked] = if (checked) cur + id else cur - id
        }
    }

    override suspend fun clearChecklist() { store.edit { it[kChecked] = emptySet() } }

    override suspend fun setChecklistBrief(brief: CableBriefInput) {
        store.edit { p ->
            p[kQfu] = brief.qfu; p[kTurn] = brief.turn; p[kAhead] = brief.ahead; p[kField] = brief.field; p[kThreat] = brief.threat
        }
    }

    override suspend fun setLocationAsked() { store.edit { it[kLocationAsked] = true } }
    override suspend fun setVarioSound(on: Boolean) { store.edit { it[kSound] = on } }
    override suspend fun setVoiceAnnouncements(on: Boolean) { store.edit { it[kVoice] = on } }
    override suspend fun setFollowEnabled(on: Boolean) { store.edit { it[kFollow] = on } }
    override suspend fun setFollowRegistration(registration: String) { store.edit { it[kFollowReg] = registration } }
    override suspend fun setLightMode(on: Boolean) { store.edit { it[kLightMode] = on } }
}
