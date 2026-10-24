package com.neutronstar.glidercopilot.precog

import com.neutronstar.glidercopilot.domain.DayPlanner
import com.neutronstar.glidercopilot.domain.DayScore
import com.neutronstar.glidercopilot.domain.DayScoreResult
import com.neutronstar.glidercopilot.domain.LiftType
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Choix du jour en Prévol (JB, 22/10/2026) et score du jour, communs à Android et iOS :
 * 0 = aujourd'hui, 1 = demain, 2 = après-demain (heure de Paris).
 */
object DayChoice {
    private val PARIS = TimeZone.of("Europe/Paris")
    const val MAX_OFFSET = 2

    /** Jour proposé à l'ouverture : aujourd'hui avant 17 h, demain ensuite (comme avant le choix du jour). */
    fun defaultOffset(now: Instant = Clock.System.now()): Int = if (now.toLocalDateTime(PARIS).hour < 17) 0 else 1

    /** Date du jour choisi, à partir d'un jour imposé (données d'essai) ou d'aujourd'hui. */
    fun date(offsetDays: Int, base: LocalDate? = null, now: Instant = Clock.System.now()): LocalDate =
        (base ?: now.toLocalDateTime(PARIS).date).plus(offsetDays.coerceIn(0, MAX_OFFSET), DateTimeUnit.DAY)

    /** Score du jour sur 100 (indicatif) : vent moyen à 10 m sur les heures exploitables. */
    fun score(day: DayWeather): DayScoreResult {
        val usable = day.hours.filter { it.analysis.climbMs >= DayPlanner.USABLE_CLIMB_MS && it.analysis.liftType != LiftType.NONE }
        val winds = usable.mapNotNull { it.surface.wind10mMs }
        return DayScore.compute(day.summary, if (winds.isEmpty()) null else winds.average() * 3.6)
    }
}
