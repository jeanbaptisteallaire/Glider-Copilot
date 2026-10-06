package com.neutronstar.glidercopilot.domain

/** Club FFVP. [position] est nulle tant que la plateforme n'a pas été géolocalisée. */
data class Club(
    val id: String,
    val name: String,
    val shortName: String?,
    val city: String,
    val postcode: String,
    val phone: String?,
    val airfieldIcao: String?,
    val airfieldName: String?,
    val position: LatLon?,
    val isFederation: Boolean = false,
) {
    /** Département (deux premiers caractères du code postal, 2A/2B non gérés en v1). */
    val departement: String get() = postcode.take(2)
    val displayName: String get() = shortName?.let { "$it · $name" } ?: name
}

object ClubLocator {
    /** Clubs géolocalisés triés par distance croissante à [from]. La fédération est exclue. */
    fun byDistance(clubs: List<Club>, from: LatLon): List<Pair<Club, Double>> =
        clubs.asSequence()
            .filter { !it.isFederation && it.position != null }
            .map { it to Geo.distanceKm(from, it.position!!) }
            .sortedBy { it.second }
            .toList()

    fun nearest(clubs: List<Club>, from: LatLon): Club? = byDistance(clubs, from).firstOrNull()?.first
}

/**
 * V20.4 — recherche dans le choix du club (Prévol) : par sigle de 4 lettres (code OACI du terrain, ex. LFNL, ou sigle du
 * club, ex. ACAM) et par mot du nom, de la ville ou du terrain. Insensible à la casse et aux accents ; chaque mot tapé
 * doit trouver une correspondance (début de mot ou partie du nom). Sigle exact en tête de liste.
 */
object ClubSearch {
    fun search(clubs: List<Club>, query: String): List<Club> {
        val tokens = words(query)
        if (tokens.isEmpty()) return clubs
        val q = tokens.joinToString(" ")
        return clubs.asSequence()
            .mapNotNull { club ->
                val codes = listOfNotNull(club.airfieldIcao, club.shortName).map(::norm)
                val text = listOfNotNull(club.name, club.city, club.airfieldName).joinToString(" ").let(::norm)
                val textWords = words(text)
                val ok = tokens.all { t ->
                    codes.any { it.startsWith(t) } || textWords.any { it.startsWith(t) } || (t.length >= 3 && text.contains(t))
                }
                if (!ok) return@mapNotNull null
                val rank = when {
                    codes.any { it == q } -> 0
                    codes.any { it.startsWith(q) } -> 1
                    norm(club.name).startsWith(q) -> 2
                    else -> 3
                }
                rank to club
            }
            .sortedBy { it.first } // tri stable : l'ordre d'origine est gardé à rang égal
            .map { it.second }
            .toList()
    }

    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun words(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }
}
