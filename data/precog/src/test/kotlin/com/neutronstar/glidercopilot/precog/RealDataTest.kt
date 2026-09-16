package com.neutronstar.glidercopilot.precog

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.ThermalModel
import com.neutronstar.glidercopilot.domain.WindLayers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Réponses réelles relevées par la CI le 16/09/2026 sur LFNL (43.80028, 3.78167). */
class RealDataTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResource("fixtures/$name")) { "fixture $name" }.readText()

    @Test fun realForecastSurface() {
        val env = Envelope(Json.parse(fixture("real_arpege_forecast_20260916.json")))
        assertEquals(103, env.steps.size)
        val s = ForecastMapper.surface(env)
        val noon = s.first { it.validTime == Instant.parse("2026-09-16T12:00:00Z") }
        assertTrue("T2m ${noon.temperature2mK}", noon.temperature2mK in 280.0..315.0)
        assertTrue("SW ${noon.shortwaveNetWm2}", (noon.shortwaveNetWm2 ?: -1.0) in 50.0..1000.0)
        assertNotNull(noon.dewpoint2mK)
        assertNotNull(noon.pressureSurfacePa)
    }

    @Test fun realProfileAnalysis() {
        val fc = ForecastMapper.surface(Envelope(Json.parse(fixture("real_arpege_forecast_20260916.json"))))
        val lv = ProfileMapper.levels(Envelope(Json.parse(fixture("real_arpege_profile_20260916_12Z.json"))), Instant.parse("2026-09-16T12:00:00Z"))
        assertEquals(24, lv.size)
        val ground = ThermalModel.groundAltitude(lv)!!
        assertTrue("sol $ground", ground in 100.0..600.0)
        val a = ThermalModel.analyse(fc.first { it.validTime == Instant.parse("2026-09-16T12:00:00Z") }, lv)
        println("LFNL 12Z : sol=${ground.toInt()} m, sommet=${a.dryTopAglM.toInt()} m sol, baseCu=${a.cloudBaseAglM?.toInt()} m, plafond=${a.ceilingMslM.toInt()} m QNH, type=${a.liftType}, w*=${"%.2f".format(a.wStarMs)}, montée=${"%.2f".format(a.climbMs)}")
        assertTrue(a.ceilingAglM in 0.0..3100.0)
        val winds = WindLayers.compute(lv, ground, fc[6].wind10mMs, fc[6].wind10mFromDeg)
        assertTrue(winds.size >= 3)
    }

    @Test fun realVigilance() {
        val v = VigilanceMapper.forDepartement(Json.parse(fixture("real_vigilance_20260916.json")), "34")
        assertNotNull(v)
        assertTrue(v!!.colorId in 1..4)
        assertEquals(v.colorId == 1, v.phenomena.isEmpty())
    }

    @Test fun realRepositoryDay() = runBlocking {
        val profiles = listOf("09", "12", "15").associateWith { fixture("real_arpege_profile_20260916_${it}Z.json") }
        val http = HttpClient { url, _ ->
            when {
                "/arpege/forecast" in url -> HttpResult(200, fixture("real_arpege_forecast_20260916.json"), null)
                "/arpege/profile" in url -> {
                    val h = Regex("T(\\d\\d)%3A").find(url)?.groupValues?.get(1)
                    profiles[h]?.let { HttpResult(200, it, null) } ?: HttpResult(404, null, null)
                }
                "/vigilance" in url -> HttpResult(200, fixture("real_vigilance_20260916.json"), null)
                else -> HttpResult(404, null, null)
            }
        }
        val clock = Clock.fixed(Instant.parse("2026-09-16T07:00:00Z"), ZoneOffset.UTC)
        val repo = WeatherRepository(PrecogApi(http, MemoryResponseCache(), clock = clock), clock = clock, io = Dispatchers.Unconfined)
        val r = repo.loadDay(LatLon(43.80028, 3.78167), "34", LocalDate.of(2026, 9, 16))
        assertTrue("$r", r is WeatherResult.Success)
        val d = (r as WeatherResult.Success).day
        assertEquals(3, d.hours.size)
        d.hours.forEach { println("  ${it.analysis.validTime} plafond ${it.analysis.ceilingMslM.toInt()} m ${it.analysis.liftType} montée ${"%.1f".format(it.analysis.climbMs)}") }
        println("  qualité ${d.summary.quality}, vigilance ${d.vigilance?.colorLabel}")
    }
}
