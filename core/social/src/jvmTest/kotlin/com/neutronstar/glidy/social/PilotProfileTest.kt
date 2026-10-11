package com.neutronstar.glidy.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PilotProfileTest {
    @Test fun `pseudo normalise`() {
        assertEquals("jb_allaire", Usernames.normalize(" @JB Allaire "))
        assertEquals("eleve.pilote", Usernames.normalize("Élève.Pilote"))
    }

    @Test fun `regles du pseudo`() {
        assertNull(Usernames.problem("jb.allaire"))
        assertNull(Usernames.problem("pilote_42"))
        assertTrue(Usernames.problem("ab") != null)
        assertTrue(Usernames.problem("a".repeat(21)) != null)
        assertTrue(Usernames.problem("Majuscule") != null)
        assertTrue(Usernames.problem(".point") != null)
        assertTrue(Usernames.problem("deux..points") != null)
        assertTrue(Usernames.problem("") != null)
    }

    @Test fun `suggestion depuis le nom`() {
        val s = Usernames.suggestFrom("Jean-Baptiste Allaire")
        assertEquals("jean_baptiste.allair", s)
        assertNull(Usernames.problem(s))
        assertNull(Usernames.problem(Usernames.suggestFrom("Zo")))
        assertNull(Usernames.problem(Usernames.suggestFrom("")))
    }

    @Test fun `initiales`() {
        assertEquals("JA", PilotProfile(displayName = "Jean-Baptiste Allaire").initials)
        assertEquals("MA", PilotProfile(displayName = "marie").initials)
        assertEquals("?", PilotProfile().initials)
    }

    @Test fun `validation et total d'heures`() {
        val ok = PilotProfile(displayName = "JB", username = "jb_allaire", minutesBeforeApp = 120)
        assertTrue(ok.validate().isEmpty())
        assertEquals(2, PilotProfile().validate().size) // nom + pseudo
        assertEquals(120L + 90L, totalFlightMinutes(ok, 90 * 60))
    }
}
