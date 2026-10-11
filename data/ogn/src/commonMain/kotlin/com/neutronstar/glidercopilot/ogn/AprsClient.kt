package com.neutronstar.glidercopilot.ogn

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.IOException

sealed interface AprsState {
    data object Idle : AprsState
    data class Connecting(val attempt: Int) : AprsState
    data class Connected(val server: String?, val since: Instant, val filter: String) : AprsState
    data class Waiting(val reason: String, val retryInSeconds: Long) : AprsState
}

data class AprsLine(val text: String, val received: Instant)

/**
 * Connexion TCP APRS-IS, propre à la plateforme (java.net.Socket sur Android, Ktor sockets sur iOS).
 * [readLine] rend null après ~30 s sans donnée (comme soTimeout) et lève IOException si la connexion est fermée.
 */
interface AprsConnection {
    suspend fun readLine(): String?
    suspend fun write(text: String)
    fun close()
}

/** Fil de lecture bloquante (Dispatchers.IO). */
internal expect val aprsDispatcher: CoroutineDispatcher

/** Ouvre la connexion (délai de connexion 15 s). */
expect suspend fun openAprsConnection(host: String, port: Int): AprsConnection

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
    private val clock: Clock = Clock.System,
    private val keepaliveSeconds: Long = 180,
    private val silenceTimeoutSeconds: Long = 90,
    private val backoffSeconds: List<Long> = listOf(5, 10, 30, 60, 120),
    private val connect: suspend (String, Int) -> AprsConnection = ::openAprsConnection,
) {
    fun loginLine(filter: String): String =
        "user $user pass -1 vers $appName $appVersion" + (if (filter.isNotBlank()) " filter $filter" else "")

    fun lines(filter: () -> String, state: MutableStateFlow<AprsState>): Flow<AprsLine> = flow {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            attempt++
            state.value = AprsState.Connecting(attempt)
            val f = filter()
            var socket: AprsConnection? = null
            var gotData = false
            try {
                socket = connect(host, port)
                val s = socket
                val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { runCatching { s.close() } }
                try {
                    s.write(loginLine(f) + "\r\n")
                    var lastWrite = clock.now().toEpochMilliseconds()
                    var lastRead = lastWrite
                    var server: String? = null
                    while (currentCoroutineContext().isActive) {
                        val line = s.readLine()
                        val now = clock.now().toEpochMilliseconds()
                        if (line != null) {
                            lastRead = now
                            if (!gotData) {
                                gotData = true
                                attempt = 0
                            }
                            if (line.startsWith("# aprsc") || line.startsWith("# javAPRSSrvr")) {
                                if (server == null) {
                                    server = line.removePrefix("# ").trim()
                                    state.value = AprsState.Connected(server, clock.now(), f)
                                }
                            } else if (line.startsWith("# logresp")) {
                                // « # logresp GLIDY1234 unverified, server GLIDERN3 »
                                server = line.substringAfter("server ", "").trim().ifEmpty { server }
                                state.value = AprsState.Connected(server, clock.now(), f)
                            } else if (REJECTED.containsMatchIn(line)) {
                                // le serveur refuse l'indicatif (plus de 9 caractères) ou le filtre : il n'enverra rien
                                emit(AprsLine(line, clock.now()))
                                throw IOException(line.removePrefix("# ").trim())
                            }
                            emit(AprsLine(line, clock.now()))
                        }
                        if (now - lastRead > silenceTimeoutSeconds * 1000) throw IOException("silence du serveur")
                        if (now - lastWrite > keepaliveSeconds * 1000) {
                            s.write("#keepalive\r\n")
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
    }.flowOn(aprsDispatcher)

    companion object {
        /** Lignes de refus du serveur : l'indicatif ou le filtre est invalide, la connexion ne servira à rien. */
        private val REJECTED = Regex("(?i)invalid (username|callsign|filter)|login failed|not authorized")

        /**
         * Indicatif de lecture propre à l'installation. **Neuf caractères au maximum** : APRS-IS répond
         * « # Invalid username format » au-delà et n'envoie plus aucune trame (défaut trouvé en session 7).
         */
        fun userFor(installId: Long): String = "GLIDY" + (installId % 10000).toString().padStart(4, '0')
    }
}
