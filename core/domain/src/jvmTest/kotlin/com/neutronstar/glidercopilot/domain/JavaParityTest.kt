package com.neutronstar.glidercopilot.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/** iOS / KMP étape 1 : les remplaçants communs rendent exactement ce que rendaient les appels Java (Android inchangé). */
class JavaParityTest {
    private val rnd = Random(20261011)
    private fun values() = List(20_000) { (rnd.nextDouble() - 0.5) * 10.0.let { m -> Math.pow(m, rnd.nextInt(-3, 6).toDouble()) } } +
        listOf(0.0, -0.0, 0.05, 0.15, 0.25, 0.35, 1.45, 2.5, -2.5, 1e-9, 123456.789, Double.MIN_VALUE)

    @Test fun mathHelpers() = values().forEach {
        assertEquals(Math.toRadians(it), toRadians(it), 0.0)
        assertEquals(Math.toDegrees(it), toDegrees(it), 0.0)
        assertEquals(Math.round(it), roundHalfUp(it))
    }

    @Test fun roundHandlesNaNAndInfinities() {
        assertEquals(Math.round(Double.NaN), roundHalfUp(Double.NaN))
        assertEquals(Math.round(Double.POSITIVE_INFINITY), roundHalfUp(Double.POSITIVE_INFINITY))
        assertEquals(Math.round(0.49999999999999994), roundHalfUp(0.49999999999999994))
    }

    @Test fun intPadding() = (0..100_000 step 7).forEach {
        assertEquals("%05d".format(Locale.ROOT, it), it.zeroPad(5))
        assertEquals("%02d".format(Locale.ROOT, it), it.zeroPad(2))
    }

    @Test fun secondsBetweenMatchesDuration() = repeat(5_000) {
        val a = java.time.Instant.ofEpochSecond(rnd.nextLong(0, 4_000_000_000), rnd.nextLong(0, 1_000_000_000))
        val b = java.time.Instant.ofEpochSecond(rnd.nextLong(0, 4_000_000_000), rnd.nextLong(0, 1_000_000_000))
        val ka = kotlinx.datetime.Instant.fromEpochSeconds(a.epochSecond, a.nano)
        val kb = kotlinx.datetime.Instant.fromEpochSeconds(b.epochSecond, b.nano)
        assertEquals(java.time.Duration.between(a, b).seconds, secondsBetween(ka, kb))
        assertEquals(a.toString(), ka.toString())   // format du cache disque precog
    }
}
