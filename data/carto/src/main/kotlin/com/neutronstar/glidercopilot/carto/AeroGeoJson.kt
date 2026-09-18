package com.neutronstar.glidercopilot.carto

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.aero.AeroData
import com.neutronstar.glidercopilot.domain.aero.AltitudeLimit
import com.neutronstar.glidercopilot.domain.aero.Airport
import com.neutronstar.glidercopilot.domain.aero.Airspace
import com.neutronstar.glidercopilot.domain.aero.Navaid
import com.neutronstar.glidercopilot.domain.aero.ReportingPoint
import com.neutronstar.glidercopilot.precog.Json
import java.time.Instant

/** Lecture du fichier <région>-aero.geojson produit par tools/pack/build_region.py. */
object AeroGeoJson {
    fun parse(text: String): AeroData {
        val root = Json.parse(text)
        val airspaces = ArrayList<Airspace>()
        val airports = ArrayList<Airport>()
        val navaids = ArrayList<Navaid>()
        val points = ArrayList<ReportingPoint>()
        for (f in root["features"]?.arr.orEmpty()) {
            val p = f["properties"] ?: continue
            val g = f["geometry"] ?: continue
            val id = p["id"]?.str ?: continue
            val name = p["name"]?.str ?: ""
            when (p["layer"]?.str) {
                "airspace" -> {
                    if (g["type"]?.str != "Polygon") continue
                    val rings = g["coordinates"]?.arr.orEmpty().map { ring ->
                        ring.arr.mapNotNull { pt -> pt.arr.takeIf { it.size >= 2 }?.let { LatLon(it[1].num ?: 0.0, it[0].num ?: 0.0) } }
                    }.filter { it.size >= 3 }
                    if (rings.isEmpty()) continue
                    airspaces += Airspace(
                        id = id, name = name, type = p["type"]?.int ?: 0, icaoClass = p["cls"]?.int,
                        lower = AltitudeLimit.fromOpenAip(p["lo_v"]?.num, p["lo_u"]?.int, p["lo_r"]?.int),
                        upper = AltitudeLimit.fromOpenAip(p["up_v"]?.num, p["up_u"]?.int, p["up_r"]?.int),
                        rings = rings, byNotam = (p["notam"] as? Json.Bool)?.value == true,
                    )
                }
                "airport" -> point(g)?.let {
                    airports += Airport(id, name, p["icao"]?.str, p["type"]?.int ?: 2, it, p["elev"]?.int, p["freq"]?.str, p["rwy"]?.str)
                }
                "navaid" -> point(g)?.let { navaids += Navaid(id, p["ident"]?.str, name, p["type"]?.int ?: -1, it, p["freq"]?.str) }
                "reporting" -> point(g)?.let { points += ReportingPoint(id, name, it, (p["compulsory"] as? Json.Bool)?.value == true) }
            }
        }
        return AeroData(airspaces, airports, navaids, points, root["fetched"]?.str?.let { runCatching { Instant.parse(it) }.getOrNull() })
    }

    private fun point(g: Json): LatLon? {
        if (g["type"]?.str != "Point") return null
        val c = g["coordinates"]?.arr ?: return null
        val lon = c.getOrNull(0)?.num ?: return null
        val lat = c.getOrNull(1)?.num ?: return null
        return LatLon(lat, lon)
    }
}

/** Pistes connues des terrains du pack : un point par terrain, avec l'orientation à appliquer à l'icône. */
object RunwayGeoJson {
    fun build(airports: List<com.neutronstar.glidercopilot.domain.aero.Airport>): String {
        val features = airports.mapNotNull { a ->
            val rwy = com.neutronstar.glidercopilot.domain.aero.Runways.forIcao(a.icao) ?: return@mapNotNull null
            """{"type":"Feature","properties":{"hdg":${rwy.headingDeg},"len":${rwy.lengthM},"hard":${rwy.hard}},""" +
                """"geometry":{"type":"Point","coordinates":[${a.position.lon},${a.position.lat}]}}"""
        }
        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }
}
