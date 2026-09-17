package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Échantillon capteur horodaté en nanosecondes depuis le début du rejeu. */
sealed interface SensorSample {
    val timeNs: Long
    data class Baro(override val timeNs: Long, val hPa: Double) : SensorSample
    data class Accel(override val timeNs: Long, val upMs2: Double) : SensorSample
    data class Gps(override val timeNs: Long, val fix: GpsFix) : SensorSample
}

/**
 * Banc de rejeu : reconstruit à partir d'une trace IGC (1 Hz) les flux qu'auraient produits les capteurs d'un
 * téléphone — baromètre à [baroHz] (bruit [baroNoiseM] en altitude), accélération verticale à [accelHz]
 * (dérivée seconde de la trajectoire + vibrations [accelNoise] + biais [accelBias]) et GPS à 1 Hz.
 * Sert aux tests du filtre, à la démonstration hors saison et aux captures CI.
 */
class SensorReplay(
    fixes: List<IgcFix>,
    private val baroHz: Int = 25,
    private val accelHz: Int = 50,
    private val baroNoiseM: Double = 0.3,
    private val accelNoise: Double = 0.4,
    private val accelBias: Double = 0.15,
    private val withBaro: Boolean = true,
    private val withAccel: Boolean = true,
    seed: Int = 7,
) {
    private val pts = fixes.filter { it.valid }.let { l -> l.filterIndexed { i, f -> i == 0 || f.time.epochSecond > l[i - 1].time.epochSecond } }
    private val rnd = Random(seed)
    val start: Instant = pts.firstOrNull()?.time ?: Instant.EPOCH
    val durationS: Double = if (pts.size < 2) 0.0 else (pts.last().time.epochSecond - pts.first().time.epochSecond).toDouble()
    private val ts = DoubleArray(pts.size) { (pts[it].time.epochSecond - start.epochSecond).toDouble() }
    private val hs = DoubleArray(pts.size) { (pts[it].pressureAltM ?: pts[it].gnssAltM ?: 0).toDouble() }
    // pentes lissées (différences centrées sur ±2 points) : une trace IGC arrondie au mètre donne sinon une accélération hachée
    private val vs = DoubleArray(pts.size) { i ->
        val a = max(0, i - 2); val b = min(pts.lastIndex, i + 2)
        if (b == a) 0.0 else (hs[b] - hs[a]) / (ts[b] - ts[a])
    }

    private fun segment(t: Double): Int {
        var lo = 0; var hi = ts.size - 1
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (ts[m] <= t) lo = m else hi = m }
        return lo
    }

    /** Altitude « vraie » (Hermite cubique) à t secondes. */
    fun altitudeAt(t: Double): Double = hermite(t).first

    /** Vitesse verticale « vraie » à t secondes. */
    fun climbAt(t: Double): Double = hermite(t).second

    private fun hermite(t: Double): Triple<Double, Double, Double> {
        if (pts.size < 2) return Triple(hs.firstOrNull() ?: 0.0, 0.0, 0.0)
        val tc = t.coerceIn(0.0, ts.last())
        val i = segment(tc).coerceAtMost(ts.size - 2)
        val dt = ts[i + 1] - ts[i]
        val s = (tc - ts[i]) / dt
        val p0 = hs[i]; val p1 = hs[i + 1]; val m0 = vs[i] * dt; val m1 = vs[i + 1] * dt
        val h = (2 * s * s * s - 3 * s * s + 1) * p0 + (s * s * s - 2 * s * s + s) * m0 + (-2 * s * s * s + 3 * s * s) * p1 + (s * s * s - s * s) * m1
        val dh = ((6 * s * s - 6 * s) * p0 + (3 * s * s - 4 * s + 1) * m0 + (-6 * s * s + 6 * s) * p1 + (3 * s * s - 2 * s) * m1) / dt
        val ddh = ((12 * s - 6) * p0 + (6 * s - 4) * m0 + (-12 * s + 6) * p1 + (6 * s - 2) * m1) / (dt * dt)
        return Triple(h, dh, ddh)
    }

    private fun gauss() = rnd.nextDouble().let { u1 -> kotlin.math.sqrt(-2 * kotlin.math.ln(1 - u1)) * kotlin.math.cos(2 * Math.PI * rnd.nextDouble()) }

    /** Tous les échantillons dans l'ordre chronologique, produits paresseusement. */
    fun samples(): Sequence<SensorSample> = sequence {
        if (pts.size < 2) return@sequence
        val endNs = (durationS * 1e9).toLong()
        val baroStep = 1_000_000_000L / baroHz
        val accelStep = 1_000_000_000L / accelHz
        var nb = 0L; var na = 0L; var ng = 0L
        var gpsIdx = 0
        while (true) {
            val tb = if (withBaro) nb * baroStep else Long.MAX_VALUE
            val ta = if (withAccel) na * accelStep else Long.MAX_VALUE
            val tg = ng * 1_000_000_000L
            val next = minOf(tb, ta, tg)
            if (next > endNs) break
            when (next) {
                tg -> {
                    while (gpsIdx < pts.lastIndex && ts[gpsIdx + 1] <= tg / 1e9) gpsIdx++
                    yield(SensorSample.Gps(tg, gpsFix(gpsIdx)))
                    ng++
                }
                tb -> {
                    val h = altitudeAt(tb / 1e9) + gauss() * baroNoiseM
                    yield(SensorSample.Baro(tb, Isa.pressureAt(h)))
                    nb++
                }
                else -> {
                    val a = hermite(ta / 1e9).third + accelBias + gauss() * accelNoise
                    yield(SensorSample.Accel(ta, a))
                    na++
                }
            }
        }
    }

    private fun gpsFix(i: Int): GpsFix {
        val f = pts[i]
        val prev = pts[max(0, i - 1)]; val next = pts[min(pts.lastIndex, i + 1)]
        val span = (next.time.epochSecond - prev.time.epochSecond).coerceAtLeast(1)
        val dist = Geo.distanceKm(prev.position, next.position)
        return GpsFix(
            time = f.time,
            position = f.position,
            altitudeM = (f.gnssAltM ?: f.pressureAltM)?.toDouble(),
            groundSpeedKmh = dist / span * 3600.0,
            trackDeg = if (dist > 0.002) Geo.bearingDeg(prev.position, next.position) else null,
            accuracyM = 4.0,
        )
    }

    companion object {
        fun position(fix: IgcFix): LatLon = fix.position
    }
}
