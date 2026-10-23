package com.neutronstar.glidercopilot.domain.flight

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Langage visuel de LIGHT PFD porté dans Pilotage (JB, 22/10/2026) : échelle vario commune à la barre et à la trace,
 * ruban d'altitude « ouvert » à tambour au service de la marge de sécurité. Calculs et textes ici, une seule fois ;
 * Compose et SwiftUI ne font que dessiner.
 */
object VarioPalette {
    const val MIN_MS = -3.0
    const val MAX_MS = 4.0

    /**
     * Couleurs « flash », très saturées, pour la trace et la barre (toujours posées avec un liseré sombre) :
     * rouge (−3) → orange → jaune (0) → vert (+1, +2) → cyan (+3) → bleu (+4). ARGB.
     */
    val flashStops: List<Pair<Double, Long>> = listOf(
        -3.0 to 0xFFFF2A2A,
        -1.5 to 0xFFFF8A00,
        0.0 to 0xFFFFE500,
        1.0 to 0xFF46E83C,
        2.0 to 0xFF14D27A,
        3.0 to 0xFF00D9FF,
        4.0 to 0xFF3D8BFF,
    )

    /**
     * Même sens, valeurs assombries pour un TEXTE sur fond clair (thème blanc de Pilotage) : contraste ≥ 4,5:1 sur blanc.
     * Le jaune pur est illisible sur blanc : à zéro on passe à un ocre foncé.
     */
    val inkStops: List<Pair<Double, Long>> = listOf(
        -3.0 to 0xFFC4161C,
        -1.5 to 0xFFAD4E00,
        0.0 to 0xFF7A6200,
        1.0 to 0xFF1D7A2A,
        2.0 to 0xFF0B7346,
        3.0 to 0xFF006F86,
        4.0 to 0xFF1F55C9,
    )

    /** Liseré sombre sous la trace et autour de la barre : la couleur ressort sur un fond de carte sombre comme clair. */
    const val CASING: Long = 0xFF0B0E13

    fun flash(ms: Double): Long = interpolate(flashStops, ms)
    fun ink(ms: Double): Long = interpolate(inkStops, ms)

    /** Position de la valeur sur la barre verticale : 0 = bas (−3), 1 = haut (+4) ; bornée. */
    fun fraction(ms: Double): Double = (min(max(ms, MIN_MS), MAX_MS) - MIN_MS) / (MAX_MS - MIN_MS)

    /** Au-delà de l'échelle : la flèche reste en butée et le chiffre, lui, n'est jamais borné. */
    fun overRange(ms: Double): Boolean = ms > MAX_MS || ms < MIN_MS

    /** Gros chiffre au-dessus de la barre : « +1,8 », « −2,4 », « 0,0 » ; virgule française, vrai signe moins, jamais borné. */
    fun valueText(ms: Double): String {
        if (ms.isNaN()) return "–,–"
        val t = (ms * 10).roundToInt()
        val a = abs(t)
        val body = "${a / 10},${a % 10}"
        return when { t > 0 -> "+$body"; t < 0 -> "−$body"; else -> body }
    }

    /** Graduations chiffrées de la barre, de haut en bas. */
    val ticks: List<Int> = (MAX_MS.toInt() downTo MIN_MS.toInt()).toList()

    /** Libellé d'une graduation : « +4 », « 0 », « −3 » (vrai signe moins). */
    fun tickLabel(v: Int): String = when {
        v > 0 -> "+$v"
        v < 0 -> "−${-v}"
        else -> "0"
    }

    internal fun interpolate(stops: List<Pair<Double, Long>>, ms: Double): Long {
        if (ms.isNaN() || ms <= stops.first().first) return stops.first().second
        for (k in 1 until stops.size) {
            val (v1, c1) = stops[k]
            val (v0, c0) = stops[k - 1]
            if (ms <= v1) return lerp(c0, c1, (ms - v0) / (v1 - v0))
        }
        return stops.last().second
    }

    private fun lerp(a: Long, b: Long, t: Double): Long {
        fun ch(c: Long, s: Int) = ((c shr s) and 0xFF).toInt()
        fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).roundToInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Contraste WCAG entre deux couleurs ARGB opaques (sert aux tests de lisibilité sur fond clair). */
    fun contrast(a: Long, b: Long): Double {
        fun lum(c: Long): Double {
            fun lin(s: Int): Double {
                val v = ((c shr s) and 0xFF) / 255.0
                return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * lin(16) + 0.7152 * lin(8) + 0.0722 * lin(0)
        }
        val (hi, lo) = lum(a).let { la -> lum(b).let { lb -> max(la, lb) to min(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }
}

/** Tambour d'altitude : gros chiffres fixes (centaines et plus) + deux derniers chiffres qui défilent par pas de 10 m. */
data class AltitudeDrum(
    /** Centaines et plus, ex. « 12 » pour 1 247 m ; « » sous 100 m. Le signe moins est porté ici. */
    val high: String,
    /** Rangées du rouleau, de haut en bas, centrées sur la rangée lue (index [CENTER]) : ex. « 60 », « 50 », « 40 », « 30 », « 20 ». */
    val rows: List<String>,
    /** Décalage du rouleau (0 … 1) : part de la rangée suivante déjà montée dans la fenêtre. */
    val roll: Double,
) {
    companion object { const val CENTER = 2 }
}

/** Repère de minimum sur le ruban : position verticale relative (−1 = bas du ruban, +1 = haut), ou en butée. */
data class TapeMarker(val position: Double, val clamped: Boolean)

/** Image du ruban d'altitude façon Thales, au service de la marge de sécurité (le « SÉCU » de Wind Glider). */
data class AltitudeTapeState(
    /** Faux si l'altitude manque ou est périmée : tirets, jamais de valeur figée (« l'information fausse doit avoir l'air fausse »). */
    val valid: Boolean,
    val drum: AltitudeDrum?,
    /** Minimum (altitude de sécurité) : hachuré cyan sur le filet ; null si inconnu. */
    val minimum: TapeMarker?,
    val level: MarginLevel,
    /** Sous le minimum : bloc rouge plein sur la rangée de lecture. */
    val belowMinimum: Boolean,
    /** Écart à afficher sous le tambour : « −180 m » (sous le minimum) ou « +320 m ». Vide si inconnu. */
    val gapText: String,
    /** Graduations visibles (m), filet sans échelle chiffrée (choix de JB) : seulement des traits. */
    val ticksM: List<Int>,
)

object AltitudeTape {
    /** Demi-hauteur du ruban, en mètres d'altitude (± 250 m visibles autour de la lecture). */
    const val HALF_RANGE_M = 250.0
    const val TICK_M = 50
    private const val STEP_M = 10

    fun drum(altM: Double): AltitudeDrum {
        val neg = altM < 0
        val a = abs(altM)
        val v = a / STEP_M
        val base = floor(v).toLong()
        val roll = v - base
        val tensAt = { k: Long -> (((base + k) * STEP_M) % 100).toInt().let { if (it < 0) it + 100 else it } }
        val rows = (2 downTo -2).map { k -> tensAt(k.toLong()).let { if (it < 10) "0$it" else "$it" } }
        val hundreds = (base * STEP_M) / 100
        val high = (if (neg) "−" else "") + if (hundreds > 0) hundreds.toString() else ""
        return AltitudeDrum(high, rows, if (neg) 1 - roll else roll)
    }

    fun state(altM: Double?, minimumM: Double?, marginKnown: Boolean): AltitudeTapeState {
        if (altM == null || !altM.isFinite()) {   // NaN ou infini : tirets, jamais un tambour qui tourne sans fin
            return AltitudeTapeState(false, null, null, MarginLevel.UNKNOWN, false, "", emptyList())
        }
        val margin = if (marginKnown && minimumM != null && !minimumM.isNaN()) altM - minimumM else null
        val level = PilotDeclutter.marginLevel(margin != null, margin ?: Double.NaN)
        val marker = margin?.let {
            val pos = -it / HALF_RANGE_M
            TapeMarker(pos.coerceIn(-1.0, 1.0), abs(pos) > 1.0)
        }
        val first = ((altM - HALF_RANGE_M) / TICK_M).let { floor(it).toInt() + 1 } * TICK_M
        val ticks = generateSequence(first) { it + TICK_M }.takeWhile { it <= altM + HALF_RANGE_M }.toList()
        return AltitudeTapeState(
            valid = true,
            drum = drum(altM),
            minimum = marker,
            level = level,
            belowMinimum = margin != null && margin < 0,
            gapText = margin?.let { gap(it) } ?: "",
            ticksM = ticks,
        )
    }

    /** Position verticale relative d'une altitude sur le ruban (−1 bas … +1 haut), pour les graduations. */
    fun position(altM: Double, atM: Double): Double = (atM - altM) / HALF_RANGE_M

    /**
     * « −180 m », « +320 m » : vrai signe moins, par pas de 10 m (la précision d'un altimètre de planeur).
     * Arrondi PRUDENT (vers le bas) : on n'affiche jamais plus de marge qu'on n'en a, et un écart négatif
     * ne s'affiche jamais « 0 m » à côté du bloc rouge (−3 m → « −10 m »).
     */
    fun gap(marginM: Double): String {
        val r = floor(marginM / 10).toInt() * 10
        return when {
            r > 0 -> "+$r m"
            r < 0 -> "−${-r} m"
            else -> "0 m"
        }
    }
}
