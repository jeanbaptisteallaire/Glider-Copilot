package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.secondsBetween
import com.neutronstar.glidercopilot.precog.PlatformLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** Aéronef autour, prêt à dessiner (mêmes règles d'étiquette que TrafficMark d'Android). */
data class TrafficPlane(
    val id: String,
    val lat: Double,
    val lon: Double,
    val trackDeg: Double?,
    val altitudeM: Double?,
    /** immatriculation ou numéro de concours (DDB, si le propriétaire l'autorise), vide sinon */
    val fullLabel: String,
    /** repli court (2 lettres) quand l'appareil n'est pas identifié */
    val shortLabel: String,
    val typeLabel: String,
    /** écart d'altitude avec mon planeur (« +120 »), vide si inconnu */
    val relativeAltText: String,
    val climbMs: Double?,
    val speedKmh: Double?,
    val circling: Boolean,
    val ageS: Long,
)

/** Pompe repérée par le réseau (spirales d'autres planeurs). */
data class TrafficPump(val lat: Double, val lon: Double, val climbMs: Double, val aircraftCount: Int, val ageMinutes: Long)

/** État du trafic OGN à un instant : ce que Pilotage et Carte affichent, identique sur Android et iOS. */
data class TrafficSnapshot(
    val planes: List<TrafficPlane>,
    val pumps: List<TrafficPump>,
    val connected: Boolean,
    val statusLabel: String,
    val warning: String?,
    val framesPerMin: Int,
    /** pastille de Pilotage après « OGN » : « 12 aéronefs · 2 pompes » (légende .traffic-key de la maquette v8) */
    val pill: String,
)

/**
 * Session de trafic OGN en direct (commune Android / iOS) : connexion APRS-IS autour d'un centre (terrain du club
 * ou position), mémoire des trajectoires, détection des pompes, étiquettes DDB. Rien n'est écrit sur disque
 * (pas de redistribution, ODbL) ; les choix « tracked » / « identified » de la DDB sont respectés par TrafficStore.
 */
class OgnLiveSession(
    private val ddb: OgnDeviceDatabase?,
    private val user: String,
    private val appVersion: String,
    private val radiusKm: Int = RADIUS_KM,
    private val detailRadiusKm: Double = DETAIL_KM,
    private val clock: () -> Instant = { Clock.System.now() },
    private val client: AprsClient = AprsClient(user, appVersion = appVersion),
) {
    @kotlin.concurrent.Volatile private var centre: LatLon? = null
    @kotlin.concurrent.Volatile private var ownAddresses: List<Pair<AddressType, String>> = emptyList()
    private val store = TrafficStore(DeviceDirectory { ddb?.cachedDevice(it) }, centre = { centre }, detailRadiusKm = detailRadiusKm)
    private val state = MutableStateFlow<AprsState>(AprsState.Idle)
    private val lock = PlatformLock()
    private val frameTimes = ArrayDeque<Instant>()
    private var lastFrameAt: Instant? = null
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /** Démarre (ou redémarre si le centre a bougé de plus de 20 km : le filtre APRS est relu à chaque connexion). */
    fun start(scope: CoroutineScope, centre: LatLon, own: List<Pair<AddressType, String>> = emptyList()) {
        val old = this.centre
        this.centre = centre
        ownAddresses = own
        if (running && old != null && OwnGlider.distanceKm(old, centre) < 20.0) return
        job?.cancel()
        job = scope.launch {
            launch { runCatching { ddb?.refresh() } }
            launch {
                var tick = 0
                while (true) {
                    delay(1000)
                    if (tick++ % 5 == 0) store.analyse(clock())
                }
            }
            client.lines({ OwnGlider.aprsFilter(this@OgnLiveSession.centre ?: centre, radiusKm, ownAddresses) }, state).collect { handle(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        state.value = AprsState.Idle
    }

    private fun handle(line: AprsLine) {
        val fix = (OgnParser.parse(line.text, line.received) as? OgnLine.Aircraft)?.fix ?: return
        if (!store.add(fix)) return
        lock.withLock {
            lastFrameAt = line.received
            frameTimes.addLast(line.received)
            while (frameTimes.isNotEmpty() && secondsBetween(frameTimes.first(), line.received) > 60) frameTimes.removeFirst()
        }
    }

    /** [ownAltitudeM] : altitude de mon planeur, pour l'écart affiché à côté de chaque étiquette. */
    fun snapshot(ownAltitudeM: Double?): TrafficSnapshot {
        val now = clock()
        val ownIds = ownAddresses.map { it.second }.toSet()
        val planes = store.aircraft(now, exclude = ownIds).map { a ->
            val alt = a.last.altitudeM
            TrafficPlane(
                id = a.address,
                lat = a.last.position.lat,
                lon = a.last.position.lon,
                trackDeg = a.last.trackDeg,
                altitudeM = alt,
                fullLabel = a.label ?: "",
                shortLabel = a.shortLabel,
                typeLabel = a.typeLabel,
                relativeAltText = if (ownAltitudeM != null && alt != null) signed((alt - ownAltitudeM).roundToInt()) else "",
                climbMs = a.last.climbMs,
                speedKmh = a.last.groundSpeedKmh,
                circling = a.circling,
                ageS = secondsBetween(a.last.time, now).coerceAtLeast(0),
            )
        }
        val pumps = store.thermals(now, exclude = ownIds).map {
            TrafficPump(it.position.lat, it.position.lon, it.climbMs, it.aircraftCount, it.ageMinutes(now))
        }
        val (fpm, last) = lock.withLock { frameTimes.size to lastFrameAt }
        val st = state.value
        return TrafficSnapshot(
            planes = planes,
            pumps = pumps,
            connected = st is AprsState.Connected,
            statusLabel = statusLabel(st, store.framesAccepted),
            warning = when {
                st is AprsState.Waiting && Regex("(?i)invalid|refus").containsMatchIn(st.reason) -> "OGN a refusé la connexion (${st.reason})."
                st is AprsState.Connected && (last == null || secondsBetween(last, now) > 120) -> "Aucune trame OGN depuis 2 min : vérifiez le réseau."
                else -> null
            },
            framesPerMin = fpm,
            pill = pill(planes.size, null, pumps.size),
        )
    }

    companion object {
        /** rayon large : la carte montre tout le sud de la France ; seules les trajectoires proches sont gardées en entier */
        const val RADIUS_KM = 250
        const val DETAIL_KM = 60.0

        fun statusLabel(state: AprsState, frames: Long): String = when (state) {
            AprsState.Idle -> "Réseau OGN à l'arrêt (app en arrière-plan)"
            is AprsState.Connecting -> "Connexion à aprs.glidernet.org…" + if (state.attempt > 1) " (essai ${state.attempt})" else ""
            is AprsState.Connected -> "Connecté · ${state.server?.takeIf { !it.startsWith("aprsc") } ?: "aprs.glidernet.org"} · $frames trames reçues"
            is AprsState.Waiting -> "Hors réseau (${state.reason}) · nouvel essai dans ${state.retryInSeconds} s"
        }

        /** Même texte que la légende trafic de FlightScreen (Android) ; [near] = aéronefs à 15 km (mode démo). */
        fun pill(planes: Int, near: Int?, pumps: Int): String =
            "$planes aéronef" + (if (planes > 1) "s" else "") + (near?.let { " ($it à 15 km)" } ?: "") +
                " · $pumps pompe" + (if (pumps > 1) "s" else "")

        private fun signed(n: Int) = (if (n >= 0) "+" else "−") + abs(n)
    }
}
