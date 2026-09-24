package com.neutronstar.glidercopilot

import android.content.Context
import androidx.core.content.edit
import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.feature.flight.FlightTraffic
import com.neutronstar.glidercopilot.feature.flight.OwnOgn
import com.neutronstar.glidercopilot.feature.flight.ThermalMark
import com.neutronstar.glidercopilot.feature.flight.TrafficMark
import com.neutronstar.glidercopilot.feature.prevol.OgnNetworkSource
import com.neutronstar.glidercopilot.feature.prevol.OgnNetworkUi
import com.neutronstar.glidercopilot.ogn.AddressType
import com.neutronstar.glidercopilot.ogn.AprsClient
import com.neutronstar.glidercopilot.ogn.AprsLine
import com.neutronstar.glidercopilot.ogn.AprsState
import com.neutronstar.glidercopilot.ogn.DeviceDirectory
import com.neutronstar.glidercopilot.ogn.OgnLine
import com.neutronstar.glidercopilot.ogn.OgnParser
import com.neutronstar.glidercopilot.ogn.OwnGlider
import com.neutronstar.glidercopilot.ogn.TrafficStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Trafic OGN en direct autour du terrain du club (filtre r/ + b/ pour mon planeur), ou rejeu d'un enregistrement
 * anonymisé (démonstration, captures CI). Tourne tant que l'app est au premier plan ; rien n'est écrit sur disque.
 */
class OgnLiveRepository(
    private val context: Context,
    private val clubs: ClubRepository,
    private val glider: GliderRepository,
    private val prefs: UserPreferences,
) : OgnNetworkSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // rayon large : l'onglet Carte montre tout le sud de la France ; seules les trajectoires proches sont gardées en entier
    private val store = TrafficStore(
        DeviceDirectory { glider.ddb.cachedDevice(it) },
        centre = { clubPosition },
        detailRadiusKm = DETAIL_KM,
    )
    private val aprsState = MutableStateFlow<AprsState>(AprsState.Idle)
    private val _network = MutableStateFlow(OgnNetworkUi(radiusKm = RADIUS_KM))
    override val network: StateFlow<OgnNetworkUi> = _network.asStateFlow()
    private val _traffic = MutableStateFlow(FlightTraffic())
    val traffic: StateFlow<FlightTraffic> = _traffic.asStateFlow()

    /** Trames du planeur suivi (Suivi & debug), dans l'ordre de réception. */
    private val _followFixes = kotlinx.coroutines.flow.MutableSharedFlow<com.neutronstar.glidercopilot.ogn.OgnFix>(extraBufferCapacity = 64)
    val followFixes: kotlinx.coroutines.flow.SharedFlow<com.neutronstar.glidercopilot.ogn.OgnFix> = _followFixes
    @Volatile private var followSet: Set<String> = emptySet()

    /** Rejeu : activé par l'extra d'intention « glidy.ogn.replay » (captures CI, démonstration hors saison). */
    @Volatile var replay: Boolean = false
    private var job: Job? = null

    // horloge : réelle en direct, accélérée pendant le rejeu
    @Volatile private var replayStartReal = 0L
    @Volatile private var replayStartVirtual: Instant = Instant.EPOCH
    private fun now(): Instant = if (replay) replayStartVirtual.plusMillis((System.currentTimeMillis() - replayStartReal) * REPLAY_SPEED) else Instant.now()

    private val frameTimes = ArrayDeque<Instant>()
    @Volatile private var lastFrameAt: Instant? = null
    private val intervals = ArrayDeque<Double>()
    private val latencies = ArrayDeque<Double>()
    private val lastTimeByAddress = HashMap<String, Instant>()
    @Volatile private var pairedRegistration: String? = null
    @Volatile private var clubPosition: LatLon? = null

    init {
        scope.launch { prefs.pairedRegistration.collect { pairedRegistration = it } }
        scope.launch { clubs.selectedClub.collect { clubPosition = it?.position } }
        scope.launch {
            combine(glider.followDevices, prefs.followEnabled) { d, on -> if (on) d.map { it.deviceId }.toSet() else emptySet() }.collect { followSet = it }
        }
    }

    fun start() {
        if (job?.isActive == true) return
        // S8, correctif : sans ce rafraîchissement, la DDB n'était chargée que par un appairage/suivi — le
        // trafic environnant affichait l'adresse radio brute (ex. « DDB179 ») au lieu de l'immatriculation
        // tant que le pilote n'avait rien appairé depuis le dernier lancement (cache DDB vide ou périmé).
        if (!replay) scope.launch(Dispatchers.IO) { runCatching { glider.ddb.refresh() } }
        job = scope.launch {
            launch { publishLoop() }
            if (replay) replayLines().collect { handle(it) } else liveLines().collect { handle(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        aprsState.value = AprsState.Idle
        publish()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun liveLines(): Flow<AprsLine> = channelFlow {
        val client = AprsClient(AprsClient.userFor(installId()), appVersion = BuildConfig.VERSION_NAME)
        val devices = combine(glider.ownDevices, glider.followDevices, prefs.followEnabled) { own, follow, on -> own + if (on) follow else emptyList() }
        val params = combine(clubs.selectedClub.map { it?.position }, devices) { pos, own -> pos to own }.distinctUntilChanged()
        params.collectLatest { (pos, own) ->
            if (pos == null) return@collectLatest
            val addresses = own.map { d ->
                when (d.deviceType) { "F" -> AddressType.FLARM; "O" -> AddressType.OGN; "I" -> AddressType.ICAO; else -> AddressType.UNKNOWN } to d.deviceId
            }
            val filter = OwnGlider.aprsFilter(pos, RADIUS_KM, addresses)
            client.lines({ filter }, aprsState).collect { send(it) }
        }
    }

    /** Enregistrement anonymisé des assets, rejoué en boucle ×[REPLAY_SPEED] avec des heures ramenées au présent. */
    private fun replayLines(): Flow<AprsLine> = flow {
        val raw = context.assets.open("ogn/replay.aprs").bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val time = Regex(":/(\\d{2})(\\d{2})(\\d{2})h")
        fun secs(l: String) = time.find(l)?.groupValues?.let { it[1].toInt() * 3600 + it[2].toInt() * 60 + it[3].toInt() }
        val t0 = raw.firstNotNullOf { secs(it) }
        val fmt = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC)
        replayStartReal = System.currentTimeMillis()
        replayStartVirtual = Instant.now()
        aprsState.value = AprsState.Connected("rejeu local", Instant.now(), "rejeu")
        var loopStart = replayStartVirtual
        while (true) {
            for (line in raw) {
                val t = secs(line) ?: continue
                val virtual = loopStart.plusSeconds((t - t0).toLong())
                while (now().isBefore(virtual)) delay(20)
                emit(AprsLine(line.replace(time, ":/${fmt.format(virtual)}h"), now()))
            }
            loopStart = now().plusSeconds(5)
            store.analyse(now())
        }
    }

    private fun handle(line: AprsLine) {
        val parsed = OgnParser.parse(line.text, line.received) as? OgnLine.Aircraft ?: return
        val fix = parsed.fix
        if (!store.add(fix)) return
        if (fix.address in followSet) _followFixes.tryEmit(fix)
        synchronized(frameTimes) {
            lastFrameAt = line.received
            frameTimes.addLast(line.received)
            while (frameTimes.isNotEmpty() && Duration.between(frameTimes.first(), line.received).seconds > 60) frameTimes.removeFirst()
        }
        synchronized(intervals) {
            lastTimeByAddress.put(fix.address, fix.time)?.let { prev ->
                val d = Duration.between(prev, fix.time).toMillis() / 1000.0
                if (d > 0 && d < 120) push(intervals, d)
            }
            push(latencies, fix.latency.toMillis() / 1000.0)
        }
    }

    private fun push(q: ArrayDeque<Double>, v: Double) {
        q.addLast(v)
        while (q.size > 600) q.removeFirst()
    }

    private suspend fun publishLoop() {
        var tick = 0
        while (true) {
            delay(1000)
            if (tick++ % 5 == 0) store.analyse(now())
            publish()
        }
    }

    private fun publish() {
        val now = now()
        val ownIds = glider.ownDevices.value.map { it.deviceId }.toSet()
        val own = ownIds + followSet
        val ownStatus = ownIds.flatMap { store.track(it) }.let { OwnGlider.status(it) }
        val aircraft = store.aircraft(now, exclude = own)
        val thermals = store.thermals(now, exclude = own)
        val (medInt, medLat) = synchronized(intervals) { OwnGlider.median(intervals.toList()) to OwnGlider.median(latencies.toList()) }
        val state = aprsState.value
        val reg = pairedRegistration
        val ownAlt = ownStatus?.takeIf { it.isFresh(now) }?.last?.altitudeM
        _traffic.value = FlightTraffic(
            aircraft = aircraft.map { a ->
                val rel = if (ownAlt != null && a.last.altitudeM != null) " " + signed((a.last.altitudeM!! - ownAlt).roundToInt()) else ""
                TrafficMark(
                    position = a.last.position,
                    trackDeg = a.last.trackDeg,
                    label = (a.label ?: "·") + rel,
                    altitudeM = a.last.altitudeM,
                    circling = a.circling,
                    id = a.address,
                    shortLabel = a.shortLabel,
                    // S8, correctif : l'adresse radio (ex. « DDB179 ») ressemblait à une fausse immatriculation
                    // sur la carte — vide plutôt, pour que FlightMap/TrafficMapScreen retombent sur leur repli
                    // déjà prévu (2 lettres, puis le type d'aéronef) au lieu d'afficher un identifiant trompeur.
                    fullLabel = a.label ?: "",
                    typeLabel = a.typeLabel,
                    speedKmh = a.last.groundSpeedKmh,
                    climbMs = a.last.climbMs,
                    ageS = Duration.between(a.last.time, now).seconds.coerceAtLeast(0),
                )
            },
            thermals = thermals.map { ThermalMark(it.position, it.climbMs, it.aircraftCount, it.ageMinutes(now)) },
            own = ownStatus?.let { st ->
                OwnOgn(st.last.position, st.last.trackDeg, st.last.altitudeM, st.last.climbMs, st.medianLatencyS, Duration.between(st.last.received, now).seconds)
            },
            live = state is AprsState.Connected,
            replay = replay,
        )
        if (followSet.isNotEmpty()) {
            val ft = followSet.flatMap { store.track(it) }.maxByOrNull { it.time }
            glider.publishFollowLine(ft?.let { f ->
                val ago = Duration.between(f.received, now).seconds
                listOfNotNull(
                    if (ago < 90) "vu il y a $ago s" else "vu il y a ${ago / 60} min",
                    f.altitudeM?.let { "${it.roundToInt()} m" },
                    f.climbMs?.let { String.format(Locale.FRANCE, "%+.1f m/s", it) },
                    clubPosition?.let { String.format(Locale.FRANCE, "%.1f km du terrain", Geo.distanceKm(it, f.position)) },
                ).joinToString(" · ")
            })
        } else glider.publishFollowLine(null)
        val clubPos = clubPosition
        _network.value = OgnNetworkUi(
            framesPerMin = synchronized(frameTimes) { frameTimes.size },
            lastFrameAgoS = lastFrameAt?.let { Duration.between(it, now).seconds },
            warning = when {
                state is AprsState.Waiting && Regex("(?i)invalid|refus").containsMatchIn(state.reason) ->
                    "Le serveur OGN a refusé la connexion (${state.reason}). Rien ne sera reçu tant que ce n'est pas corrigé."
                state is AprsState.Connected && !replay && (lastFrameAt == null || Duration.between(lastFrameAt, now).seconds > 120) ->
                    "Connexion ouverte mais aucune trame depuis plus de deux minutes : vérifiez le réseau, sinon signalez-le."
                else -> null
            },
            statusLabel = when (state) {
                AprsState.Idle -> "Réseau OGN à l'arrêt (app en arrière-plan)"
                is AprsState.Connecting -> "Connexion à aprs.glidernet.org…" + if (state.attempt > 1) " (essai ${state.attempt})" else ""
                is AprsState.Connected -> if (replay) "Rejeu local ×$REPLAY_SPEED" else "Connecté · ${state.server?.takeIf { !it.startsWith("aprsc") } ?: "aprs.glidernet.org"} · ${store.framesAccepted} trames reçues"
                is AprsState.Waiting -> "Hors réseau (${state.reason}) · nouvel essai dans ${state.retryInSeconds} s"
            },
            connected = state is AprsState.Connected,
            replay = replay,
            radiusKm = RADIUS_KM,
            aircraftCount = aircraft.size,
            thermalCount = thermals.size,
            medianIntervalS = medInt,
            medianLatencyS = medLat,
            frames = store.framesAccepted,
            ownLabel = reg?.takeIf { glider.ownDevices.value.isNotEmpty() },
            ownSeen = ownStatus?.isFresh(now) == true,
            ownLine = ownStatus?.let { st ->
                val ago = Duration.between(st.last.received, now)
                val dist = clubPos?.let { Geo.distanceKm(it, st.last.position) }
                listOfNotNull(
                    if (ago.seconds < 90) "vu il y a ${ago.seconds} s" else "vu il y a ${ago.toMinutes()} min",
                    dist?.let { String.format(Locale.FRANCE, "%.1f km du terrain", it) },
                    st.last.altitudeM?.let { "${it.roundToInt()} m" },
                    st.last.climbMs?.let { String.format(Locale.FRANCE, "%+.1f m/s", it) },
                    st.medianIntervalS?.let { String.format(Locale.FRANCE, "cadence %.0f s", it) },
                    st.medianLatencyS?.let { String.format(Locale.FRANCE, "retard %.1f s", it) },
                ).joinToString(" · ")
            },
            ddbStatus = ddbStatusLabel(now),
        )
    }

    /**
     * S8, correctif : diagnostic visible sans avoir à voler — si la DDB n'est jamais chargée, tout le
     * trafic environnant s'affiche par son adresse radio au lieu de son immatriculation (l'appairage de
     * son propre planeur, lui, ne dépend pas de cette copie — voir GliderRepository.pair()).
     */
    private fun ddbStatusLabel(now: Instant): String {
        val st = glider.ddb.status()
        val at = st.fetchedAt ?: return "Base des immatriculations (DDB) : jamais chargée" + if (replay) " (rejeu, normal)" else " — réessaiera au prochain démarrage"
        val age = Duration.between(at, now)
        val ago = if (age.toHours() < 20) "il y a ${age.toMinutes().coerceAtLeast(0)} min" else DDB_FMT.format(at)
        return "Base des immatriculations (DDB) : ${st.deviceCount} appareils, ${if (st.offline) "copie locale" else "à jour"} $ago"
    }

    private fun signed(n: Int) = (if (n >= 0) "+" else "−") + kotlin.math.abs(n)

    /**
     * Numéro aléatoire tiré au premier lancement et gardé localement (S9) : sert seulement à composer
     * l'indicatif APRS en lecture seule « GLIDYnnnn ». Plus d'ANDROID_ID (identifiant matériel
     * déconseillé par la Play Store, et à déclarer dans le formulaire Sécurité des données).
     */
    private fun installId(): Long {
        val sp = context.getSharedPreferences("glidy_install", Context.MODE_PRIVATE)
        val known = sp.getLong("install_id", -1L)
        if (known >= 0) return known
        val fresh = java.security.SecureRandom().nextInt(Int.MAX_VALUE).toLong()
        sp.edit { putLong("install_id", fresh) }
        return fresh
    }

    @Suppress("unused")
    private fun near(a: LatLon, b: LatLon) = Geo.distanceKm(a, b)

    companion object {
        /** Rayon du filtre APRS : de quoi remplir l'onglet Carte (sud de la France). */
        const val RADIUS_KM = 250
        /** Au-delà, seules les dernières positions sont gardées (mémoire). */
        const val DETAIL_KM = 60.0
        const val REPLAY_SPEED = 4L
        private val DDB_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.FRANCE).withZone(ZoneId.of("Europe/Paris"))
    }
}
