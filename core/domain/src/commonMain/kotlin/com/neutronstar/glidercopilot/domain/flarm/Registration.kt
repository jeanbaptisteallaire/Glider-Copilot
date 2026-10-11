package com.neutronstar.glidercopilot.domain.flarm

/** Immatriculation saisie par le pilote : mêmes règles que la maquette v8 (majuscules, A-Z 0-9 et tiret). */
object Registration {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 10

    fun normalize(raw: String): String =
        raw.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' }.take(MAX_LENGTH)

    fun isValid(normalized: String): Boolean = normalized.length >= MIN_LENGTH

    /** Clé de comparaison tolérante : « FCJAB », « F-CJAB » et « f-cjab » désignent le même planeur. */
    fun key(raw: String): String = normalize(raw).replace("-", "")
}
