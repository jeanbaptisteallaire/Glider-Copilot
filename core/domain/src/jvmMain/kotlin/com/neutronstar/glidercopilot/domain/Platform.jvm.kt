package com.neutronstar.glidercopilot.domain

import java.util.Locale

actual fun formatFixed(value: Double, decimals: Int, decimalComma: Boolean): String =
    String.format(if (decimalComma) Locale.FRANCE else Locale.ROOT, "%.${decimals}f", value)

actual fun normalizeNfd(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
