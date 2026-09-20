package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.flarm.Registration
import com.neutronstar.glidercopilot.precog.CachedResponse
import com.neutronstar.glidercopilot.precog.HttpClient
import com.neutronstar.glidercopilot.precog.Json
import com.neutronstar.glidercopilot.precog.ResponseCache
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** Fiche de la Device Database OGN. */
data class DdbDevice(
    val deviceType: String,
    val deviceId: String,
    val aircraftModel: String?,
    val registration: String,
    val competitionNumber: String?,
    /** false : le propriétaire refuse le suivi. L'app ne doit pas suivre ce boîtier. */
    val tracked: Boolean,
    /** false : le propriétaire refuse l'identification. L'immatriculation ne doit pas être affichée aux tiers. */
    val identified: Boolean,
) {
    val deviceTypeLabel: String get() = when (deviceType) { "F" -> "FLARM"; "O" -> "OGN"; "I" -> "ICAO"; else -> deviceType }
}

sealed interface PairingLookup {
    data class Found(val devices: List<DdbDevice>, val fetchedAt: Instant, val offline: Boolean) : PairingLookup
    data class NotTracked(val device: DdbDevice) : PairingLookup
    data class NotFound(val fetchedAt: Instant, val offline: Boolean) : PairingLookup
    data class Unavailable(val reason: String) : PairingLookup
}

object DdbParser {
    fun parse(text: String): List<DdbDevice> = Json.parse(text)["devices"]?.arr.orEmpty().mapNotNull { j ->
        val id = j["device_id"]?.str?.uppercase() ?: return@mapNotNull null
        val reg = j["registration"]?.str?.trim().orEmpty()
        if (reg.isEmpty()) return@mapNotNull null
        DdbDevice(
            deviceType = j["device_type"]?.str ?: "?",
            deviceId = id,
            aircraftModel = j["aircraft_model"]?.str?.takeIf { it.isNotBlank() },
            registration = reg.uppercase(),
            competitionNumber = j["cn"]?.str?.takeIf { it.isNotBlank() },
            tracked = !yesNoIsNo(j["tracked"]),
            identified = !yesNoIsNo(j["identified"]),
        )
    }

    private fun yesNoIsNo(j: Json?): Boolean = when (j) {
        is Json.Str -> j.value.equals("N", ignoreCase = true) || j.value == "0" || j.value.equals("false", ignoreCase = true)
        is Json.Bool -> !j.value
        is Json.Num -> j.value == 0.0
        else -> false
    }

    /** Toutes les fiches de l'immatriculation (un planeur peut porter FLARM et balise OGN). FLARM en tête. */
    fun find(devices: List<DdbDevice>, registration: String): List<DdbDevice> {
        val key = Registration.key(registration)
        return devices.filter { Registration.key(it.registration) == key }
            .sortedBy { if (it.deviceType == "F") 0 else 1 }
    }
}

/**
 * Consultation de la DDB (https://ddb.glidernet.org). Le fichier complet est mis en cache disque
 * et réutilisé 24 h ; hors réseau, la dernière copie sert, signalée « hors ligne ».
 */
class OgnDeviceDatabase(
    private val http: HttpClient,
    private val cache: ResponseCache,
    private val clock: () -> Instant = Instant::now,
    private val url: String = DDB_URL,
) {
    @Volatile private var parsed: Pair<Instant, List<DdbDevice>>? = null

    /** État de la copie locale de la DDB, pour diagnostic à l'écran (Prévol) — voir [refresh]/[status]. */
    data class DdbStatus(val fetchedAt: Instant?, val deviceCount: Int, val offline: Boolean)

    @Volatile private var lastStatus = DdbStatus(fetchedAt = null, deviceCount = 0, offline = false)

    /** Dernier état connu de la copie locale, sans re-toucher le disque — à afficher tel quel. */
    fun status(): DdbStatus = lastStatus

    /**
     * Rafraîchit (ou télécharge) la copie locale de la DDB si besoin (S8, correctif) : sans cet appel,
     * [cachedDevice] ne résout jamais aucune adresse tant qu'aucun appairage ni suivi n'a déclenché [lookup] —
     * le trafic environnant retombait alors sur l'adresse radio brute pour tout le monde. À appeler au
     * démarrage du réseau OGN, avant même que le pilote n'ait appairé son planeur.
     * Renvoie vrai si une copie (fraîche ou mise en cache hors ligne) est disponible après l'appel.
     */
    fun refresh(): Boolean {
        val loaded = load()
        lastStatus = if (loaded != null) DdbStatus(loaded.fetchedAt, loaded.devices.size, loaded.offline) else lastStatus
        return loaded != null
    }

    fun lookup(registration: String): PairingLookup {
        val reg = Registration.normalize(registration)
        if (!Registration.isValid(reg)) return PairingLookup.Unavailable("Immatriculation trop courte")
        val (devices, fetchedAt, offline) = load() ?: return PairingLookup.Unavailable("Base OGN injoignable et aucune copie locale")
        val hits = DdbParser.find(devices, reg)
        return when {
            hits.isEmpty() -> PairingLookup.NotFound(fetchedAt, offline)
            hits.none { it.tracked } -> PairingLookup.NotTracked(hits.first())
            else -> PairingLookup.Found(hits.filter { it.tracked }, fetchedAt, offline)
        }
    }

    private data class Loaded(val devices: List<DdbDevice>, val fetchedAt: Instant, val offline: Boolean)

    private fun load(): Loaded? {
        val now = clock()
        val cached = cache.read(url)
        if (cached != null && Duration.between(cached.fetchedAt, now) < MAX_AGE) return Loaded(devicesOf(cached), cached.fetchedAt, false)
        return try {
            val r = http.get(url, cached?.etag)
            when {
                r.code == 304 && cached != null -> {
                    val refreshed = cached.copy(fetchedAt = now)
                    cache.write(url, refreshed)
                    Loaded(devicesOf(refreshed), now, false)
                }
                r.code in 200..299 && r.body != null -> {
                    val fresh = CachedResponse(r.body!!, r.etag, now)
                    val list = DdbParser.parse(fresh.body)
                    cache.write(url, fresh)
                    parsed = now to list
                    Loaded(list, now, false)
                }
                cached != null -> Loaded(devicesOf(cached), cached.fetchedAt, true)
                else -> null
            }
        } catch (e: IOException) {
            cached?.let { Loaded(devicesOf(it), it.fetchedAt, true) }
        } catch (e: RuntimeException) {
            cached?.let { Loaded(devicesOf(it), it.fetchedAt, true) }
        }
    }

    @Volatile private var index: Pair<Instant, Map<String, DdbDevice>>? = null

    /**
     * Fiche DDB d'une adresse radio, lue dans la copie locale uniquement (jamais d'appel réseau : utilisé pour chaque trame).
     * null si la base n'a pas encore été téléchargée ou si l'appareil n'y est pas déclaré.
     */
    fun cachedDevice(address: String): DdbDevice? {
        // appelé pour chaque trame : la copie disque (plusieurs Mo) n'est relue qu'au plus toutes les 10 min
        val nowMs = clock().toEpochMilli()
        var idx = index?.second
        if (idx == null || nowMs - indexCheckedAt > 600_000) {
            indexCheckedAt = nowMs
            val cached = cache.read(url)
            idx = if (cached == null) emptyMap()
            else index?.takeIf { it.first == cached.fetchedAt }?.second
                ?: devicesOf(cached).associateBy { it.deviceId }.also { index = cached.fetchedAt to it }
            if (cached == null) index = Instant.EPOCH to idx
        }
        return idx[address.uppercase()]
    }

    @Volatile private var indexCheckedAt = 0L

    private fun devicesOf(c: CachedResponse): List<DdbDevice> {
        parsed?.let { (at, list) -> if (at == c.fetchedAt) return list }
        return DdbParser.parse(c.body).also { parsed = c.fetchedAt to it }
    }

    companion object {
        const val DDB_URL = "https://ddb.glidernet.org/download/?j=1&t=1"
        val MAX_AGE: Duration = Duration.ofHours(24)
    }
}
