package com.neutronstar.glidy.flightcloud

import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.flightarchive.SyncState
import java.time.Duration

data class SyncReport(
    val uploaded: Int = 0,
    val restored: Int = 0,
    val failed: Int = 0,
    val alreadySynced: Int = 0,
    val message: String? = null,
)

/**
 * Sauvegarde du carnet (S12). Règles :
 * - le carnet local reste la référence ; le cloud n'est qu'une copie (fichier IGC d'origine + métadonnées) ;
 * - jamais pendant un vol : l'hôte n'appelle [sync] que si aucun vol n'est enregistré ;
 * - idempotent : un vol déjà envoyé (SYNCED) n'est pas renvoyé ; côté serveur, la clé (pilote, empreinte)
 *   empêche les doublons ; les vols présents en ligne mais absents du téléphone sont restaurés.
 * [blocking] exécute un appel réseau hors du fil principal (Dispatchers.IO côté Android).
 */
class FlightSyncService(
    private val client: SupabaseClient,
    private val repository: FlightArchiveRepository,
    private val blocking: suspend (() -> Unit) -> Unit = { it() },
) {
    suspend fun sync(restore: Boolean = true): SyncReport {
        if (!client.isConfigured) return SyncReport(message = "Sauvegarde cloud non configurée")
        if (client.session == null) return SyncReport(message = "Non connecté")
        var uploaded = 0; var failed = 0; var already = 0; var restored = 0
        val local = repository.listFlights()
        for (flight in local) {
            if (flight.localState != LocalFileState.AVAILABLE || flight.isExample()) continue
            if (flight.syncState == SyncState.SYNCED) { already++; continue }
            val bytes = repository.readIgc(flight.id)
            if (bytes == null) { failed++; continue }
            repository.updateSyncState(flight.id, SyncState.UPLOADING, flight.remoteId)
            var result: CloudResult<CloudFlightRecord> = CloudResult.Failed("non exécuté")
            blocking { result = client.uploadFlight(flight.toRecord(), bytes) }
            when (val r = result) {
                is CloudResult.Ok -> { repository.updateSyncState(flight.id, SyncState.SYNCED, r.value.storagePath); uploaded++ }
                is CloudResult.Failed -> { repository.updateSyncState(flight.id, if (r.retryable) SyncState.FAILED_RETRYABLE else SyncState.CONFLICT, flight.remoteId); failed++ }
                else -> { repository.updateSyncState(flight.id, SyncState.QUEUED, flight.remoteId); return SyncReport(uploaded, 0, failed, already, "Connexion expirée : reconnectez-vous") }
            }
        }
        if (restore) {
            var listed: CloudResult<List<CloudFlightRecord>> = CloudResult.Failed("non exécuté")
            blocking { listed = client.listFlights() }
            val remote = (listed as? CloudResult.Ok)?.value.orEmpty()
            val known = repository.listFlights().map { it.file.sha256 }.toSet()
            for (record in remote.filter { it.sha256 !in known && it.storagePath.isNotBlank() }) {
                var bytes: CloudResult<ByteArray> = CloudResult.Failed("non exécuté")
                blocking { bytes = client.downloadIgc(record) }
                val data = (bytes as? CloudResult.Ok)?.value
                if (data == null) { failed++; continue }
                when (val imported = repository.importIgc(record.fileName, data.inputStream())) {
                    is ImportIgcResult.Imported -> { repository.updateSyncState(imported.flight.id, SyncState.SYNCED, record.storagePath); restored++ }
                    is ImportIgcResult.Duplicate -> already++
                    else -> failed++
                }
            }
        }
        val msg = when {
            failed > 0 -> "$failed vol(s) non sauvegardé(s) — nouvel essai à la prochaine ouverture"
            uploaded + restored == 0 -> "Carnet à jour en ligne"
            else -> listOfNotNull(
                uploaded.takeIf { it > 0 }?.let { "$it vol(s) sauvegardé(s)" },
                restored.takeIf { it > 0 }?.let { "$it vol(s) restauré(s)" },
            ).joinToString(" · ")
        }
        return SyncReport(uploaded, restored, failed, already, msg)
    }

    private fun ArchivedFlight.isExample() = file.fileName.startsWith("exemple-", ignoreCase = true)

    private fun ArchivedFlight.toRecord() = CloudFlightRecord(
        id = id.value,
        sha256 = file.sha256,
        fileName = file.fileName,
        sizeBytes = file.sizeBytes,
        storagePath = "",
        startedAt = summary?.startedAt?.toString(),
        endedAt = summary?.endedAt?.toString(),
        durationSeconds = summary?.let { Duration.between(it.startedAt, it.endedAt).seconds },
        distanceMeters = summary?.distanceMeters,
        altitudeMinMeters = summary?.minimumAltitudeMeters,
        altitudeMaxMeters = summary?.maximumAltitudeMeters,
        gainMeters = summary?.positiveGainMeters,
        pilot = pilot,
        gliderType = gliderType,
        gliderId = gliderId,
    )
}
