package com.neutronstar.glidercopilot

import com.neutronstar.glidercopilot.domain.checklist.CableBriefInput
import com.neutronstar.glidercopilot.feature.checklist.ChecklistStore
import com.neutronstar.glidercopilot.feature.prevol.GliderSource
import com.neutronstar.glidercopilot.feature.prevol.PairingStatus
import com.neutronstar.glidercopilot.ogn.OgnDeviceDatabase
import com.neutronstar.glidercopilot.ogn.PairingLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Planeur du jour : préférences locales + Device Database OGN pour vérifier l'appairage FLARM. */
class GliderRepository(private val prefs: UserPreferences, private val ddb: OgnDeviceDatabase) : GliderSource {
    override val pairedRegistration: Flow<String?> = prefs.pairedRegistration
    override val recentRegistrations: Flow<List<String>> = prefs.recentRegistrations
    override val autoTakeoff: Flow<Boolean> = prefs.autoTakeoff

    override suspend fun pair(registration: String): PairingStatus {
        prefs.setPairedRegistration(registration)
        prefs.pushRegistration(registration)
        return currentStatus(registration)
    }

    override suspend fun currentStatus(registration: String): PairingStatus = withContext(Dispatchers.IO) {
        when (val r = runCatching { ddb.lookup(registration) }.getOrElse { PairingLookup.Unavailable(it.message ?: "erreur") }) {
            is PairingLookup.Found -> {
                val d = r.devices.first()
                PairingStatus.Paired(
                    device = listOfNotNull("${d.deviceTypeLabel} ${d.deviceId}", d.aircraftModel).joinToString(" · "),
                    source = source(r.fetchedAt, r.offline),
                )
            }
            is PairingLookup.NotFound -> PairingStatus.NotFound(source(r.fetchedAt, r.offline))
            is PairingLookup.NotTracked -> PairingStatus.NotTracked
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
