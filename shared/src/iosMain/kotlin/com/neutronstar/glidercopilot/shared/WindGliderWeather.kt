package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.precog.FileResponseCache
import com.neutronstar.glidercopilot.precog.KtorHttpClient
import com.neutronstar.glidercopilot.precog.PrecogApi
import com.neutronstar.glidercopilot.precog.WeatherRepository
import com.neutronstar.glidercopilot.precog.WeatherResult
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Point d'entrée Swift de la météo Prévol (édition Lite iOS). Même chaîne que GliderApp.kt côté Android :
 * PrecogApi (cadences du guide PRECOG, ETag, Retry-After) + cache disque dans Library/Caches/precog.
 * [apiKey] : clé PRECOG lue dans Info.plist (fichier Secrets.xcconfig, jamais dans le dépôt).
 */
class WindGliderWeather(apiKey: String, userAgent: String) {
    private val repo = WeatherRepository(
        PrecogApi(
            KtorHttpClient(userAgent = userAgent, apiKey = apiKey),
            FileResponseCache((cachesDir() + "/precog").toPath()),
            freshFor = { PrecogApi.recommendedFreshness(it) },
        ),
    )

    @Throws(Exception::class)
    suspend fun loadDay(lat: Double, lon: Double, departement: String?): WeatherResult =
        repo.loadDay(LatLon(lat, lon), departement)

    companion object {
        /** Secondes Unix d'un instant métier (pratique côté Swift : Date(timeIntervalSince1970:)). */
        fun epochSeconds(i: Instant): Double = i.toEpochMilliseconds() / 1000.0

        private fun cachesDir(): String =
            (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: "/tmp"
    }
}
