package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.ogn.AddressType
import com.neutronstar.glidercopilot.ogn.AprsClient
import com.neutronstar.glidercopilot.ogn.PairingLookup
import com.neutronstar.glidercopilot.ogn.OgnDeviceDatabase
import com.neutronstar.glidercopilot.ogn.OgnLiveSession
import com.neutronstar.glidercopilot.ogn.TrafficSnapshot
import com.neutronstar.glidercopilot.precog.FileResponseCache
import com.neutronstar.glidercopilot.precog.KtorHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * Trafic OGN de l'édition complète iOS : même session qu'Android (OgnLiveSession, data:ogn), DDB mise en cache dans
 * [cacheDir]. Démarre autour du terrain choisi (ou de la position), s'arrête en arrière-plan hors vol.
 * Aucune exception ne remonte vers Swift.
 */
class IosTraffic(cacheDir: String, installId: Long, appVersion: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ddb = runCatching {
        OgnDeviceDatabase(KtorHttpClient(userAgent = "WindGlider/$appVersion (iOS)", timeoutMs = 30_000), FileResponseCache((cacheDir.trimEnd('/') + "/ogn").toPath()))
    }.getOrNull()
    // filtre APRS réduit au rayon utile (60 km au lieu de 250) : beaucoup moins de trames, radio cellulaire moins
    // sollicitée en vol (revue énergie 1) ; Android garde son rayon
    private val session = OgnLiveSession(ddb, AprsClient.userFor(installId), appVersion, radiusKm = OgnLiveSession.DETAIL_KM.toInt())

    val running: Boolean get() = session.running
    /** Boîtiers FLARM/OGN de mon planeur appairé : filtre APRS (mon planeur suivi même loin) et exclu du trafic affiché. */
    @kotlin.concurrent.Volatile private var own: List<Pair<AddressType, String>> = emptyList()
    @kotlin.concurrent.Volatile private var centre: LatLon? = null

    fun start(lat: Double, lon: Double) { runCatching { centre = LatLon(lat, lon); session.start(scope, LatLon(lat, lon), own) } }
    fun stop() { runCatching { session.stop() } }

    /**
     * Appairage du planeur du jour par son immatriculation (comme GliderRepository d'Android) : cherche ses boîtiers
     * dans la Device Database OGN (copie locale si pas de réseau). Bloquant (téléchargement de la base) : à appeler
     * hors du fil principal. Le filtre APRS est relu à la prochaine connexion : la session redémarre si elle tourne.
     */
    fun pair(registration: String): IosPairing {
        val r = runCatching { ddb?.lookup(registration) ?: PairingLookup.Unavailable("base OGN absente") }
            .getOrElse { PairingLookup.Unavailable(it.message ?: "erreur") }
        val devices = (r as? PairingLookup.Found)?.devices.orEmpty()
        if (r !is PairingLookup.Unavailable) {
            own = devices.map { d ->
                when (d.deviceType) { "F" -> AddressType.FLARM; "O" -> AddressType.OGN; "I" -> AddressType.ICAO; else -> AddressType.UNKNOWN } to d.deviceId
            }
            val c = centre
            if (session.running && c != null) runCatching { session.stop(); session.start(scope, c, own) }
        }
        val at = when (r) { is PairingLookup.Found -> r.fetchedAt; is PairingLookup.NotFound -> r.fetchedAt; else -> null }
        val offline = when (r) { is PairingLookup.Found -> r.offline; is PairingLookup.NotFound -> r.offline; else -> false }
        val d = devices.firstOrNull()
        return IosPairing(
            state = when (r) {
                is PairingLookup.Found -> IosPairing.State.PAIRED
                is PairingLookup.NotFound -> IosPairing.State.NOT_FOUND
                is PairingLookup.NotTracked -> IosPairing.State.NOT_TRACKED
                is PairingLookup.Unavailable -> IosPairing.State.UNVERIFIED
            },
            device = d?.let { listOfNotNull((if (it.deviceType == "F") "ID " else "${it.deviceTypeLabel} ") + it.deviceId, it.aircraftModel).joinToString(" · ") } ?: "",
            fetchedAtEpochS = at?.epochSeconds ?: 0L,
            offline = offline,
        )
    }

    /** Planeur désappairé : plus de filtre sur ses boîtiers. */
    fun unpair() {
        own = emptyList()
        val c = centre
        if (session.running && c != null) runCatching { session.stop(); session.start(scope, c, own) }
    }

    /** [ownAltitudeM] : NaN si inconnue. */
    fun snapshot(ownAltitudeM: Double): TrafficSnapshot =
        runCatching { session.snapshot(ownAltitudeM.takeIf { !it.isNaN() }) }
            .getOrElse { TrafficSnapshot(emptyList(), emptyList(), false, "Réseau OGN indisponible", null, 0, OgnLiveSession.pill(0, null, 0)) }
}

/** Résultat de l'appairage pour Swift : état, boîtier trouvé (« ID DD1234 · ASK 21 »), date de la base OGN lue. */
data class IosPairing(val state: State, val device: String, val fetchedAtEpochS: Long, val offline: Boolean) {
    enum class State { PAIRED, NOT_FOUND, NOT_TRACKED, UNVERIFIED }
}
