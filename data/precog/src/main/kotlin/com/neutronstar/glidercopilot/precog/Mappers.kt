package com.neutronstar.glidercopilot.precog

import com.neutronstar.glidercopilot.domain.ProfileLevel
import com.neutronstar.glidercopilot.domain.SurfaceConditions
import com.neutronstar.glidercopilot.domain.VigilanceStatus
import java.time.Duration
import java.time.Instant

object ForecastMapper {
    /** Conditions de surface par échéance. Les champs cumulés sont différenciés entre échéances consécutives. */
    fun surface(env: Envelope): List<SurfaceConditions> {
        val out = ArrayList<SurfaceConditions>()
        env.steps.forEachIndexed { i, step ->
            val t = step["valid_time"]?.str?.let(Envelope::parseInstant) ?: return@forEachIndexed
            val t2m = env.si(step, "temperature_2m") ?: return@forEachIndexed
            out += SurfaceConditions(
                validTime = t,
                temperature2mK = t2m,
                dewpoint2mK = env.si(step, "dewpoint_2m"),
                pressureSurfacePa = env.si(step, "pressure_surface"),
                shortwaveNetWm2 = flux(env, i, "shortwave_radiation_net") ?: flux(env, i, "solar_radiation")?.let { it * 0.8 },
                totalCloudPct = env.si(step, "total_cloud_cover"),
                lowCloudPct = env.si(step, "low_cloud_cover"),
                capeJkg = env.si(step, "cape"),
                wind10mMs = env.si(step, "wind_speed_10m"),
                wind10mFromDeg = env.si(step, "wind_direction_10m"),
            )
        }
        return out
    }

    /** Flux moyen (W/m²) sur [t-1, t] pour un champ cumulé ou moyenné depuis le réseau ; valeur directe sinon. */
    fun flux(env: Envelope, i: Int, field: String): Double? {
        val sem = env.semantics[field]
        val step = env.steps[i]
        val type = sem?.stepType
        if (type != "accum" && type != "avg") return env.si(step, field)
        if (i == 0) return null
        val prev = env.steps[i - 1]
        val t1 = step["valid_time"]?.str?.let(Envelope::parseInstant) ?: return null
        val t0 = prev["valid_time"]?.str?.let(Envelope::parseInstant) ?: return null
        val dt = Duration.between(t0, t1).seconds.toDouble()
        if (dt <= 0) return null
        val a = env.si(prev, field) ?: return null
        val b = env.si(step, field) ?: return null
        if (type == "accum") return (b - a) / dt
        val ref = env.referenceTime ?: return null
        val s1 = Duration.between(ref, t1).seconds.toDouble()
        val s0 = Duration.between(ref, t0).seconds.toDouble()
        return (b * s1 - a * s0) / dt
    }
}

object ProfileMapper {
    fun levels(env: Envelope, validTime: Instant? = null): List<ProfileLevel> {
        val step = env.steps.firstOrNull {
            validTime == null || it["valid_time"]?.str?.let(Envelope::parseInstant) == validTime
        } ?: return emptyList()
        return step["levels"]?.arr?.mapNotNull { l ->
            val h = l["level_m"]?.num ?: return@mapNotNull null
            val t = env.si(l, "temperature") ?: return@mapNotNull null
            val p = env.si(l, "pressure") ?: return@mapNotNull null
            ProfileLevel(
                heightAglM = h,
                temperatureK = t,
                dewpointK = env.si(l, "dewpoint"),
                pressurePa = p,
                windSpeedMs = env.si(l, "wind_speed"),
                windFromDeg = env.si(l, "wind_direction"),
                geopotentialM2s2 = env.si(l, "geopotential"),
                cloudFraction = l["cloud_fraction"]?.num,
            )
        }?.sortedBy { it.heightAglM } ?: emptyList()
    }
}

object VigilanceMapper {
    private val defaultColors = mapOf(1 to "Vert", 2 to "Jaune", 3 to "Orange", 4 to "Rouge")

    /** Niveau du département sur la première période publiée. Structure Météo-France lue défensivement. */
    fun forDepartement(root: Json, departement: String): VigilanceStatus? {
        val tables = root["code_tables"]
        val colorLabels = table(tables?.get("vigilance_color")).ifEmpty { defaultColors }
        val phenomenonLabels = table(tables?.get("vigilance_phenomenon"))
        val periods = root["carte"]?.get("product")?.get("periods")?.arr ?: return null
        val period = periods.firstOrNull() ?: return null
        val domain = period["timelaps"]?.get("domain_ids")?.arr
            ?.firstOrNull { it["domain_id"]?.str == departement } ?: return null
        val color = domain["max_color_id"]?.int ?: return null
        val phenomena = domain["phenomenon_items"]?.arr.orEmpty()
            .filter { (it["phenomenon_max_color_id"]?.int ?: 1) > 1 }
            .mapNotNull { p ->
                val id = p["phenomenon_id"]?.int ?: return@mapNotNull null
                val c = p["phenomenon_max_color_id"]?.int ?: 1
                "${phenomenonLabels[id] ?: "Phénomène $id"} (${colorLabels[c] ?: c})"
            }
        return VigilanceStatus(departement, color, colorLabels[color] ?: "Niveau $color", phenomena)
    }

    private fun table(t: Json?): Map<Int, String> =
        t?.get("entries")?.arr?.mapNotNull { e ->
            e["code"]?.int?.let { c -> e["label"]?.str?.let { c to it } }
        }?.toMap() ?: emptyMap()
}
