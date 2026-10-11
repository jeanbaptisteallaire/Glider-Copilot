package com.neutronstar.glidy.flightcloud

import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

/** Transport Android (inchangé). */
class UrlConnectionTransport(private val timeoutMs: Int = 30_000) : CloudTransport {
    override fun send(request: CloudRequest): CloudResponse {
        val c = URL(request.url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = request.method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            request.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (request.body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(request.body.size)
                c.outputStream.use { it.write(request.body) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            return CloudResponse(code, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            c.disconnect()
        }
    }
}

/** Compression des IGC envoyés au stockage : code Java d'origine (Android inchangé). */
internal actual fun gzipBytes(bytes: ByteArray): ByteArray =
    ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

internal actual fun gunzipBytes(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
