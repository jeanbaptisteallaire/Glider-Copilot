package com.neutronstar.glidy.flightarchive.data

import com.neutronstar.glidy.flightarchive.CompletedFlightGateway
import com.neutronstar.glidy.flightarchive.CompletedFlightResult
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.PendingFlightRecovery
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Boîte de dépôt privée entre la fin d'enregistrement Pilotage et l'archive Mes vols. */
class AtomicCompletedFlightGateway(
    private val repository: FlightArchiveRepository,
    private val inboxDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CompletedFlightGateway {
    private val mutex = Mutex()

    override suspend fun submitCompletedIgc(
        fileName: String,
        source: InputStream,
    ): CompletedFlightResult = mutex.withLock {
        withContext(ioDispatcher) {
            inboxDirectory.mkdirs()
            val stem = UUID.randomUUID().toString()
            val displayName = sanitizeDisplayName(fileName)
            val completed = File(inboxDirectory, "$stem--$displayName")
            val partial = File(inboxDirectory, "${completed.name}.part")
            try {
                val accepted = copyBounded(source, partial)
                if (!accepted) {
                    partial.delete()
                    return@withContext CompletedFlightResult.TooLarge
                }
                if (!partial.renameTo(completed)) {
                    partial.copyTo(completed, overwrite = false)
                    partial.delete()
                }
                importAndConsume(completed, displayName).toCompletedResult()
            } catch (error: Exception) {
                partial.delete()
                CompletedFlightResult.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    override suspend fun recoverPending(): PendingFlightRecovery = mutex.withLock {
        withContext(ioDispatcher) {
            inboxDirectory.mkdirs()
            val abandoned = inboxDirectory.listFiles { file ->
                file.isFile && file.name.endsWith(".igc.part", ignoreCase = true)
            }.orEmpty().count { it.delete() }
            var imported = 0
            var duplicates = 0
            var rejected = 0
            inboxDirectory.listFiles { file ->
                file.isFile && file.extension.equals("igc", ignoreCase = true)
            }.orEmpty().sortedBy(File::getName).forEach { file ->
                val displayName = file.name.substringAfter("--", file.name)
                when (importAndConsume(file, displayName)) {
                    is ImportIgcResult.Imported -> imported += 1
                    is ImportIgcResult.Duplicate -> duplicates += 1
                    else -> rejected += 1
                }
            }
            PendingFlightRecovery(imported, duplicates, rejected, abandoned)
        }
    }

    private suspend fun importAndConsume(file: File, displayName: String): ImportIgcResult {
        val result = file.inputStream().buffered().use { repository.importIgc(displayName, it) }
        when (result) {
            is ImportIgcResult.Imported,
            is ImportIgcResult.Duplicate,
            ImportIgcResult.TooLarge,
            is ImportIgcResult.Invalid -> file.delete()
            is ImportIgcResult.Failed -> Unit // Conservé pour une récupération au prochain démarrage.
        }
        return result
    }

    private fun copyBounded(source: InputStream, target: File): Boolean {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        target.outputStream().buffered().use { output ->
            while (true) {
                val count = source.read(buffer)
                if (count < 0) return true
                total += count
                if (total > MAX_IGC_BYTES) return false
                output.write(buffer, 0, count)
            }
        }
    }

    private fun sanitizeDisplayName(name: String): String {
        val candidate = name.substringAfterLast('/').substringAfterLast('\\').trim()
        val withExtension = if (candidate.endsWith(".igc", ignoreCase = true)) candidate else "$candidate.igc"
        return withExtension.take(180).ifBlank { "vol-pilotage.igc" }
    }

    private fun ImportIgcResult.toCompletedResult(): CompletedFlightResult = when (this) {
        is ImportIgcResult.Imported -> CompletedFlightResult.Imported(flight)
        is ImportIgcResult.Duplicate -> CompletedFlightResult.Duplicate(existingFlight)
        is ImportIgcResult.Invalid -> CompletedFlightResult.Invalid(error)
        ImportIgcResult.TooLarge -> CompletedFlightResult.TooLarge
        is ImportIgcResult.Failed -> CompletedFlightResult.Failed(reason)
    }

    private companion object {
        const val MAX_IGC_BYTES = 64L * 1024L * 1024L
    }
}
