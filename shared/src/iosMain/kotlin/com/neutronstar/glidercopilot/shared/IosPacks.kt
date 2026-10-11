package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.carto.AeroGeoJson
import com.neutronstar.glidercopilot.carto.PackCatalog
import com.neutronstar.glidercopilot.carto.PackFile
import com.neutronstar.glidercopilot.carto.PackInfo
import com.neutronstar.glidercopilot.carto.PmTiles
import com.neutronstar.glidercopilot.carto.TerrariumTerrain
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.Terrain
import com.neutronstar.glidercopilot.domain.aero.AeroData
import kotlinx.datetime.Clock
import okio.FileSystem
import okio.Path.Companion.toPath

/** Pack chargé : terrains openAIP et relief Copernicus, lus localement (aucune dépendance réseau en vol). */
class ActivePack(val info: PackInfo, val aero: AeroData, val terrain: Terrain, internal val pm: PmTiles?) {
    val name: String get() = info.name
    val aeroExpired: Boolean get() = info.aeroExpired(Clock.System.now())
    val airportCount: Int get() = aero.airports.size
    val reliefLoaded: Boolean get() = pm != null
    fun close() { runCatching { pm?.close() } }
}

/**
 * Cartes hors ligne de l'édition Lite iOS : même catalogue et mêmes packs régionaux qu'Android (release « cartes »).
 * iOS ne garde que le relief et les données aéro (la sécurité) ; le fond vectoriel attend MapLibre iOS.
 * Le téléchargement (URLSession, SHA-256) est fait en Swift ; le manifeste est écrit en dernier, comme sur Android.
 */
object IosPacks {
    const val CATALOG_URL = PackCatalog.URL
    val ROLES = setOf("relief", "aero")
    private val fs = FileSystem.SYSTEM

    fun parseCatalog(text: String): List<PackInfo> = runCatching { PackCatalog.parse(text) }.getOrDefault(emptyList())

    fun packFor(catalog: List<PackInfo>, lat: Double, lon: Double): PackInfo? = PackCatalog.forPosition(catalog, LatLon(lat, lon))

    /** Même convention qu'Android : <dossier des fichiers>/<id>-manifest.json. */
    fun manifestUrl(pack: PackInfo): String = pack.files.first().url.substringBeforeLast('/') + "/${pack.id}-manifest.json"

    fun parseManifest(text: String): PackInfo? = runCatching { PackCatalog.parseManifest(text) }.getOrNull()

    fun neededFiles(pack: PackInfo): List<PackFile> = pack.files.filter { it.role in ROLES }

    fun neededBytes(pack: PackInfo): Long = neededFiles(pack).sumOf { it.size }

    /** Manifeste d'un pack installé dont le relief et l'aéro sont complets, sinon null. */
    fun installed(root: String, id: String): PackInfo? = runCatching {
        val dir = "$root/$id".toPath()
        if (fs.metadataOrNull(dir)?.isDirectory != true) return null
        val m = dir / "manifest.json"
        if (!fs.exists(m)) return null
        val info = parseManifest(fs.read(m) { readUtf8() }) ?: return null
        info.takeIf { p -> neededFiles(p).all { f -> fs.metadataOrNull(dir / f.name)?.size == f.size } }
    }.getOrNull()

    /** Pack installé couvrant le point (club en bordure de région : le plus proche à moins de 250 km). */
    fun installedFor(root: String, lat: Double, lon: Double): PackInfo? {
        val r = root.toPath()
        if (!fs.exists(r)) return null
        val all = runCatching { fs.list(r) }.getOrDefault(emptyList()).mapNotNull { installed(root, it.name) }
        return PackCatalog.forPosition(all, LatLon(lat, lon))
    }

    fun load(root: String, info: PackInfo): ActivePack {
        val dir = "$root/${info.id}".toPath()
        val aero = info.file("aero")?.let { dir / it.name }?.takeIf { fs.exists(it) }
            ?.let { p -> runCatching { AeroGeoJson.parse(fs.read(p) { readUtf8() }) }.getOrNull() } ?: AeroData.EMPTY
        val pm = info.file("relief")?.let { dir / it.name }?.takeIf { fs.exists(it) }?.let { runCatching { PmTiles(it) }.getOrNull() }
        return ActivePack(info, aero, pm?.let { TerrariumTerrain(it) } ?: Terrain.NONE, pm)
    }
}
