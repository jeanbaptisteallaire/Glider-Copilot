package com.neutronstar.glidy.common

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

/** Même rendu que String.format : arrondi demi-haut, sans séparateur de milliers, « - » pour les négatifs. */
actual fun formatFixed(value: Double, decimals: Int, decimalComma: Boolean): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    return FormatterCache.get(decimals, decimalComma).stringFromNumber(NSNumber(value)) ?: value.toString()
}

/**
 * Un NSNumberFormatter par réglage et par fil (il n'est pas sûr entre fils) : en créer un à chaque appel coûtait
 * ~35 % du calcul de l'écran de vol (profil Time Profiler sur iPhone 15, nuit du 23/10/2026). Sortie identique.
 */
@kotlin.native.concurrent.ThreadLocal
private object FormatterCache {
    private val cache = HashMap<Int, NSNumberFormatter>()
    fun get(decimals: Int, decimalComma: Boolean): NSNumberFormatter = cache.getOrPut(decimals * 2 + if (decimalComma) 1 else 0) {
        NSNumberFormatter().apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            minimumFractionDigits = decimals.toULong()
            maximumFractionDigits = decimals.toULong()
            minimumIntegerDigits = 1u
            usesGroupingSeparator = false
            roundingMode = NSNumberFormatterRoundHalfUp
            decimalSeparator = if (decimalComma) "," else "."
            minusSign = "-"
        }
    }
}

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun normalizeNfd(s: String): String = (s as NSString).decomposedStringWithCanonicalMapping
