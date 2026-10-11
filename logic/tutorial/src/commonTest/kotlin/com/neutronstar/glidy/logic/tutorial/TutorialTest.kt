package com.neutronstar.glidy.logic.tutorial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorialTest {
    @Test fun sameTourOnBothPlatforms() {
        val s = Tutorial.steps
        assertEquals(16, s.size)
        assertEquals(listOf(4, 9, 3), TourPart.entries.map { p -> s.count { it.part == p } })
        assertEquals("Prévol · 1 / 16", s.first().header("fr", 1, s.size))
        assertEquals("Votre terrain", s.first().title("fr"))
        assertEquals("Your airfield", s.first().title("en"))
    }

    @Test fun spotsStayInsideTheirCapture() {
        for (st in Tutorial.steps) {
            assertTrue(st.image.startsWith("tour_"), st.image)
            assertTrue(st.l < st.r && st.t < st.b && st.r <= 1080f && st.b <= st.imageHeight, st.titleFr)
        }
    }
}
