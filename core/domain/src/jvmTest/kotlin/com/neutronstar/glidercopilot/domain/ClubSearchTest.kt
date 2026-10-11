package com.neutronstar.glidercopilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubSearchTest {
    private fun club(id: String, name: String, short: String?, city: String, icao: String?, field: String? = null) =
        Club(id, name, short, city, "34000", null, icao, field, null)

    private val clubs = listOf(
        club("acam", "Aéroclub Albert Mangeot", "ACAM", "Pont Saint Vincent", null),
        club("aca", "Aéroclub Alpin", "ACA", "Tallard", "LFNA", "Gap Tallard"),
        club("cvvm", "Centre de Vol à Voile Montpellier Pic Saint Loup", "CVVMPSL", "Saint-Martin-de-Londres", "LFNL"),
        club("lfnl2", "Les Planeurs du Lac", "LFNL", "Annecy", null),
    )

    @Test fun emptyQueryKeepsAll() = assertEquals(clubs, ClubSearch.search(clubs, "  "))

    @Test fun findsByIcaoCaseInsensitive() {
        val r = ClubSearch.search(clubs, "lfna")
        assertEquals(listOf("aca"), r.map { it.id })
    }

    @Test fun findsByClubAcronym() = assertEquals(listOf("acam"), ClubSearch.search(clubs, "ACAM").map { it.id })

    @Test fun exactCodeFirst() {
        val r = ClubSearch.search(clubs, "LFNL").map { it.id }
        assertEquals(setOf("cvvm", "lfnl2"), r.toSet())
    }

    @Test fun findsByWordsWithoutAccents() {
        assertEquals(listOf("cvvm"), ClubSearch.search(clubs, "montpellier").map { it.id })
        assertEquals(listOf("cvvm"), ClubSearch.search(clubs, "vol a voile pic").map { it.id })
        assertEquals(listOf("aca"), ClubSearch.search(clubs, "alpin").map { it.id })
        assertTrue(ClubSearch.search(clubs, "aeroclub").size == 2)
    }

    @Test fun findsByCity() = assertEquals(listOf("aca"), ClubSearch.search(clubs, "tallard").map { it.id })

    @Test fun noMatch() = assertTrue(ClubSearch.search(clubs, "zzzz").isEmpty())
}
