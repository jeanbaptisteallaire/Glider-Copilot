package com.neutronstar.glidercopilot.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Distance orthodromique en km (formule de haversine). */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
    }

    /** Route initiale de [a] vers [b], en degrés vrais 0–360. */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Point atteint depuis [from] en suivant [bearingDeg] sur [distKm]. */
    fun destination(from: LatLon, bearingDeg: Double, distKm: Double): LatLon {
        val d = distKm / EARTH_RADIUS_KM
        val br = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(from.lat); val l1 = Math.toRadians(from.lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(br))
        val l2 = l1 + atan2(sin(br) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return LatLon(Math.toDegrees(p2), Math.toDegrees(l2))
    }
}

/** Relief : altitude du sol (m, mer) en un point, null hors couverture. */
fun interface Terrain {
    fun elevationM(p: LatLon): Double?

    companion object {
        val NONE = Terrain { null }
    }
}
