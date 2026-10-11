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
    val f = NSNumberFormatter()
    f.locale = NSLocale(localeIdentifier = "en_US_POSIX")
    f.minimumFractionDigits = decimals.toULong()
    f.maximumFractionDigits = decimals.toULong()
    f.minimumIntegerDigits = 1u
    f.usesGroupingSeparator = false
    f.roundingMode = NSNumberFormatterRoundHalfUp
    f.decimalSeparator = if (decimalComma) "," else "."
    f.minusSign = "-"
    return f.stringFromNumber(NSNumber(value)) ?: value.toString()
}

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun normalizeNfd(s: String): String = (s as NSString).decomposedStringWithCanonicalMapping
