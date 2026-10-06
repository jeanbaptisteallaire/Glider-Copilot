package com.neutronstar.glidercopilot.precog

import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed interface Fetch {
    /** [offline] : le réseau a échoué, la copie locale est servie. */
    data class Ok(val json: Json, val fetchedAt: Instant, val offline: Boolean) : Fetch
    /** 404 : donnée absente (hors emprise, produit en pause) — pas une panne. */
    data object NotAvailable : Fetch
    data class Failed(val reason: String) : Fetch
}

/**
 * Accès precog. V20.4 — règles d'usage de l'API PRECOG (conditions d'utilisation + guide d'intégration §3–4), pour
 * limiter les pics de demande quand beaucoup de pilotes ouvrent l'app en même temps :
 * - [freshFor] : une copie reçue il y a moins de ce délai est servie sans appeler le réseau (ARPEGE : réseau toutes les
 *   6 h, sonder plus vite ne rend rien de plus frais) ; au-delà, revalidation conditionnelle `If-None-Match` (→ 304) ;
 * - `429` / `503` : on attend le `Retry-After` annoncé avant tout nouvel appel, la copie affichée est gardée,
 *   jamais de nouvel essai en boucle ;
 * - `404` : état normal (hors emprise, produit en pause), pas une panne.
 */
class PrecogApi(
    private val http: HttpClient,
    private val cache: ResponseCache,
    private val baseUrl: String = "https://precog-api.com/v1",
    private val clock: Clock = Clock.systemUTC(),
    private val freshFor: (pathAndQuery: String) -> Duration = { Duration.ZERO },
) {
    /** Pas d'appel avant cet instant (dernier `Retry-After` reçu). */
    @Volatile private var backoffUntil: Instant = Instant.EPOCH

    fun get(pathAndQuery: String): Fetch {
        val url = baseUrl + pathAndQuery
        val cached = cache.read(url)
        val now = clock.instant()
        if (cached != null && now.isBefore(cached.fetchedAt.plus(freshFor(pathAndQuery)))) {
            return parsed(cached, offline = false)
        }
        if (now.isBefore(backoffUntil)) {
            return cached?.let { parsed(it, offline = true) }
                ?: Fetch.Failed("service météo très sollicité, nouvel essai dans ${Duration.between(now, backoffUntil).seconds + 1} s")
        }
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
                    val t = clock.instant()
                    cache.write(url, CachedResponse(r.body, r.etag, t))
                    Fetch.Ok(json, t, offline = false)
                }
                r.code == 404 -> Fetch.NotAvailable
                r.code == 429 || r.code == 503 -> {
                    backoffUntil = clock.instant().plusSeconds((r.retryAfterS ?: DEFAULT_RETRY_S).coerceIn(1, MAX_RETRY_S))
                    cached?.let { parsed(it, offline = true) }
                        ?: Fetch.Failed("service météo très sollicité (HTTP ${r.code}), réessayez plus tard")
                }
                // 401/403 : clé absente, invalide ou révoquée — dit clairement, copie locale servie si elle existe
                r.code == 401 || r.code == 403 -> cached?.let { parsed(it, offline = true) }
                    ?: Fetch.Failed("clé d'API météo refusée (HTTP ${r.code})")
                else -> cached?.let { parsed(it, offline = true) }
                    ?: Fetch.Failed("HTTP ${r.code}")
            }
        } catch (e: IOException) {
            cached?.let { parsed(it, offline = true) }
                ?: Fetch.Failed("réseau indisponible")
        } catch (e: JsonParseException) {
            Fetch.Failed("réponse illisible")
        }
    }

    private fun parsed(c: CachedResponse, offline: Boolean): Fetch =
        try {
            Fetch.Ok(Json.parse(c.body), c.fetchedAt, offline)
        } catch (e: JsonParseException) {
            Fetch.Failed("réponse illisible")
        }

    companion object {
        private const val DEFAULT_RETRY_S = 60L
        /** Un quota de clé peut renvoyer jusqu'à minuit UTC : 24 h au plus. */
        private const val MAX_RETRY_S = 24 * 3600L

        /** Cadences du guide PRECOG §3 : ARPEGE (réseau / 6 h) → 30 min ; Vigilance → 15 min. */
        fun recommendedFreshness(pathAndQuery: String): Duration = when {
            pathAndQuery.startsWith("/arpege/") -> Duration.ofMinutes(30)
            pathAndQuery.startsWith("/vigilance") -> Duration.ofMinutes(15)
            else -> Duration.ofMinutes(15)
        }
    }
}
