package com.neutronstar.glidy.social

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {
    @Test fun `recherche sans accents sur nom et pseudo`() = runBlocking {
        val repo = DemoSocialRepository()
        assertEquals(listOf("p03"), repo.search("lea").map { it.id })
        assertEquals(listOf("p05"), repo.search("EMILIE").map { it.id })
        assertEquals(listOf("p02"), repo.search("@hugo").map { it.id })
        assertTrue(repo.search("fontaine").any { it.id == "p03" })
        assertTrue(repo.search("").isEmpty())
        assertTrue(repo.search("zzz").isEmpty())
    }

    @Test fun `fil vide sans abonnement puis rempli`() = runBlocking {
        val store = MemoryFollowStore()
        val repo = DemoSocialRepository(store)
        assertTrue(repo.feed(null).items.isEmpty())
        repo.setFollowing("p01", true)
        repo.setFollowing("p06", true)
        assertEquals(setOf("p01", "p06"), store.load())
        val first = repo.feed(null, pageSize = 5)
        assertEquals(5, first.items.size)
        assertTrue(first.items.all { it.pilot.id in setOf("p01", "p06") })
        assertTrue(first.items.zipWithNext().all { (a, b) -> !a.startedAt.isBefore(b.startedAt) })
        assertTrue(repo.suggestions().none { it.id == "p01" })
    }

    @Test fun `pagination par curseur sans doublon ni trou`() = runBlocking {
        val repo = DemoSocialRepository()
        repo.pilots.forEach { repo.setFollowing(it.id, true) }
        val seen = ArrayList<String>()
        var cursor: String? = null
        do {
            val page = repo.feed(cursor, pageSize = 7)
            seen += page.items.map { it.id }
            cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(seen.size, seen.toSet().size)
        assertEquals(repo.pilots.sumOf { runBlocking { repo.flightsOf(it.id).size } }, seen.size)
        assertTrue(seen.size > 60)
    }

    @Test fun `ne plus suivre retire les vols`() = runBlocking {
        val repo = DemoSocialRepository()
        repo.setFollowing("p02", true)
        assertTrue(repo.feed(null).items.isNotEmpty())
        repo.setFollowing("p02", false)
        assertTrue(repo.feed(null).items.isEmpty())
        assertNull(repo.feed(null).nextCursor)
    }

    @Test fun `traces normalisees dans le carre unite`() = runBlocking {
        val repo = DemoSocialRepository()
        val f = repo.flightsOf("p04")
        assertTrue(f.isNotEmpty())
        f.forEach { flight ->
            assertTrue(flight.route.size > 10)
            assertTrue(flight.route.all { (x, y) -> x in 0f..1f && y in 0f..1f })
            assertTrue(flight.distanceMeters > 0 && flight.durationSeconds > 0)
        }
        // déterministe
        assertEquals(f.map { it.distanceMeters }, DemoSocialRepository().flightsOf("p04").map { it.distanceMeters })
    }
}
