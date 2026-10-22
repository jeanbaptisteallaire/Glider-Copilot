package com.neutronstar.glidercopilot.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Niveau du score du jour : la couleur de l'anneau suit le sens fixe de la charte (rouge, ambre, vert, cyan). */
enum class ScoreLevel { POOR, FAIR, GOOD, GREAT }

/** Score météo vol à voile du jour, sur 100 (indicatif), et ce qui le compose (montrable au pilote). */
data class DayScoreResult(
    val score: Int,
    val level: ScoreLevel,
    val climbPts: Int,
    val ceilingPts: Int,
    val durationPts: Int,
    val windPenalty: Int,
    val overdevPenalty: Int,
)

/**
 * Score du jour (JB, 22/10/2026), façon anneau Apple Watch. Indicatif, jamais une autorisation de vol :
 * - montée exploitable : 40 pts (0 à 0,5 m/s → 40 à 2,5 m/s) ;
 * - plafond au-dessus du sol : 25 pts (0 à 500 m → 25 à 2 000 m) ;
 * - durée exploitable (déclenchement → fin) : 20 pts (0 → 6 h) ;
 * - base de 15 pts dès qu'il y a une ascendance exploitable ;
 * - vent moyen à 10 m sur les heures exploitables : −15 pts au plus (dès 15 km/h, plein à 35 km/h) ;
 * - risque de surdéveloppement : −15 pts.
 * Sans ascendance exploitable : 0.
 */
object DayScore {
    fun compute(summary: DaySummary, meanWind10mKmh: Double?): DayScoreResult {
        if (summary.quality == DayQuality.NONE || summary.best == null) {
            return DayScoreResult(0, ScoreLevel.POOR, 0, 0, 0, 0, 0)
        }
        val usable = summary.hours.filter { it.liftType != LiftType.NONE && it.climbMs >= DayPlanner.USABLE_CLIMB_MS }
        val climb = usable.maxOfOrNull { it.climbMs } ?: 0.0
        val ceiling = usable.maxOfOrNull { it.ceilingAglM } ?: 0.0
        val hours = if (summary.triggerTime != null && summary.endTime != null)
            secondsBetween(summary.triggerTime, summary.endTime) / 3600.0 + 1.0 else 0.0
        val climbPts = ramp(climb, 0.5, 2.5, 40)
        val ceilingPts = ramp(ceiling, 500.0, 2000.0, 25)
        val durationPts = ramp(hours, 0.0, 6.0, 20)
        val windPenalty = meanWind10mKmh?.let { ramp(it, 15.0, 35.0, 15) } ?: 0
        val overdev = if (summary.overdevelopmentRisk) 15 else 0
        val score = (15 + climbPts + ceilingPts + durationPts - windPenalty - overdev).coerceIn(0, 100)
        return DayScoreResult(score, level(score), climbPts, ceilingPts, durationPts, windPenalty, overdev)
    }

    fun level(score: Int): ScoreLevel = when {
        score < 30 -> ScoreLevel.POOR
        score < 55 -> ScoreLevel.FAIR
        score < 80 -> ScoreLevel.GOOD
        else -> ScoreLevel.GREAT
    }

    private fun ramp(v: Double, from: Double, to: Double, pts: Int): Int =
        (min(max((v - from) / (to - from), 0.0), 1.0) * pts).roundToInt()
}
