package com.neutronstar.glidy.flightarchive.data

import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightSummary
import com.neutronstar.glidy.flightarchive.GeoBounds
import com.neutronstar.glidy.flightarchive.GeoPoint
import com.neutronstar.glidy.flightarchive.IgcFileRef
import com.neutronstar.glidy.flightarchive.IgcParseResult
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.flightarchive.ParsedIgcFlight
import com.neutronstar.glidy.flightarchive.ReconciliationResult
import com.neutronstar.glidy.flightarchive.RemoveFlightResult
import com.neutronstar.glidy.flightarchive.StreamingIgcFlightParser
import com.neutronstar.glidy.flightarchive.SyncState
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomFlightArchiveRepository(
    private val dao: FlightDao,
    private val archiveDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : FlightArchiveRepository {
    private val parser = StreamingIgcFlightParser()
    private val mutex = Mutex()

    override suspend fun importIgc(fileName: String, source: InputStream): ImportIgcResult =
        mutex.withLock {
            withContext(ioDispatcher) {
                archiveDirectory.mkdirs()
                val temporary = File.createTempFile("import-", ".igc.part", archiveDirectory)
                val digest = MessageDigest.getInstance("SHA-256")

                try {
                    val size = copyAndHash(source, temporary, digest)
                        ?: return@withContext ImportIgcResult.TooLarge.also { temporary.delete() }
                    val sha256 = digest.digest().toHex()
                    dao.findBySha256(sha256)?.let { existing ->
                        temporary.delete()
                        return@withContext ImportIgcResult.Duplicate(existing.toDomain())
                    }

                    val parsed = temporary.bufferedReader(Charsets.ISO_8859_1).use(parser::parse)
                    if (parsed is IgcParseResult.Failure) {
                        temporary.delete()
                        return@withContext ImportIgcResult.Invalid(parsed.error)
                    }

                    val flight = (parsed as IgcParseResult.Success).flight
                    val relativePath = "$sha256.igc"
                    val destination = File(archiveDirectory, relativePath)
                    if (!temporary.renameTo(destination)) {
                        temporary.copyTo(destination, overwrite = false)
                        temporary.delete()
                    }

                    val timestamp = now()
                    val entity = flight.toEntity(
                        id = stableId(sha256),
                        relativePath = relativePath,
                        fileName = sanitizeDisplayName(fileName),
                        sizeBytes = size,
                        sha256 = sha256,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    )
                    dao.upsert(entity)
                    ImportIgcResult.Imported(entity.toDomain())
                } catch (error: Exception) {
                    temporary.delete()
                    ImportIgcResult.Failed(error.message ?: error::class.java.simpleName)
                }
            }
        }

    override suspend fun reconcile(): ReconciliationResult = mutex.withLock {
        withContext(ioDispatcher) {
            archiveDirectory.mkdirs()
            val files = archiveDirectory.listFiles { file ->
                file.isFile && file.extension.equals("igc", ignoreCase = true)
            }.orEmpty().associateBy(File::getName)
            val existing = dao.listAll()
            var discovered = 0
            var updated = 0
            var missing = 0
            var invalid = 0

            existing.forEach { entity ->
                val file = files[entity.relativePath]
                if (file == null && entity.localState != LocalFileState.MISSING.name) {
                    dao.upsert(entity.copy(localState = LocalFileState.MISSING.name, updatedAtEpochMillis = now()))
                    missing += 1
                } else if (file != null && entity.localState == LocalFileState.MISSING.name) {
                    val indexed = indexExistingFile(file, entity.fileName, entity.createdAtEpochMillis)
                    if (indexed != null) {
                        dao.upsert(indexed)
                        updated += 1
                    } else {
                        dao.upsert(entity.copy(localState = LocalFileState.INVALID.name, updatedAtEpochMillis = now()))
                        invalid += 1
                    }
                }
            }

            val knownPaths = existing.mapTo(mutableSetOf()) { it.relativePath }
            files.values.filterNot { it.name in knownPaths }.forEach { file ->
                val indexed = indexExistingFile(file, file.name, now())
                if (indexed == null) {
                    val sha256 = sha256(file)
                    dao.upsert(invalidEntity(file, sha256))
                    invalid += 1
                } else {
                    dao.upsert(indexed)
                    discovered += 1
                }
            }

            ReconciliationResult(discovered, updated, missing, invalid)
        }
    }

    override suspend fun listFlights(): List<ArchivedFlight> = withContext(ioDispatcher) {
        dao.listAll().map { it.toDomain() }
    }

    override suspend fun findFlight(id: FlightId): ArchivedFlight? = withContext(ioDispatcher) {
        dao.findById(id.value)?.toDomain()
    }

    override suspend fun removeLocalFlight(id: FlightId): RemoveFlightResult = mutex.withLock {
        withContext(ioDispatcher) {
            val entity = dao.findById(id.value) ?: return@withContext RemoveFlightResult.NotFound
            val file = File(archiveDirectory, entity.relativePath)
            if (file.exists() && !file.delete()) {
                return@withContext RemoveFlightResult.Failed("Impossible de supprimer le fichier IGC")
            }
            dao.deleteById(id.value)
            RemoveFlightResult.Removed
        }
    }

    private suspend fun indexExistingFile(file: File, displayName: String, createdAt: Long): FlightEntity? {
        val sha256 = sha256(file)
        val parsed = file.bufferedReader(Charsets.ISO_8859_1).use(parser::parse)
        if (parsed !is IgcParseResult.Success) return null
        return parsed.flight.toEntity(
            id = stableId(sha256),
            relativePath = file.name,
            fileName = displayName,
            sizeBytes = file.length(),
            sha256 = sha256,
            createdAt = createdAt,
            updatedAt = now(),
        )
    }

    private fun invalidEntity(file: File, sha256: String): FlightEntity {
        val timestamp = now()
        return FlightEntity(
            id = stableId(sha256), relativePath = file.name, fileName = file.name,
            sizeBytes = file.length(), sha256 = sha256,
            startedAtEpochMillis = null, endedAtEpochMillis = null,
            pointCount = 0, validPointCount = 0, distanceMeters = null,
            minimumAltitudeMeters = null, maximumAltitudeMeters = null, positiveGainMeters = null,
            boundsSouth = null, boundsWest = null, boundsNorth = null, boundsEast = null,
            pilot = null, gliderType = null, gliderId = null,
            localState = LocalFileState.INVALID.name, syncState = SyncState.LOCAL_ONLY.name,
            remoteId = null, previewTrack = "", altitudeProfile = "",
            createdAtEpochMillis = timestamp, updatedAtEpochMillis = timestamp,
        )
    }

    private fun copyAndHash(source: InputStream, target: File, digest: MessageDigest): Long? {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        target.outputStream().buffered().use { output ->
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_IGC_BYTES) return null
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
            }
        }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ParsedIgcFlight.toEntity(
        id: String,
        relativePath: String,
        fileName: String,
        sizeBytes: Long,
        sha256: String,
        createdAt: Long,
        updatedAt: Long,
    ): FlightEntity {
        val previewPoints = points.sampled(MAX_PREVIEW_POINTS)
        val bounds = summary.bounds
        return FlightEntity(
            id = id, relativePath = relativePath, fileName = fileName, sizeBytes = sizeBytes, sha256 = sha256,
            startedAtEpochMillis = summary.startedAt.toEpochMilli(),
            endedAtEpochMillis = summary.endedAt.toEpochMilli(),
            pointCount = summary.pointCount, validPointCount = summary.validPointCount,
            distanceMeters = summary.distanceMeters,
            minimumAltitudeMeters = summary.minimumAltitudeMeters,
            maximumAltitudeMeters = summary.maximumAltitudeMeters,
            positiveGainMeters = summary.positiveGainMeters,
            boundsSouth = bounds?.south, boundsWest = bounds?.west,
            boundsNorth = bounds?.north, boundsEast = bounds?.east,
            pilot = metadata.pilot, gliderType = metadata.gliderType, gliderId = metadata.gliderId,
            localState = LocalFileState.AVAILABLE.name, syncState = SyncState.LOCAL_ONLY.name,
            remoteId = null,
            previewTrack = previewPoints.joinToString(";") { "${it.latitude},${it.longitude}" },
            altitudeProfile = previewPoints.mapNotNull { it.gpsAltitudeMeters ?: it.pressureAltitudeMeters }
                .joinToString(","),
            createdAtEpochMillis = createdAt, updatedAtEpochMillis = updatedAt,
        )
    }

    private fun FlightEntity.toDomain(): ArchivedFlight {
        val summary = if (startedAtEpochMillis != null && endedAtEpochMillis != null) {
            FlightSummary(
                startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
                endedAt = Instant.ofEpochMilli(endedAtEpochMillis),
                pointCount = pointCount,
                validPointCount = validPointCount,
                distanceMeters = distanceMeters,
                minimumAltitudeMeters = minimumAltitudeMeters,
                maximumAltitudeMeters = maximumAltitudeMeters,
                positiveGainMeters = positiveGainMeters,
                bounds = if (listOf(boundsSouth, boundsWest, boundsNorth, boundsEast).all { it != null }) {
                    GeoBounds(boundsSouth!!, boundsWest!!, boundsNorth!!, boundsEast!!)
                } else null,
            )
        } else null

        return ArchivedFlight(
            id = FlightId(id),
            file = IgcFileRef(relativePath, fileName, sizeBytes, sha256),
            summary = summary,
            pilot = pilot,
            gliderType = gliderType,
            gliderId = gliderId,
            localState = LocalFileState.valueOf(localState),
            syncState = SyncState.valueOf(syncState),
            remoteId = remoteId,
            previewTrack = previewTrack.split(';').mapNotNull { encoded ->
                val values = encoded.split(',')
                val latitude = values.getOrNull(0)?.toDoubleOrNull()
                val longitude = values.getOrNull(1)?.toDoubleOrNull()
                if (latitude == null || longitude == null) null else GeoPoint(latitude, longitude)
            },
            altitudeProfileMeters = altitudeProfile.split(',').mapNotNull(String::toIntOrNull),
        )
    }

    private fun sanitizeDisplayName(name: String): String {
        val candidate = name.substringAfterLast('/').substringAfterLast('\\').trim()
        val withExtension = if (candidate.endsWith(".igc", ignoreCase = true)) candidate else "$candidate.igc"
        return withExtension.take(MAX_DISPLAY_NAME_LENGTH).ifBlank { "vol.igc" }
    }

    private fun stableId(sha256: String): String =
        UUID.nameUUIDFromBytes("glidy-igc:$sha256".toByteArray()).toString()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun <T> List<T>.sampled(maximum: Int): List<T> {
        if (size <= maximum) return this
        val step = (size - 1).toDouble() / (maximum - 1)
        return List(maximum) { index -> this[(index * step).toInt().coerceAtMost(lastIndex)] }
    }

    private companion object {
        const val MAX_IGC_BYTES = 64L * 1024L * 1024L
        const val MAX_PREVIEW_POINTS = 512
        const val MAX_DISPLAY_NAME_LENGTH = 180
    }
}
