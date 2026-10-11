package com.neutronstar.glidy.common

import kotlinx.datetime.Instant
import kotlin.math.roundToLong

/*
 * iOS / KMP étape 1 — remplaçants communs de java.lang.Math, String.format et java.time.Duration.
 * Chacun rend exactement la même valeur que l'appel Java qu'il remplace (Android inchangé).
 */

/** Instant.EPOCH de java.time. */
val EPOCH: Instant = Instant.fromEpochSeconds(0)

/** `java.time.Duration.between(from, to).getSeconds()` : secondes entières, arrondi vers −∞ comme java.time. */
fun secondsBetween(from: Instant, to: Instant): Long {
    val s = to.epochSeconds - from.epochSeconds
    return if (to.nanosecondsOfSecond < from.nanosecondsOfSecond) s - 1 else s
}

/** `Math.toRadians` (même constante que le JDK, donc même arrondi). */
fun toRadians(deg: Double): Double = deg * 0.017453292519943295

/** `Math.toDegrees` (même constante que le JDK). */
fun toDegrees(rad: Double): Double = rad * 57.29577951308232

/** `Math.round(double)` : demi vers +∞, NaN → 0. */
fun roundHalfUp(x: Double): Long = if (x.isNaN()) 0L else x.roundToLong()

/** `String.format(locale, "%.<decimals>f", value)` ; [decimalComma] = Locale.FRANCE, sinon Locale.ROOT. */
expect fun formatFixed(value: Double, decimals: Int, decimalComma: Boolean = false): String

/** `%0<width>d` pour un entier positif ou nul. */
fun Int.zeroPad(width: Int): String = toString().padStart(width, '0')

/** Accès explicite à [zeroPad] depuis un autre paquet portant une extension du même nom. */
object ZeroPad { fun Int.pad(width: Int): String = zeroPad(width) }

/** Décomposition canonique (NFD) de `java.text.Normalizer`. */
expect fun normalizeNfd(s: String): String

/** `URLEncoder.encode(s, "UTF-8")` : lettres, chiffres et `.-*_` gardés, espace → `+`, le reste en %XX (UTF-8). */
fun formUrlEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = (b.toInt() and 0xFF)
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '*' || ch == '_' -> append(ch)
            ch == ' ' -> append('+')
            else -> { append('%'); append(HEX[c shr 4]); append(HEX[c and 0xF]) }
        }
    }
}

/** `"%02x".format(b)` / `"\\u%04x".format(c)` : hexadécimal minuscule complété à [width] chiffres. */
fun Int.toHexPadded(width: Int): String = toString(16).padStart(width, '0')

private const val HEX = "0123456789ABCDEF"
