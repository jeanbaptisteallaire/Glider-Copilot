package com.neutronstar.glidy.flightcloud

import okio.IOException

data class CloudRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class CloudResponse(val code: Int, val body: ByteArray) {
    val text: String get() = body.decodeToString()
    val ok: Boolean get() = code in 200..299
}

/** Transport HTTP bloquant (appelé hors du fil principal). Remplacé par un faux serveur dans les tests. */
fun interface CloudTransport {
    @Throws(IOException::class)
    fun send(request: CloudRequest): CloudResponse
}
