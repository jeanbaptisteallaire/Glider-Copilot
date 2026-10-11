package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidercopilot.domain.checklist.CableBriefInput
import com.neutronstar.glidercopilot.domain.checklist.CablePlan
import com.neutronstar.glidercopilot.domain.checklist.Checklists
import com.neutronstar.glidercopilot.domain.checklist.progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    @Test fun countsMatchTheMockup() {
        assertEquals(listOf("CRIS", "TVBCR", "VERDO", "APRÈS"), Checklists.all.map { it.code })
        assertEquals(listOf(18, 10, 8, 7), Checklists.all.map { it.items.size })
        val ids = Checklists.all.flatMap { cl -> cl.items.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Checklists.all.first().openByDefault)
    }

    @Test fun progressAndCompletion() {
        val tv = Checklists.all.first { it.id == "tvbcr" }
        assertEquals("0/10", tv.progress(emptySet()).toString())
        val all = tv.items.map { it.id }.toSet()
        assertTrue(tv.progress(all).complete)
    }

    @Test fun defaultCablePlan() {
        val p = CablePlan.from(CableBriefInput())
        assertEquals("Piste 27 · menace :", p.rows[0].lead)
        assertEquals("vent NO et trafic remorqué", p.rows[0].text)
        assertEquals("Rupture sous 80 m :", p.rows[1].lead)
        assertEquals("Entre 80 et 100 m :", p.rows[2].lead)
        assertEquals("rejoindre champ au nord de l’axe si l’axe ne suffit pas.", p.rows[2].text)
        assertEquals("Sous 80 m : atterrissage devant, axe conservé au maximum", p.aheadCheck)
        assertEquals("À partir de 100 m : circuit ou demi-tour adapté selon le briefing local", p.turnCheck)
    }

    @Test fun cablePlanRulesFollowJavascript() {
        val p = CablePlan.from(CableBriefInput(qfu = " ", turn = "50", ahead = "120m", field = "", threat = ""))
        assertEquals("Piste — · menace :", p.rows[0].lead)
        assertEquals("aucune menace particulière", p.rows[0].text)
        assertEquals("Entre 120 et 120 m :", p.rows[2].lead)   // demi-tour jamais sous le seuil bas
        assertEquals("Entre 120 et 120 m : terrain identifié", p.fieldCheck)
        assertEquals(0, CablePlan.from(CableBriefInput(ahead = "abc")).let { it.rows[1].lead.filter(Char::isDigit).toInt() })
        assertNull(CablePlan.leadingInt("x12"))
        assertEquals(-5, CablePlan.leadingInt(" -5 m"))
    }
}
