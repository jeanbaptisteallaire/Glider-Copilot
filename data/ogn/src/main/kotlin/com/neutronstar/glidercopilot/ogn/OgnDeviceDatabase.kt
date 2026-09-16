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

    private fun devicesOf(c: CachedResponse): List<DdbDevice> {
        parsed?.let { (at, list) -> if (at == c.fetchedAt) return list }
        return DdbParser.parse(c.body).also { parsed = c.fetchedAt to it }
    }

    companion object {
        const val DDB_URL = "https://ddb.glidernet.org/download/?j=1&t=1"
        val MAX_AGE: Duration = Duration.ofHours(24)
    }
}
