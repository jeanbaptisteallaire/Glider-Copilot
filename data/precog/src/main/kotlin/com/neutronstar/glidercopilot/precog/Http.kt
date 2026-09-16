package com.neutronstar.glidercopilot.precog

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant

data class HttpResult(val code: Int, val body: String?, val etag: String?)

fun interface HttpClient {
    /** GET avec revalidation conditionnelle. Lève IOException si le réseau est indisponible. */
    @Throws(IOException::class)
    fun get(url: String, ifNoneMatch: String?): HttpResult
}

class UrlConnectionHttpClient(
    private val userAgent: String = "GliderCopilot/0.1 (Android)",
    private val timeoutMs: Int = 20_000,
) : HttpClient {
    override fun get(url: String, ifNoneMatch: String?): HttpResult {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "application/json")
            if (ifNoneMatch != null) c.setRequestProperty("If-None-Match", ifNoneMatch)
            val code = c.responseCode
            val body = if (code in 200..299) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
            return HttpResult(code, body, c.getHeaderField("ETag"))
        } finally {
            c.disconnect()
        }
    }
}

data class CachedResponse(val body: String, val etag: String?, val fetchedAt: Instant)

interface ResponseCache {
    fun read(key: String): CachedResponse?
    fun write(key: String, value: CachedResponse)
}

class MemoryResponseCache : ResponseCache {
    private val m = HashMap<String, CachedResponse>()
    override fun read(key: String) = synchronized(m) { m[key] }
    override fun write(key: String, value: CachedResponse) { synchronized(m) { m[key] = value } }
}

/** Cache disque : un fichier par URL (nom = SHA-1). Ligne 1 = ETag, ligne 2 = date, reste = corps. */
class FileResponseCache(private val dir: File) : ResponseCache {
    init { dir.mkdirs() }

    private fun file(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.cache")
    }

    override fun read(key: String): CachedResponse? = runCatching {
        val f = file(key)
        if (!f.exists()) return null
        val parts = f.readText().split('\n', limit = 3)
        CachedResponse(parts[2], parts[0].ifEmpty { null }, Instant.parse(parts[1]))
    }.getOrNull()

    override fun write(key: String, value: CachedResponse) {
        val target = file(key)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText("${value.etag ?: ""}\n${value.fetchedAt}\n${value.body}")
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }
}
