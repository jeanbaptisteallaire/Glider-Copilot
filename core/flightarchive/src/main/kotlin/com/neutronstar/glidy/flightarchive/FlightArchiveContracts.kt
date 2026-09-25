package com.neutronstar.glidy.flightarchive

import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** Identité locale stable, créée avant tout compte utilisateur ou stockage distant. */
@JvmInline
value class FlightId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) { "FlightId must be a UUID" }
    }

    companion object {
        fun new(): FlightId = FlightId(UUID.randomUUID().toString())
    }
}

/** Référence relative à un fichier IGC privé. Aucun chemin absolu ne traverse le domaine. */
data class IgcFileRef(
    val relativePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        require(relativePath.isNotBlank())
        require(fileName.endsWith(".igc", ignoreCase = true))
        require(sizeBytes >= 0)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south in -90.0..90.0 && north in -90.0..90.0 && south <= north)
        require(west in -180.0..180.0 && east in -180.0..180.0)
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}

data class FlightSummary(
    val startedAt: Instant,
    val endedAt: Instant,
    val pointCount: Int,
    val validPointCount: Int,
    val distanceMeters: Long?,
    val minimumAltitudeMeters: Int?,
    val maximumAltitudeMeters: Int?,
    val positiveGainMeters: Int?,
    val bounds: GeoBounds?,
) {
    init {
        require(!endedAt.isBefore(startedAt))
        require(pointCount >= 0)
        require(validPointCount in 0..pointCount)
        require(distanceMeters == null || distanceMeters >= 0)
        require(positiveGainMeters == null || positiveGainMeters >= 0)
    }
}

enum class LocalFileState { AVAILABLE, RECORDING, MISSING, INVALID }

enum class SyncState {
    LOCAL_ONLY,
    QUEUED,
    UPLOADING,
    SYNCED,
    FAILED_RETRYABLE,
    CONFLICT,
}

data class ArchivedFlight(
    val id: FlightId,
    val file: IgcFileRef,
    val summary: FlightSummary?,
    val pilot: String?,
    val gliderType: String?,
    val gliderId: String?,
    val localState: LocalFileState,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val remoteId: String? = null,
    val previewTrack: List<GeoPoint> = emptyList(),
    val altitudeProfileMeters: List<Int> = emptyList(),
)

/** Fichier terminé produit par Pilotage, importé manuellement ou fourni à l'app autonome. */
data class CompletedIgcFile(
    val relativePath: String,
    val fileName: String,
    val observedSizeBytes: Long,
)

/** Port d'entrée. L'implémentation pourra scanner un dossier privé ou écouter un événement de fin de vol. */
interface RecordedFlightSource {
    suspend fun listCompletedFiles(): List<CompletedIgcFile>
}

/**
 * Frontière avec Pilotage. Pilotage remet uniquement un fichier fermé ; l'implémentation en fait
 * une copie privée et atomique avant de l'indexer. Elle ne modifie jamais le fichier d'origine.
 */
interface CompletedFlightGateway {
    suspend fun submitCompletedIgc(fileName: String, source: InputStream): CompletedFlightResult
    suspend fun recoverPending(): PendingFlightRecovery
}

sealed interface CompletedFlightResult {
    data class Imported(val flight: ArchivedFlight) : CompletedFlightResult
    data class Duplicate(val existingFlight: ArchivedFlight) : CompletedFlightResult
    data object TooLarge : CompletedFlightResult
    data class Invalid(val error: IgcParseError) : CompletedFlightResult
    data class Failed(val reason: String) : CompletedFlightResult
}

data class PendingFlightRecovery(
    val imported: Int,
    val duplicates: Int,
    val rejected: Int,
    val abandonedPartials: Int,
) {
    init {
        require(listOf(imported, duplicates, rejected, abandonedPartials).all { it >= 0 })
    }
}

/** Port central de l'archive locale. */
interface FlightArchiveRepository {
    /** Copie la source dans l'archive privée. Le dépôt ne ferme pas le flux fourni. */
    suspend fun importIgc(fileName: String, source: InputStream): ImportIgcResult
    suspend fun reconcile(): ReconciliationResult
    suspend fun listFlights(): List<ArchivedFlight>
    suspend fun findFlight(id: FlightId): ArchivedFlight?
    suspend fun removeLocalFlight(id: FlightId): RemoveFlightResult

    /**
     * Trace complète relue depuis le fichier IGC (GLIDY S11 : rejeu 3D à pleine résolution, au lieu des
     * 512 points d'aperçu de l'index). null si le vol ou son fichier n'est plus disponible.
     */
    suspend fun loadTrack(id: FlightId): List<IgcTrackPoint>? = null
}

sealed interface ImportIgcResult {
    data class Imported(val flight: ArchivedFlight) : ImportIgcResult
    data class Duplicate(val existingFlight: ArchivedFlight) : ImportIgcResult
    data class Invalid(val error: IgcParseError) : ImportIgcResult
    data object TooLarge : ImportIgcResult
    data class Failed(val reason: String) : ImportIgcResult
}

data class ReconciliationResult(
    val discovered: Int,
    val updated: Int,
    val missing: Int,
    val invalid: Int,
) {
    init {
        require(listOf(discovered, updated, missing, invalid).all { it >= 0 })
    }
}

sealed interface RemoveFlightResult {
    data object Removed : RemoveFlightResult
    data object NotFound : RemoveFlightResult
    data class Failed(val reason: String) : RemoveFlightResult
}

/** Port de partage. L'implémentation Android présente le sélecteur sans exposer de chemin absolu. */
interface FlightShareGateway {
    suspend fun share(id: FlightId): ShareFlightResult
}

sealed interface ShareFlightResult {
    data object Presented : ShareFlightResult
    data object NotFound : ShareFlightResult
    data object FileUnavailable : ShareFlightResult
    data class Failed(val reason: String) : ShareFlightResult
}

/** Contrat cloud. La phase 1 utilise uniquement une implémentation inactive. */
interface FlightCloudGateway {
    val enabled: Boolean
    suspend fun enqueueUpload(flight: ArchivedFlight): CloudQueueResult
}

sealed interface CloudQueueResult {
    data object Queued : CloudQueueResult
    data object Disabled : CloudQueueResult
    data class Rejected(val reason: String) : CloudQueueResult
}
