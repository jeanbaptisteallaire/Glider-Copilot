package com.neutronstar.glidercopilot.precog

import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.IOException
import platform.Foundation.NSLock

actual class PlatformLock actual constructor() {
    private val l = NSLock()
    actual fun <T> withLock(block: () -> T): T {
        l.lock()
        try { return block() } finally { l.unlock() }
    }
}

actual val systemFileSystem: FileSystem get() = FileSystem.SYSTEM

internal actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

/**
 * Équivalent iOS de `UrlConnectionHttpClient` (Ktor, moteur Darwin = NSURLSession). Mêmes en-têtes ; la compression
 * gzip est négociée et décompressée par NSURLSession. Appel bloquant : à lancer hors du fil principal
 * (WeatherRepository le fait sur [ioDispatcher]). Toute panne réseau devient une `IOException`.
 */
class KtorHttpClient(
    private val userAgent: String = "WindGlider (iOS)",
    private val timeoutMs: Long = 20_000,
    private val apiKey: String = "",
) : HttpClient {
    private val client = KtorClient(Darwin) {
        expectSuccess = false
        followRedirects = true
        install(HttpTimeout) {
            connectTimeoutMillis = timeoutMs
            requestTimeoutMillis = timeoutMs * 2
            socketTimeoutMillis = timeoutMs
        }
    }

    override fun get(url: String, ifNoneMatch: String?): HttpResult = runBlocking {
        try {
            val r = client.get(url) {
                header("User-Agent", userAgent)
                header("Accept", "application/json")
                if (ifNoneMatch != null) header("If-None-Match", ifNoneMatch)
                if (apiKey.isNotBlank()) header("X-API-Key", apiKey)
            }
            val code = r.status.value
            HttpResult(
                code,
                if (code in 200..299) r.bodyAsText(Charsets.UTF_8) else null,
                r.headers["ETag"],
                parseRetryAfter(r.headers["Retry-After"]),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "réseau indisponible", e)
        }
    }
}
