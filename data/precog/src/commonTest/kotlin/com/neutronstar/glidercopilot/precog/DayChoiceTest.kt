package com.neutronstar.glidercopilot.precog

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DayChoiceTest {
    @Test
    fun jourParDefautAujourdhuiAvant17hPuisDemain() {
        assertEquals(0, DayChoice.defaultOffset(Instant.parse("2026-10-24T14:59:00Z")))   // 16 h 59 à Paris
        assertEquals(1, DayChoice.defaultOffset(Instant.parse("2026-10-24T15:00:00Z")))   // 17 h à Paris
    }

    @Test
    fun dateBorneeAApresDemain() {
        val base = LocalDate(2026, 10, 31)
        assertEquals(LocalDate(2026, 11, 2), DayChoice.date(2, base))
        assertEquals(LocalDate(2026, 11, 2), DayChoice.date(9, base))
        assertEquals(base, DayChoice.date(-1, base))
    }
}
