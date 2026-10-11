package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.IgcFileRef
import com.neutronstar.glidy.flightarchive.IgcParseResult
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.flightarchive.ReconciliationResult
import com.neutronstar.glidy.flightarchive.RemoveFlightResult
import com.neutronstar.glidy.flightarchive.StreamingIgcFlightParser
import com.neutronstar.glidy.flightarchive.SyncState
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.ByteString.Companion.toByteString

/**
 * Carnet iOS vu comme un FlightArchiveRepository (contrat commun de core:flightarchive) : les IGC du dossier de
 * l'app (ceux que lit FlightLibrary) + un index `archive.tsv` (une ligne par fichier : nom, identifiant, état, chemin distant) (identifiant stable, état de sauvegarde en ligne).
 * Sert à FlightSyncService, le même service de sauvegarde / restauration qu'Android.
 * En attendant data:flightarchive en Room KMP (plan iOS, phase 1).
 */
class IosFlightArchive(dir: String) : FlightArchiveRepository {
    private val fs = FileSystem.SYSTEM
    private val root = dir.toPath()
    private val indexPath = root / "archive.tsv"
    private val parser = StreamingIgcFlightParser()

    private data class Entry(val id: String, val sync: SyncState, val remote: String?)

    private fun loadIndex(): MutableMap<String, Entry> = runCatching {
        fs.read(indexPath) { readUtf8() }.lines().filter { it.isNotBlank() }.associate { line ->
            val c = line.split('\t')
            c[0] to Entry(c[1], SyncState.valueOf(c[2]), c.getOrNull(3)?.takeIf { it.isNotEmpty() })
        }.toMutableMap()
    }.getOrElse { mutableMapOf() }

    private fun saveIndex(index: Map<String, Entry>) {
        fs.createDirectories(root)
        val tmp = root / "archive.tsv.tmp"
        fs.write(tmp) {
            index.forEach { (name, e) -> writeUtf8("$name\t${e.id}\t${e.sync.name}\t${e.remote.orEmpty()}\n") }
        }
        fs.atomicMove(tmp, indexPath)
    }

    private fun files(): List<Path> =
        if (!fs.exists(root)) emptyList() else fs.list(root).filter { it.name.endsWith(".igc", ignoreCase = true) }

    private fun toFlight(path: Path, e: Entry): ArchivedFlight? {
        val bytes = runCatching { fs.read(path) { readByteArray() } }.getOrNull() ?: return null
        val parsed = parser.parse(bytes.decodeToString()) as? IgcParseResult.Success ?: return null
        val f = parsed.flight
        return ArchivedFlight(
            id = FlightId(e.id),
            file = IgcFileRef(path.name, path.name, bytes.size.toLong(), bytes.toByteString().sha256().hex()),
            summary = f.summary,
            pilot = f.metadata.pilot,
            gliderType = f.metadata.gliderType,
            gliderId = f.metadata.gliderId,
            localState = LocalFileState.AVAILABLE,
            syncState = e.sync,
            remoteId = e.remote,
        )
    }

    override suspend fun listFlights(): List<ArchivedFlight> {
        val index = loadIndex()
        var changed = false
        val out = files().mapNotNull { p ->
            val e = index[p.name] ?: Entry(FlightId.new().value, SyncState.LOCAL_ONLY, null).also { index[p.name] = it; changed = true }
            toFlight(p, e)
        }
        if (changed) saveIndex(index)
        return out
    }

    override suspend fun findFlight(id: FlightId): ArchivedFlight? = listFlights().firstOrNull { it.id == id }

    override suspend fun importIgc(fileName: String, source: Source): ImportIgcResult {
        val bytes = runCatching { source.buffer().readByteArray() }.getOrElse { return ImportIgcResult.Failed(it.message ?: "lecture") }
        if (bytes.size > MAX_BYTES) return ImportIgcResult.TooLarge
        val parsed = parser.parse(bytes.decodeToString())
        if (parsed is IgcParseResult.Failure) return ImportIgcResult.Invalid(parsed.error)
        val sha = bytes.toByteString().sha256().hex()
        listFlights().firstOrNull { it.file.sha256 == sha }?.let { return ImportIgcResult.Duplicate(it) }
        fs.createDirectories(root)
        val base = fileName.substringAfterLast('/').removeSuffix(".gz").let { if (it.endsWith(".igc", true)) it else "$it.igc" }
        var name = base
        var n = 1
        while (fs.exists(root / name)) name = base.removeSuffix(".igc").removeSuffix(".IGC") + "-${n++}.igc"
        fs.write(root / name) { write(bytes) }
        val index = loadIndex()
        val e = Entry(FlightId.new().value, SyncState.LOCAL_ONLY, null)
        index[name] = e
        saveIndex(index)
        return toFlight(root / name, e)?.let { ImportIgcResult.Imported(it) } ?: ImportIgcResult.Failed("relecture impossible")
    }

    override suspend fun readIgc(id: FlightId): ByteArray? {
        val name = loadIndex().entries.firstOrNull { it.value.id == id.value }?.key ?: return null
        return runCatching { fs.read(root / name) { readByteArray() } }.getOrNull()
    }

    override suspend fun updateSyncState(id: FlightId, state: SyncState, remoteId: String?): Boolean {
        val index = loadIndex()
        val key = index.entries.firstOrNull { it.value.id == id.value }?.key ?: return false
        index[key] = index.getValue(key).copy(sync = state, remote = remoteId)
        saveIndex(index)
        return true
    }

    override suspend fun removeLocalFlight(id: FlightId): RemoveFlightResult {
        val index = loadIndex()
        val key = index.entries.firstOrNull { it.value.id == id.value }?.key ?: return RemoveFlightResult.NotFound
        runCatching { fs.delete(root / key) }
        index.remove(key)
        saveIndex(index)
        return RemoveFlightResult.Removed
    }

    override suspend fun reconcile(): ReconciliationResult = ReconciliationResult(0, 0, 0, 0)

    /** Remet tous les vols en « local seulement » (compte supprimé, comme CloudHost.deleteAccount d'Android). */
    fun markAllLocal() {
        val index = loadIndex()
        saveIndex(index.mapValues { it.value.copy(sync = SyncState.LOCAL_ONLY, remote = null) })
    }

    /** Nombre de vols sauvegardés en ligne / total (carte « Sauvegarde en ligne »). */
    fun counts(): IntArray {
        val names = files().map { it.name }.filterNot { it.startsWith("exemple-", true) }.toSet()
        val index = loadIndex()
        return intArrayOf(names.count { index[it]?.sync == SyncState.SYNCED }, names.size)
    }

    private companion object { const val MAX_BYTES = 20L * 1024 * 1024 }
}
