package com.neutronstar.glidercopilot.ogn

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okio.IOException

internal actual val aprsDispatcher: CoroutineDispatcher = Dispatchers.IO

/** iOS : sockets Ktor (même contrat que java.net.Socket : 15 s pour se connecter, null après 30 s de silence). */
actual suspend fun openAprsConnection(host: String, port: Int): AprsConnection {
    val selector = SelectorManager(Dispatchers.IO)
    val socket = try {
        withTimeout(15_000) { aSocket(selector).tcp().connect(host, port) }
    } catch (e: Throwable) {
        selector.close()
        throw IOException(if (e is TimeoutCancellationException) "délai de connexion dépassé" else (e.message ?: "réseau indisponible"))
    }
    val read = socket.openReadChannel()
    val write = socket.openWriteChannel(autoFlush = true)
    return object : AprsConnection {
        override suspend fun readLine(): String? = try {
            withTimeout(30_000) { read.readUTF8Line() } ?: throw IOException("connexion fermée par le serveur")
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "connexion perdue")
        }

        override suspend fun write(text: String) {
            try { write.writeStringUtf8(text) } catch (e: Exception) { throw IOException(e.message ?: "écriture impossible") }
        }

        override fun close() {
            runCatching { socket.close() }
            runCatching { selector.close() }
        }
    }
}
