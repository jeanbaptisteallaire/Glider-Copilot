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
