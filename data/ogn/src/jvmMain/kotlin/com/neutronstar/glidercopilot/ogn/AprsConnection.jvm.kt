package com.neutronstar.glidercopilot.ogn

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

internal actual val aprsDispatcher: CoroutineDispatcher = Dispatchers.IO

/** Android : java.net.Socket, exactement comme avant la migration KMP (soTimeout 30 s, UTF-8). */
actual suspend fun openAprsConnection(host: String, port: Int): AprsConnection =
    SocketAprsConnection(Socket().apply { connect(InetSocketAddress(host, port), 15_000) })

class SocketAprsConnection(private val socket: Socket) : AprsConnection {
    init { socket.soTimeout = 30_000 }
    private val out = socket.getOutputStream()
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

    override suspend fun readLine(): String? = try {
        reader.readLine() ?: throw IOException("connexion fermée par le serveur")
    } catch (e: SocketTimeoutException) {
        null
    }

    override suspend fun write(text: String) {
        out.write(text.toByteArray())
        out.flush()
    }

    override fun close() { runCatching { socket.close() } }
}
