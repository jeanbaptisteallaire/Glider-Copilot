package com.neutronstar.glidy.flightcloud

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CloudRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class CloudResponse(val code: Int, val body: ByteArray) {
    val text: String get() = body.toString(Charsets.UTF_8)
    val ok: Boolean get() = code in 200..299
}

/** Transport HTTP bloquant (appelé hors du fil principal). Remplacé par un faux serveur dans les tests. */
fun interface CloudTransport {
    @Throws(IOException::class)
    fun send(request: CloudRequest): CloudResponse
}

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
