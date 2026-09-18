package com.neutronstar.glidy.flightarchive

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

/** Port central de l'archive locale. */
interface FlightArchiveRepository {
    suspend fun reconcile(): ReconciliationResult
    suspend fun listFlights(): List<ArchivedFlight>
    suspend fun findFlight(id: FlightId): ArchivedFlight?
    suspend fun removeLocalFlight(id: FlightId): RemoveFlightResult
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

