package com.neutronstar.glidercopilot.domain.checklist

/*
 * Contenu de la page Check-lists, recopié tel quel de la maquette v8 (session 2).
 * Références : supports Formation-Sécurité FFVP « CRIS », « TVBCR », « VERDO » et enseignements du BEA.
 */

/** Ligne cochable. [bold] est le préfixe en gras (« C — Commandes »), [small] la précision en petit. */
data class CheckItem(
    val id: String,
    val text: String,
    val bold: String? = null,
    val small: String? = null,
    val dynamic: DynamicText? = null,
)

/** Lignes dont le texte suit le briefing rupture de câble saisi par le pilote. */
enum class DynamicText { AHEAD, FIELD, TURN }

sealed interface Block {
    data class Subtitle(val text: String) : Block
    data class Items(val items: List<CheckItem>) : Block
    /** Champs du briefing rupture de câble et plan généré. */
    data object CableBrief : Block
}

data class Checklist(
    val id: String,
    val code: String,
    val title: String,
    val subtitle: String,
    val openByDefault: Boolean,
    val blocks: List<Block>,
) {
    val items: List<CheckItem> get() = blocks.filterIsInstance<Block.Items>().flatMap { it.items }
}

object Checklists {
    const val PAGE_TITLE = "Check-lists"
    const val PAGE_TAG = "CRIS · TVBCR\nVERDO"
    const val INTRO = "Aide-mémoire à dire à haute voix. Le manuel de vol, les consignes du club et le briefing de piste restent prioritaires."
    const val RESET_NOTE = "Les cases sont remises à zéro à la demande."
    const val RESET_BUTTON = "Tout effacer"
    const val RESET_TOAST = "Check-lists remises à zéro"
    const val SOURCE = "Références de conception : supports Formation-Sécurité FFVP « CRIS », « TVBCR » et « VERDO », et enseignements du BEA. " +
        "Les seuils de rupture de câble affichés ici sont modifiables et doivent être validés pour le site et le planeur du jour."

    private fun items(prefix: String, vararg list: CheckItem) = Block.Items(list.mapIndexed { i, it -> it.copy(id = "$prefix.$i") })
    private fun t(text: String, small: String? = null) = CheckItem("", text, small = small)
    private fun b(bold: String, text: String) = CheckItem("", text, bold = bold)
    private fun d(kind: DynamicText, placeholder: String) = CheckItem("", placeholder, dynamic = kind)

    val all: List<Checklist> = listOf(
        Checklist(
            id = "cris", code = "CRIS", title = "Avant vol et décollage",
            subtitle = "Préparation · actions vitales · rupture de câble", openByDefault = true,
            blocks = listOf(
                Block.Subtitle("Préparation du jour"),
                items(
                    "cris.prep",
                    t("Visite prévol ou tour complet du planeur effectué", "Cellule, pneus, antenne, BO et éclisse, batteries, coffre et documents."),
                    t("Masse et centrage vérifiés", "Gueuses, bagages, siège et coussins adaptés au pilote."),
                    t("Menaces du jour annoncées", "Météo, vent traversier, trafic, relief, piste et particularités locales."),
                    t("Personne ni obstacle devant les ailes et sur l’axe"),
                ),
                Block.CableBrief,
                Block.Subtitle("Actions vitales · CRIS"),
                items(
                    "cris.vital",
                    b("C — Commandes", "libres et dans le bon sens"),
                    t("Compensateur réglé · volets positionnés et verrouillés"),
                    b("R — Radio", "fréquence et volume vérifiés"),
                    b("I — Instruments", "FLARM actif · alti QNH · compas QFU"),
                    b("S — Sécurité", "harnais serrés · verrière fermée, verrouillée et contrôlée"),
                    t("Aérofreins fermés et verrouillés"),
                    t("Câble attaché, fusible et crochet adaptés au mode de lancement"),
                    t("Ailes horizontales · main sur la poignée jaune"),
                ),
                Block.Subtitle("Briefing sécurité décollage"),
                items(
                    "cris.brief",
                    t("En cas de rupture : larguer, rendre la main et prendre la VOA"),
                    d(DynamicText.AHEAD, "Sous le seuil bas : atterrissage devant, axe conservé au maximum"),
                    d(DynamicText.FIELD, "Hauteur intermédiaire : terrain de dégagement identifié"),
                    d(DynamicText.TURN, "Au-dessus du seuil briefé : circuit ou demi-tour adapté, décision tenue"),
                    t("Briefing compris et annoncé à haute voix"),
                    t("Piste libre · message de départ effectué"),
                ),
            ),
        ),
        Checklist(
            id = "tvbcr", code = "TVBCR", title = "Briefing atterrissage",
            subtitle = "Avant d’entrer en vent arrière", openByDefault = false,
            blocks = listOf(
                items(
                    "tvbcr",
                    b("T — Train", "sorti et contrôlé"),
                    t("Trafic en vol et au sol recherché"),
                    b("V — Vent", "orientation, force et dérive évaluées"),
                    t("VOA calculée · volets positionnés"),
                    b("B — Ballasts", "vides"),
                    t("Cabine et « bazar » rangés et sécurisés"),
                    b("C — Ceintures", "serrées"),
                    t("Compensateur réglé pour l’approche"),
                    b("R — Radio", "fréquence, volume et message"),
                    t("Plan d’atterrissage annoncé : vent arrière, base, finale et point d’aboutissement"),
                ),
            ),
        ),
        Checklist(
            id = "verdo", code = "VERDO", title = "Atterrissage en campagne",
            subtitle = "Choix et reconnaissance du champ", openByDefault = false,
            blocks = listOf(
                items(
                    "verdo",
                    t("Décision prise assez tôt et champ choisi", "Ne plus poursuivre une recherche d’ascendance tardive."),
                    b("V — Vent", "force, direction, régularité et dérive"),
                    b("E — État de surface", "culture, couleurs, sillons et humidité"),
                    b("R — Relief", "pente, dévers, rivière et environnement"),
                    b("D — Dimensions", "plus grand champ possible dans l’axe du vent"),
                    b("O — Obstacles", "fils, poteaux, clôtures, piquets, arroseurs et bordures"),
                    t("TVBCR effectué pour la prise de terrain choisie"),
                    t("VERDO annoncé de tête et à haute voix"),
                ),
            ),
        ),
        Checklist(
            id = "postvache", code = "APRÈS", title = "Après la vache",
            subtitle = "Personnes · planeur · alerte · récupération", openByDefault = false,
            blocks = listOf(
                items(
                    "postvache",
                    t("Vérifier les occupants et appeler le 112 si nécessaire"),
                    t("Sécuriser le planeur et couper les équipements"),
                    t("Transmettre la position et l’état des personnes au club"),
                    t("En cas d’urgence aéronautique ou disparition : 191"),
                    t("Ne pas déplacer un planeur endommagé sans autorisation"),
                    t("Identifier et remercier le propriétaire du terrain"),
                    t("Organiser la récupération sans provoquer de dégâts supplémentaires"),
                ),
            ),
        ),
    )
}

/** Valeurs saisies dans le briefing rupture de câble (texte brut, comme les champs de la maquette). */
data class CableBriefInput(
    val qfu: String = "27",
    val turn: String = "100",
    val ahead: String = "80",
    val field: String = "champ au nord de l’axe",
    val threat: String = "vent NO et trafic remorqué",
)

data class PlanRow(val lead: String, val text: String)

/** Plan de rupture de câble et libellés dynamiques, mêmes règles que renderCablePlan() de la maquette. */
data class CablePlan(
    val rows: List<PlanRow>,
    val aheadCheck: String,
    val fieldCheck: String,
    val turnCheck: String,
) {
    fun textFor(kind: DynamicText): String = when (kind) {
        DynamicText.AHEAD -> aheadCheck
        DynamicText.FIELD -> fieldCheck
        DynamicText.TURN -> turnCheck
    }

    companion object {
        fun from(input: CableBriefInput): CablePlan {
            val ahead = maxOf(0, leadingInt(input.ahead) ?: 0)
            val turn = maxOf(ahead, leadingInt(input.turn) ?: ahead)
            val field = input.field.trim().ifEmpty { "terrain identifié" }
            val qfu = input.qfu.trim().ifEmpty { "—" }
            val threat = input.threat.trim().ifEmpty { "aucune menace particulière" }
            return CablePlan(
                rows = listOf(
                    PlanRow("Piste $qfu · menace :", threat),
                    PlanRow("Rupture sous $ahead m :", "larguer, VOA, atterrir devant dans l’axe."),
                    PlanRow("Entre $ahead et $turn m :", "rejoindre $field si l’axe ne suffit pas."),
                    PlanRow("À partir de $turn m :", "circuit ou demi-tour adapté seulement selon le briefing local ; décider puis tenir la décision."),
                ),
                aheadCheck = "Sous $ahead m : atterrissage devant, axe conservé au maximum",
                fieldCheck = "Entre $ahead et $turn m : $field",
                turnCheck = "À partir de $turn m : circuit ou demi-tour adapté selon le briefing local",
            )
        }

        /** Équivalent de parseInt() JavaScript : entier en tête de chaîne, espaces initiaux ignorés. */
        fun leadingInt(s: String): Int? {
            val t = s.trimStart()
            var end = 0
            if (end < t.length && (t[end] == '-' || t[end] == '+')) end++
            val digitsStart = end
            while (end < t.length && t[end].isDigit()) end++
            if (end == digitsStart) return null
            return t.substring(0, end).toIntOrNull()
        }
    }
}

data class Progress(val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done == total
    override fun toString() = "$done/$total"
}

fun Checklist.progress(checked: Set<String>): Progress = items.let { list -> Progress(list.count { it.id in checked }, list.size) }
