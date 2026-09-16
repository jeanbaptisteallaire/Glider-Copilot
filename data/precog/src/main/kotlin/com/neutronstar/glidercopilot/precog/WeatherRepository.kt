package com.neutronstar.glidercopilot.precog

import com.neutronstar.glidercopilot.domain.DayPlanner
import com.neutronstar.glidercopilot.domain.DaySummary
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.ProfileLevel
import com.neutronstar.glidercopilot.domain.SurfaceConditions
import com.neutronstar.glidercopilot.domain.ThermalAnalysis
import com.neutronstar.glidercopilot.domain.ThermalModel
import com.neutronstar.glidercopilot.domain.VigilanceStatus
import com.neutronstar.glidercopilot.domain.WindLayer
import com.neutronstar.glidercopilot.domain.WindLayers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class HourWeather(
    val analysis: ThermalAnalysis,
    val surface: SurfaceConditions,
    val winds: List<WindLayer>,
)

data class SourceInfo(
    val product: String,
    val attribution: String,
    val referenceTime: Instant?,
    val fetchedAt: Instant,
    val offline: Boolean,
) {
    /** Périmée : copie hors ligne, ou réseau de plus de 12 h. */
    fun isStale(now: Instant): Boolean =
        offline || (referenceTime != null && now.isAfter(referenceTime.plusSeconds(12 * 3600)))
}

data class DayWeather(
    val date: LocalDate,
    val location: LatLon,
    val gridDistanceKm: Double?,
    val summary: DaySummary,
    val hours: List<HourWeather>,
    val vigilance: VigilanceStatus?,
    val sources: List<SourceInfo>,
)

sealed interface WeatherResult {
    data class Success(val day: DayWeather) : WeatherResult
    data class Error(val message: String) : WeatherResult
}

class WeatherRepository(
    private val api: PrecogApi,
    private val zone: ZoneId = ZoneId.of("Europe/Paris"),
    private val clock: Clock = Clock.systemUTC(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val firstHour: Int = 9,
    private val lastHour: Int = 18,
) {
    /** Jour étudié : aujourd'hui avant 17 h locales, demain ensuite. */
    fun targetDate(now: Instant = clock.instant()): LocalDate {
        val local = now.atZone(zone)
        return if (local.hour < 17) local.toLocalDate() else local.toLocalDate().plusDays(1)
    }

    suspend fun loadDay(position: LatLon, departement: String?, date: LocalDate = targetDate()): WeatherResult =
        withContext(io) { load(position, departement, date) }

    private suspend fun load(position: LatLon, departement: String?, date: LocalDate): WeatherResult {
        val q = String.format(Locale.ROOT, "lat=%.5f&lon=%.5f", position.lat, position.lon)
        val forecast = when (val f = api.get("/arpege/forecast?$q")) {
            is Fetch.Ok -> f
            Fetch.NotAvailable -> return WeatherResult.Error("Prévision indisponible pour ce point (hors de l'emprise France ?)")
            is Fetch.Failed -> return WeatherResult.Error("Météo inaccessible : ${f.reason}")
        }
        val env = Envelope(forecast.json)
        val surfaces = ForecastMapper.surface(env).filter {
            val l = it.validTime.atZone(zone)
            l.toLocalDate() == date && l.hour in firstHour..lastHour
        }
        if (surfaces.isEmpty()) return WeatherResult.Error("Aucune échéance pour le $date dans le réseau servi")

        val sources = mutableListOf(
            SourceInfo("ARPEGE surface", env.attribution ?: "Source : Météo-France", env.referenceTime, forecast.fetchedAt, forecast.offline),
        )

        val limiter = Semaphore(3)
        val profiles: List<Triple<SurfaceConditions, List<ProfileLevel>, Fetch.Ok?>> = coroutineScope {
            surfaces.map { s ->
                async {
                    limiter.withPermit {
                        val at = URLEncoder.encode(s.validTime.toString(), "UTF-8")
                        when (val p = api.get("/arpege/profile?$q&at=$at")) {
                            is Fetch.Ok -> Triple(s, ProfileMapper.levels(Envelope(p.json), s.validTime), p)
                            else -> Triple(s, emptyList(), null)
                        }
                    }
                }
            }.awaitAll()
        }
        profiles.mapNotNull { it.third }.maxByOrNull { it.fetchedAt }?.let { p ->
            val pe = Envelope(p.json)
            sources += SourceInfo(
                "ARPEGE profil vertical", pe.attribution ?: "Source : Météo-France", pe.referenceTime, p.fetchedAt,
                profiles.any { it.third?.offline == true },
            )
        }

        val hours = profiles.mapNotNull { (s, levels, _) ->
            if (levels.isEmpty()) return@mapNotNull null
            val a = ThermalModel.analyse(s, levels)
            HourWeather(a, s, WindLayers.compute(levels, a.groundAltitudeM, s.wind10mMs, s.wind10mFromDeg))
        }
        if (hours.isEmpty()) return WeatherResult.Error("Profils verticaux indisponibles")

        var vigilance: VigilanceStatus? = null
        if (departement != null) {
            val v = api.get("/vigilance")
            if (v is Fetch.Ok) {
                vigilance = VigilanceMapper.forDepartement(v.json, departement)
                sources += SourceInfo(
                    "Vigilance", v.json["attribution"]?.str ?: "Source : Météo-France",
                    v.json["update_time"]?.str?.let(Envelope::parseInstant), v.fetchedAt, v.offline,
                )
            }
        }

        return WeatherResult.Success(
            DayWeather(
                date, position, env.pointDistanceKm,
                DayPlanner.summarize(hours.map { it.analysis }), hours, vigilance, sources,
            ),
        )
    }
}
