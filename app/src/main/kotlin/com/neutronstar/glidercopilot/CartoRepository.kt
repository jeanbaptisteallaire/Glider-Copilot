package com.neutronstar.glidercopilot

import android.content.Context
import com.neutronstar.glidercopilot.carto.AeroGeoJson
import com.neutronstar.glidercopilot.carto.PackCatalog
import com.neutronstar.glidercopilot.carto.PackDownloader
import com.neutronstar.glidercopilot.carto.PackInfo
import com.neutronstar.glidercopilot.carto.UrlStreamOpener
import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.domain.aero.AeroData
import com.neutronstar.glidercopilot.feature.prevol.OfflineMapSource
import com.neutronstar.glidercopilot.feature.prevol.OfflineMapUi
import com.neutronstar.glidercopilot.precog.CachedResponse
import com.neutronstar.glidercopilot.precog.FileResponseCache
import com.neutronstar.glidercopilot.precog.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Instant

/** Carte hors ligne active : pack installé, dossier et données aéro déjà lues. */
data class ActiveMap(val pack: PackInfo, val dir: File, val aeroText: String?, val aero: AeroData)

/**
 * Packs de carte hors ligne : catalogue publié par la CI (release « cartes »), pack de la région du club,
 * téléchargement vérifié, lecture des données openAIP. Rien ici n'est nécessaire à la sécurité en vol.
 */
class CartoRepository(
    context: Context,
    private val http: HttpClient,
    private val clubs: ClubRepository,
    userAgent: String,
) : OfflineMapSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root = File(context.filesDir, "packs").apply { mkdirs() }
    private val catalogCache = FileResponseCache(File(context.cacheDir, "cartes"))
    private val downloader = PackDownloader(UrlStreamOpener(userAgent))

    private val _state = MutableStateFlow(OfflineMapUi())
    override val state: StateFlow<OfflineMapUi> = _state.asStateFlow()
    private val _active = MutableStateFlow<ActiveMap?>(null)
    val active: StateFlow<ActiveMap?> = _active.asStateFlow()

    @Volatile private var catalog: List<PackInfo> = emptyList()
    @Volatile private var club: Club? = null
    @Volatile private var cancelRequested = false
    private var job: Job? = null

    init {
        scope.launch {
            clubs.selectedClub.distinctUntilChanged().collect { c ->
                club = c
                recompute()
                if (catalog.isEmpty()) loadCatalog()
            }
        }
    }

    override fun refreshCatalog() { scope.launch { loadCatalog() } }

    private fun loadCatalog() {
        _state.update { it.copy(catalogLoading = true, catalogError = null) }
        val cached = catalogCache.read(PackCatalog.URL)
        val text = try {
            val r = http.get(PackCatalog.URL, cached?.etag)
            when {
                r.code == 304 && cached != null -> cached.body
                r.code in 200..299 && r.body != null -> r.body!!.also { catalogCache.write(PackCatalog.URL, CachedResponse(it, r.etag, Instant.now())) }
                else -> cached?.body
            }
        } catch (e: IOException) {
            cached?.body
        }
        catalog = text?.let { runCatching { PackCatalog.parse(it) }.getOrNull() }.orEmpty()
        _state.update { it.copy(catalogLoading = false, catalogError = if (text == null) "Catalogue des cartes injoignable" else null) }
        recompute()
    }

    /** Pack de la région du club : celui du catalogue, sinon un pack déjà installé qui couvre le terrain. */
    private fun recompute() {
        val pos = club?.position
        val available = pos?.let { PackCatalog.forPosition(catalog, it) }
        val installedAll = root.listFiles()?.mapNotNull { PackDownloader.installed(root, it.name) }.orEmpty()
        val installed = available?.let { a -> installedAll.firstOrNull { it.id == a.id } }
            ?: pos?.let { PackCatalog.forPosition(installedAll, it) }
        val active = installed?.let { loadActive(it) }
        _active.value = active
        val around = if (active != null && pos != null) active.aero.airspacesAround(pos, AROUND_KM).take(12) else emptyList()
        _state.update {
            it.copy(
                available = available,
                installed = installed,
                outOfCoverage = pos != null && catalog.isNotEmpty() && available == null,
                fieldLabel = club?.let { c -> c.airfieldIcao ?: c.shortName ?: c.name },
                airspacesAround = around,
                aeroFetched = active?.aero?.fetched,
                now = Instant.now(),
            )
        }
    }

    private fun loadActive(p: PackInfo): ActiveMap {
        _active.value?.let { if (it.pack == p) return it }
        val dir = File(root, p.id)
        val text = p.file("aero")?.let { File(dir, it.name) }?.takeIf { it.exists() }?.readText()
        val aero = text?.let { runCatching { AeroGeoJson.parse(it) }.getOrNull() } ?: AeroData.EMPTY
        return ActiveMap(p, dir, text, aero)
    }

    override fun download() {
        val pack = _state.value.available ?: return
        if (job?.isActive == true) return
        cancelRequested = false
        job = scope.launch {
            _state.update { it.copy(progress = 0f, downloadError = null) }
            try {
                val manifestUrl = pack.files.first().url.substringBeforeLast('/') + "/${pack.id}-manifest.json"
                val r = http.get(manifestUrl, null)
                val manifestText = r.body?.takeIf { r.code in 200..299 } ?: throw IOException("manifeste introuvable (HTTP ${r.code})")
                val manifest = PackCatalog.parseManifest(manifestText) ?: throw IOException("manifeste illisible")
                downloader.install(manifest, manifestText, root, { done, total ->
                    _state.update { s -> s.copy(progress = if (total > 0) done.toFloat() / total else 0f) }
                }, { cancelRequested })
                _state.update { it.copy(progress = null) }
            } catch (e: Exception) {
                _state.update { it.copy(progress = null, downloadError = if (cancelRequested) null else (e.message ?: "échec du téléchargement")) }
            }
            recompute()
        }
    }

    override fun cancel() { cancelRequested = true }

    companion object {
        const val AROUND_KM = 15.0
    }
}
