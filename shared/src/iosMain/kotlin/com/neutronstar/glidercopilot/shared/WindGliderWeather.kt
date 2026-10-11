package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.precog.FileResponseCache
import com.neutronstar.glidercopilot.precog.HttpClient
import com.neutronstar.glidercopilot.precog.HttpResult
import com.neutronstar.glidercopilot.precog.KtorHttpClient
import com.neutronstar.glidercopilot.precog.MemoryResponseCache
import com.neutronstar.glidercopilot.precog.PrecogApi
import com.neutronstar.glidercopilot.precog.WeatherRepository
import com.neutronstar.glidercopilot.precog.WeatherResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Point d'entrée Swift de la météo Prévol (édition Lite iOS). Même chaîne que GliderApp.kt côté Android :
 * PrecogApi (cadences du guide PRECOG, ETag, Retry-After) + cache disque dans Library/Caches/precog.
 */
class WindGliderWeather private constructor(
    private val repo: WeatherRepository,
    /** Jour imposé (données d'essai) ; null = jour étudié habituel (aujourd'hui avant 17 h, sinon demain). */
    private val fixedDate: LocalDate?,
    /** Vrai pour les données d'essai : l'écran l'affiche en clair. */
    val isTestData: Boolean,
) {
    /** [apiKey] : clé PRECOG lue dans Info.plist (fichier Secrets.xcconfig, jamais dans le dépôt). */
    constructor(apiKey: String, userAgent: String) : this(
        WeatherRepository(
            PrecogApi(
                KtorHttpClient(userAgent = userAgent, apiKey = apiKey),
                FileResponseCache((cachesDir() + "/precog").toPath()),
                freshFor = { PrecogApi.recommendedFreshness(it) },
            ),
        ),
        null,
        false,
    )

    @Throws(Exception::class)
    suspend fun loadDay(lat: Double, lon: Double, departement: String?): WeatherResult =
        if (fixedDate != null) repo.loadDay(LatLon(lat, lon), departement, fixedDate) else repo.loadDay(LatLon(lat, lon), departement)

    companion object {
        /** Secondes Unix d'un instant métier (pratique côté Swift : Date(timeIntervalSince1970:)). */
        fun epochSeconds(i: Instant): Double = i.toEpochMilliseconds() / 1000.0

        /**
         * Build de développement seulement : réponses synthétiques des tests precog (data/precog/src/jvmTest/resources/fixtures),
         * journée du 16/09/2026, sans réseau. Sert à vérifier les écrans sans clé ; jamais dans une version publiée.
         */
        fun testData(fixturesDir: String): WindGliderWeather {
            fun read(name: String) = runCatching { FileSystem.SYSTEM.read("$fixturesDir/$name".toPath()) { readUtf8() } }.getOrNull()
            val routes = mapOf(
                "/arpege/forecast" to read("arpege_forecast_synth.json"),
                "/arpege/profile" to read("arpege_profile_synth.json"),
                "/vigilance" to read("vigilance_synth.json"),
            )
            val http = HttpClient { url, _ ->
                val body = routes.entries.firstOrNull { url.contains(it.key) }?.value
                if (body != null) HttpResult(200, body, "\"essai\"") else HttpResult(404, null, null)
            }
            val clock = object : Clock { override fun now() = Instant.parse("2026-09-16T07:00:00Z") }
            return WindGliderWeather(
                WeatherRepository(PrecogApi(http, MemoryResponseCache(), clock = clock), clock = clock),
                LocalDate(2026, 9, 16),
                true,
            )
        }

        private fun cachesDir(): String =
            (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: "/tmp"
    }
}
