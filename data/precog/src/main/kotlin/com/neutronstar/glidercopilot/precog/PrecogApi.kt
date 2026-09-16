package com.neutronstar.glidercopilot.precog

import java.io.IOException
import java.time.Clock
import java.time.Instant

sealed interface Fetch {
    /** [offline] : le réseau a échoué, la copie locale est servie. */
    data class Ok(val json: Json, val fetchedAt: Instant, val offline: Boolean) : Fetch
    /** 404 : donnée absente (hors emprise, produit en pause) — pas une panne. */
    data object NotAvailable : Fetch
    data class Failed(val reason: String) : Fetch
}

class PrecogApi(
    private val http: HttpClient,
    private val cache: ResponseCache,
    private val baseUrl: String = "https://precog-api.com/v1",
    private val clock: Clock = Clock.systemUTC(),
) {
    fun get(pathAndQuery: String): Fetch {
        val url = baseUrl + pathAndQuery
        val cached = cache.read(url)
        return try {
            val r = http.get(url, cached?.etag)
            when {
                r.code == 304 && cached != null -> {
                    val refreshed = cached.copy(fetchedAt = clock.instant())
                    cache.write(url, refreshed)
                    Fetch.Ok(Json.parse(cached.body), refreshed.fetchedAt, offline = false)
                }
                r.code in 200..299 && r.body != null -> {
                    val json = Json.parse(r.body)
                    val now = clock.instant()
                    cache.write(url, CachedResponse(r.body, r.etag, now))
                    Fetch.Ok(json, now, offline = false)
                }
                r.code == 404 -> Fetch.NotAvailable
                else -> cached?.let { Fetch.Ok(Json.parse(it.body), it.fetchedAt, offline = true) }
                    ?: Fetch.Failed("HTTP ${r.code}")
            }
        } catch (e: IOException) {
            cached?.let { Fetch.Ok(Json.parse(it.body), it.fetchedAt, offline = true) }
                ?: Fetch.Failed("réseau indisponible")
        } catch (e: JsonParseException) {
            Fetch.Failed("réponse illisible")
        }
    }
}
