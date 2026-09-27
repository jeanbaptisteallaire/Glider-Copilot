package com.neutronstar.glidy.social

import java.text.Normalizer
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * GLIDY — fil des vols (S17). Modèle et port du futur réseau social, plus une implémentation de
 * démonstration (pilotes fictifs, traces synthétiques) en attendant Supabase (S18 : tables profiles,
 * follows, vue feed — voir docs/supabase). Kotlin pur, déterministe, testable hors ligne.
 */

/** Pilote visible sur le fil (profil public). */
data class FeedPilot(
    val id: String,
    val displayName: String,
    val username: String,
    val club: String,
    val experience: ExperienceLevel?,
    val bio: String = "",
    /** Pilote fictif de démonstration (jamais un vrai compte). */
    val isDemo: Boolean = false,
) {
    val initials: String get() = PilotProfile(displayName = displayName, username = username).initials
}

/** Vol publié. [route] : trace normalisée dans le carré unité (x → est, y → sud), prête pour une vignette. */
data class FeedFlight(
    val id: String,
    val pilot: FeedPilot,
    val startedAt: Instant,
    val durationSeconds: Long,
    val distanceMeters: Long,
    val maxAltitudeMeters: Int,
    val site: String,
    val route: List<Pair<Float, Float>>,
)

/** Page du fil, triée du plus récent au plus ancien. [nextCursor] null = fin du fil. */
data class FeedPage(val items: List<FeedFlight>, val nextCursor: String?)

/** Pilotes suivis, gardés sur le téléphone (S18 : table `follows`). */
interface FollowStore {
    suspend fun load(): Set<String>
    suspend fun save(ids: Set<String>)
}

class MemoryFollowStore(initial: Set<String> = emptySet()) : FollowStore {
    private var ids = initial
    override suspend fun load() = ids
    override suspend fun save(ids: Set<String>) { this.ids = ids }
}

/** Port du réseau social : recherche, suivi, fil paginé (curseur). */
interface SocialRepository {
    suspend fun search(query: String, limit: Int = 20): List<FeedPilot>
    suspend fun suggestions(limit: Int = 6): List<FeedPilot>
    suspend fun following(): Set<String>
    suspend fun setFollowing(pilotId: String, follow: Boolean): Set<String>
    /** Vols des pilotes suivis. [cursor] null = première page. */
    suspend fun feed(cursor: String?, pageSize: Int = 18): FeedPage
    suspend fun flightsOf(pilotId: String): List<FeedFlight>
}

/** Recherche sans accents ni casse, sur le début des mots du nom et du pseudo. */
fun FeedPilot.matches(query: String): Boolean {
    val q = fold(query.trim().removePrefix("@"))
    if (q.isEmpty()) return false
    val words = (fold(displayName).split(Regex("[\\s\\-]+")) + fold(username).split('_', '.') + fold(username))
    return words.any { it.startsWith(q) } || fold(displayName).startsWith(q)
}

internal fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * Réseau de démonstration : 14 pilotes fictifs et leurs vols synthétiques (thermiques + transitions),
 * générés de façon déterministe. Les suivis passent par [store] (persistés par l'app).
 */
class DemoSocialRepository(
    private val store: FollowStore = MemoryFollowStore(),
    private val now: Instant = Instant.parse("2026-09-27T18:00:00Z"),
) : SocialRepository {

    val pilots: List<FeedPilot> = DEMO_PILOTS
    private val flights: List<FeedFlight> by lazy { generateFlights() }

    override suspend fun search(query: String, limit: Int): List<FeedPilot> =
        pilots.filter { it.matches(query) }.take(limit)

    override suspend fun suggestions(limit: Int): List<FeedPilot> {
        val followed = store.load()
        return pilots.filterNot { it.id in followed }.take(limit)
    }

    override suspend fun following(): Set<String> = store.load()

    override suspend fun setFollowing(pilotId: String, follow: Boolean): Set<String> {
        require(pilots.any { it.id == pilotId }) { "pilote inconnu" }
        val next = store.load().let { if (follow) it + pilotId else it - pilotId }
        store.save(next)
        return next
    }

    override suspend fun feed(cursor: String?, pageSize: Int): FeedPage {
        val followed = store.load()
        val all = flights.filter { it.pilot.id in followed }
        val start = cursor?.let { c -> all.indexOfFirst { it.id == c }.let { if (it < 0) all.size else it + 1 } } ?: 0
        val page = all.drop(start).take(pageSize)
        val next = if (start + page.size < all.size) page.lastOrNull()?.id else null
        return FeedPage(page, next)
    }

    override suspend fun flightsOf(pilotId: String): List<FeedFlight> = flights.filter { it.pilot.id == pilotId }

    private fun generateFlights(): List<FeedFlight> {
        val out = ArrayList<FeedFlight>()
        pilots.forEachIndexed { pi, pilot ->
            val rng = Lcg(1_000L + pi * 7919L)
            val count = 6 + rng.nextInt(10)
            var day = rng.nextInt(3)
            repeat(count) { fi ->
                day += 1 + rng.nextInt(9)
                val hours = 1.0 + rng.nextDouble() * (if (pilot.experience == ExperienceLevel.STUDENT) 1.5 else 5.0)
                val start = now.minusSeconds(day * 86_400L).minusSeconds(rng.nextInt(5 * 3600).toLong())
                val (route, km) = syntheticTrack(rng, legs = 2 + rng.nextInt(4))
                val durationS = (hours * 3600).roundToLong()
                val distanceM = (km * (0.6 + hours * 0.35) * 1000).roundToLong().coerceAtLeast(8_000)
                out += FeedFlight(
                    id = "demo-${pilot.id}-$fi",
                    pilot = pilot,
                    startedAt = start,
                    durationSeconds = durationS,
                    distanceMeters = distanceM,
                    maxAltitudeMeters = 1400 + rng.nextInt(1800),
                    site = pilot.club.substringBefore(" (").ifBlank { "Terrain exemple" },
                    route = route,
                )
            }
        }
        return out.sortedByDescending { it.startedAt }
    }

    /** Trace plausible : transitions entre thermiques, spirales serrées, retour vers le départ. */
    private fun syntheticTrack(rng: Lcg, legs: Int): Pair<List<Pair<Float, Float>>, Double> {
        val pts = ArrayList<Pair<Double, Double>>()
        var x = 0.0
        var y = 0.0
        pts += x to y
        var heading = rng.nextDouble() * 2 * PI
        var km = 0.0
        val waypoints = ArrayList<Pair<Double, Double>>()
        repeat(legs) {
            heading += (0.9 + rng.nextDouble() * 1.6) * (if (rng.nextInt(2) == 0) 1 else -1)
            val len = 8 + rng.nextDouble() * 25
            waypoints += (x + len * cos(heading)) to (y + len * sin(heading))
            x = waypoints.last().first; y = waypoints.last().second
        }
        waypoints += 0.0 to 0.0 // retour
        x = 0.0; y = 0.0
        for ((wx, wy) in waypoints) {
            val dx = wx - x
            val dy = wy - y
            val d = sqrt(dx * dx + dy * dy)
            val steps = (d / 1.2).toInt().coerceAtLeast(2)
            for (s in 1..steps) {
                val px = x + dx * s / steps + (rng.nextDouble() - 0.5) * 0.6
                val py = y + dy * s / steps + (rng.nextDouble() - 0.5) * 0.6
                pts += px to py
                // thermique de temps en temps : petite spirale
                if (rng.nextInt(6) == 0) {
                    val r = 0.25 + rng.nextDouble() * 0.2
                    val dir = if (rng.nextInt(2) == 0) 1 else -1
                    val a0 = rng.nextDouble() * 2 * PI
                    val loops = 2 + rng.nextInt(3)
                    for (k in 1..(loops * 10)) {
                        val a = a0 + dir * k * 2 * PI / 10
                        pts += (px + r * cos(a) + k * 0.01) to (py + r * sin(a) + k * 0.01)
                    }
                }
            }
            km += d
            x = wx; y = wy
        }
        return normalize(pts) to km
    }

    private fun normalize(pts: List<Pair<Double, Double>>): List<Pair<Float, Float>> {
        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }
        val span = maxOf(maxX - minX, maxY - minY, 1e-6)
        val ox = (span - (maxX - minX)) / 2
        val oy = (span - (maxY - minY)) / 2
        return pts.map { (px, py) ->
            val nx = 0.08 + ((px - minX + ox) / span) * 0.84
            val ny = 0.08 + (1 - (py - minY + oy) / span) * 0.84
            nx.toFloat() to ny.toFloat()
        }
    }

    private class Lcg(seed: Long) {
        private var s = seed xor 0x5DEECE66DL
        fun next(): Int { s = (s * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1); return (s ushr 16).toInt() }
        fun nextInt(n: Int): Int = Math.floorMod(next(), n)
        fun nextDouble(): Double = (next().toLong() and 0xFFFFFFFFL) / 4294967296.0
    }

    companion object {
        /** Pilotes fictifs : noms, pseudos et clubs inventés, tous marqués « exemple ». */
        val DEMO_PILOTS: List<FeedPilot> = listOf(
            FeedPilot("p01", "Camille Rousset", "camille.rousset", "Club exemple · Cévennes", ExperienceLevel.INSTRUCTOR, "Formatrice, amoureuse des lignes de crête.", true),
            FeedPilot("p02", "Hugo Marchal", "hugo_vv", "Club exemple · Alpes du Sud", ExperienceLevel.PASSENGER, "Cross en montagne, un Discus et beaucoup de café.", true),
            FeedPilot("p03", "Léa Fontaine", "lea.fontaine", "Club exemple · Montagne Noire", ExperienceLevel.LICENSED, "Premier 300 km cet été !", true),
            FeedPilot("p04", "Thomas Guérin", "tguerin", "Club exemple · Vosges", ExperienceLevel.STUDENT, "Élève, lâché en juin.", true),
            FeedPilot("p05", "Émilie Carrel", "emilie_carrel", "Club exemple · Jura", ExperienceLevel.LICENSED, "", true),
            FeedPilot("p06", "Nicolas Béranger", "nico.beranger", "Club exemple · Provence", ExperienceLevel.INSTRUCTOR, "Instructeur, remorqueur le week-end.", true),
            FeedPilot("p07", "Sarah Lemoine", "sarah_lm", "Club exemple · Pyrénées", ExperienceLevel.PASSENGER, "Ondes et biplace.", true),
            FeedPilot("p08", "Julien Arnaud", "julien.arnaud", "Club exemple · Causses", ExperienceLevel.LICENSED, "", true),
            FeedPilot("p09", "Manon Perrin", "manon_perrin", "Club exemple · Massif central", ExperienceLevel.STUDENT, "Objectif brevet 2027.", true),
            FeedPilot("p10", "Antoine Lefèvre", "antoine_lf", "Club exemple · Champagne", ExperienceLevel.LICENSED, "Plaine, cumulus et vent de nord.", true),
            FeedPilot("p11", "Chloé Vasseur", "chloe.vasseur", "Club exemple · Alpes du Nord", ExperienceLevel.PASSENGER, "", true),
            FeedPilot("p12", "Maxime Delorme", "max_delorme", "Club exemple · Bourgogne", ExperienceLevel.INSTRUCTOR, "Chef pilote, fan de compétition.", true),
            FeedPilot("p13", "Inès Barbier", "ines.barbier", "Club exemple · Corse", ExperienceLevel.LICENSED, "", true),
            FeedPilot("p14", "Paul Girard", "paulgirard", "Club exemple · Bretagne", ExperienceLevel.STUDENT, "Premiers vols en campagne.", true),
        )
    }
}
