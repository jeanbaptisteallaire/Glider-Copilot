package com.neutronstar.glidercopilot.domain.flight

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/**
 * Traces superposées (spirales) : un passage recouvert par des tours plus récents s'estompe un peu à chaque tour,
 * sans jamais disparaître. Une trace qui n'est recouverte par rien reste nette, même après une heure de vol.
 * Port commun de la V3 iOS (FluidMapView.overlapFade). Points supposés à 1 Hz (indices ≈ secondes).
 */
object TraceFade {
    /** Opacité par niveau : 0 = net … 5 = le plus fade (plancher, toujours visible). */
    val ALPHA = doubleArrayOf(1.0, 0.8, 0.68, 0.58, 0.5, 0.45)

    private const val CELL_M = 30.0
    private const val GAP = 15

    /** Niveau d'estompage (0…5) de chaque point de la trace [lat]/[lon]. */
    fun levels(lat: DoubleArray, lon: DoubleArray): IntArray {
        val n = lat.size
        val out = IntArray(n)
        if (n <= 30) return out
        val c = cos(lat[0] * PI / 180)
        fun kx(i: Int) = floor(lon[i] * 111_320 * c / CELL_M).toInt()
        fun ky(i: Int) = floor(lat[i] * 110_574 / CELL_M).toInt()
        val grid = HashMap<Long, MutableList<Int>>()
        fun key(x: Int, y: Int) = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
        for (i in 0 until n) grid.getOrPut(key(kx(i), ky(i))) { ArrayList() }.add(i)
        val later = ArrayList<Int>()
        for (i in 0 until n) {
            val x = kx(i); val y = ky(i)
            later.clear()
            for (dx in -1..1) for (dy in -1..1) grid[key(x + dx, y + dy)]?.forEach { j -> if (j > i + GAP) later += j }
            if (later.isEmpty()) continue
            later.sort()
            var passes = 1
            var prev = later[0]
            for (k in 1 until later.size) { if (later[k] - prev > GAP) passes++; prev = later[k] }
            out[i] = minOf(passes, 5)
        }
        return out
    }
}
