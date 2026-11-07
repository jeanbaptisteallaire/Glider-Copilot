package com.neutronstar.glidercopilot.precog

import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** iOS / KMP : lancé sur la JVM et sur le simulateur iOS. */
class CommonHttpTest {
    private val now = Instant.parse("2026-09-16T08:00:00Z")

    @Test fun retryAfter() {
        assertEquals(120L, parseRetryAfter("120", now))
        assertEquals(0L, parseRetryAfter("-5", now))
        assertEquals(60L, parseRetryAfter("Wed, 16 Sep 2026 08:01:00 GMT", now))
        assertEquals(60L, parseRetryAfter("16 Sep 2026 08:01:00 GMT", now))
        assertEquals(3660L, parseRetryAfter("Wed, 16 Sep 2026 10:01:00 +0100", now))
        assertEquals(0L, parseRetryAfter("Wed, 16 Sep 2026 07:00:00 GMT", now))
        assertNull(parseRetryAfter("Thu, 16 Sep 2026 08:01:00 GMT", now))   // jour de semaine faux, refusé comme java.time
        assertNull(parseRetryAfter("bientôt", now))
        assertNull(parseRetryAfter(null, now))
    }

    @Test fun formEncoding() {
        assertEquals("2026-09-16T12%3A00%3A00Z", formUrlEncode("2026-09-16T12:00:00Z"))
        assertEquals("a+b%C3%A9*_.-~", formUrlEncode("a bé*_.-~").replace("%7E", "~"))
    }

    @Test fun fileCacheRoundTripAndLayout() {
        val fs = FakeFileSystem()
        val dir = "/cache/precog".toPath()
        val c = FileResponseCache(dir, fs)
        assertNull(c.read("https://x/a"))
        c.write("https://x/a", CachedResponse("{\"k\":1}\nligne 2", "\"e1\"", now))
        assertEquals(CachedResponse("{\"k\":1}\nligne 2", "\"e1\"", now), c.read("https://x/a"))
        c.write("https://x/a", CachedResponse("{}", null, now))
        assertEquals(CachedResponse("{}", null, now), c.read("https://x/a"))
        // nom = SHA-1 hexadécimal de l'URL, comme l'ancienne version java.io
        assertEquals(1, fs.list(dir).size)
        assertEquals("\n2026-09-16T08:00:00Z\n{}", fs.read(fs.list(dir).single()) { readUtf8() })
    }
}
