package com.neutronstar.glidercopilot

import android.content.Context
import android.util.Log
import com.neutronstar.glidy.flightarchive.CompletedFlightGateway
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightShareGateway
import com.neutronstar.glidy.flightarchive.data.LocalArchiveModule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * S10 — pont entre GLIDY et le carnet « Mes vols » (modules `core/data:flightarchive`).
 *
 * Le moteur de vol reste l'unique producteur des fichiers IGC : il remet ici un fichier **déjà fermé**,
 * copié de façon atomique dans l'archive privée, puis indexé. Tout se passe en tâche de fond ; aucune erreur
 * d'archivage ne remonte vers le moteur de vol (règle GLIDY : la sécurité ne dépend de rien d'autre).
 */
class FlightArchiveHost(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** CI (S11) : --ez glidy.flights.replay3d true ouvre directement le rejeu 3D du vol le plus récent. */
    @Volatile var openReplayOnStart = false

    val repository: FlightArchiveRepository by lazy { LocalArchiveModule.create(app) }
    val shareGateway: FlightShareGateway by lazy {
        // un seul FileProvider dans l'app (autorité « .files », chemins dans res/xml/file_paths.xml)
        LocalArchiveModule.createShareGateway(app, repository, authority = "${app.packageName}.files")
    }
    val completedGateway: CompletedFlightGateway by lazy { LocalArchiveModule.createCompletedFlightGateway(app, repository) }

    /** À appeler après la fermeture complète d'un fichier IGC par le moteur de vol. */
    fun submitClosedIgc(file: File) {
        scope.launch {
            lock.withLock {
                runCatching { file.inputStream().use { completedGateway.submitCompletedIgc(file.name, it) } }
                    .onSuccess { Log.i(TAG, "vol archivé : ${file.name} → $it") }
                    .onFailure { Log.w(TAG, "archivage impossible : ${file.name}", it) }
            }
        }
    }

    /**
     * Reprend les IGC déjà présents (vols enregistrés avant la V0.9, ou appli arrêtée pendant un vol) :
     * la déduplication SHA-256 de l'archive rend l'opération sans risque si elle se répète. Les fichiers
     * modifiés dans les 5 dernières minutes sont laissés de côté (vol peut-être en cours d'écriture).
     */
    fun importExistingIgc() {
        scope.launch {
            lock.withLock {
                val root = app.getExternalFilesDir(null) ?: app.filesDir
                val files = listOf("igc", "igc-rejeu").flatMap { d ->
                    File(root, d).listFiles { f -> f.isFile && f.name.endsWith(".igc", ignoreCase = true) }?.toList().orEmpty()
                }
                val now = System.currentTimeMillis()
                var n = 0
                files.filter { now - it.lastModified() > 5 * 60_000L }.forEach { f ->
                    runCatching { f.inputStream().use { repository.importIgc(f.name, it) } }
                        .onSuccess { n++ }
                        .onFailure { Log.w(TAG, "reprise impossible : ${f.name}", it) }
                }
                Log.i(TAG, "reprise des IGC existants : $n fichier(s) examiné(s)")
            }
        }
    }

    /** Vol synthétique d'exemple (Saint-Martin-de-Londres) embarqué dans les assets. */
    fun importDemoFlight(onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = lock.withLock {
                runCatching {
                    app.assets.open(DEMO_ASSET).use { repository.importIgc(DEMO_NAME, it) }
                }.onFailure { Log.w(TAG, "vol d'exemple impossible", it) }.isSuccess
            }
            onDone(ok)
        }
    }

    companion object {
        private const val TAG = "GlidyFlights"
        const val DEMO_ASSET = "flights/saint-martin-de-londres-vol-synthetique.igc"
        const val DEMO_NAME = "exemple-saint-martin-de-londres-vol-synthetique.igc"
    }
}
