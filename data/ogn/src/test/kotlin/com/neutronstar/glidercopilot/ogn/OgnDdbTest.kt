package com.neutronstar.glidercopilot.ogn

import com.neutronstar.glidercopilot.domain.flarm.Registration
import com.neutronstar.glidercopilot.precog.HttpResult
import com.neutronstar.glidercopilot.precog.MemoryResponseCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

class OgnDdbTest {
    private fun text(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun normalizesRegistrations() {
        assertEquals("F-CJAB", Registration.normalize("  f-cjab "))
        assertEquals("F-CJAB", Registration.normalize("F-C J/AB"))
        assertEquals(Registration.key("FCJAB"), Registration.key("f-cjab"))
        assertFalse(Registration.isValid(Registration.normalize("f-")))
        assertEquals(10, Registration.normalize("ABCDEFGHIJKLMN").length)
    }

    @Test fun parsesAndSkipsEmptyRegistrations() {
        val list = DdbParser.parse(text("ddb_synth.json"))
        assertEquals(4, list.size)
        assertFalse(list.first { it.registration == "F-CHID" }.tracked)
        assertFalse(list.first { it.registration == "F-CPSL" }.identified)
    }

    @Test fun findsFlarmFirstWithTolerantKey() {
        val list = DdbParser.parse(text("ddb_synth.json"))
        val hits = DdbParser.find(list, "fcjab")
        assertEquals(listOf("DD1234", "07ABCD"), hits.map { it.deviceId })
    }

    @Test fun lookupRespectsTrackingChoiceAndCaches() {
        var calls = 0
        var now = Instant.parse("2026-09-16T08:00:00Z")
        val db = OgnDeviceDatabase({ _, _ -> calls++; HttpResult(200, text("ddb_synth.json"), "\"e1\"") }, MemoryResponseCache(), { now })
        val found = db.lookup("F-CJAB")
        assertTrue(found is PairingLookup.Found)
        assertEquals("DD1234", (found as PairingLookup.Found).devices.first().deviceId)
        assertTrue(db.lookup("F-CHID") is PairingLookup.NotTracked)
        assertTrue(db.lookup("F-XXXX") is PairingLookup.NotFound)
        assertEquals(1, calls)
        now = now.plus(Duration.ofHours(25))
        db.lookup("F-CJAB")
        assertEquals(2, calls)
    }

    @Test fun offlineFallsBackToLastCopy() {
        val cache = MemoryResponseCache()
        var online = true
        var now = Instant.parse("2026-09-16T08:00:00Z")
        val db = OgnDeviceDatabase({ _, _ -> if (online) HttpResult(200, text("ddb_synth.json"), null) else throw IOException("réseau") }, cache, { now })
        db.lookup("F-CJAB")
        online = false
        now = now.plus(Duration.ofDays(3))
        val r = db.lookup("F-CJAB") as PairingLookup.Found
        assertTrue(r.offline)
        val none = OgnDeviceDatabase({ _, _ -> throw IOException("réseau") }, MemoryResponseCache()).lookup("F-CJAB")
        assertTrue(none is PairingLookup.Unavailable)
    }
}

/** Échantillon réel de la DDB OGN (planeurs F-C, enregistré par la CI le 16/09/2026). */
class RealDdbTest {
    private val list = DdbParser.parse(javaClass.classLoader!!.getResource("real_ddb_fc_sample_20260916.json")!!.readText())

    @Test fun parsesRealSample() {
        assertEquals(400, list.size)
        assertTrue(list.all { it.registration.startsWith("F-C") })
    }

    @Test fun findsRealGlider() {
        val hit = DdbParser.find(list, "f-cphi").first()
        assertEquals("004839", hit.deviceId)
        assertEquals("Duo Discus", hit.aircraftModel)
        assertEquals("FLARM", hit.deviceTypeLabel)
    }
}
