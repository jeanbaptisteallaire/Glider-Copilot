package com.neutronstar.glidercopilot.precog

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** iOS / KMP étape 1 : mêmes résultats que les appels java.net / java.time / java.io remplacés. */
class JavaParityTest {
    @get:Rule val tmp = TemporaryFolder()
    private val rnd = Random(20261011)

    @Test fun formEncodingMatchesUrlEncoder() {
        val alphabet = "aZ09 .-*_~:+/?&=%éàç€😀\u0000\n\"'"
        repeat(5_000) {
            val s = String(CharArray(rnd.nextInt(0, 30)) { alphabet[rnd.nextInt(alphabet.length)] })
            assertEquals(URLEncoder.encode(s, "UTF-8"), formUrlEncode(s))
        }
    }

    @Test fun rfc1123MatchesJavaTime() = repeat(5_000) {
        val t = java.time.Instant.ofEpochSecond(rnd.nextLong(0, 4_000_000_000))
        val off = ZoneOffset.ofHoursMinutes(rnd.nextInt(-12, 13), 0)
        val s = DateTimeFormatter.RFC_1123_DATE_TIME.format(t.atOffset(if (rnd.nextBoolean()) ZoneOffset.UTC else off))
        assertEquals(s, t.epochSecond, parseRfc1123(s)!!.epochSeconds)
    }

    @Test fun readsCacheFilesWrittenByThePreviousJavaIoVersion() {
        val dir = tmp.newFolder("precog")
        val key = "https://precog-api.com/v1/arpege/forecast?lat=43.80028&lon=3.78167"
        // écriture exacte de l'ancienne FileResponseCache (java.io + MessageDigest)
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        File(dir, "$h.cache").writeText("\"e-gzip\"\n2026-09-16T08:00:00.123Z\n{\"a\":\"é\"}\nfin")
        val c = FileResponseCache(dir)
        assertEquals(CachedResponse("{\"a\":\"é\"}\nfin", "\"e-gzip\"", Instant.parse("2026-09-16T08:00:00.123Z")), c.read(key))
        c.write(key, CachedResponse("{}", null, Instant.parse("2026-09-16T09:00:00Z")))
        assertEquals("\n2026-09-16T09:00:00Z\n{}", File(dir, "$h.cache").readText())
    }
}
