package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/** Aéronef visible dans le trafic, après application des choix de confidentialité. */
data class TrafficAircraft(
    val address: String,
    val aircraftType: Int,
    /** Libellé affichable : immatriculation ou numéro de concours si la DDB l'autorise, sinon null (anonyme). */
    val label: String?,
    val last: OgnFix,
    val circling: Boolean,
) {
    /**
     * Deux caractères pour l'icône de la carte : les deux dernières lettres de l'immatriculation quand la DDB
     * l'autorise, sinon les deux derniers caractères de l'adresse radio (les avions et jets vus par leur adresse
     * ICAO ne sont pas dans la base).
     */
    val shortLabel: String
        get() = label?.filter { it.isLetterOrDigit() }?.takeLast(2)?.uppercase()?.takeIf { it.length == 2 }
            ?: address.takeLast(2).uppercase()

    val typeLabel: String get() = when (aircraftType) {
        1 -> "Planeur"; 2 -> "Remorqueur"; 3 -> "Hélicoptère"; 4 -> "Parachutiste"; 5 -> "Largueur"
        6 -> "Delta"; 7 -> "Parapente"; 8 -> "Avion"; 9 -> "Jet"; 11 -> "Ballon"; 12 -> "Dirigeable"
        13 -> "Drone"; 14 -> "Planeur électrique"; else -> "Aéronef"
    }
}

/** Pompe détectée dans les spirales des autres planeurs. */
data class NetworkThermal(
    val position: LatLon,
    /** Montée moyenne mesurée pendant la spirale (m/s). */
    val climbMs: Double,
    val topAltitudeM: Double?,
    val lastSeen: Instant,
    val aircraftCount: Int,
) {
    fun ageMinutes(now: Instant): Long = Duration.between(lastSeen, now).toMinutes()
}

/** Spirale d'un aéronef, détectée sur sa trajectoire. */
data class CirclingEpisode(
    val address: String,
    val start: Instant,
    val end: Instant,
    val center: LatLon,
    val turnDeg: Double,
    val climbMs: Double,
    val topAltitudeM: Double?,
)

/**
 * Détection de spirale sur une suite de positions d'un même aéronef.
 * Le cap est déduit des trames (champ route, ou taux de virage « rot ») ; une spirale est retenue quand le virage
 * cumulé dans un même sens dépasse [minTurnDeg] en au moins [minDuration] ; la montée vient de l'altitude GPS.
 */
object ThermalDetector {
    const val MIN_TURN_RATE_DEG_S = 4.0
    const val MAX_GAP_S = 25L

    fun episodes(fixes: List<OgnFix>, minTurnDeg: Double = 540.0, minDuration: Duration = Duration.ofSeconds(30)): List<CirclingEpisode> {
        val out = ArrayList<CirclingEpisode>()
        val sorted = fixes.sortedBy { it.time }
        var run = ArrayList<OgnFix>()
        var turn = 0.0
        var sign = 0

        fun close() {
            if (run.size >= 3) {
                val dur = Duration.between(run.first().time, run.last().time)
                if (abs(turn) >= minTurnDeg && dur >= minDuration) {
                    val alts = run.mapNotNull { it.altitudeM }
                    val climb = if (alts.size >= 2 && dur.seconds > 0) {
                        (run.last().altitudeM ?: alts.last()) .minus(run.first().altitudeM ?: alts.first()) / dur.seconds
                    } else run.mapNotNull { it.climbMs }.average().takeIf { !it.isNaN() } ?: 0.0
                    out += CirclingEpisode(
                        address = run.first().address,
                        start = run.first().time,
                        end = run.last().time,
                        center = LatLon(run.map { it.position.lat }.average(), run.map { it.position.lon }.average()),
                        turnDeg = abs(turn),
                        climbMs = climb,
                        topAltitudeM = alts.maxOrNull(),
                    )
                }
            }
            run = ArrayList(); turn = 0.0; sign = 0
        }

        for (i in sorted.indices) {
            val f = sorted[i]
            val prev = sorted.getOrNull(i - 1)
            if (prev == null) { run.add(f); continue }
            val dt = Duration.between(prev.time, f.time).seconds
            if (dt <= 0) continue
            if (dt > MAX_GAP_S) { close(); run.add(f); continue }
            val rate = turnRate(prev, f, dt)
            val s = if ((rate ?: 0.0) > 0) 1 else -1
            if (rate != null && abs(rate) >= MIN_TURN_RATE_DEG_S && (sign == 0 || s == sign)) {
                if (run.isEmpty()) run.add(prev)
                sign = s
                turn += rate * dt
                run.add(f)
            } else {
                close()
                run.add(f)
            }
        }
        close()
        return out
    }

    /** °/s positifs à droite : différence de route entre deux trames, sinon moyenne des taux « rot ». */
    private fun turnRate(a: OgnFix, b: OgnFix, dt: Long): Double? {
        val ta = a.trackDeg
        val tb = b.trackDeg
        if (ta != null && tb != null && dt <= 12) {
            var d = tb - ta
            while (d > 180) d -= 360
            while (d < -180) d += 360
            return d / dt
        }
        val ra = a.turnDegS
        val rb = b.turnDegS
        return when {
            ra != null && rb != null -> (ra + rb) / 2
            rb != null -> rb
            else -> null
        }
    }

    /** Regroupe les spirales proches (même pompe) : distance < [mergeKm] et moins de [mergeWindow] d'écart. */
    fun thermals(episodes: List<CirclingEpisode>, minClimbMs: Double = 0.3, mergeKm: Double = 1.2, mergeWindow: Duration = Duration.ofMinutes(15)): List<NetworkThermal> {
        val groups = ArrayList<MutableList<CirclingEpisode>>()
        for (e in episodes.filter { it.climbMs >= minClimbMs }.sortedBy { it.end }) {
            val g = groups.firstOrNull { grp ->
                val last = grp.maxBy { it.end }
                Geo.distanceKm(last.center, e.center) < mergeKm && Duration.between(last.end, e.end).abs() <= mergeWindow
            }
            if (g != null) g += e else groups += mutableListOf(e)
        }
        return groups.map { g ->
            val weight = g.sumOf { Duration.between(it.start, it.end).seconds.toDouble() }.coerceAtLeast(1.0)
            NetworkThermal(
                position = LatLon(g.sumOf { it.center.lat * Duration.between(it.start, it.end).seconds } / weight,
                    g.sumOf { it.center.lon * Duration.between(it.start, it.end).seconds } / weight),
                climbMs = g.sumOf { it.climbMs * Duration.between(it.start, it.end).seconds } / weight,
                topAltitudeM = g.mapNotNull { it.topAltitudeM }.maxOrNull(),
                lastSeen = g.maxOf { it.end },
                aircraftCount = g.map { it.address }.distinct().size,
            )
        }
    }
}

/** Identité DDB utile à l'affichage : null = aucun enregistrement (anonyme mais suivi autorisé). */
fun interface DeviceDirectory {
    fun lookup(address: String): DdbDevice?
}

/**
 * Trafic OGN en mémoire, borné dans le temps : trajectoires 30 min, pompes 45 min, rien au-delà de 24 h.
 * Les trames furtives, « no-tracking » ou d'appareils dont la DDB refuse le suivi ne sont jamais conservées.
 */
class TrafficStore(
    private val directory: DeviceDirectory = DeviceDirectory { null },
    private val trackWindow: Duration = Duration.ofMinutes(30),
    private val thermalWindow: Duration = Duration.ofMinutes(45),
    /** Centre de la zone d'intérêt : au-delà de [detailRadiusKm], seule la dernière position est gardée. */
    private val centre: () -> com.neutronstar.glidercopilot.domain.LatLon? = { null },
    private val detailRadiusKm: Double = 60.0,
) {
    private val tracks = LinkedHashMap<String, ArrayDeque<OgnFix>>()
    private val labels = HashMap<String, String?>()
    private val episodes = ArrayList<CirclingEpisode>()
    private val lastAnalysis = HashMap<String, Instant>()

    var framesAccepted = 0L; private set
    var framesRejected = 0L; private set

    @Synchronized
    fun add(fix: OgnFix): Boolean {
        if (fix.hidden) { framesRejected++; return false }
        val device = directory.lookup(fix.address)
        if (device != null && !device.tracked) { framesRejected++; return false }
        labels[fix.address] = device?.takeIf { it.identified }?.let { it.competitionNumber ?: it.registration }
        val q = tracks.getOrPut(fix.address) { ArrayDeque() }
        // même trame relayée par plusieurs récepteurs, ou trame plus ancienne arrivée en retard
        q.lastOrNull()?.let { if (!fix.time.isAfter(it.time)) return false }
        q.addLast(fix)
        // au loin (carte du sud de la France), la trajectoire n'apporte rien : on ne garde que les dernières positions
        val c = centre()
        if (c != null && com.neutronstar.glidercopilot.domain.Geo.distanceKm(c, fix.position) > detailRadiusKm) {
            while (q.size > 3) q.removeFirst()
        }
        framesAccepted++
        return true
    }

    @Synchronized
    fun purge(now: Instant) {
        val horizon = now.minus(trackWindow)
        val it = tracks.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            while (e.value.isNotEmpty() && e.value.first().time.isBefore(horizon)) e.value.removeFirst()
            if (e.value.isEmpty()) { it.remove(); labels.remove(e.key); lastAnalysis.remove(e.key) }
        }
        episodes.removeAll { it.end.isBefore(now.minus(thermalWindow)) || it.end.isBefore(now.minus(Duration.ofHours(24))) }
    }

    /** Relance la détection de spirales pour les trajectoires qui ont avancé depuis la dernière analyse. */
    @Synchronized
    fun analyse(now: Instant) {
        purge(now)
        for ((addr, q) in tracks) {
            val last = q.lastOrNull() ?: continue
            if (lastAnalysis[addr] == last.time) continue
            lastAnalysis[addr] = last.time
            val found = ThermalDetector.episodes(q.toList())
            episodes.removeAll { it.address == addr }
            episodes += found
        }
    }

    @Synchronized
    fun aircraft(now: Instant, maxAge: Duration = Duration.ofMinutes(2), exclude: Set<String> = emptySet()): List<TrafficAircraft> =
        tracks.mapNotNull { (addr, q) ->
            val last = q.lastOrNull() ?: return@mapNotNull null
            if (addr in exclude || Duration.between(last.time, now) > maxAge) return@mapNotNull null
            val circling = episodes.any { it.address == addr && Duration.between(it.end, last.time).seconds <= 20 }
            TrafficAircraft(addr, last.aircraftType, labels[addr], last, circling)
        }

    @Synchronized
    fun thermals(now: Instant, exclude: Set<String> = emptySet()): List<NetworkThermal> =
        ThermalDetector.thermals(episodes.filter { it.address !in exclude && !it.end.isBefore(now.minus(thermalWindow)) })

    @Synchronized
    fun track(address: String): List<OgnFix> = tracks[address]?.toList().orEmpty()

    @Synchronized
    fun size(): Int = tracks.size
}

/** Suivi de mon planeur par ses adresses FLARM/OGN : dernière position, vario OGN, cadence et retard mesurés. */
data class OwnGliderStatus(
    val last: OgnFix,
    val medianIntervalS: Double?,
    val medianLatencyS: Double?,
    val frames: Int,
) {
    fun isFresh(now: Instant, max: Duration = Duration.ofSeconds(60)) = Duration.between(last.received, now) <= max
}

object OwnGlider {
    fun status(fixes: List<OgnFix>): OwnGliderStatus? {
        val sorted = fixes.sortedBy { it.time }
        val last = sorted.lastOrNull() ?: return null
        val intervals = sorted.zipWithNext { a, b -> Duration.between(a.time, b.time).seconds.toDouble() }.filter { it > 0 && it < 600 }
        val latencies = sorted.takeLast(30).map { it.latency.toMillis() / 1000.0 }
        return OwnGliderStatus(last, median(intervals), median(latencies), sorted.size)
    }

    fun median(v: List<Double>): Double? = if (v.isEmpty()) null else v.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Filtre APRS-IS : zone autour du terrain + mes appareils où qu'ils soient (b/ = indicatif exact). */
    fun aprsFilter(center: LatLon, radiusKm: Int, ownAddresses: List<Pair<AddressType, String>>): String {
        val zone = "r/%.4f/%.4f/%d".format(java.util.Locale.ROOT, center.lat, center.lon, radiusKm)
        if (ownAddresses.isEmpty()) return zone
        val calls = ownAddresses.flatMap { (type, addr) ->
            when (type) {
                AddressType.FLARM -> listOf("FLR$addr")
                AddressType.OGN -> listOf("OGN$addr")
                AddressType.ICAO -> listOf("ICA$addr")
                AddressType.UNKNOWN -> listOf("FLR$addr", "OGN$addr", "ICA$addr")
            }
        }
        return "$zone b/" + calls.joinToString("/")
    }

    /** Distance horizontale approximative utile au tri du trafic (km). */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val kx = 111.32 * cos(Math.toRadians((a.lat + b.lat) / 2))
        return max(0.0, kotlin.math.hypot((a.lon - b.lon) * kx, (a.lat - b.lat) * 110.57))
    }
}
