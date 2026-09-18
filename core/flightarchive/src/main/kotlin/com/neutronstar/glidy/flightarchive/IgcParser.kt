package com.neutronstar.glidy.flightarchive

import java.io.BufferedReader
import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class IgcMetadata(
    val date: LocalDate,
    val pilot: String?,
    val gliderType: String?,
    val gliderId: String?,
)

data class IgcTrackPoint(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val pressureAltitudeMeters: Int?,
    val gpsAltitudeMeters: Int?,
)

data class ParsedIgcFlight(
    val metadata: IgcMetadata,
    val points: List<IgcTrackPoint>,
    val summary: FlightSummary,
    val warnings: List<IgcParseWarning>,
)

enum class IgcWarningCode {
    MALFORMED_B_RECORD,
    INVALID_COORDINATES,
    INVALID_FIX,
    NON_MONOTONIC_TIME,
}

data class IgcParseWarning(
    val lineNumber: Int,
    val code: IgcWarningCode,
    val message: String,
)

sealed interface IgcParseError {
    data object EmptyFile : IgcParseError
    data object MissingDateHeader : IgcParseError
    data class InvalidDateHeader(val lineNumber: Int) : IgcParseError
    data object NoValidFix : IgcParseError
}

sealed interface IgcParseResult {
    data class Success(val flight: ParsedIgcFlight) : IgcParseResult
    data class Failure(val error: IgcParseError) : IgcParseResult
}

interface IgcFlightParser {
    fun parse(reader: Reader): IgcParseResult
}

/**
 * Parseur IGC sans dépendance Android. Le Reader est parcouru une seule fois et
 * le texte IGC original n'est jamais modifié.
 */
class StreamingIgcFlightParser : IgcFlightParser {
    override fun parse(reader: Reader): IgcParseResult {
        val buffered = if (reader is BufferedReader) reader else reader.buffered()
        var sawContent = false
        var date: LocalDate? = null
        var invalidDateLine: Int? = null
        var pilot: String? = null
        var gliderType: String? = null
        var gliderId: String? = null
        var bRecordCount = 0
        var dayOffset = 0L
        var previousSecondOfDay: Int? = null
        var previousInstant: Instant? = null
        val points = mutableListOf<IgcTrackPoint>()
        val warnings = mutableListOf<IgcParseWarning>()

        buffered.forEachLineIndexed { lineNumber, rawLine ->
            val line = rawLine.trimEnd()
            if (line.isNotBlank()) sawContent = true

            when {
                isDateHeader(line) -> {
                    val parsedDate = parseDateHeader(line)
                    if (parsedDate == null) invalidDateLine = lineNumber else date = parsedDate
                }
                line.startsWith("HFPLT") || line.startsWith("HOPLT") -> pilot = headerValue(line)
                line.startsWith("HFGTY") || line.startsWith("HOGTY") -> gliderType = headerValue(line)
                line.startsWith("HFGID") || line.startsWith("HOGID") -> gliderId = headerValue(line)
                line.startsWith("B") -> {
                    bRecordCount += 1
                    val flightDate = date
                    if (flightDate == null) return@forEachLineIndexed

                    when (val record = parseBRecord(line)) {
                        is BRecordResult.Malformed -> warnings += IgcParseWarning(
                            lineNumber = lineNumber,
                            code = record.code,
                            message = record.message,
                        )
                        is BRecordResult.Valid -> {
                            if (!record.isValidFix) {
                                warnings += IgcParseWarning(
                                    lineNumber,
                                    IgcWarningCode.INVALID_FIX,
                                    "Point marqué invalide par l'enregistreur",
                                )
                                return@forEachLineIndexed
                            }

                            val previousSecond = previousSecondOfDay
                            if (previousSecond != null && record.secondOfDay < previousSecond) {
                                if (previousSecond - record.secondOfDay > HALF_DAY_SECONDS) {
                                    dayOffset += 1
                                } else {
                                    warnings += IgcParseWarning(
                                        lineNumber,
                                        IgcWarningCode.NON_MONOTONIC_TIME,
                                        "Horodatage antérieur au point précédent",
                                    )
                                    return@forEachLineIndexed
                                }
                            }

                            val instant = LocalDateTime.of(
                                flightDate.plusDays(dayOffset),
                                LocalTime.ofSecondOfDay(record.secondOfDay.toLong()),
                            ).toInstant(ZoneOffset.UTC)

                            if (previousInstant != null && instant.isBefore(previousInstant)) {
                                warnings += IgcParseWarning(
                                    lineNumber,
                                    IgcWarningCode.NON_MONOTONIC_TIME,
                                    "Horodatage incohérent",
                                )
                                return@forEachLineIndexed
                            }

                            points += IgcTrackPoint(
                                timestamp = instant,
                                latitude = record.latitude,
                                longitude = record.longitude,
                                pressureAltitudeMeters = record.pressureAltitude,
                                gpsAltitudeMeters = record.gpsAltitude,
                            )
                            previousSecondOfDay = record.secondOfDay
                            previousInstant = instant
                        }
                    }
                }
            }
        }

        if (!sawContent) return IgcParseResult.Failure(IgcParseError.EmptyFile)
        val flightDate = date ?: return if (invalidDateLine != null) {
            IgcParseResult.Failure(IgcParseError.InvalidDateHeader(invalidDateLine!!))
        } else {
            IgcParseResult.Failure(IgcParseError.MissingDateHeader)
        }
        if (points.isEmpty()) return IgcParseResult.Failure(IgcParseError.NoValidFix)

        val summary = summarize(points = points, pointCount = bRecordCount)
        return IgcParseResult.Success(
            ParsedIgcFlight(
                metadata = IgcMetadata(
                    date = flightDate,
                    pilot = pilot.nullIfBlank(),
                    gliderType = gliderType.nullIfBlank(),
                    gliderId = gliderId.nullIfBlank(),
                ),
                points = points,
                summary = summary,
                warnings = warnings,
            ),
        )
    }

    private fun summarize(points: List<IgcTrackPoint>, pointCount: Int): FlightSummary {
        var distanceMeters = 0.0
        var gainMeters = 0
        var minimumAltitude: Int? = null
        var maximumAltitude: Int? = null
        var previousAltitude: Int? = null

        points.forEachIndexed { index, point ->
            if (index > 0) distanceMeters += haversineMeters(points[index - 1], point)
            val altitude = point.gpsAltitudeMeters ?: point.pressureAltitudeMeters
            if (altitude != null) {
                minimumAltitude = minimumAltitude?.let { minOf(it, altitude) } ?: altitude
                maximumAltitude = maximumAltitude?.let { maxOf(it, altitude) } ?: altitude
                previousAltitude?.let { previous ->
                    if (altitude > previous) gainMeters += altitude - previous
                }
                previousAltitude = altitude
            }
        }

        return FlightSummary(
            startedAt = points.first().timestamp,
            endedAt = points.last().timestamp,
            pointCount = pointCount,
            validPointCount = points.size,
            distanceMeters = distanceMeters.roundToLong(),
            minimumAltitudeMeters = minimumAltitude,
            maximumAltitudeMeters = maximumAltitude,
            positiveGainMeters = if (minimumAltitude == null) null else gainMeters,
            bounds = GeoBounds(
                south = points.minOf { it.latitude },
                west = points.minOf { it.longitude },
                north = points.maxOf { it.latitude },
                east = points.maxOf { it.longitude },
            ),
        )
    }

    private fun parseBRecord(line: String): BRecordResult {
        if (line.length < MIN_B_RECORD_LENGTH) {
            return BRecordResult.Malformed(
                IgcWarningCode.MALFORMED_B_RECORD,
                "Enregistrement B trop court",
            )
        }

        val hour = line.substring(1, 3).toIntOrNull()
        val minute = line.substring(3, 5).toIntOrNull()
        val second = line.substring(5, 7).toIntOrNull()
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return BRecordResult.Malformed(
                IgcWarningCode.MALFORMED_B_RECORD,
                "Heure UTC invalide",
            )
        }

        val latitude = parseCoordinate(
            digits = line.substring(7, 14),
            hemisphere = line[14],
            degreeDigits = 2,
            maximumDegree = 90,
        )
        val longitude = parseCoordinate(
            digits = line.substring(15, 23),
            hemisphere = line[23],
            degreeDigits = 3,
            maximumDegree = 180,
        )
        if (latitude == null || longitude == null) {
            return BRecordResult.Malformed(
                IgcWarningCode.INVALID_COORDINATES,
                "Coordonnées IGC invalides",
            )
        }

        return BRecordResult.Valid(
            secondOfDay = hour!! * 3600 + minute!! * 60 + second!!,
            latitude = latitude,
            longitude = longitude,
            isValidFix = line[24] == 'A',
            pressureAltitude = line.substring(25, 30).toIntOrNull(),
            gpsAltitude = line.substring(30, 35).toIntOrNull(),
        )
    }

    private fun parseCoordinate(
        digits: String,
        hemisphere: Char,
        degreeDigits: Int,
        maximumDegree: Int,
    ): Double? {
        if (!digits.all(Char::isDigit)) return null
        val degrees = digits.take(degreeDigits).toIntOrNull() ?: return null
        val minuteDigits = digits.drop(degreeDigits)
        val minutes = minuteDigits.toIntOrNull()?.div(1000.0) ?: return null
        if (degrees > maximumDegree || minutes >= 60.0) return null
        if (degrees == maximumDegree && minutes > 0.0) return null
        val sign = when (hemisphere) {
            'N', 'E' -> 1.0
            'S', 'W' -> -1.0
            else -> return null
        }
        return sign * (degrees + minutes / 60.0)
    }

    private fun haversineMeters(a: IgcTrackPoint, b: IgcTrackPoint): Double {
        val latitudeDelta = Math.toRadians(b.latitude - a.latitude)
        val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
        val aLatitude = Math.toRadians(a.latitude)
        val bLatitude = Math.toRadians(b.latitude)
        val value = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(aLatitude) * cos(bLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(value.coerceIn(0.0, 1.0)))
    }

    private fun isDateHeader(line: String): Boolean =
        line.startsWith("HFDTE") || line.startsWith("HODTE")

    private fun parseDateHeader(line: String): LocalDate? {
        val match = DATE_PATTERN.find(line) ?: return null
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val shortYear = match.groupValues[3].toInt()
        val year = if (shortYear >= 80) 1900 + shortYear else 2000 + shortYear
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun headerValue(line: String): String? =
        line.substringAfter(':', missingDelimiterValue = "").trim().nullIfBlank()

    private sealed interface BRecordResult {
        data class Valid(
            val secondOfDay: Int,
            val latitude: Double,
            val longitude: Double,
            val isValidFix: Boolean,
            val pressureAltitude: Int?,
            val gpsAltitude: Int?,
        ) : BRecordResult

        data class Malformed(
            val code: IgcWarningCode,
            val message: String,
        ) : BRecordResult
    }

    private companion object {
        const val MIN_B_RECORD_LENGTH = 35
        const val HALF_DAY_SECONDS = 12 * 60 * 60
        const val EARTH_RADIUS_METERS = 6_371_000.0
        val DATE_PATTERN = Regex("^H[FO]DTE(?:DATE)?:?(\\d{2})(\\d{2})(\\d{2})")
    }
}

private inline fun BufferedReader.forEachLineIndexed(block: (lineNumber: Int, line: String) -> Unit) {
    var lineNumber = 0
    while (true) {
        val line = readLine() ?: break
        lineNumber += 1
        block(lineNumber, line)
    }
}

private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
