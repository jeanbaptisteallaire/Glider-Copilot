package com.neutronstar.glidy.flightcloud

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Métadonnées d'un vol côté cloud (table public.flights, docs/supabase/schema.sql). */
data class CloudFlightRecord(
    val id: String,
    val sha256: String,
    val fileName: String,
    val sizeBytes: Long,
    val storagePath: String,
    val startedAt: String?,
    val endedAt: String?,
    val durationSeconds: Long?,
    val distanceMeters: Long?,
    val altitudeMinMeters: Int?,
    val altitudeMaxMeters: Int?,
    val gainMeters: Int?,
    val pilot: String?,
    val gliderType: String?,
    val gliderId: String?,
)

sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    data object NotConfigured : CloudResult<Nothing>
    data object SignedOut : CloudResult<Nothing>
    data class Failed(val reason: String, val retryable: Boolean = true) : CloudResult<Nothing>
}

/**
 * Client Supabase minimal (Auth par code reçu par e-mail, PostgREST, Storage) — sans SDK, en HTTP direct.
 * Connexion en deux temps : [sendCode] envoie un code à 6 chiffres par e-mail, [verifyCode] ouvre la session.
 * Aucun mot de passe, aucun lien à ouvrir (pas de lien profond à gérer dans l'app).
 */
class SupabaseClient(
    private val config: CloudConfig,
    private val transport: CloudTransport,
    private val sessions: SessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    val isConfigured: Boolean get() = config.isConfigured
    val session: CloudSession? get() = sessions.load()

    fun sendCode(email: String): CloudResult<Unit> = guarded {
        val r = transport.send(post("/auth/v1/otp", mapOf("email" to email.trim(), "create_user" to true), token = null))
        if (r.ok) CloudResult.Ok(Unit) else fail(r)
    }

    fun verifyCode(email: String, code: String): CloudResult<CloudSession> = guarded {
        val r = transport.send(post("/auth/v1/verify", mapOf("type" to "email", "email" to email.trim(), "token" to code.trim()), token = null))
        if (!r.ok) return@guarded fail(r)
        val s = sessionFrom(MiniJson.parse(r.text).obj(), fallbackEmail = email.trim())
            ?: return@guarded CloudResult.Failed("réponse de connexion illisible", retryable = false)
        sessions.save(s)
        CloudResult.Ok(s)
    }

    fun signOut() {
        val s = sessions.load()
        sessions.save(null)
        if (s != null && config.isConfigured) runCatching { transport.send(post("/auth/v1/logout", emptyMap<String, Any?>(), s.accessToken)) }
    }

    /** Session valide (rafraîchie si elle expire dans moins d'une minute). */
    fun freshSession(): CloudResult<CloudSession> = guarded {
        val s = sessions.load() ?: return@guarded CloudResult.SignedOut
        if (s.expiresAtEpochSeconds - nowEpochSeconds() > 60) return@guarded CloudResult.Ok(s)
        val r = transport.send(post("/auth/v1/token?grant_type=refresh_token", mapOf("refresh_token" to s.refreshToken), token = null))
        if (r.code == 400 || r.code == 401) { sessions.save(null); return@guarded CloudResult.SignedOut }
        if (!r.ok) return@guarded fail(r)
        val fresh = sessionFrom(MiniJson.parse(r.text).obj(), fallbackEmail = s.email) ?: return@guarded CloudResult.SignedOut
        sessions.save(fresh)
        CloudResult.Ok(fresh)
    }

    /** Envoi idempotent : fichier compressé puis ligne de métadonnées (clé unique pilote + empreinte). */
    fun uploadFlight(record: CloudFlightRecord, igc: ByteArray): CloudResult<CloudFlightRecord> = withSession { s ->
        val path = "${s.userId}/${record.id}.igc.gz"
        val put = transport.send(
            CloudRequest(
                "POST", "${config.base}/storage/v1/object/igc/$path",
                headers(s.accessToken) + mapOf("Content-Type" to "application/gzip", "x-upsert" to "true"),
                gzip(igc),
            ),
        )
        if (!put.ok) return@withSession fail(put)
        val stored = record.copy(storagePath = path)
        val row = transport.send(
            CloudRequest(
                "POST", "${config.base}/rest/v1/flights?on_conflict=user_id,sha256",
                headers(s.accessToken) + mapOf("Content-Type" to "application/json", "Prefer" to "resolution=merge-duplicates,return=minimal"),
                MiniJson.write(listOf(stored.toRow(s.userId))).toByteArray(),
            ),
        )
        if (row.ok) CloudResult.Ok(stored) else fail(row)
    }

    fun listFlights(): CloudResult<List<CloudFlightRecord>> = withSession { s ->
        val r = transport.send(CloudRequest("GET", "${config.base}/rest/v1/flights?select=*&order=started_at.desc", headers(s.accessToken)))
        if (!r.ok) return@withSession fail(r)
        CloudResult.Ok(MiniJson.parse(r.text).list().map { it.obj().toRecord() })
    }

    fun downloadIgc(record: CloudFlightRecord): CloudResult<ByteArray> = withSession { s ->
        val r = transport.send(CloudRequest("GET", "${config.base}/storage/v1/object/authenticated/igc/${record.storagePath}", headers(s.accessToken)))
        if (!r.ok) return@withSession fail(r)
        CloudResult.Ok(gunzip(r.body))
    }

    /**
     * Suppression du compte et de tous les vols en ligne (exigence Google Play) : fichiers du dossier du pilote
     * via l'API Storage, puis `delete_my_account()` qui efface le compte et, en cascade, ses lignes.
     */
    fun deleteAccount(): CloudResult<Unit> = withSession { s ->
        val listed = transport.send(
            CloudRequest(
                "POST", "${config.base}/storage/v1/object/list/igc",
                headers(s.accessToken) + ("Content-Type" to "application/json"),
                MiniJson.write(mapOf("prefix" to s.userId, "limit" to 10_000)).toByteArray(),
            ),
        )
        if (!listed.ok) return@withSession fail(listed)
        val names = MiniJson.parse(listed.text).list().mapNotNull { it.obj()["name"] as? String }.map { "${s.userId}/$it" }
        if (names.isNotEmpty()) {
            val del = transport.send(
                CloudRequest(
                    "DELETE", "${config.base}/storage/v1/object/igc",
                    headers(s.accessToken) + ("Content-Type" to "application/json"),
                    MiniJson.write(mapOf("prefixes" to names)).toByteArray(),
                ),
            )
            if (!del.ok) return@withSession fail(del)
        }
        val rpc = transport.send(post("/rest/v1/rpc/delete_my_account", emptyMap<String, Any?>(), s.accessToken))
        if (!rpc.ok) return@withSession fail(rpc)
        sessions.save(null)
        CloudResult.Ok(Unit)
    }

    // ------------------------------------------------------------------------------------------------

    private fun <T> guarded(block: () -> CloudResult<T>): CloudResult<T> {
        if (!config.isConfigured) return CloudResult.NotConfigured
        return try { block() } catch (e: IOException) {
            CloudResult.Failed("réseau indisponible (${e.javaClass.simpleName})")
        } catch (e: RuntimeException) {
            CloudResult.Failed("réponse inattendue : ${e.message}", retryable = false)
        }
    }

    private fun <T> withSession(block: (CloudSession) -> CloudResult<T>): CloudResult<T> = guarded {
        when (val s = freshSession()) {
            is CloudResult.Ok -> block(s.value)
            is CloudResult.Failed -> s
            CloudResult.SignedOut -> CloudResult.SignedOut
            CloudResult.NotConfigured -> CloudResult.NotConfigured
        }
    }

    private fun headers(token: String?): Map<String, String> =
        mapOf("apikey" to config.anonKey, "Authorization" to "Bearer ${token ?: config.anonKey}")

    private fun post(path: String, body: Any?, token: String?) = CloudRequest(
        "POST", config.base + path, headers(token) + ("Content-Type" to "application/json"), MiniJson.write(body).toByteArray(),
    )

    private fun fail(r: CloudResponse): CloudResult.Failed {
        val message = runCatching { MiniJson.parse(r.text).obj().let { it["msg"] ?: it["message"] ?: it["error_description"] ?: it["error"] } }.getOrNull()
        return CloudResult.Failed("HTTP ${r.code}${message?.let { " : $it" } ?: ""}", retryable = r.code >= 500 || r.code == 429)
    }

    private fun sessionFrom(json: Map<String, Any?>, fallbackEmail: String): CloudSession? {
        val access = json["access_token"] as? String ?: return null
        val refresh = json["refresh_token"] as? String ?: return null
        val user = json["user"].obj()
        val id = user["id"] as? String ?: return null
        val expiresAt = (json["expires_at"] as? Double)?.toLong()
            ?: ((json["expires_in"] as? Double)?.toLong() ?: 3600L) + nowEpochSeconds()
        return CloudSession(access, refresh, id, user["email"] as? String ?: fallbackEmail, expiresAt)
    }

    private fun CloudFlightRecord.toRow(userId: String): Map<String, Any?> = linkedMapOf(
        "id" to id, "user_id" to userId, "sha256" to sha256, "file_name" to fileName, "size_bytes" to sizeBytes,
        "storage_path" to storagePath, "started_at" to startedAt, "ended_at" to endedAt, "duration_s" to durationSeconds,
        "distance_m" to distanceMeters, "altitude_min_m" to altitudeMinMeters, "altitude_max_m" to altitudeMaxMeters,
        "gain_m" to gainMeters, "pilot" to pilot, "glider_type" to gliderType, "glider_id" to gliderId,
    )

    private fun Map<String, Any?>.toRecord() = CloudFlightRecord(
        id = this["id"] as? String ?: "",
        sha256 = this["sha256"] as? String ?: "",
        fileName = this["file_name"] as? String ?: "vol.igc",
        sizeBytes = (this["size_bytes"] as? Double)?.toLong() ?: 0L,
        storagePath = this["storage_path"] as? String ?: "",
        startedAt = this["started_at"] as? String,
        endedAt = this["ended_at"] as? String,
        durationSeconds = (this["duration_s"] as? Double)?.toLong(),
        distanceMeters = (this["distance_m"] as? Double)?.toLong(),
        altitudeMinMeters = (this["altitude_min_m"] as? Double)?.toInt(),
        altitudeMaxMeters = (this["altitude_max_m"] as? Double)?.toInt(),
        gainMeters = (this["gain_m"] as? Double)?.toInt(),
        pilot = this["pilot"] as? String,
        gliderType = this["glider_type"] as? String,
        gliderId = this["glider_id"] as? String,
    )

    companion object {
        fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

        @Suppress("unused")
        internal fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
