package com.neutronstar.glidercopilot.precog

import com.neutronstar.glidercopilot.domain.secondsBetween
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** [retryAfterS] : en-tête `Retry-After` (429 / 503), en secondes. */
data class HttpResult(val code: Int, val body: String?, val etag: String?, val retryAfterS: Long? = null)

/**
 * Client HTTP minimal. Implémentations : `UrlConnectionHttpClient` (jvmMain, Android) et `KtorHttpClient` (iosMain).
 * `okio.IOException` = `java.io.IOException` sur la JVM.
 */
fun interface HttpClient {
    /** GET avec revalidation conditionnelle. Lève IOException si le réseau est indisponible. */
    @Throws(IOException::class)
    fun get(url: String, ifNoneMatch: String?): HttpResult
}

/** `Retry-After` : nombre de secondes ou date HTTP. Null si absent ou illisible. */
fun parseRetryAfter(v: String?, now: Instant = Clock.System.now()): Long? {
    val t = v?.trim().orEmpty()
    if (t.isEmpty()) return null
    t.toLongOrNull()?.let { return it.coerceAtLeast(0) }
    return parseRfc1123(t)?.let { secondsBetween(now, it).coerceAtLeast(0) }
}

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
private val RFC_1123 =
    Regex("""^(?:([A-Za-z]{3}), )?(\d{1,2}) ([A-Za-z]{3}) (\d{4}) (\d{2}):(\d{2})(?::(\d{2}))? (GMT|[+-]\d{4})$""")

/**
 * Date HTTP, comme `DateTimeFormatter.RFC_1123_DATE_TIME` : jour de semaine facultatif (vérifié s'il est présent),
 * secondes facultatives, « GMT » ou décalage ±HHMM, insensible à la casse.
 */
internal fun parseRfc1123(s: String): Instant? = runCatching {
    val m = RFC_1123.matchEntire(s) ?: return null
    val month = MONTHS.indexOf(m.groupValues[3].lowercase()) + 1
    if (month == 0) return null
    val dt = LocalDateTime(
        m.groupValues[4].toInt(), month, m.groupValues[2].toInt(),
        m.groupValues[5].toInt(), m.groupValues[6].toInt(), m.groupValues[7].ifEmpty { "0" }.toInt(),
    )
    val dow = m.groupValues[1]
    if (dow.isNotEmpty() && DAYS.indexOf(dow.lowercase()) != dt.dayOfWeek.ordinal) return null
    val off = m.groupValues[8]
    val utc = dt.toInstant(TimeZone.UTC)
    if (off.equals("GMT", ignoreCase = true)) utc else {
        val sign = if (off[0] == '-') -1 else 1
        utc - (off.substring(1, 3).toInt().hours + off.substring(3, 5).toInt().minutes) * sign
    }
}.getOrNull()

data class CachedResponse(val body: String, val etag: String?, val fetchedAt: Instant)

interface ResponseCache {
    fun read(key: String): CachedResponse?
    fun write(key: String, value: CachedResponse)
}

class MemoryResponseCache : ResponseCache {
    private val m = HashMap<String, CachedResponse>()
    private val lock = PlatformLock()
    override fun read(key: String) = lock.withLock { m[key] }
    override fun write(key: String, value: CachedResponse) { lock.withLock { m[key] = value } }
}

/** Verrou d'exclusion mutuelle (JVM : `synchronized`, iOS : NSLock). */
expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}

/** Système de fichiers de l'appareil (`FileSystem.SYSTEM`). */
expect val systemFileSystem: FileSystem

/** Cache disque : un fichier par URL (nom = SHA-1). Ligne 1 = ETag, ligne 2 = date, reste = corps. */
class FileResponseCache(private val dir: Path, private val fs: FileSystem = systemFileSystem) : ResponseCache {
    init { runCatching { fs.createDirectories(dir) } }

    private fun file(key: String): Path = dir / (key.encodeUtf8().sha1().hex() + ".cache")

    override fun read(key: String): CachedResponse? = runCatching {
        val f = file(key)
        if (!fs.exists(f)) return null
        val parts = fs.read(f) { readUtf8() }.split('\n', limit = 3)
        CachedResponse(parts[2], parts[0].ifEmpty { null }, Instant.parse(parts[1]))
    }.getOrNull()

    override fun write(key: String, value: CachedResponse) {
        val target = file(key)
        val tmp = dir / (target.name + ".tmp")
        fs.write(tmp) { writeUtf8("${value.etag ?: ""}\n${value.fetchedAt}\n${value.body}") }
        try {
            fs.atomicMove(tmp, target)
        } catch (e: IOException) {
            fs.delete(target, mustExist = false); fs.atomicMove(tmp, target)
        }
    }
}

