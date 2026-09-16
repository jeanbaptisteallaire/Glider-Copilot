package com.neutronstar.glidercopilot.precog

import java.time.Instant

data class FieldSemantics(val stepType: String?, val accumFrom: String?, val period: String?)

/** Enveloppe commune des routes /forecast et /profile de precog. */
class Envelope(val root: Json) {
    val referenceTime: Instant? = root["reference_time"]?.str?.let(::parseInstant)
    val attribution: String? = root["attribution"]?.str
    val units: Map<String, String> =
        root["units"]?.obj?.map?.mapNotNull { (k, v) -> v.str?.let { k to it } }?.toMap() ?: emptyMap()
    val semantics: Map<String, FieldSemantics> = root["semantics"]?.obj?.map?.mapValues { (_, v) ->
        FieldSemantics(v["step_type"]?.str, v["accum_from"]?.str, v["period"]?.str)
    } ?: emptyMap()
    val steps: List<Json> = root["steps"]?.arr ?: emptyList()
    val pointDistanceKm: Double? = root["point"]?.get("distance_km")?.num

    fun validTimes(): List<Instant> = steps.mapNotNull { it["valid_time"]?.str?.let(::parseInstant) }

    /** Valeur d'un champ convertie en SI d'après `units`. Null si absente ou unité inconnue. */
    fun si(node: Json, field: String): Double? {
        val raw = node[field]?.num ?: return null
        val unit = units[field] ?: return null
        return Units.toSi(raw, unit)
    }

    companion object {
        fun parseInstant(s: String): Instant? = runCatching { Instant.parse(s.replace("+00:00", "Z")) }.getOrNull()
    }
}

/** Conversions vers le SI. Une unité inconnue rend null : jamais d'hypothèse silencieuse. */
object Units {
    fun toSi(v: Double, unit: String): Double? = when (unit.trim()) {
        "K" -> v
        "degC", "°C", "C" -> v + 273.15
        "Pa" -> v
        "hPa" -> v * 100
        "m s-1", "m/s" -> v
        "km h-1", "km/h" -> v / 3.6
        "kt", "knot" -> v * 0.514444
        "%" -> v
        "1" -> v * 100
        "J m-2", "W m-2", "m2 s-2", "J kg-1", "m", "degree true", "degree", "deg", "kg m-2", "mm" -> v
        else -> null
    }
}
