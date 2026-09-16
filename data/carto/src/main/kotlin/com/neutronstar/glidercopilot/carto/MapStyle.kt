package com.neutronstar.glidercopilot.carto

import java.io.File

/** Couleurs de carte (hex #rrggbb), fournies par la charte de l'app. */
data class MapPalette(
    val background: String = "#000000",
    val earth: String = "#0c0c0c",
    val wood: String = "#0e1710",
    val water: String = "#08151d",
    val waterLine: String = "#143140",
    val roadMajor: String = "#4a4a4a",
    val roadMinor: String = "#262626",
    val label: String = "#c4c4c4",
    val halo: String = "#000000",
    val contour: String = "#ffffff",
    val hillShadow: String = "#000000",
    val hillHighlight: String = "#4a4a4a",
    val controlled: String = "#69c8ff",
    val restricted: String = "#ff9f43",
    val information: String = "#b7f7a5",
    val other: String = "#adadad",
    val airport: String = "#b7f7a5",
    val navaid: String = "#c4c4c4",
    val route: String = "#b7f7a5",
    val glider: String = "#f5f5f5",
)

/** JSON brut à insérer tel quel (GeoJSON déjà sérialisé). */
class RawJson(val text: String)

object MapStyle {
    const val SRC_TRACE = "glidy-trace"
    const val SRC_GLIDER = "glidy-glider"
    const val SRC_ROUTE = "glidy-route"
    const val IMG_GLIDER = "glidy-glider-icon"

    /**
     * Style MapLibre complet du pack installé dans [packDir]. Tout est local : PMTiles en file://, glyphes dans les assets,
     * GeoJSON aéro intégré. [glyphs] : modèle d'URL des glyphes (asset://glyphs/{fontstack}/{range}.pbf sur Android).
     */
    fun build(pack: PackInfo, packDir: File, aeroGeoJson: String?, p: MapPalette = MapPalette(), glyphs: String = "asset://glyphs/{fontstack}/{range}.pbf"): String {
        fun pm(role: String) = pack.file(role)?.let { "pmtiles://file://" + File(packDir, it.name).absolutePath }
        val sources = linkedMapOf<String, Any?>()
        pm("fond")?.let { sources["fond"] = mapOf("type" to "vector", "url" to it, "attribution" to "© OpenStreetMap") }
        pm("relief")?.let { sources["relief"] = mapOf("type" to "raster-dem", "url" to it, "encoding" to "terrarium", "tileSize" to 512) }
        pm("courbes")?.let { sources["courbes"] = mapOf("type" to "vector", "url" to it) }
        sources["aero"] = mapOf("type" to "geojson", "data" to (aeroGeoJson?.let(::RawJson) ?: EMPTY_FC))
        sources[SRC_ROUTE] = mapOf("type" to "geojson", "data" to EMPTY_FC)
        sources[SRC_TRACE] = mapOf("type" to "geojson", "data" to EMPTY_FC)
        sources[SRC_GLIDER] = mapOf("type" to "geojson", "data" to EMPTY_FC)

        val layers = ArrayList<Any>()
        layers += mapOf("id" to "background", "type" to "background", "paint" to mapOf("background-color" to p.background))
        if ("fond" in sources) {
            layers += fill("earth", "fond", "earth", p.earth)
            layers += fill("wood", "fond", "landcover", p.wood, filter = listOf("in", listOf("get", "kind"), listOf("literal", listOf("forest", "wood"))), opacity = 0.9)
            layers += fill("landuse-wood", "fond", "landuse", p.wood, filter = listOf("in", listOf("get", "kind"), listOf("literal", listOf("forest", "wood", "nature_reserve", "national_park"))), opacity = 0.7)
        }
        if ("relief" in sources) {
            layers += mapOf(
                "id" to "hillshade", "type" to "hillshade", "source" to "relief",
                "paint" to mapOf(
                    "hillshade-shadow-color" to p.hillShadow, "hillshade-highlight-color" to p.hillHighlight,
                    "hillshade-accent-color" to p.hillShadow, "hillshade-exaggeration" to 0.55,
                ),
            )
        }
        if ("fond" in sources) {
            layers += fill("water", "fond", "water", p.water, filter = listOf("==", listOf("geometry-type"), "Polygon"))
            layers += line("rivers", "fond", "water", p.waterLine, width = zoomInterp(9, 0.6, 14, 2.0),
                filter = listOf("all", listOf("==", listOf("geometry-type"), "LineString"), listOf("in", listOf("get", "kind"), listOf("literal", listOf("river", "canal")))))
        }
        if ("courbes" in sources) {
            val index = listOf("==", listOf("%", listOf("get", "ele"), 500), 0)
            layers += line("contours", "courbes", "contours", p.contour, width = 0.5, opacity = 0.07, filter = listOf("!", index), minzoom = 10.0)
            layers += line("contours-index", "courbes", "contours", p.contour, width = 0.9, opacity = 0.16, filter = index, minzoom = 9.0)
            layers += mapOf(
                "id" to "contours-label", "type" to "symbol", "source" to "courbes", "source-layer" to "contours", "minzoom" to 11.5, "filter" to index,
                "layout" to mapOf("symbol-placement" to "line", "text-field" to listOf("concat", listOf("to-string", listOf("get", "ele")), " m"),
                    "text-font" to listOf("Noto Sans Regular"), "text-size" to 9),
                "paint" to mapOf("text-color" to p.label, "text-opacity" to 0.45, "text-halo-color" to p.halo, "text-halo-width" to 1),
            )
        }
        if ("fond" in sources) {
            layers += line("roads-minor", "fond", "roads", p.roadMinor, width = zoomInterp(11, 0.4, 14, 1.4), minzoom = 11.0,
                filter = listOf("in", listOf("get", "kind"), listOf("literal", listOf("minor_road", "medium_road"))))
            layers += line("roads-major", "fond", "roads", p.roadMajor, width = zoomInterp(7, 0.5, 14, 2.4), minzoom = 7.0,
                filter = listOf("in", listOf("get", "kind"), listOf("literal", listOf("highway", "major_road"))))
        }
        // espaces aériens : remplissage très léger, contour coloré par famille, nom le long du bord
        val familyColor = listOf(
            "match", listOf("get", "type"),
            listOf(4, 7, 13, 14, 23, 24, 26, 36), p.controlled,
            listOf(1, 2, 3, 8, 9, 12, 16, 17, 18, 19, 25, 29, 30, 31), p.restricted,
            listOf(5, 6, 21, 22, 28, 32, 33), p.information,
            p.other,
        )
        val isAirspace = listOf("==", listOf("get", "layer"), "airspace")
        layers += mapOf("id" to "airspace-fill", "type" to "fill", "source" to "aero", "filter" to listOf("all", isAirspace, listOf("!=", listOf("get", "type"), 33)),
            "paint" to mapOf("fill-color" to familyColor, "fill-opacity" to 0.05))
        layers += mapOf("id" to "airspace-line", "type" to "line", "source" to "aero", "filter" to isAirspace,
            "paint" to mapOf("line-color" to familyColor, "line-width" to zoomInterp(7, 0.8, 13, 1.8),
                "line-opacity" to listOf("match", listOf("get", "type"), 33, 0.35, 0.85),
                "line-dasharray" to listOf("literal", listOf(3, 1.5))))
        layers += mapOf("id" to "airspace-label", "type" to "symbol", "source" to "aero", "minzoom" to 9.5, "filter" to listOf("all", isAirspace, listOf("!=", listOf("get", "type"), 33)),
            "layout" to mapOf("symbol-placement" to "line", "symbol-spacing" to 420, "text-field" to listOf("get", "name"),
                "text-font" to listOf("Noto Sans Medium"), "text-size" to 10, "text-offset" to listOf("literal", listOf(0, 0.9))),
            "paint" to mapOf("text-color" to familyColor, "text-halo-color" to p.halo, "text-halo-width" to 1.2))
        // localités
        if ("fond" in sources) {
            layers += mapOf(
                "id" to "places", "type" to "symbol", "source" to "fond", "source-layer" to "places",
                "filter" to listOf("all", listOf("==", listOf("get", "kind"), "locality"), listOf("<=", listOf("get", "min_zoom"), 12)),
                "layout" to mapOf("text-field" to listOf("coalesce", listOf("get", "name:fr"), listOf("get", "name")), "text-font" to listOf("Noto Sans Regular"),
                    "text-size" to zoomInterp(8, 10, 13, 13), "text-padding" to 6, "symbol-sort-key" to listOf("get", "min_zoom")),
                "paint" to mapOf("text-color" to p.label, "text-opacity" to 0.8, "text-halo-color" to p.halo, "text-halo-width" to 1.2),
            )
        }
        // balises et terrains
        val isNavaid = listOf("==", listOf("get", "layer"), "navaid")
        layers += mapOf("id" to "navaid", "type" to "circle", "source" to "aero", "minzoom" to 8.0, "filter" to isNavaid,
            "paint" to mapOf("circle-radius" to 3, "circle-color" to p.background, "circle-stroke-color" to p.navaid, "circle-stroke-width" to 1.5))
        layers += mapOf("id" to "navaid-label", "type" to "symbol", "source" to "aero", "minzoom" to 9.0, "filter" to isNavaid,
            "layout" to mapOf("text-field" to listOf("coalesce", listOf("get", "ident"), listOf("get", "name")), "text-font" to listOf("Noto Sans Regular"),
                "text-size" to 9, "text-anchor" to "top", "text-offset" to listOf("literal", listOf(0, 0.6))),
            "paint" to mapOf("text-color" to p.navaid, "text-halo-color" to p.halo, "text-halo-width" to 1))
        val isAirport = listOf("all", listOf("==", listOf("get", "layer"), "airport"), listOf("!", listOf("in", listOf("get", "type"), listOf("literal", listOf(4, 7, 8)))))
        layers += mapOf("id" to "airport", "type" to "circle", "source" to "aero", "filter" to isAirport,
            "paint" to mapOf(
                "circle-radius" to zoomInterp(7, 2.5, 12, 5.5),
                "circle-color" to listOf("match", listOf("get", "type"), 1, p.airport, 6, p.background, p.airport),
                "circle-stroke-color" to p.airport, "circle-stroke-width" to 1.5,
            ))
        layers += mapOf("id" to "airport-label", "type" to "symbol", "source" to "aero", "minzoom" to 8.5, "filter" to isAirport,
            "layout" to mapOf("text-field" to listOf("coalesce", listOf("get", "icao"), listOf("get", "name")), "text-font" to listOf("Noto Sans Medium"),
                "text-size" to zoomInterp(8.5, 9, 13, 12), "text-anchor" to "left", "text-offset" to listOf("literal", listOf(0.8, 0))),
            "paint" to mapOf("text-color" to p.airport, "text-halo-color" to p.halo, "text-halo-width" to 1.2))
        // couches dynamiques : route vers le terrain, trace colorée par le vario, planeur
        layers += mapOf("id" to "route", "type" to "line", "source" to SRC_ROUTE,
            "paint" to mapOf("line-color" to p.route, "line-width" to 2, "line-dasharray" to listOf("literal", listOf(3, 2))))
        layers += mapOf("id" to "trace", "type" to "line", "source" to SRC_TRACE, "layout" to mapOf("line-cap" to "round"),
            "paint" to mapOf("line-color" to listOf("get", "color"), "line-width" to 3.5))
        layers += mapOf("id" to "glider", "type" to "symbol", "source" to SRC_GLIDER,
            "layout" to mapOf("icon-image" to IMG_GLIDER, "icon-rotate" to listOf("get", "heading"), "icon-rotation-alignment" to "map",
                "icon-allow-overlap" to true, "icon-ignore-placement" to true))

        val style = linkedMapOf<String, Any?>(
            "version" to 8,
            "name" to "GLIDY ${pack.id}",
            "glyphs" to glyphs,
            "sources" to sources,
            "layers" to layers,
        )
        return write(style)
    }

    private val EMPTY_FC = mapOf("type" to "FeatureCollection", "features" to emptyList<Any>())

    private fun zoomInterp(z1: Number, v1: Number, z2: Number, v2: Number) = listOf("interpolate", listOf("linear"), listOf("zoom"), z1, v1, z2, v2)

    private fun fill(id: String, src: String, layer: String, color: String, filter: Any? = null, opacity: Double = 1.0) = linkedMapOf<String, Any?>(
        "id" to id, "type" to "fill", "source" to src, "source-layer" to layer,
        "paint" to mapOf("fill-color" to color, "fill-opacity" to opacity),
    ).apply { if (filter != null) put("filter", filter) }

    private fun line(id: String, src: String, layer: String, color: String, width: Any, opacity: Double = 1.0, filter: Any? = null, minzoom: Double? = null) = linkedMapOf<String, Any?>(
        "id" to id, "type" to "line", "source" to src, "source-layer" to layer,
        "paint" to mapOf("line-color" to color, "line-width" to width, "line-opacity" to opacity),
    ).apply { if (filter != null) put("filter", filter); if (minzoom != null) put("minzoom", minzoom) }

    /** Sérialiseur JSON minimal (Map, List, String, Number, Boolean, null, RawJson). */
    fun write(v: Any?): String = StringBuilder().also { append(it, v) }.toString()

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is RawJson -> sb.append(v.text)
            is String -> {
                sb.append('"')
                for (ch in v) when {
                    ch == '"' -> sb.append("\\\"")
                    ch == '\\' -> sb.append("\\\\")
                    ch == '\n' -> sb.append("\\n")
                    ch.code < 0x20 -> sb.append("\\u%04x".format(ch.code))
                    else -> sb.append(ch)
                }
                sb.append('"')
            }
            is Boolean -> sb.append(v)
            is Double -> if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) sb.append(v.toLong()) else sb.append(v)
            is Float -> append(sb, v.toDouble())
            is Number -> sb.append(v)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    append(sb, k.toString()); sb.append(':'); append(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; append(sb, x) }
                sb.append(']')
            }
            else -> append(sb, v.toString())
        }
    }
}
