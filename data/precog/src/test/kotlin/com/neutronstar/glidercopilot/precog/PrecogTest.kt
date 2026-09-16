package com.neutronstar.glidercopilot.precog

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.LiftType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class PrecogTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResource("fixtures/$name")) { "fixture $name" }.readText()

    @Test fun parsesJsonEdgeCases() {
        val j = Json.parse("""{"a":[1,-2.5e2,true,null,"x\"yé"],"b":{}}""")
        assertEquals(-250.0, j["a"]!!.arr[1].num!!, 1e-9)
        assertEquals("x\"yé", j["a"]!!.arr[4].str)
        assertTrue(j["a"]!!.arr[3] is Json.Null)
    }

    @Test fun differentiatesAccumulatedFlux() {
        val env = Envelope(Json.parse(fixture("arpege_forecast_synth.json")))
        val s = ForecastMapper.surface(env)
        assertNull(s[0].shortwaveNetWm2)
        val noonUtc = s.first { it.validTime == Instant.parse("2026-09-16T11:00:00Z") }
        // 13 h locales : ~700 W/m² par construction
        assertEquals(700.0 * kotlin.math.sin(Math.PI * 6 / 13), noonUtc.shortwaveNetWm2!!, 1.0)
    }

    @Test fun mapsProfileAndAnalyses() {
        val env = Envelope(Json.parse(fixture("arpege_profile_synth.json")))
        val levels = ProfileMapper.levels(env, Instant.parse("2026-09-16T12:00:00Z"))
        assertEquals(24, levels.size)
        assertEquals(0.0, levels.last().cloudFraction!!, 1e-9)
        assertEquals(3000.0, levels.last().heightAglM, 1e-9)
    }

    @Test fun vigilanceForDepartement() {
        val v = VigilanceMapper.forDepartement(Json.parse(fixture("vigilance_synth.json")), "34")!!
        assertEquals(2, v.colorId)
        assertEquals("Jaune", v.colorLabel)
        assertEquals(listOf("Orages (Jaune)"), v.phenomena)
        assertNull(VigilanceMapper.forDepartement(Json.parse(fixture("vigilance_synth.json")), "99"))
    }

    @Test fun unknownUnitIsNeverGuessed() {
        assertNull(Units.toSi(1.0, "furlong"))
        assertEquals(1500.0, Units.toSi(15.0, "hPa")!!, 1e-9)
    }

    private class FakeHttp(val routes: Map<String, String>, var down: Boolean = false) : HttpClient {
        var calls = 0
        override fun get(url: String, ifNoneMatch: String?): HttpResult {
            calls++
            if (down) throw IOException("hors ligne")
            if (ifNoneMatch == "\"v1\"") return HttpResult(304, null, "\"v1\"")
            val key = routes.keys.firstOrNull { url.contains(it) } ?: return HttpResult(404, null, null)
            return HttpResult(200, routes[key], "\"v1\"")
        }
    }

    @Test fun etagRevalidationAndOfflineFallback() {
        val http = FakeHttp(mapOf("/vigilance" to fixture("vigilance_synth.json")))
        val api = PrecogApi(http, MemoryResponseCache(), clock = Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC))
        assertTrue(api.get("/vigilance") is Fetch.Ok)
        val second = api.get("/vigilance") as Fetch.Ok
        assertTrue(!second.offline)
        http.down = true
        val third = api.get("/vigilance") as Fetch.Ok
        assertTrue(third.offline)
        assertTrue(api.get("/inconnu") is Fetch.Failed)
    }

    @Test fun repositoryBuildsDay() = runBlocking {
        val http = FakeHttp(mapOf(
            "/arpege/forecast" to fixture("arpege_forecast_synth.json"),
            "/arpege/profile" to fixture("arpege_profile_synth.json"),
            "/vigilance" to fixture("vigilance_synth.json"),
        ))
        val clock = Clock.fixed(Instant.parse("2026-09-16T07:00:00Z"), ZoneOffset.UTC)
        val repo = WeatherRepository(PrecogApi(http, MemoryResponseCache(), clock = clock), clock = clock, io = Dispatchers.Unconfined)
        val r = repo.loadDay(LatLon(43.8, 3.78), "34", LocalDate.of(2026, 9, 16))
        assertTrue("résultat $r", r is WeatherResult.Success)
        val day = (r as WeatherResult.Success).day
        assertTrue(day.hours.isNotEmpty())
        val h = day.hours.first()
        assertEquals(180.0, h.analysis.groundAltitudeM, 0.5)
        assertTrue(day.hours.any { it.analysis.liftType != LiftType.NONE })
        assertNotNull(day.vigilance)
        assertTrue(day.sources.size >= 2)
        assertTrue(h.winds.isNotEmpty())
    }
}
