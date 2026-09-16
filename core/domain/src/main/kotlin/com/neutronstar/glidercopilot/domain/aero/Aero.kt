package com.neutronstar.glidercopilot.domain.aero

import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Instant
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * Données aéronautiques openAIP (énumérations de l'API Core openAIP, schéma v1).
 * Les limites verticales gardent leur référence d'origine : jamais de conversion silencieuse.
 */

enum class AltUnit { METER, FEET, FLIGHT_LEVEL }
enum class AltRef { GND, MSL, STD }

data class AltitudeLimit(val value: Double, val unit: AltUnit, val ref: AltRef) {
    val isSurface: Boolean get() = ref == AltRef.GND && value == 0.0

    /** Libellé de carte française : SFC, 2 500 ft AMSL, 1 000 ft ASFC, FL115. */
    val label: String
        get() = when {
            isSurface -> "SFC"
            unit == AltUnit.FLIGHT_LEVEL -> "FL" + value.roundToInt().toString().padStart(3, '0')
            else -> {
                val u = if (unit == AltUnit.METER) "m" else "ft"
                val r = when (ref) { AltRef.GND -> "ASFC"; AltRef.MSL -> "AMSL"; AltRef.STD -> "STD" }
                "${grouped(value.roundToInt())} $u $r"
            }
        }

    /**
     * Altitude approchée en mètres au-dessus du niveau de la mer.
     * [groundM] : altitude du sol sous le point (limites ASFC) ; les niveaux de vol sont convertis en atmosphère standard
     * (QNH inconnu) : c'est une valeur d'affichage, pas une valeur d'alerte réglementaire.
     */
    fun approxMetersAmsl(groundM: Double = 0.0): Double {
        val meters = when (unit) {
            AltUnit.METER -> value
            AltUnit.FEET -> value * FT
            AltUnit.FLIGHT_LEVEL -> value * 100 * FT
        }
        return if (ref == AltRef.GND) groundM + meters else meters
    }

    companion object {
        const val FT = 0.3048

        /** Codes openAIP : unité 0 m, 1 ft, 6 FL ; référence 0 GND, 1 MSL, 2 STD. */
        fun fromOpenAip(value: Double?, unit: Int?, ref: Int?): AltitudeLimit? {
            if (value == null || unit == null || ref == null) return null
            val u = when (unit) { 0 -> AltUnit.METER; 1 -> AltUnit.FEET; 6 -> AltUnit.FLIGHT_LEVEL; else -> return null }
            val r = when (ref) { 0 -> AltRef.GND; 1 -> AltRef.MSL; 2 -> AltRef.STD; else -> return null }
            return AltitudeLimit(value, u, r)
        }

        private fun grouped(n: Int) = "%,d".format(Locale.ROOT, n).replace(',', ' ')
    }
}

/** Famille d'espace, pour la couleur de carte et le tri. */
enum class AirspaceFamily { CONTROLLED, RESTRICTED, INFORMATION, OTHER }

data class Airspace(
    val id: String,
    val name: String,
    val type: Int,
    val icaoClass: Int?,
    val lower: AltitudeLimit?,
    val upper: AltitudeLimit?,
    /** Premier anneau = contour extérieur, suivants = trous. */
    val rings: List<List<LatLon>>,
    val byNotam: Boolean = false,
) {
    val family: AirspaceFamily get() = familyOf(type)

    /** Classe OACI A–G, null pour les espaces spéciaux (code 8). */
    val classLetter: String? get() = icaoClass?.let { listOf("A", "B", "C", "D", "E", "F", "G").getOrNull(it) }

    val typeLabel: String get() = TYPE_LABELS[type] ?: "Espace"

    val verticalLabel: String get() = "${lower?.label ?: "?"} → ${upper?.label ?: "?"}"

    private val bounds: DoubleArray by lazy {
        val pts = rings.firstOrNull().orEmpty()
        doubleArrayOf(pts.minOf { it.lon }, pts.minOf { it.lat }, pts.maxOf { it.lon }, pts.maxOf { it.lat })
    }

    fun contains(p: LatLon): Boolean {
        val outer = rings.firstOrNull() ?: return false
        if (outer.size < 3) return false
        val b = bounds
        if (p.lon < b[0] || p.lon > b[2] || p.lat < b[1] || p.lat > b[3]) return false
        return inRing(outer, p) && rings.drop(1).none { inRing(it, p) }
    }

    /** Distance horizontale au bord en km, 0 à l'intérieur. Projection locale équirectangulaire (précise à < 1 % sous 100 km). */
    fun distanceKm(p: LatLon): Double {
        if (contains(p)) return 0.0
        val kx = 111.32 * cos(Math.toRadians(p.lat))
        val ky = 110.574
        var best = Double.MAX_VALUE
        for (ring in rings) for (i in ring.indices) {
            val a = ring[i]
            val c = ring[(i + 1) % ring.size]
            val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * ky
            val cx = (c.lon - p.lon) * kx; val cy = (c.lat - p.lat) * ky
            val dx = cx - ax; val dy = cy - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < best) best = d
        }
        return best
    }

    companion object {
        fun familyOf(type: Int): AirspaceFamily = when (type) {
            4, 7, 13, 14, 23, 24, 26, 36 -> AirspaceFamily.CONTROLLED
            1, 2, 3, 8, 9, 12, 16, 17, 18, 19, 25, 29, 30, 31 -> AirspaceFamily.RESTRICTED
            5, 6, 21, 22, 28, 32, 33 -> AirspaceFamily.INFORMATION
            else -> AirspaceFamily.OTHER
        }

        val TYPE_LABELS = mapOf(
            0 to "Autre", 1 to "Zone réglementée", 2 to "Zone dangereuse", 3 to "Zone interdite", 4 to "CTR", 5 to "TMZ", 6 to "RMZ",
            7 to "TMA", 8 to "TRA", 9 to "TSA", 10 to "FIR", 11 to "UIR", 12 to "ADIZ", 13 to "ATZ", 14 to "MATZ", 15 to "Voie aérienne",
            16 to "Route militaire", 17 to "Zone d'alerte", 18 to "Zone d'avertissement", 19 to "Zone protégée", 20 to "HTZ",
            21 to "Secteur vol à voile", 22 to "Transpondeur", 23 to "TIZ", 24 to "TIA", 25 to "Zone d'entraînement militaire",
            26 to "CTA", 27 to "Secteur ACC", 28 to "Activité sportive", 29 to "Survol basse altitude réglementé", 30 to "Route militaire",
            31 to "Route TSA/TRA", 32 to "Secteur VFR", 33 to "SIV", 34 to "LTA", 35 to "UTA", 36 to "CTR militaire",
        )

        private fun inRing(ring: List<LatLon>, p: LatLon): Boolean {
            var inside = false
            var j = ring.size - 1
            for (i in ring.indices) {
                val a = ring[i]; val b = ring[j]
                if ((a.lat > p.lat) != (b.lat > p.lat) &&
                    p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
                ) inside = !inside
                j = i
            }
            return inside
        }
    }
}

data class Airport(
    val id: String,
    val name: String,
    val icao: String?,
    val type: Int,
    val position: LatLon,
    val elevationM: Int?,
    val frequency: String?,
    val runway: String?,
) {
    val isGliderSite: Boolean get() = type == 1
    val typeLabel: String get() = when (type) {
        0 -> "Aéroport"; 1 -> "Vol à voile"; 2 -> "Aérodrome"; 3 -> "Aéroport international"; 4 -> "Hélistation militaire"
        5 -> "Base militaire"; 6 -> "ULM"; 7 -> "Hélistation"; 8 -> "Fermé"; 9 -> "Aérodrome IFR"; 10 -> "Hydrobase"
        11 -> "Piste"; 12 -> "Piste agricole"; 13 -> "Altiport"; else -> "Terrain"
    }
}

data class Navaid(val id: String, val ident: String?, val name: String, val type: Int, val position: LatLon, val frequency: String?) {
    val typeLabel: String get() = listOf("DME", "TACAN", "NDB", "VOR", "VOR-DME", "VORTAC", "DVOR", "DVOR-DME", "DVORTAC").getOrElse(type) { "Balise" }
}

data class ReportingPoint(val id: String, val name: String, val position: LatLon, val compulsory: Boolean)

data class AeroData(
    val airspaces: List<Airspace>,
    val airports: List<Airport>,
    val navaids: List<Navaid>,
    val reportingPoints: List<ReportingPoint>,
    val fetched: Instant?,
) {
    /** Espaces à moins de [radiusKm] du point, du plus proche au plus lointain puis du plus bas au plus haut. */
    fun airspacesAround(p: LatLon, radiusKm: Double): List<Pair<Airspace, Double>> =
        airspaces.asSequence()
            .map { it to it.distanceKm(p) }
            .filter { it.second <= radiusKm }
            .sortedWith(compareBy({ it.second }, { it.first.lower?.approxMetersAmsl() ?: 0.0 }))
            .toList()

    companion object {
        val EMPTY = AeroData(emptyList(), emptyList(), emptyList(), emptyList(), null)
    }
}
