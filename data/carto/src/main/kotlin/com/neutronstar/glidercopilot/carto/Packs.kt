package com.neutronstar.glidercopilot.carto

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.precog.Json
import java.time.Instant

/** Fichier d'un pack : fond, relief, courbes ou aéro. */
data class PackFile(val role: String, val name: String, val url: String, val size: Long, val sha256: String)

/** Pack régional tel que décrit par catalog.json (release « cartes ») ou par son manifeste installé. */
data class PackInfo(
    val id: String,
    val name: String,
    val version: String,
    val created: Instant?,
    /** ouest, sud, est, nord. */
    val bbox: DoubleArray,
    val aeroValidUntil: Instant?,
    val attribution: String,
    val files: List<PackFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }
    fun file(role: String): PackFile? = files.firstOrNull { it.role == role }

    fun contains(p: LatLon): Boolean = p.lon >= bbox[0] && p.lon <= bbox[2] && p.lat >= bbox[1] && p.lat <= bbox[3]
    val center: LatLon get() = LatLon((bbox[1] + bbox[3]) / 2, (bbox[0] + bbox[2]) / 2)

    /** Données aéro périmées : la carte reste utilisable, l'app le signale. */
    fun aeroExpired(now: Instant): Boolean = aeroValidUntil?.let { now.isAfter(it) } ?: true

    override fun equals(other: Any?) = other is PackInfo && other.id == id && other.version == version
    override fun hashCode() = id.hashCode() * 31 + version.hashCode()
}

object PackCatalog {
    const val URL = "https://github.com/jeanbaptisteallaire/Glider-Copilot/releases/download/cartes/catalog.json"

    fun parse(text: String): List<PackInfo> = Json.parse(text)["packs"]?.arr.orEmpty().mapNotNull(::packOf)

    fun parseManifest(text: String): PackInfo? = packOf(Json.parse(text))

    /** Pack qui contient le point ; à défaut le plus proche (club en bordure de région). */
    fun forPosition(packs: List<PackInfo>, p: LatLon): PackInfo? =
        packs.filter { it.contains(p) }.minByOrNull { Geo.distanceKm(it.center, p) }
            ?: packs.minByOrNull { Geo.distanceKm(it.center, p) }?.takeIf { Geo.distanceKm(it.center, p) < 250 }

    private fun packOf(j: Json): PackInfo? {
        val id = j["id"]?.str ?: return null
        val bbox = j["bbox"]?.arr?.mapNotNull { it.num }?.takeIf { it.size == 4 } ?: return null
        return PackInfo(
            id = id,
            name = j["name"]?.str ?: id,
            version = j["version"]?.str ?: "0",
            created = j["created"]?.str?.let(::instant),
            bbox = bbox.toDoubleArray(),
            aeroValidUntil = j["aeroValidUntil"]?.str?.let(::instant),
            attribution = j["attribution"]?.str ?: "",
            files = j["files"]?.arr.orEmpty().mapNotNull { f ->
                PackFile(
                    role = f["role"]?.str ?: return@mapNotNull null,
                    name = f["name"]?.str ?: return@mapNotNull null,
                    url = f["url"]?.str ?: return@mapNotNull null,
                    size = f["size"]?.num?.toLong() ?: 0L,
                    sha256 = f["sha256"]?.str ?: "",
                )
            },
        )
    }

    private fun instant(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()
}
