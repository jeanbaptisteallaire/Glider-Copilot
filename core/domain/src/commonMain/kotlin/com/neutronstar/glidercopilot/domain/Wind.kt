package com.neutronstar.glidercopilot.domain

data class WindLayer(
    val altitudeMslM: Double,
    val label: String,
    val speedKmh: Double,
    val fromDeg: Double,
)

object WindLayers {
    val DEFAULT_ALTITUDES = listOf(1000.0, 1500.0, 2000.0, 2500.0, 3000.0)

    /**
     * Vent interpolé (sur les composantes u/v) aux altitudes QNH demandées, dans l'emprise du profil.
     * La première couche est le vent au sol (10 m) quand il est fourni.
     */
    fun compute(
        profile: List<ProfileLevel>,
        groundAltitudeM: Double,
        surfaceSpeedMs: Double?,
        surfaceFromDeg: Double?,
        altitudesMsl: List<Double> = DEFAULT_ALTITUDES,
    ): List<WindLayer> {
        val pts = profile.sortedBy { it.heightAglM }.mapNotNull { l ->
            if (l.windSpeedMs == null || l.windFromDeg == null) null
            else Triple(groundAltitudeM + l.heightAglM, Atmosphere.windComponents(l.windSpeedMs, l.windFromDeg), l)
        }
        val out = mutableListOf<WindLayer>()
        if (surfaceSpeedMs != null && surfaceFromDeg != null) {
            out += WindLayer(groundAltitudeM + 10, "Sol", Atmosphere.msToKmh(surfaceSpeedMs), surfaceFromDeg)
        }
        if (pts.size < 2) return out
        for (alt in altitudesMsl) {
            if (alt < pts.first().first || alt > pts.last().first) continue
            val i = pts.indexOfFirst { it.first >= alt }.coerceAtLeast(1)
            val (h0, c0) = pts[i - 1].let { it.first to it.second }
            val (h1, c1) = pts[i].let { it.first to it.second }
            val f = if (h1 == h0) 0.0 else (alt - h0) / (h1 - h0)
            val u = c0.first + (c1.first - c0.first) * f
            val v = c0.second + (c1.second - c0.second) * f
            val (s, d) = Atmosphere.windFromComponents(u, v)
            out += WindLayer(alt, "${alt.toInt()} m", Atmosphere.msToKmh(s), d)
        }
        return out
    }
}
