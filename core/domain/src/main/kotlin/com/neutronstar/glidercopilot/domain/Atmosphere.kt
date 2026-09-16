package com.neutronstar.glidercopilot.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Constantes et conversions physiques. Aucune valeur n'est convertie en dehors de ce fichier. */
object Atmosphere {
    const val G = 9.80665
    const val R_DRY = 287.05
    const val CP = 1005.0
    const val KAPPA = R_DRY / CP
    const val P0 = 100_000.0

    fun kelvinToCelsius(k: Double) = k - 273.15

    /** Température potentielle (K) à partir de T (K) et p (Pa). */
    fun potentialTemperature(tK: Double, pPa: Double) = tK * (P0 / pPa).pow(KAPPA)

    fun airDensity(pPa: Double, tK: Double) = pPa / (R_DRY * tK)

    /** Composantes (u vers l'est, v vers le nord) d'un vent venant de [fromDeg]. */
    fun windComponents(speed: Double, fromDeg: Double): Pair<Double, Double> {
        val r = Math.toRadians(fromDeg)
        return Pair(-speed * sin(r), -speed * cos(r))
    }

    /** Vitesse et direction d'où vient le vent (°) à partir de u, v. */
    fun windFromComponents(u: Double, v: Double): Pair<Double, Double> {
        val speed = hypot(u, v)
        var dir = Math.toDegrees(atan2(-u, -v))
        if (dir < 0) dir += 360.0
        return Pair(speed, dir)
    }

    fun msToKmh(ms: Double) = ms * 3.6
}
