package com.neutronstar.glidercopilot.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayScoreTest {
    private fun hour(h: Int, climb: Double, ceilingAgl: Double, lift: LiftType = LiftType.CUMULUS, cape: Double? = null) = ThermalAnalysis(
        validTime = Instant.parse("2026-10-22T${h.toString().padStart(2, '0')}:00:00Z"),
        groundAltitudeM = 200.0, dryTopAglM = ceilingAgl, dryTopCapped = false, cloudBaseAglM = ceilingAgl,
        ceilingAglM = ceilingAgl, liftType = lift, wStarMs = climb + 0.5, climbMs = climb, capeJkg = cape, totalCloudPct = 30.0,
    )

    @Test fun sansAscendanceZero() {
        val s = DayPlanner.summarize(listOf(hour(10, 0.2, 300.0, LiftType.NONE)))
        assertEquals(0, DayScore.compute(s, 5.0).score)
        assertEquals(ScoreLevel.POOR, DayScore.compute(s, 5.0).level)
    }

    @Test fun belleJourneeExcellente() {
        val s = DayPlanner.summarize((10..16).map { hour(it, 2.8, 2100.0) })
        val r = DayScore.compute(s, 8.0)
        assertEquals(100, r.score)
        assertEquals(ScoreLevel.GREAT, r.level)
    }

    @Test fun ventEtSurdeveloppementPenalisent() {
        val hours = (11..15).map { hour(it, 1.5, 1250.0, cape = 1200.0) }
        val calm = DayScore.compute(DayPlanner.summarize(hours), 5.0)
        val windy = DayScore.compute(DayPlanner.summarize(hours), 40.0)
        assertEquals(15, calm.overdevPenalty)
        assertEquals(15, windy.windPenalty)
        assertEquals(calm.score - 15, windy.score)
    }

    @Test fun borneEntre0Et100() {
        val r = DayScore.compute(DayPlanner.summarize(listOf(hour(12, 0.6, 520.0, cape = 2000.0))), 50.0)
        assertTrue(r.score in 0..100)
    }
}
