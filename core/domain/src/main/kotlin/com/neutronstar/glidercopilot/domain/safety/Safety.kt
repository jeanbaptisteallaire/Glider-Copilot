package com.neutronstar.glidercopilot.domain.safety

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.Terrain
import com.neutronstar.glidercopilot.domain.flight.GpsFix
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Vent (d'où il vient, en degrés vrais) et sa source. */
data class Wind(val fromDeg: Double, val speedKmh: Double, val source: WindSource, val time: Instant) {
    /** Composantes du vecteur « vers où il souffle » en km/h (est, nord). */
    val east: Double get() = -speedKmh * sin(Math.toRadians(fromDeg))
    val north: Double get() = -speedKmh * cos(Math.toRadians(fromDeg))

    fun ageMinutes(now: Instant): Long = Duration.between(time, now).toMinutes()

    companion object {
        fun fromVector(east: Double, north: Double, source: WindSource, time: Instant): Wind {
            val speed = hypot(east, north)
            val from = (Math.toDegrees(atan2(-east, -north)) + 360) % 360
            return Wind(from, speed, source, time)
        }
    }
}

enum class WindSource { CIRCLING, FORECAST }

/**
 * Vent par la dérive en spirale : sur un tour complet à vitesse air et taux de virage constants, la moyenne des vecteurs
 * vitesse sol échantillonnés régulièrement est le vent. Chaque tour mesuré est lissé avec les précédents.
 */
class WindEstimator(private val smoothing: Double = 0.35, private val maxAge: Duration = Duration.ofMinutes(25)) {
    private data class V(val time: Instant, val east: Double, val north: Double, val track: Double)
    private val samples = ArrayDeque<V>()
    private var east = 0.0
    private var north = 0.0
    private var last: Instant? = null
    var turns = 0; private set
    private var lastTurnEnd: Instant? = null

    fun onFix(fix: GpsFix) {
        val gs = fix.groundSpeedKmh ?: return
        val trk = fix.trackDeg ?: return
        if (gs < 25) { samples.clear(); return }
        val rad = Math.toRadians(trk)
        samples.addLast(V(fix.time, gs * sin(rad), gs * cos(rad), trk))
        while (samples.isNotEmpty() && Duration.between(samples.first().time, fix.time).seconds > 70) samples.removeFirst()
        // remonte le temps jusqu'à 360° de virage cumulé, dans un seul sens
        var turn = 0.0
        var start = -1
        for (i in samples.lastIndex downTo 1) {
            var d = samples[i].track - samples[i - 1].track
            if (d > 180) d -= 360
            if (d < -180) d += 360
            if (turn != 0.0 && Math.signum(d) != Math.signum(turn) && abs(d) > 3) return
            turn += d
            if (abs(turn) >= 360) { start = i; break }
        }
        if (start < 0) return
        val window = samples.toList().subList(start, samples.size)
        val dur = Duration.between(window.first().time, window.last().time).seconds
        if (dur !in 14..65) return
        if (lastTurnEnd != null && Duration.between(lastTurnEnd, fix.time).seconds < dur * 0.8) return   // tours disjoints
        val me = window.map { it.east }.average()
        val mn = window.map { it.north }.average()
        if (last == null || Duration.between(last, fix.time) > maxAge) { east = me; north = mn } else {
            east += smoothing * (me - east); north += smoothing * (mn - north)
        }
        last = fix.time; lastTurnEnd = fix.time; turns++
    }

    fun wind(now: Instant): Wind? {
        val t = last ?: return null
        if (Duration.between(t, now) > maxAge) return null
        return Wind.fromVector(east, north, WindSource.CIRCLING, t)
    }
}

/** Terrain d'arrivée candidat (openAIP ou terrain du club). */
data class FieldOption(val id: String, val name: String, val position: LatLon, val elevationM: Double, val isClub: Boolean = false, val icao: String? = null) {
    /** Étiquette courte : code OACI, sinon début du nom. */
    val code: String get() = icao ?: name.split(' ', '-').filter { it.length > 2 }.take(2).joinToString(" ").take(14).ifEmpty { name.take(14) }

    companion object {
        /** « SAINT MARTIN DE LONDRE » → « Saint-Martin de Londre » lisible à l'écran et à la voix. */
        fun prettyName(raw: String): String {
            if (raw.any { it.isLowerCase() }) return raw
            val small = setOf("DE", "DU", "DES", "LA", "LE", "LES", "SUR", "EN", "D", "L", "ET", "AUX")
            return raw.lowercase().split(' ').mapIndexed { i, w ->
                if (i > 0 && w.uppercase() in small) w else w.split('-').joinToString("-") { p -> p.replaceFirstChar { it.titlecase() } }
            }.joinToString(" ")
        }
    }
}

data class SafetyConfig(
    val finesse: Double,
    /** Vitesse air de plané de sécurité (km/h). */
    val glideSpeedKmh: Double = 90.0,
    /** Hauteur d'arrivée au-dessus du terrain (m). */
    val arrivalMarginM: Double = 300.0,
    /** Hauteur minimale au-dessus du relief le long de la route (m). */
    val reliefClearanceM: Double = 100.0,
)

data class ProfileSample(val sKm: Double, val terrainM: Double?)

/** Premier obstacle du relief qui impose plus d'altitude que l'arrivée au terrain. */
data class ReliefConflict(val sKm: Double, val terrainM: Double, val requiredAltM: Double)

data class GlideResult(
    val field: FieldOption,
    val distanceKm: Double,
    val bearingDeg: Double,
    val groundSpeedKmh: Double,
    /** Finesse sol : finesse air × vitesse sol / vitesse air (vent compris). */
    val effectiveFinesse: Double,
    /** Altitude nécessaire pour l'arrivée seule (sans relief). */
    val fieldRequiredM: Double,
    /** Altitude nécessaire en tenant compte du relief. */
    val requiredM: Double,
    val marginM: Double,
    val arrivalAglM: Double,
    val relief: ReliefConflict?,
    val profile: List<ProfileSample>,
    val terrainKnown: Boolean,
) {
    val reachable: Boolean get() = marginM >= 0
}

object GlideComputer {
    /** Vitesse sol sur la route [bearingDeg] à vitesse air [tas] avec le vent ; null si le vent l'interdit. */
    fun groundSpeed(bearingDeg: Double, tas: Double, wind: Wind?): Double? {
        if (wind == null || wind.speedKmh < 1) return tas
        val b = Math.toRadians(bearingDeg)
        val along = wind.east * sin(b) + wind.north * cos(b)           // vent arrière > 0
        val cross = wind.east * cos(b) - wind.north * sin(b)
        if (abs(cross) >= tas) return null
        val gs = sqrt(tas * tas - cross * cross) + along
        return if (gs < 10) null else gs
    }

    fun toField(from: LatLon, altitudeM: Double, field: FieldOption, cfg: SafetyConfig, wind: Wind?, terrain: Terrain, stepKm: Double = 0.1): GlideResult {
        val d = Geo.distanceKm(from, field.position)
        val brg = if (d > 0.01) Geo.bearingDeg(from, field.position) else 0.0
        val gs = groundSpeed(brg, cfg.glideSpeedKmh, wind)
        val eff = if (gs == null) 0.5 else cfg.finesse * gs / cfg.glideSpeedKmh
        val fieldReq = field.elevationM + cfg.arrivalMarginM + d * 1000 / eff
        var required = fieldReq
        var conflict: ReliefConflict? = null
        val n = max(2, min(600, (d / stepKm).toInt()))
        val profile = ArrayList<ProfileSample>(n + 1)
        var known = 0
        for (i in 0..n) {
            val s = d * i / n
            val t = terrain.elevationM(if (i == 0) from else if (i == n) field.position else Geo.destination(from, brg, s))
            profile += ProfileSample(s, t)
            if (t == null) continue
            known++
            if (s < 0.2 || s > d - 0.8) continue                    // décollage de la mesure et approche finale
            val need = t + cfg.reliefClearanceM + s * 1000 / eff
            if (need > required) {
                required = need
                if (altitudeM < need && conflict == null) conflict = ReliefConflict(s, t, need)
            }
        }
        if (altitudeM >= required) conflict = null
        return GlideResult(
            field = field, distanceKm = d, bearingDeg = brg, groundSpeedKmh = gs ?: 0.0, effectiveFinesse = eff,
            fieldRequiredM = fieldReq, requiredM = required, marginM = altitudeM - required,
            arrivalAglM = altitudeM - d * 1000 / eff - field.elevationM, relief = conflict, profile = profile,
            terrainKnown = known > n / 2,
        )
    }

    /** Position et altitude dans [seconds] : route et vitesse sol actuelles (dérive du vent en spirale), montée moyenne. */
    fun project(fix: GpsFix, altitudeM: Double, climbMs: Double, circling: Boolean, wind: Wind?, seconds: Long = 120): Pair<LatLon, Double> {
        val alt = altitudeM + climbMs * seconds
        val (east, north) = if (circling || fix.trackDeg == null || fix.groundSpeedKmh == null) {
            (wind?.east ?: 0.0) to (wind?.north ?: 0.0)
        } else {
            val r = Math.toRadians(fix.trackDeg)
            fix.groundSpeedKmh * sin(r) to fix.groundSpeedKmh * cos(r)
        }
        val km = hypot(east, north) * seconds / 3600.0
        val brg = (Math.toDegrees(atan2(east, north)) + 360) % 360
        return (if (km < 0.001) fix.position else Geo.destination(fix.position, brg, km)) to alt
    }
}

/**
 * Terrain de référence : celui du club tant qu'il est rejoignable ; sinon le meilleur terrain rejoignable (avec hystérésis
 * pour éviter les allers-retours) ; si aucun ne l'est, celui qui demande le moins d'altitude. Choix manuel prioritaire.
 */
class FieldSelector(private val switchGainM: Double = 100.0, private val backToClubM: Double = 80.0) {
    var selected: FieldOption? = null; private set
    var manual: FieldOption? = null

    data class Choice(val field: FieldOption, val result: GlideResult, val changed: Boolean, val auto: Boolean)

    fun choose(results: List<GlideResult>): Choice? {
        if (results.isEmpty()) return null
        manual?.let { m -> results.firstOrNull { it.field.id == m.id }?.let { r -> return done(r, auto = false) } }
        val club = results.firstOrNull { it.field.isClub }
        val best = results.maxBy { it.marginM }
        val current = selected?.let { s -> results.firstOrNull { it.field.id == s.id } }
        val pick = when {
            club != null && club.marginM >= 0 && (current == null || current.field.isClub || club.marginM >= backToClubM) -> club
            current != null && current.marginM >= 0 && best.marginM < current.marginM + switchGainM -> current
            current != null && current.marginM < 0 && best.marginM < current.marginM + switchGainM / 2 -> current
            else -> best
        }
        return done(pick, auto = true)
    }

    private fun done(r: GlideResult, auto: Boolean): Choice {
        val changed = selected != null && selected!!.id != r.field.id
        selected = r.field
        return Choice(r.field, r, changed, auto)
    }
}

enum class AlertLevel { INFO, WARNING, URGENT }

data class SafetyAlert(val kind: Kind, val text: String, val level: AlertLevel) {
    enum class Kind { MARGIN_LOW, MARGIN_BELOW, MARGIN_RECOVERED, PROJECTED_BELOW, RELIEF, FIELD_CHANGE }
}

/**
 * Alertes de sécurité (voix + vibration) : seuils avec hystérésis, délai minimal par type, alerte « sous la sécurité »
 * toujours répétée toutes les [urgentRepeat].
 */
class SafetyAlerts(
    private val lowM: Double = 150.0,
    private val hysteresisM: Double = 50.0,
    private val cooldown: Duration = Duration.ofSeconds(60),
    private val urgentRepeat: Duration = Duration.ofSeconds(30),
) {
    private var level = 2          // 2 confortable, 1 faible, 0 sous la sécurité
    private var reliefActive = false
    private var projectedActive = false
    private val last = HashMap<SafetyAlert.Kind, Instant>()

    private fun ok(k: SafetyAlert.Kind, now: Instant, every: Duration = cooldown): Boolean {
        val l = last[k]
        if (l != null && Duration.between(l, now) < every) return false
        last[k] = now
        return true
    }

    fun update(choice: FieldSelectorChoice, projectedMarginM: Double?, now: Instant): List<SafetyAlert> {
        val out = ArrayList<SafetyAlert>()
        val r = choice.result
        // dans le circuit d'atterrissage (terrain à moins de 2,5 km, arrivée directe possible) : pas d'alerte de marge
        val inCircuit = r.distanceKm < 2.5 && r.arrivalAglM > 50 && r.relief == null
        val m = if (inCircuit) max(r.marginM, lowM + hysteresisM) else r.marginM
        val newLevel = when {
            m < 0 -> 0
            m < lowM -> if (level == 0 && m < hysteresisM) 0 else 1
            m >= lowM + hysteresisM -> 2
            else -> if (level == 0) 1 else level
        }
        val cap = "Cap ${r.field.name.take(24)}, ${Math.round(r.bearingDeg / 10.0) * 10} degrés, ${fmtKm(r.distanceKm)}"
        if (choice.changed) {
            if (ok(SafetyAlert.Kind.FIELD_CHANGE, now, Duration.ofSeconds(20))) out += SafetyAlert(SafetyAlert.Kind.FIELD_CHANGE, "Terrain de repli : ${r.field.name.take(24)}. $cap", AlertLevel.WARNING)
        }
        when {
            newLevel == 0 && (level != 0 || ok(SafetyAlert.Kind.MARGIN_BELOW, now, urgentRepeat)) -> {
                last[SafetyAlert.Kind.MARGIN_BELOW] = now
                out += SafetyAlert(SafetyAlert.Kind.MARGIN_BELOW, "Sous la sécurité. $cap", AlertLevel.URGENT)
            }
            newLevel == 1 && level == 2 && ok(SafetyAlert.Kind.MARGIN_LOW, now) ->
                out += SafetyAlert(SafetyAlert.Kind.MARGIN_LOW, "Marge faible, ${(Math.round(m / 10.0) * 10)} mètres", AlertLevel.WARNING)
            newLevel == 2 && level < 2 && ok(SafetyAlert.Kind.MARGIN_RECOVERED, now) ->
                out += SafetyAlert(SafetyAlert.Kind.MARGIN_RECOVERED, "Marge rétablie", AlertLevel.INFO)
        }
        level = newLevel
        val relief = r.relief != null && m < 0 && r.fieldRequiredM <= r.requiredM - 20
        if (relief && !reliefActive && ok(SafetyAlert.Kind.RELIEF, now)) {
            out += SafetyAlert(SafetyAlert.Kind.RELIEF, "Relief sur la route du terrain à ${fmtKm(r.relief!!.sKm)}", AlertLevel.URGENT)
        }
        reliefActive = relief
        val projected = projectedMarginM != null && projectedMarginM < 0 && m >= 0
        if (projected && !projectedActive && ok(SafetyAlert.Kind.PROJECTED_BELOW, now)) {
            out += SafetyAlert(SafetyAlert.Kind.PROJECTED_BELOW, "Marge nulle dans deux minutes", AlertLevel.WARNING)
        }
        projectedActive = projected
        return out
    }

    private fun fmtKm(km: Double) = if (km < 10) "${String.format(java.util.Locale.FRANCE, "%.1f", km)} kilomètres".replace(",0 ", " ") else "${Math.round(km)} kilomètres"
}

typealias FieldSelectorChoice = FieldSelector.Choice

/** État de sécurité publié vers l'écran. */
data class SafetyState(
    val choice: FieldSelector.Choice,
    val projectedMarginM: Double?,
    val projectedPosition: LatLon?,
    val wind: Wind?,
    val alternatives: List<GlideResult>,
)

/**
 * Moteur de sécurité : à chaque position (1 Hz), vent, arrivée sur chaque terrain candidat (relief du pack), choix du terrain,
 * projection à 2 min et alertes. Ne dépend d'aucun réseau : relief et terrains viennent du pack hors ligne.
 */
class SafetyEngine(
    private val terrain: () -> Terrain,
    private val fields: () -> List<FieldOption>,
    private val alert: (SafetyAlert) -> Unit,
    private val forecastWind: () -> Wind? = { null },
    private val candidatesRadiusKm: Double = 40.0,
) {
    val wind = WindEstimator()
    val selector = FieldSelector()
    private val alerts = SafetyAlerts()
    var state: SafetyState? = null; private set

    fun onFix(fix: GpsFix) = wind.onFix(fix)

    /** [armed] : alertes vocales seulement en vol. */
    fun update(fix: GpsFix, altitudeM: Double, climbMs: Double, circling: Boolean, cfg: SafetyConfig, now: Instant, armed: Boolean): SafetyState? {
        val t = terrain()
        val w = wind.wind(now) ?: forecastWind()
        val all = fields()
        val near = all.filter { it.isClub || Geo.distanceKm(fix.position, it.position) <= candidatesRadiusKm }
            .sortedBy { Geo.distanceKm(fix.position, it.position) }.take(8)
        if (near.isEmpty()) { state = null; return null }
        // relief complet pour le terrain courant et le club ; estimation sans relief pour trier les autres
        val results = near.map { f ->
            val detailed = f.isClub || f.id == selector.selected?.id || f.id == selector.manual?.id
            GlideComputer.toField(fix.position, altitudeM, f, cfg, w, if (detailed) t else Terrain.NONE)
        }.let { rough ->
            val top = rough.sortedByDescending { it.marginM }.take(3).map { it.field.id }.toSet()
            rough.map { r -> if (r.terrainKnown || r.field.id !in top) r else GlideComputer.toField(fix.position, altitudeM, r.field, cfg, w, t) }
        }
        val choice = selector.choose(results) ?: return null
        val (pp, pa) = GlideComputer.project(fix, altitudeM, climbMs, circling, w)
        val projected = GlideComputer.toField(pp, pa, choice.field, cfg, w, t, stepKm = 0.25).marginM
        val s = SafetyState(choice, projected, pp, w, results.sortedByDescending { it.marginM })
        state = s
        if (armed) alerts.update(choice, projected, now).forEach(alert)
        return s
    }
}
