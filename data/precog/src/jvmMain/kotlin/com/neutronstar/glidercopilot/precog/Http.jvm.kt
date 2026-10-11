package com.neutronstar.glidercopilot.precog

import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual class PlatformLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = synchronized(this, block)
}

actual val systemFileSystem: FileSystem get() = FileSystem.SYSTEM

/** Appel Android inchangé : `FileResponseCache(File(cacheDir, "precog"))`. */
fun FileResponseCache(dir: File): FileResponseCache = FileResponseCache(dir.toOkioPath())

class UrlConnectionHttpClient(
    private val userAgent: String = "GliderCopilot/0.1 (Android)",
    private val timeoutMs: Int = 20_000,
    /**
     * Clé d'API precog (préfixe `pcg_`), injectée au build depuis le secret GitHub PRECOG_API_KEY — jamais
     * dans le dépôt. Envoyée en en-tête `X-API-Key`, comme l'exige precog (réponse 401 : « clé d'API (X-API-Key) requis »).
     * Vide = aucune en-tête (autres services : OGN, cartes).
     */
    private val apiKey: String = "",
) : HttpClient {
    override fun get(url: String, ifNoneMatch: String?): HttpResult {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "application/json")
            if (ifNoneMatch != null) c.setRequestProperty("If-None-Match", ifNoneMatch)
            if (apiKey.isNotBlank()) c.setRequestProperty("X-API-Key", apiKey)
            // V20.4 (guide PRECOG §3) : réponses compressées, décompressées ici (l'ETag est gardé tel quel, suffixe -gzip compris)
            c.setRequestProperty("Accept-Encoding", "gzip")
            val code = c.responseCode
            val body = if (code in 200..299) {
                val raw = c.inputStream
                val stream = if (c.contentEncoding.equals("gzip", ignoreCase = true)) java.util.zip.GZIPInputStream(raw) else raw
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else null
            return HttpResult(code, body, c.getHeaderField("ETag"), parseRetryAfter(c.getHeaderField("Retry-After")))
        } finally {
            c.disconnect()
        }
    }
}
