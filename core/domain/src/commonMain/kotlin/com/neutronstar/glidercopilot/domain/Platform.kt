package com.neutronstar.glidercopilot.domain

import com.neutronstar.glidy.common.EPOCH as CommonEpoch
import kotlinx.datetime.Instant

/*
 * iOS / KMP — les remplaçants communs de java.lang.Math, String.format et java.time.Duration vivent dans core:common
 * (partagé avec les modules Mes vols). Ces alias gardent les noms historiques du métier : aucun appelant ne change.
 */

/** Instant.EPOCH de java.time. */
val EPOCH: Instant = CommonEpoch

/** `java.time.Duration.between(from, to).getSeconds()` : secondes entières, arrondi vers −∞ comme java.time. */
fun secondsBetween(from: Instant, to: Instant): Long = com.neutronstar.glidy.common.secondsBetween(from, to)

/** `Math.toRadians` (même constante que le JDK, donc même arrondi). */
fun toRadians(deg: Double): Double = com.neutronstar.glidy.common.toRadians(deg)

/** `Math.toDegrees` (même constante que le JDK). */
fun toDegrees(rad: Double): Double = com.neutronstar.glidy.common.toDegrees(rad)

/** `Math.round(double)` : demi vers +∞, NaN → 0. */
fun roundHalfUp(x: Double): Long = com.neutronstar.glidy.common.roundHalfUp(x)

/** `String.format(locale, "%.<decimals>f", value)` ; [decimalComma] = Locale.FRANCE, sinon Locale.ROOT. */
fun formatFixed(value: Double, decimals: Int, decimalComma: Boolean = false): String =
    com.neutronstar.glidy.common.formatFixed(value, decimals, decimalComma)

/** `%0<width>d` pour un entier positif ou nul. */
fun Int.zeroPad(width: Int): String = with(com.neutronstar.glidy.common.ZeroPad) { this@zeroPad.pad(width) }

/** Décomposition canonique (NFD) de `java.text.Normalizer`. */
fun normalizeNfd(s: String): String = com.neutronstar.glidy.common.normalizeNfd(s)
