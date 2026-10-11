package com.neutronstar.glidercopilot.domain.flight

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Atmosphère type OACI (ISA) : conversions pression ↔ altitude pression. */
object Isa {
    const val P0_HPA = 1013.25
    private const val K = 44330.77
    private const val N = 0.1902632

    /** Altitude pression (m) pour une pression statique en hPa, référence 1013,25 hPa. */
    fun pressureAltitude(hPa: Double, referenceHpa: Double = P0_HPA): Double = K * (1 - (hPa / referenceHpa).pow(N))

    fun pressureAt(altitudeM: Double, referenceHpa: Double = P0_HPA): Double = referenceHpa * (1 - altitudeM / K).pow(1 / N)
}

/**
 * Accélération verticale « vers le haut » (m/s²) à partir de l'accéléromètre et du vecteur gravité du téléphone,
 * tous deux dans le repère de l'appareil. Au repos l'accéléromètre mesure +g le long de la verticale : a = acc·ĝ − g.
 */
object VerticalAcceleration {
    const val G = 9.80665

    fun compute(accX: Double, accY: Double, accZ: Double, gX: Double, gY: Double, gZ: Double): Double? {
        val norm = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (norm < 5.0) return null   // gravité non estimée (capteur en cours d'initialisation, chute libre)
        return (accX * gX + accY * gY + accZ * gZ) / norm - G
    }
}

/**
 * Vario à filtre de Kalman : état [altitude, vitesse verticale, biais d'accélération].
 * Prédiction à chaque échantillon d'accélération verticale (~50 Hz), correction à chaque mesure baro (~25 Hz).
 * Sans accéléromètre, le même filtre fonctionne en « baro seul » (accélération nulle, bruit de modèle plus fort).
 *
 * Réglages par défaut : bruit baro 0,35 m (écart type typique d'un capteur de téléphone),
 * bruit d'accélération 0,6 m/s² (vibrations, turbulence), dérive du biais 0,02 m/s²/√s.
 */
class VarioFilter(
    private val baroNoiseM: Double = 0.35,
    private val accelNoise: Double = 0.6,
    private val baroOnlyAccelNoise: Double = 1.2,
    private val biasDrift: Double = 0.02,
) {
    // état
    var altitude = 0.0; private set
    var climb = 0.0; private set
    var bias = 0.0; private set
    private val p = Array(3) { DoubleArray(3) }
    private var initialized = false
    private var lastTimeNs = 0L
    private var lastAccel = 0.0
    private var lastAccelNs = 0L

    /** Vrai tant que l'accéléromètre alimente le filtre (moins de 0,5 s sans échantillon). */
    fun usesAccelerometer(nowNs: Long): Boolean = lastAccelNs != 0L && nowNs - lastAccelNs < 500_000_000L

    fun reset(altitudeM: Double, timeNs: Long) {
        altitude = altitudeM; climb = 0.0; bias = 0.0
        for (i in 0..2) for (j in 0..2) p[i][j] = 0.0
        p[0][0] = baroNoiseM * baroNoiseM; p[1][1] = 1.0; p[2][2] = 0.25
        lastTimeNs = timeNs
        initialized = true
    }

    /** Échantillon d'accélération verticale (m/s², vers le haut). */
    fun onAcceleration(upMs2: Double, timeNs: Long) {
        if (!initialized) { lastAccel = upMs2; lastAccelNs = timeNs; return }
        predict(timeNs, upMs2, withAccel = true)
        lastAccel = upMs2
        lastAccelNs = timeNs
    }

    /** Mesure baro convertie en altitude pression (m). */
    fun onBaroAltitude(altitudeM: Double, timeNs: Long) {
        if (!initialized) { reset(altitudeM, timeNs); return }
        val withAccel = usesAccelerometer(timeNs)
        predict(timeNs, if (withAccel) lastAccel else 0.0, withAccel)
        // correction : H = [1, 0, 0]
        val r = baroNoiseM * baroNoiseM
        val s = p[0][0] + r
        val k0 = p[0][0] / s
        val k1 = p[1][0] / s
        val k2 = p[2][0] / s
        val innovation = altitudeM - altitude
        altitude += k0 * innovation
        climb += k1 * innovation
        bias += k2 * innovation
        val p0 = doubleArrayOf(p[0][0], p[0][1], p[0][2])
        for (j in 0..2) {
            p[0][j] -= k0 * p0[j]
            p[1][j] -= k1 * p0[j]
            p[2][j] -= k2 * p0[j]
        }
    }

    private fun predict(timeNs: Long, accel: Double, withAccel: Boolean) {
        val dt = ((timeNs - lastTimeNs) / 1e9).coerceIn(0.0, 0.5)
        lastTimeNs = timeNs
        if (dt <= 0.0) return
        val a = if (withAccel) accel - bias else 0.0
        altitude += climb * dt + 0.5 * a * dt * dt
        climb += a * dt
        // F = [[1, dt, -dt²/2], [0, 1, -dt], [0, 0, 1]] (le biais n'intervient que si l'accéléromètre est utilisé)
        val f02 = if (withAccel) -0.5 * dt * dt else 0.0
        val f12 = if (withAccel) -dt else 0.0
        val f = arrayOf(doubleArrayOf(1.0, dt, f02), doubleArrayOf(0.0, 1.0, f12), doubleArrayOf(0.0, 0.0, 1.0))
        val fp = Array(3) { i -> DoubleArray(3) { j -> (0..2).sumOf { k -> f[i][k] * p[k][j] } } }
        for (i in 0..2) for (j in 0..2) p[i][j] = (0..2).sumOf { k -> fp[i][k] * f[j][k] }
        // bruit de modèle : accélération blanche + dérive du biais
        val q = (if (withAccel) accelNoise else baroOnlyAccelNoise).let { it * it }
        p[0][0] += q * dt.pow(4) / 4; p[0][1] += q * dt.pow(3) / 2; p[1][0] += q * dt.pow(3) / 2; p[1][1] += q * dt * dt
        p[2][2] += biasDrift * biasDrift * dt
    }
}

/** Amortissement exponentiel à constante de temps [tauS] (affichage ~1 s, son ~0,3 s). */
class Damper(private val tauS: Double) {
    var value = 0.0; private set
    private var lastNs = 0L
    fun update(x: Double, timeNs: Long): Double {
        if (lastNs == 0L) { value = x; lastNs = timeNs; return value }
        val dt = (timeNs - lastNs) / 1e9
        lastNs = timeNs
        val alpha = 1 - exp(-dt / tauS)
        value += alpha * (x - value)
        return value
    }
}

/** Qualité mesurée du baromètre : fréquence et bruit (écart type de l'altitude sur une fenêtre glissante). */
class SensorStats(private val windowS: Double = 10.0) {
    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Double>()

    fun add(v: Double, timeNs: Long) {
        times.addLast(timeNs); values.addLast(v)
        while (times.isNotEmpty() && timeNs - times.first() > windowS * 1e9) { times.removeFirst(); values.removeFirst() }
    }

    val rateHz: Double get() = if (times.size < 2) 0.0 else (times.size - 1) / ((times.last() - times.first()) / 1e9)

    /** Écart type après retrait de la tendance linéaire (le bruit, pas la montée). */
    val noise: Double
        get() {
            val n = values.size
            if (n < 5) return Double.NaN
            val t0 = times.first()
            val xs = times.map { (it - t0) / 1e9 }
            val mx = xs.average(); val my = values.average()
            val sxy = xs.indices.sumOf { (xs[it] - mx) * (values.elementAt(it) - my) }
            val sxx = xs.sumOf { (it - mx) * (it - mx) }
            val slope = if (sxx > 0) sxy / sxx else 0.0
            val res = xs.indices.map { values.elementAt(it) - (my + slope * (xs[it] - mx)) }
            return sqrt(res.sumOf { it * it } / (n - 1))
        }
}
