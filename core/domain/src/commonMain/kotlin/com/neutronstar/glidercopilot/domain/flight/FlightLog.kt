package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.LatLon
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import com.neutronstar.glidercopilot.domain.zeroPad
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import kotlin.math.abs
import kotlin.math.roundToInt

/** Position GNSS utilisée par le moteur de vol. */
data class GpsFix(
    val time: Instant,
    val position: LatLon,
    val altitudeM: Double?,
    val groundSpeedKmh: Double?,
    val trackDeg: Double?,
    val accuracyM: Double?,
    /** Hauteur GNSS au-dessus de l'ellipsoïde WGS84 (ce que donne Android), pour l'IGC. [altitudeM] est l'altitude au-dessus du niveau de la mer quand elle est connue. */
    val ellipsoidAltM: Double? = null,
)

enum class FlightPhase { GROUND, FLYING, LANDED }

sealed interface FlightEvent {
    data class Takeoff(val time: Instant) : FlightEvent
    data class Landing(val time: Instant, val duration: Duration) : FlightEvent
}

/**
 * Détection automatique : décollage quand la vitesse sol dépasse [takeoffKmh] pendant [takeoffHold]
 * (réglage « Chrono automatique au-dessus de 50 km/h »), atterrissage sous [landingKmh] pendant [landingHold].
 */
class TakeoffDetector(
    private val takeoffKmh: Double = 50.0,
    private val takeoffHold: Duration = (3).seconds,
    private val landingKmh: Double = 10.0,
    private val landingHold: Duration = (30).seconds,
) {
    var phase = FlightPhase.GROUND; private set
    var takeoffTime: Instant? = null; private set
    var landingTime: Instant? = null; private set
    private var fastSince: Instant? = null
    private var slowSince: Instant? = null

    fun flightDuration(now: Instant): Duration = takeoffTime?.let { (landingTime ?: now) - it } ?: Duration.ZERO

    fun onFix(fix: GpsFix): FlightEvent? {
        val speed = fix.groundSpeedKmh ?: return null
        return when (phase) {
            FlightPhase.GROUND, FlightPhase.LANDED -> {
                if (speed >= takeoffKmh) {
                    val since = fastSince ?: fix.time.also { fastSince = it }
                    if ((fix.time - since) >= takeoffHold) {
                        phase = FlightPhase.FLYING; takeoffTime = since; landingTime = null; slowSince = null
                        FlightEvent.Takeoff(since)
                    } else null
                } else { fastSince = null; null }
            }
            FlightPhase.FLYING -> {
                if (speed < landingKmh) {
                    val since = slowSince ?: fix.time.also { slowSince = it }
                    if ((fix.time - since) >= landingHold) {
                        phase = FlightPhase.LANDED; landingTime = since; fastSince = null
                        FlightEvent.Landing(since, (since - takeoffTime!!))
                    } else null
                } else { slowSince = null; null }
            }
        }
    }
}

/** Point de trace IGC (enregistrement B). */
data class IgcFix(val time: Instant, val position: LatLon, val valid: Boolean, val pressureAltM: Int?, val gnssAltM: Int?)

/**
 * Fichier IGC d'un enregistreur non approuvé (code fabricant XXX, pas d'enregistrement G) :
 * lisible par SeeYou, WeGlide, XCSoar ; ne vaut pas preuve pour un badge ou une compétition.
 */
object Igc {
    /** ddMMyy (ancien DateTimeFormatter « ddMMyy »). */
    private fun day(d: LocalDate) = d.dayOfMonth.zeroPad(2) + d.month.number.zeroPad(2) + (d.year % 100).zeroPad(2)

    /** HHmmss en UTC. */
    private fun hms(t: Instant) = t.toLocalDateTime(TimeZone.UTC).let { it.hour.zeroPad(2) + it.minute.zeroPad(2) + it.second.zeroPad(2) }

    /**
     * Lecture « ddMMyy » comme DateTimeFormatter.ofPattern (résolution SMART) : années 2000–2099, jour 1–31 ramené au
     * dernier jour du mois s'il le dépasse ; null si illisible.
     */
    private fun parseDay(s: String): LocalDate? {
        if (s.length != 6 || !s.all { it in '0'..'9' }) return null
        val d = s.substring(0, 2).toInt(); val m = s.substring(2, 4).toInt(); val y = 2000 + s.substring(4, 6).toInt()
        if (m !in 1..12 || d !in 1..31) return null
        val first = LocalDate(y, m, 1)
        val last = first.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).dayOfMonth
        return LocalDate(y, m, minOf(d, last))
    }

    data class Header(
        val date: LocalDate,
        val pilot: String = "",
        val gliderType: String = "",
        val gliderId: String = "",
        val appVersion: String = "",
        /** ELL : hauteur ellipsoïdale (Android) ; GEO : altitude géoïde. */
        val gnssDatum: String = "ELL",
        val comment: String? = null,
        /** Plateforme de l'enregistreur (iOS / KMP) : « Android » par défaut, en-tête Android inchangé. */
        val platform: String = "Android",
    )

    fun headerLines(h: Header): List<String> = listOf(
        "AXXXGLYGLIDY",
        "HFDTEDATE:${day(h.date)},01",
        "HFPLTPILOTINCHARGE:${clean(h.pilot)}",
        "HFCM2CREW2:",
        "HFGTYGLIDERTYPE:${clean(h.gliderType)}",
        "HFGIDGLIDERID:${clean(h.gliderId)}",
        "HFDTMGPSDATUM:WGS84",
        "HFRFWFIRMWAREVERSION:${clean(h.appVersion)}",
        "HFRHWHARDWAREVERSION:${clean(h.platform)}",
        "HFFTYFRTYPE:WINDGLIDER,${clean(h.platform)}",
        "HFGPSRECEIVER:${clean(h.platform)},telephone",
        "HFPRSPRESSALTSENSOR:${clean(h.platform)},telephone",
        "HFALGALTGPS:${clean(h.gnssDatum)}",
        "HFALPALTPRESSURE:ISA",
        "LXXXWINDGLIDER enregistreur non approuve : trace indicative",
    ) + listOfNotNull(h.comment?.let { "LXXX" + clean(it) })

    fun bRecord(f: IgcFix): String {
        val lat = abs(f.position.lat); val lon = abs(f.position.lon)
        val latDeg = lat.toInt(); val latMin = ((lat - latDeg) * 60000).roundToInt().coerceAtMost(59999)
        val lonDeg = lon.toInt(); val lonMin = ((lon - lonDeg) * 60000).roundToInt().coerceAtMost(59999)
        return "B" + hms(f.time) +
            latDeg.zeroPad(2) + latMin.zeroPad(5) + (if (f.position.lat >= 0) "N" else "S") +
            lonDeg.zeroPad(3) + lonMin.zeroPad(5) + (if (f.position.lon >= 0) "E" else "W") +
            (if (f.valid) "A" else "V") + alt5(f.pressureAltM) + alt5(f.gnssAltM)
    }

    private fun alt5(v: Int?): String = when {
        v == null -> "00000"
        v < 0 -> "-" + (-v).coerceAtMost(9999).zeroPad(4)
        else -> v.coerceAtMost(99999).zeroPad(5)
    }

    private fun clean(s: String) = s.filter { it.code in 32..126 }

    /** Nom long IGC : AAAA-MM-JJ-XXX-GLY-NN.igc. */
    fun fileName(date: LocalDate, index: Int) = "$date-XXX-GLY-${index.zeroPad(2)}.igc"

    fun parse(text: String): List<IgcFix> {
        var date = LocalDate(2000, 1, 1)
        val out = ArrayList<IgcFix>()
        var dayOffset = 0L
        var lastSec = -1
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.startsWith("HFDTE")) {
                val digits = line.removePrefix("HFDTE").removePrefix("DATE:").take(6)
                parseDay(digits)?.let { date = it }
            }
            if (!line.startsWith("B") || line.length < 35) continue
            val sec = line.substring(1, 3).toInt() * 3600 + line.substring(3, 5).toInt() * 60 + line.substring(5, 7).toInt()
            if (lastSec >= 0 && sec < lastSec - 43200) dayOffset++
            lastSec = sec
            val lat = (line.substring(7, 9).toInt() + line.substring(9, 14).toInt() / 60000.0) * (if (line[14] == 'S') -1 else 1)
            val lon = (line.substring(15, 18).toInt() + line.substring(18, 23).toInt() / 60000.0) * (if (line[23] == 'W') -1 else 1)
            fun alt(s: String) = s.toIntOrNull()
            out += IgcFix(
                time = date.plus(dayOffset, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC) + sec.toLong().seconds,
                position = LatLon(lat, lon),
                valid = line[24] == 'A',
                pressureAltM = alt(line.substring(25, 30)),
                gnssAltM = alt(line.substring(30, 35)),
            )
        }
        return out
    }
}

/**
 * Écrit la trace au fil de l'eau (une ligne B par seconde au plus). [sink] reçoit les lignes : sur Android un fichier
 * vidé régulièrement, pour qu'une coupure ne fasse perdre que les dernières secondes.
 */
class IgcRecorder(header: Igc.Header, private val sink: (String) -> Unit) {
    private var lastSecond = Long.MIN_VALUE
    var fixes = 0; private set

    init { Igc.headerLines(header).forEach(sink) }

    fun record(fix: IgcFix) {
        val s = fix.time.epochSeconds
        if (s <= lastSecond) return
        lastSecond = s
        sink(Igc.bRecord(fix))
        fixes++
    }
}

/** Annonces vocales en français, avec hystérésis et délai minimal entre deux annonces du même type. */
class AnnouncementPolicy(private val cooldown: Duration = (45).seconds) {
    enum class Kind { TAKEOFF, LANDING, SOURCE, MARGIN }

    private val last = HashMap<Kind, Instant>()
    private var marginLevel = 2   // 2 confortable, 1 faible, 0 sous la sécurité
    private var lastSource: VarioSource? = null

    private fun allowed(k: Kind, now: Instant, force: Boolean = false): Boolean {
        val l = last[k]
        if (!force && l != null && (now - l) < cooldown) return false
        last[k] = now
        return true
    }

    fun onEvent(e: FlightEvent, now: Instant): String? = when (e) {
        is FlightEvent.Takeoff -> if (allowed(Kind.TAKEOFF, now, force = true)) "Décollage détecté, chrono lancé" else null
        is FlightEvent.Landing -> if (allowed(Kind.LANDING, now, force = true)) {
            val m = e.duration.inWholeMinutes
            "Atterrissage. Vol de " + (if (m >= 60) "${m / 60} heure${if (m / 60 > 1) "s" else ""} ${m % 60} minutes" else "$m minutes") + " enregistré"
        } else null
    }

    fun onSource(source: VarioSource, now: Instant): String? {
        val prev = lastSource
        lastSource = source
        if (prev == null || prev == source) return null
        val text = when (source) {
            VarioSource.BARO_ACCEL, VarioSource.BARO -> if (prev == VarioSource.OGN || prev == VarioSource.NONE) "Vario du téléphone rétabli" else null
            VarioSource.OGN -> "Baromètre indisponible, vario OGN en secours"
            VarioSource.NONE -> "Vario indisponible"
        } ?: return null
        return if (allowed(Kind.SOURCE, now)) text else null
    }

    /** Marge de sécurité : « faible » sous [lowM], « sous la sécurité » sous 0, rétablie au-dessus de [lowM] + 50 m. */
    fun onMargin(marginM: Double, now: Instant, lowM: Double = 150.0): String? {
        val level = when {
            marginM < 0 -> 0
            marginM < lowM -> 1
            marginM > lowM + 50 -> 2
            else -> marginLevel.coerceAtMost(1).coerceAtLeast(if (marginLevel == 2) 2 else 1)
        }
        val prev = marginLevel
        marginLevel = level
        if (level == prev) return null
        val text = when {
            level == 0 -> "Sous la sécurité. Cap terrain"
            level == 1 && prev == 2 -> "Marge faible"
            level == 2 && prev < 2 -> "Marge rétablie"
            else -> null
        } ?: return null
        return if (allowed(Kind.MARGIN, now, force = level == 0)) text else null
    }
}

enum class VarioSource { BARO_ACCEL, BARO, OGN, NONE }
