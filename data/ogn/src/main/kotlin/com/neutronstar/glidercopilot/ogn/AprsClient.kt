package com.neutronstar.glidercopilot.ogn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant

sealed interface AprsState {
    data object Idle : AprsState
    data class Connecting(val attempt: Int) : AprsState
    data class Connected(val server: String?, val since: Instant, val filter: String) : AprsState
    data class Waiting(val reason: String, val retryInSeconds: Long) : AprsState
}

data class AprsLine(val text: String, val received: Instant)

/**
 * Client APRS-IS en lecture seule pour OGN (aprs.glidernet.org:14580, « pass -1 » : aucune émission possible).
 * Envoie un keepalive toutes les [keepaliveSeconds], se reconnecte avec attente croissante, relit le filtre à chaque connexion.
 */
class AprsClient(
    private val user: String,
    private val appName: String = "GLIDY",
    private val appVersion: String = "0.4",
    private val host: String = "aprs.glidernet.org",
    private val port: Int = 14580,
    private val clock: Clock = Clock.systemUTC(),
    private val keepaliveSeconds: Long = 180,
    private val silenceTimeoutSeconds: Long = 90,
    private val backoffSeconds: List<Long> = listOf(5, 10, 30, 60, 120),
    private val connect: (String, Int) -> Socket = { h, p -> Socket().apply { connect(InetSocketAddress(h, p), 15_000) } },
) {
    fun loginLine(filter: String): String =
        "user $user pass -1 vers $appName $appVersion" + (if (filter.isNotBlank()) " filter $filter" else "")

    fun lines(filter: () -> String, state: MutableStateFlow<AprsState>): Flow<AprsLine> = flow {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            attempt++
            state.value = AprsState.Connecting(attempt)
            val f = filter()
            var socket: Socket? = null
            var gotData = false
            try {
                socket = connect(host, port)
                val s = socket
                val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { runCatching { s.close() } }
                try {
                    s.soTimeout = 30_000
                    val out = s.getOutputStream()
                    out.write((loginLine(f) + "\r\n").toByteArray())
                    out.flush()
                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    var lastWrite = clock.millis()
                    var lastRead = clock.millis()
                    var server: String? = null
                    while (currentCoroutineContext().isActive) {
                        val line = try {
                            reader.readLine() ?: throw IOException("connexion fermée par le serveur")
                        } catch (e: SocketTimeoutException) {
                            null
                        }
                        val now = clock.millis()
                        if (line != null) {
                            lastRead = now
                            if (!gotData) {
                                gotData = true
                                attempt = 0
                            }
                            if (line.startsWith("# aprsc") || line.startsWith("# javAPRSSrvr")) {
                                if (server == null) {
                                    server = line.removePrefix("# ").trim()
                                    state.value = AprsState.Connected(server, clock.instant(), f)
                                }
                            } else if (line.startsWith("# logresp")) {
                                state.value = AprsState.Connected(server, clock.instant(), f)
                            }
                            emit(AprsLine(line, clock.instant()))
                        }
                        if (now - lastRead > silenceTimeoutSeconds * 1000) throw IOException("silence du serveur")
                        if (now - lastWrite > keepaliveSeconds * 1000) {
                            out.write("#keepalive\r\n".toByteArray())
                            out.flush()
                            lastWrite = now
                        }
                    }
                } finally {
                    handle?.dispose()
                }
            } catch (e: IOException) {
                if (!currentCoroutineContext().isActive) break
                val wait = backoffSeconds[(attempt - 1).coerceIn(0, backoffSeconds.lastIndex)]
                state.value = AprsState.Waiting(e.message ?: "réseau indisponible", wait)
                delay(wait * 1000)
            } finally {
                runCatching { socket?.close() }
            }
        }
        state.value = AprsState.Idle
    }.flowOn(Dispatchers.IO)

    companion object {
        /** Indicatif de lecture propre à l'installation (APRS-IS refuse deux connexions au même nom). */
        fun userFor(installId: Long): String = "GLIDY" + (installId % 100000).toString().padStart(5, '0')
    }
}
