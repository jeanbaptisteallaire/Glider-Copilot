package com.neutronstar.glidercopilot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WindAndGeoTest {
    @Test fun componentsRoundTrip() {
        for (dir in listOf(0.0, 45.0, 120.0, 300.0)) {
            val (u, v) = Atmosphere.windComponents(10.0, dir)
            val (s, d) = Atmosphere.windFromComponents(u, v)
            assertEquals(10.0, s, 1e-9)
            assertEquals(dir, d, 1e-6)
        }
    }

    @Test fun northWindBlowsSouth() {
        val (u, v) = Atmosphere.windComponents(10.0, 0.0)
        assertEquals(0.0, u, 1e-9)
        assertEquals(-10.0, v, 1e-9)
    }

    @Test fun interpolatesLayers() {
        val lv = listOf(
            ProfileLevel(500.0, 290.0, null, 90_000.0, 5.0, 270.0, null, null),
            ProfileLevel(1500.0, 285.0, null, 80_000.0, 15.0, 270.0, null, null),
        )
        val layers = WindLayers.compute(lv, 0.0, 2.0, 250.0, listOf(1000.0, 5000.0))
        assertEquals(2, layers.size)
        assertEquals("Sol", layers[0].label)
        assertEquals(36.0, layers[1].speedKmh, 1e-6)
        assertEquals(270.0, layers[1].fromDeg, 1e-6)
    }

    @Test fun nearestClub() {
        val lfnl = Club("cvvpsl", "CVV Montpellier Pic Saint Loup", "CVVPSL", "Mas-de-Londres", "34380", null, "LFNL", null, LatLon(43.80028, 3.78167))
        val lfnn = Club("acn", "Aéroclub de Narbonne", "ACN", "Narbonne", "11100", null, "LFNN", null, LatLon(43.19417, 3.05167))
        val fed = Club("ffvp", "FFVP", "FFVP", "Saint-Auban", "04160", null, null, null, LatLon(43.8, 3.79), isFederation = true)
        val montpellier = LatLon(43.61, 3.88)
        assertEquals("cvvpsl", ClubLocator.nearest(listOf(lfnn, fed, lfnl), montpellier)!!.id)
        assertEquals(21.0, Geo.distanceKm(montpellier, lfnl.position!!), 3.0)
    }
}
