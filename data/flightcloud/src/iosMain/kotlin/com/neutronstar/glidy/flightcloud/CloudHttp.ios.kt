package com.neutronstar.glidy.flightcloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.IOException
import okio.buffer
import okio.use

/**
 * Transport iOS (Ktor, moteur Darwin = NSURLSession), même contrat que UrlConnectionTransport : appel bloquant,
 * à lancer hors du fil principal ; toute panne réseau devient une IOException (le client la traite comme Android).
 */
class KtorCloudTransport(private val timeoutMs: Long = 30_000) : CloudTransport {
    private val client = HttpClient(Darwin) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = timeoutMs
            requestTimeoutMillis = timeoutMs * 2
            socketTimeoutMillis = timeoutMs
        }
    }

    override fun send(request: CloudRequest): CloudResponse = runBlocking {
        try {
            val r = client.request(request.url) {
                method = HttpMethod.parse(request.method)
                request.headers.forEach { (k, v) -> header(k, v) }
                request.body?.let { setBody(it) }
            }
            CloudResponse(r.status.value, r.readRawBytes())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "réseau indisponible", e)
        }
    }
}

internal actual fun gzipBytes(bytes: ByteArray): ByteArray {
    val out = Buffer()
    GzipSink(out).buffer().use { it.write(bytes) }
    return out.readByteArray()
}

internal actual fun gunzipBytes(bytes: ByteArray): ByteArray = GzipSource(Buffer().write(bytes)).buffer().use { it.readByteArray() }
