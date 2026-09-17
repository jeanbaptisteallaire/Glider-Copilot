package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Type d'adresse radio (bits aa du champ id). */
enum class AddressType { UNKNOWN, ICAO, FLARM, OGN }

/**
 * Trame de position d'aéronef OGN (format APRS « OGN flavoured »).
 * Unités converties : altitude m, vitesse km/h, montée m/s, virage °/s.
 */
data class OgnFix(
    /** Adresse radio sur 6 chiffres hexadécimaux, identique au device_id de la DDB. */
    val address: String,
    val addressType: AddressType,
    /** 1 planeur, 2 remorqueur, 3 hélico, 4 parachutiste, 5 largueur, 6 delta, 7 parapente, 8 avion, 9 jet, 11 ballon, 12 dirigeable, 13 drone. */
    val aircraftType: Int,
    val callsign: String,
    /** Heure UTC portée par la trame (émission). */
    val time: Instant,
    /** Heure de réception sur le téléphone. */
    val received: Instant,
    val position: LatLon,
    val altitudeM: Double?,
    val trackDeg: Double?,
    val groundSpeedKmh: Double?,
    val climbMs: Double?,
    val turnDegS: Double?,
    val stealth: Boolean,
    val noTracking: Boolean,
    val receiver: String?,
) {
    /** Retard réseau mesuré : réception − émission. */
    val latency: Duration get() = Duration.between(time, received)

    /** Diffusion interdite par le pilote (furtif ou « no-tracking ») : la trame doit être ignorée. */
    val hidden: Boolean get() = stealth || noTracking
}

sealed interface OgnLine {
    data class Aircraft(val fix: OgnFix) : OgnLine
    /** Commentaire serveur (« # aprsc … ») ou keepalive. */
    data class Server(val text: String) : OgnLine
    /** Balise de récepteur, statut, ou format non géré. */
    data class Other(val raw: String) : OgnLine
}

object OgnParser {
    private const val FT = 0.3048
    private const val KT_TO_KMH = 1.852

    private val POSITION = Regex(
        "^(?<call>[A-Za-z0-9]{3,9})>(?<dst>[A-Z0-9]+),(?<path>[^:]*):[/@]" +
            "(?<hh>\\d{2})(?<mm>\\d{2})(?<ss>\\d{2})h" +
            "(?<lat>\\d{4}\\.\\d{2})(?<ns>[NS])." +
            "(?<lon>\\d{5}\\.\\d{2})(?<ew>[EW])." +
            "(?:(?<crs>\\d{3})/(?<spd>\\d{3}))?" +
            "(?:/A=(?<alt>-?\\d{5,6}))?(?<rest>.*)$",
    )
    private val ID = Regex("\\bid([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\\b")
    private val FPM = Regex("(?<![\\w.])([+-]\\d+)fpm\\b")
    private val ROT = Regex("(?<![\\w.])([+-]\\d+(?:\\.\\d+)?)rot\\b")
    private val PRECISION = Regex("!W(\\d)(\\d)!")

    fun parse(line: String, received: Instant): OgnLine {
        if (line.startsWith("#")) return OgnLine.Server(line)
        val m = POSITION.matchEntire(line.trimEnd()) ?: return OgnLine.Other(line)
        val g = m.groups
        val rest = g["rest"]!!.value
        val id = ID.find(rest) ?: return OgnLine.Other(line)
        val flags = id.groupValues[1].toInt(16)

        var lat = g["lat"]!!.value.let { it.substring(0, 2).toInt() + it.substring(2).toDouble() / 60 }
        var lon = g["lon"]!!.value.let { it.substring(0, 3).toInt() + it.substring(3).toDouble() / 60 }
        PRECISION.find(rest)?.let {
            lat += it.groupValues[1].toInt() * 0.001 / 60
            lon += it.groupValues[2].toInt() * 0.001 / 60
        }
        if (g["ns"]!!.value == "S") lat = -lat
        if (g["ew"]!!.value == "W") lon = -lon

        val speedKt = g["spd"]?.value?.toDouble()
        val track = g["crs"]?.value?.toDouble()
        val path = g["path"]!!.value.split(',')
        val fix = OgnFix(
            address = id.groupValues[2].uppercase(),
            addressType = AddressType.entries[flags and 0x03],
            aircraftType = (flags shr 2) and 0x0F,
            callsign = g["call"]!!.value,
            time = utcTime(g["hh"]!!.value.toInt(), g["mm"]!!.value.toInt(), g["ss"]!!.value.toInt(), received),
            received = received,
            position = LatLon(lat, lon),
            altitudeM = g["alt"]?.value?.toDouble()?.times(FT),
            // cap 000 et vitesse 000 : aéronef au sol ou cap inconnu
            trackDeg = track?.takeIf { !(it == 0.0 && (speedKt ?: 0.0) == 0.0) },
            groundSpeedKmh = speedKt?.times(KT_TO_KMH),
            climbMs = FPM.find(rest)?.groupValues?.get(1)?.toDouble()?.times(FT / 60),
            turnDegS = ROT.find(rest)?.groupValues?.get(1)?.toDouble()?.times(3.0),   // 1 rot = un demi-tour par minute = 3 °/s
            stealth = flags and 0x80 != 0,
            noTracking = flags and 0x40 != 0,
            receiver = path.lastOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("q") },
        )
        return OgnLine.Aircraft(fix)
    }

    /** L'heure HHMMSS UTC est rattachée au jour de réception, à ±12 h près (passage de minuit). */
    fun utcTime(hh: Int, mm: Int, ss: Int, reference: Instant): Instant {
        val day = reference.truncatedTo(ChronoUnit.DAYS)
        var t = day.plusSeconds(LocalTime.of(hh % 24, mm % 60, ss % 60).toSecondOfDay().toLong())
        if (t.isAfter(reference.plus(Duration.ofHours(12)))) t = t.minus(Duration.ofDays(1))
        else if (t.isBefore(reference.minus(Duration.ofHours(12)))) t = t.plus(Duration.ofDays(1))
        return t
    }

    @Suppress("unused")
    private val utc = ZoneOffset.UTC
}
