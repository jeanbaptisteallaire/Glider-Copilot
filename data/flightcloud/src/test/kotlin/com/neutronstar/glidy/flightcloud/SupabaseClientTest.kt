package com.neutronstar.glidy.flightcloud

import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightSummary
import com.neutronstar.glidy.flightarchive.IgcFileRef
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.flightarchive.ReconciliationResult
import com.neutronstar.glidy.flightarchive.RemoveFlightResult
import com.neutronstar.glidy.flightarchive.SyncState
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faux serveur Supabase : enregistre les requêtes, répond selon la route. */
private class FakeSupabase : CloudTransport {
    val requests = mutableListOf<CloudRequest>()
    val storage = mutableMapOf<String, ByteArray>()
    val rows = mutableListOf<Map<String, Any?>>()
    var failStorage = false

    override fun send(request: CloudRequest): CloudResponse {
        requests += request
        val path = request.url.removePrefix(BASE)
        fun json(code: Int, v: Any?) = CloudResponse(code, MiniJson.write(v).toByteArray())
        return when {
            path == "/auth/v1/otp" -> json(200, emptyMap<String, Any?>())
            path == "/auth/v1/verify" || path.startsWith("/auth/v1/token") -> json(200, mapOf(
                "access_token" to "acc-" + requests.size, "refresh_token" to "ref", "expires_in" to 3600,
                "user" to mapOf("id" to USER, "email" to "jb@example.org"),
            ))
            path.startsWith("/storage/v1/object/igc/") && request.method == "POST" ->
                if (failStorage) json(503, mapOf("message" to "indisponible"))
                else { storage[path.removePrefix("/storage/v1/object/igc/")] = request.body!!; json(200, mapOf("Key" to path)) }
            path.startsWith("/rest/v1/flights?on_conflict") -> {
                @Suppress("UNCHECKED_CAST")
                (MiniJson.parse(request.body!!.toString(Charsets.UTF_8)) as List<Map<String, Any?>>).forEach { r -> rows.removeAll { it["sha256"] == r["sha256"] }; rows += r }
                CloudResponse(201, ByteArray(0))
            }
            path.startsWith("/rest/v1/flights?select") -> json(200, rows)
            path.startsWith("/storage/v1/object/authenticated/igc/") -> CloudResponse(200, storage[path.removePrefix("/storage/v1/object/authenticated/igc/")]!!)
            path == "/storage/v1/object/list/igc" -> json(200, storage.keys.map { mapOf("name" to it.substringAfter('/')) })
            path == "/storage/v1/object/igc" && request.method == "DELETE" -> { storage.clear(); json(200, emptyList<Any>()) }
            path == "/rest/v1/rpc/delete_my_account" -> { rows.clear(); CloudResponse(204, ByteArray(0)) }
            else -> json(404, mapOf("message" to "route inconnue $path"))
        }
    }

    companion object {
        const val BASE = "https://glidy-test.supabase.co"
        const val USER = "11111111-2222-3333-4444-555555555555"
    }
}

/** Faux carnet local en mémoire. */
private class FakeArchive(flights: List<ArchivedFlight>, private val files: MutableMap<String, ByteArray>) : FlightArchiveRepository {
    val flights = flights.toMutableList()
    override suspend fun importIgc(fileName: String, source: InputStream): ImportIgcResult {
        val bytes = source.readBytes()
        val sha = sha(bytes)
        flights.firstOrNull { it.file.sha256 == sha }?.let { return ImportIgcResult.Duplicate(it) }
        val f = flight(fileName, bytes)
        flights += f; files[f.id.value] = bytes
        return ImportIgcResult.Imported(f)
    }
    override suspend fun reconcile() = ReconciliationResult(0, 0, 0, 0)
    override suspend fun listFlights() = flights.toList()
    override suspend fun findFlight(id: FlightId) = flights.firstOrNull { it.id == id }
    override suspend fun removeLocalFlight(id: FlightId) = RemoveFlightResult.NotFound
    override suspend fun readIgc(id: FlightId) = files[id.value]
    override suspend fun updateSyncState(id: FlightId, state: SyncState, remoteId: String?): Boolean {
        val i = flights.indexOfFirst { it.id == id }
        if (i < 0) return false
        flights[i] = flights[i].copy(syncState = state, remoteId = remoteId)
        return true
    }
}

private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun flight(name: String, bytes: ByteArray) = ArchivedFlight(
    id = FlightId(UUID.nameUUIDFromBytes(bytes).toString()),
    file = IgcFileRef("a/$name", name, bytes.size.toLong(), sha(bytes)),
    summary = FlightSummary(Instant.parse("2026-09-19T10:00:00Z"), Instant.parse("2026-09-19T11:53:00Z"), 10, 10, 128_000, 190, 2133, 3745, null),
    pilot = "JB", gliderType = "Duo Discus", gliderId = "F-CPHI", localState = LocalFileState.AVAILABLE,
)

class SupabaseClientTest {
    private val config = CloudConfig(FakeSupabase.BASE, "anon-key")

    @Test fun disabledWithoutConfigurationAndSendsNothing() {
        val server = FakeSupabase()
        val client = SupabaseClient(CloudConfig.DISABLED, server, MemorySessionStore())
        assertEquals(CloudResult.NotConfigured, client.sendCode("jb@example.org"))
        assertEquals(0, server.requests.size)
    }

    @Test fun codeSignInStoresSessionAndUsesAnonKey() {
        val server = FakeSupabase(); val store = MemorySessionStore()
        val client = SupabaseClient(config, server, store) { 1_000 }
        assertTrue(client.sendCode(" jb@example.org ") is CloudResult.Ok)
        val s = (client.verifyCode("jb@example.org", "123456") as CloudResult.Ok).value
        assertEquals(FakeSupabase.USER, s.userId)
        assertEquals(4_600L, s.expiresAtEpochSeconds)
        assertEquals(s, store.load())
        assertEquals("anon-key", server.requests.first().headers["apikey"])
        assertTrue(server.requests.first().body!!.toString(Charsets.UTF_8).contains("\"email\":\"jb@example.org\""))
    }

    @Test fun expiredSessionIsRefreshedBeforeUse() {
        val server = FakeSupabase()
        val store = MemorySessionStore(CloudSession("old", "ref", FakeSupabase.USER, "jb@example.org", expiresAtEpochSeconds = 1_010))
        val client = SupabaseClient(config, server, store) { 1_000 }
        assertTrue(client.listFlights() is CloudResult.Ok)
        assertTrue(server.requests[0].url.contains("grant_type=refresh_token"))
        assertEquals("Bearer ${store.load()!!.accessToken}", server.requests[1].headers["Authorization"])
    }

    @Test fun uploadIsGzippedInThePilotFolderAndIdempotent() {
        val server = FakeSupabase()
        val client = SupabaseClient(config, server, MemorySessionStore(CloudSession("a", "r", FakeSupabase.USER, "e", 99_999)) ) { 1_000 }
        val igc = "AXXXGLYGLIDY\r\nB1000004348120N00345000EA0020000200\r\n".toByteArray()
        val f = flight("vol.igc", igc)
        val rec = CloudFlightRecord(f.id.value, f.file.sha256, "vol.igc", igc.size.toLong(), "", null, null, null, null, null, null, null, null, null, null)
        val stored = (client.uploadFlight(rec, igc) as CloudResult.Ok).value
        assertEquals("${FakeSupabase.USER}/${f.id.value}.igc.gz", stored.storagePath)
        assertArrayEquals(igc, SupabaseClient.gunzip(server.storage[stored.storagePath]!!))
        client.uploadFlight(rec, igc)
        assertEquals(1, server.rows.size)
        assertEquals(FakeSupabase.USER, server.rows.single()["user_id"])
    }

    @Test fun deleteAccountRemovesFilesThenAccountAndSignsOut() {
        val server = FakeSupabase(); val store = MemorySessionStore(CloudSession("a", "r", FakeSupabase.USER, "e", 99_999))
        val client = SupabaseClient(config, server, store) { 1_000 }
        server.storage["${FakeSupabase.USER}/x.igc.gz"] = ByteArray(3)
        assertTrue(client.deleteAccount() is CloudResult.Ok)
        assertTrue(server.storage.isEmpty())
        assertEquals("/rest/v1/rpc/delete_my_account", server.requests.last().url.removePrefix(FakeSupabase.BASE))
        assertNull(store.load())
    }

    @Test fun syncUploadsRealFlightsOnlyThenRestoresOnANewPhone() = runBlocking {
        val server = FakeSupabase()
        val session = CloudSession("a", "r", FakeSupabase.USER, "e", 99_999)
        val real = "AXXXGLYGLIDY\r\nB1000004348120N00345000EA0020000200\r\n".toByteArray()
        val demo = "AXXXGLYGLIDY\r\nB1100004348120N00345000EA0030000300\r\n".toByteArray()
        val files = mutableMapOf<String, ByteArray>()
        val a = flight("2026-09-19-XXX-GLY-01.igc", real); val d = flight("exemple-saint-martin.igc", demo)
        files[a.id.value] = real; files[d.id.value] = demo
        val phone1 = FakeArchive(listOf(a, d), files)
        val sync1 = FlightSyncService(SupabaseClient(config, server, MemorySessionStore(session)) { 1_000 }, phone1)

        val first = sync1.sync()
        assertEquals(1, first.uploaded)
        assertEquals(SyncState.SYNCED, phone1.flights.first { it.id == a.id }.syncState)
        assertEquals(SyncState.LOCAL_ONLY, phone1.flights.first { it.id == d.id }.syncState)
        assertEquals(1, sync1.sync().alreadySynced)

        val phone2 = FakeArchive(emptyList(), mutableMapOf())
        val report = FlightSyncService(SupabaseClient(config, server, MemorySessionStore(session)) { 1_000 }, phone2).sync()
        assertEquals(1, report.restored)
        assertEquals(a.file.sha256, phone2.flights.single().file.sha256)
    }

    @Test fun failedUploadIsMarkedForRetry() = runBlocking {
        val server = FakeSupabase().apply { failStorage = true }
        val igc = "AXXXGLYGLIDY\r\nB1000004348120N00345000EA0020000200\r\n".toByteArray()
        val f = flight("vol.igc", igc)
        val phone = FakeArchive(listOf(f), mutableMapOf(f.id.value to igc))
        val report = FlightSyncService(SupabaseClient(config, server, MemorySessionStore(CloudSession("a", "r", FakeSupabase.USER, "e", 99_999))) { 1_000 }, phone).sync(restore = false)
        assertEquals(1, report.failed)
        assertEquals(SyncState.FAILED_RETRYABLE, phone.flights.single().syncState)
    }

    @Test fun miniJsonRoundTrip() {
        val v = mapOf("a" to "é\"\n", "b" to listOf(1, 2.5, true, null), "c" to mapOf("d" to "x"))
        val back = MiniJson.parse(MiniJson.write(v)).obj()
        assertEquals("é\"\n", back["a"])
        assertEquals(listOf(1.0, 2.5, true, null), back["b"])
        assertEquals("x", back["c"].obj()["d"])
    }
}
