package com.neutronstar.glidercopilot

import com.neutronstar.glidercopilot.domain.checklist.CableBriefInput
import com.neutronstar.glidercopilot.feature.checklist.ChecklistStore
import com.neutronstar.glidercopilot.feature.prevol.GliderSource
import com.neutronstar.glidercopilot.feature.prevol.PairingStatus
import com.neutronstar.glidercopilot.ogn.DdbDevice
import com.neutronstar.glidercopilot.ogn.OgnDeviceDatabase
import com.neutronstar.glidercopilot.ogn.PairingLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Planeur du jour : préférences locales + Device Database OGN pour vérifier l'appairage FLARM. */
class GliderRepository(private val prefs: UserPreferences, val ddb: OgnDeviceDatabase) : GliderSource {
    private val _ownDevices = MutableStateFlow<List<DdbDevice>>(emptyList())
    /** Boîtiers FLARM/OGN de mon planeur appairé (suivi autorisé), pour le filtre APRS et le suivi OGN. */
    val ownDevices: StateFlow<List<DdbDevice>> = _ownDevices.asStateFlow()

    override val pairedRegistration: Flow<String?> = prefs.pairedRegistration
    override val recentRegistrations: Flow<List<String>> = prefs.recentRegistrations
    override val autoTakeoff: Flow<Boolean> = prefs.autoTakeoff

    private val _followDevices = MutableStateFlow<List<DdbDevice>>(emptyList())
    /** Boîtiers du planeur suivi (Suivi & debug), vides si l'option est coupée. */
    val followDevices: StateFlow<List<DdbDevice>> = _followDevices.asStateFlow()
    @Volatile var followActive = false
        private set
    private val _followLine = MutableStateFlow<String?>(null)
    override val followLine: Flow<String?> = _followLine
    override val followEnabled: Flow<Boolean> = prefs.followEnabled
    override val followRegistration: Flow<String?> = prefs.followRegistration
    /** Registre courant du suivi, pour l'étiquette de Pilotage. */
    @Volatile var followRegistrationValue: String? = null
        private set

    fun publishFollowLine(line: String?) { _followLine.value = line }

    override suspend fun setFollowEnabled(on: Boolean) = prefs.setFollowEnabled(on)

    /**
     * Suivi lancé depuis l'onglet Carte (V7.1) : on part de l'adresse radio vue sur la carte, pas d'une
     * immatriculation saisie. Le refus de suivi publié dans la DDB reste respecté — un appareil qui l'a
     * demandé n'est de toute façon jamais dans le trafic affiché.
     * Renvoie faux si la base refuse le suivi de cet appareil.
     */
    suspend fun followAddress(address: String, label: String?): Boolean = withContext(Dispatchers.IO) {
        val addr = address.uppercase()
        val known = runCatching { ddb.cachedDevice(address) }.getOrNull()
        if (known != null && !known.tracked) return@withContext false
        val registration = known?.takeIf { it.identified }?.registration ?: label?.takeIf { it.contains('-') }
        val shown = registration ?: addr
        prefs.setFollowRegistration(shown)
        followRegistrationValue = shown
        _followDevices.value = listOf(
            known ?: DdbDevice("F", addr, null, shown, null, tracked = true, identified = registration != null),
        )
        prefs.setFollowEnabled(true)
        true
    }

    override suspend fun follow(registration: String): PairingStatus {
        prefs.setFollowRegistration(registration)
        return currentFollowStatus(registration)
    }

    override suspend fun currentFollowStatus(registration: String): PairingStatus = withContext(Dispatchers.IO) {
        followRegistrationValue = registration
        when (val r = runCatching { ddb.lookup(registration) }.getOrElse { PairingLookup.Unavailable(it.message ?: "erreur") }) {
            is PairingLookup.Found -> {
                _followDevices.value = r.devices
                val d = r.devices.first()
                PairingStatus.Paired(listOfNotNull((if (d.deviceType == "F") "ID " else "${d.deviceTypeLabel} ") + d.deviceId, d.aircraftModel).joinToString(" · "), source(r.fetchedAt, r.offline))
            }
            is PairingLookup.NotFound -> { _followDevices.value = emptyList(); PairingStatus.NotFound(source(r.fetchedAt, r.offline)) }
            is PairingLookup.NotTracked -> { _followDevices.value = emptyList(); PairingStatus.NotTracked }
            is PairingLookup.Unavailable -> PairingStatus.Unverified
        }
    }

    /** Au démarrage de l'app : suivi & debug relu depuis les préférences, avant même l'ouverture de Prévol. */
    suspend fun restoreFollow(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch { prefs.followEnabled.collect { followActive = it } }
        prefs.followRegistration.first()?.let { currentFollowStatus(it) }
    }

    override suspend fun pair(registration: String): PairingStatus {
        prefs.setPairedRegistration(registration)
        prefs.pushRegistration(registration)
        return currentStatus(registration)
    }

    override suspend fun currentStatus(registration: String): PairingStatus = withContext(Dispatchers.IO) {
        when (val r = runCatching { ddb.lookup(registration) }.getOrElse { PairingLookup.Unavailable(it.message ?: "erreur") }) {
            is PairingLookup.Found -> {
                _ownDevices.value = r.devices
                val d = r.devices.first()
                PairingStatus.Paired(
                    device = listOfNotNull((if (d.deviceType == "F") "ID " else "${d.deviceTypeLabel} ") + d.deviceId, d.aircraftModel).joinToString(" · "),
                    source = source(r.fetchedAt, r.offline),
                )
            }
            is PairingLookup.NotFound -> { _ownDevices.value = emptyList(); PairingStatus.NotFound(source(r.fetchedAt, r.offline)) }
            is PairingLookup.NotTracked -> { _ownDevices.value = emptyList(); PairingStatus.NotTracked }
            is PairingLookup.Unavailable -> PairingStatus.Unverified
        }
    }

    override suspend fun setAutoTakeoff(on: Boolean) = prefs.setAutoTakeoff(on)

    private fun source(at: Instant, offline: Boolean): String =
        (if (offline) "copie locale OGN du " else "base OGN du ") + FMT.format(at)

    private companion object {
        val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.FRANCE).withZone(ZoneId.of("Europe/Paris"))
    }
}

class ChecklistRepository(private val prefs: UserPreferences) : ChecklistStore {
    override val checked: Flow<Set<String>> = prefs.checklistChecked
    override val brief: Flow<CableBriefInput> = prefs.checklistBrief
    override suspend fun setChecked(id: String, checked: Boolean) = prefs.setChecklistItem(id, checked)
    override suspend fun clearChecks() = prefs.clearChecklist()
    override suspend fun saveBrief(brief: CableBriefInput) = prefs.setChecklistBrief(brief)
}
