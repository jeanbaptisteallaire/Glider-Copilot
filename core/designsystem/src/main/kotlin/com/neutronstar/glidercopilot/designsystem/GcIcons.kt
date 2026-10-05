package com.neutronstar.glidercopilot.designsystem

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Pictogrammes au trait de la maquette v8 (viewBox 24, trait 1,8 à 2). Teinte appliquée par Icon(tint). */
object GcIcons {
    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Prevol = stroke(
        "prevol", 1.8f,
        "M16 12a4 4 0 1 1 -8 0a4 4 0 1 1 8 0",
        "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
    )
    val Checklist = stroke(
        "checklist", 1.8f,
        "M8 6h12M8 12h12M8 18h12",
        "M3.5 6l1.2 1.2L6.8 5M3.5 12l1.2 1.2 2.1-2.2M3.5 18l1.2 1.2 2.1-2.2",
    )
    val Pilotage = stroke("pilotage", 1.8f, "M12 4v15M2 10.5h20M8.5 19.5h7")
    val Sound = stroke("sound", 2f, "M4 9h4l5-4v14l-5-4H4z", "M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12")
    val SoundOff = stroke("soundOff", 2f, "M4 9h4l5-4v14l-5-4H4z", "M16.5 9.5l5 5M21.5 9.5l-5 5")
    val ChevronDown = stroke("chevronDown", 2f, "M6 9l6 6 6-6")
    val ChevronUp = stroke("chevronUp", 2f, "M6 15l6-6 6 6")
    val ChevronRight = stroke("chevronRight", 2f, "M9 6l6 6-6 6")
    val WindArrow = stroke("wind", 2.4f, "M12 20V5M6 10l6-6 6 6")
    /** Onglet Carte : carte pliée, trait au trait comme les autres onglets. */
    val Carte = stroke("carte", 1.8f, "M9 4.5 3.5 6.8v12.7L9 17.2l6 2.3 5.5-2.3V4.5L15 6.8z", "M9 4.5v12.7M15 6.8v12.7")
    /** Onglet Mes vols (S10) : trace de vol qui spirale vers le haut, départ marqué d'un point. */
    val MesVols = stroke(
        "mesVols", 1.8f,
        "M4 20c3-1 4.5-3.5 4.5-6 0-2 1.6-3.2 3.3-2.8 1.8.4 2.2 2.6.7 3.4-1.6.8-3.3-.6-2.8-2.4.7-2.6 3.8-4.2 6.5-3.6 2.5.6 4 2.8 3.8 5.4",
        "M3.6 20.4h.01",
    )
    /** Interrupteur de mode clair/sombre (V7.2). */
    val LightMode = stroke(
        "lightMode", 1.8f,
        "M12 17a5 5 0 1 0 0 -10a5 5 0 1 0 0 10",
        "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
    )
    val DarkMode = stroke("darkMode", 1.8f, "M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5")
    /** S16 — partager / publier sur le fil (flèche sortant d'un plateau). */
    val Share = stroke("share", 2f, "M12 15V3.5M7.5 8 12 3.5 16.5 8", "M5 12.5v6a1.5 1.5 0 0 0 1.5 1.5h11a1.5 1.5 0 0 0 1.5-1.5v-6")
    /** S16 — coche « déjà partagé ». */
    val Check = stroke("check", 2.4f, "M5 12.5l4.5 4.5L19 7.5")
    /** S16 — menu du profil (trois points). */
    val More = stroke("more", 2.6f, "M5 12h.01M12 12h.01M19 12h.01")
    /** S16 — importer un fichier (flèche entrant dans un plateau). */
    val Import = stroke("import", 2f, "M12 3.5V15M7.5 10.5 12 15l4.5-4.5", "M5 12.5v6a1.5 1.5 0 0 0 1.5 1.5h11a1.5 1.5 0 0 0 1.5-1.5v-6")
    /** S17 — onglet Feed : grille 3 × 3 façon réseau social. */
    val Feed = stroke(
        "feed", 1.8f,
        "M4 4h4.5v4.5H4zM9.75 4h4.5v4.5h-4.5zM15.5 4H20v4.5h-4.5z",
        "M4 9.75h4.5v4.5H4zM9.75 9.75h4.5v4.5h-4.5zM15.5 9.75H20v4.5h-4.5z",
        "M4 15.5h4.5V20H4zM9.75 15.5h4.5V20h-4.5zM15.5 15.5H20V20h-4.5z",
    )
    /** S17 — loupe de la barre de recherche. */
    val Search = stroke("search", 2f, "M10.5 17a6.5 6.5 0 1 0 0-13a6.5 6.5 0 1 0 0 13", "M15.3 15.3 20 20")
    /** S17 — effacer la recherche. */
    val Close = stroke("close", 2f, "M6 6l12 12M18 6 6 18")

    /** V18.1 — pictogramme plein façon Apple (SF Symbols « .fill ») : aplats + traits éventuels. */
    private fun glyph(name: String, fills: List<String>, strokes: List<String> = emptyList(), width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            fills.forEach { d ->
                addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd)
            }
            strokes.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** V18.1 — barre d'onglets : pictogrammes pleins, lumineux, inspirés d'Apple. */
    /** V19.1 — globe : choix de la langue (tutoriel, page d'information). */
    val Language = glyph(
        "language",
        emptyList(),
        listOf("M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M3 12h18", "M12 3c2.5 2.6 3.7 5.6 3.7 9s-1.2 6.4-3.7 9c-2.5-2.6-3.7-5.6-3.7-9s1.2-6.4 3.7-9z"),
        1.6f,
    )

    object Tab {
        /** Soleil plein. */
        val Prevol = glyph(
            "tabPrevol",
            listOf("M12 7.2a4.8 4.8 0 1 1 0 9.6a4.8 4.8 0 1 1 0-9.6z"),
            listOf("M12 1.8v2M12 20.2v2M4.8 4.8l1.4 1.4M17.8 17.8l1.4 1.4M1.8 12h2M20.2 12h2M4.8 19.2l1.4-1.4M17.8 6.2l1.4-1.4"),
            2.1f,
        )
        /** Planeur vu de dessus : grande envergure, fuselage fin, empennage. */
        val Pilotage = glyph(
            "tabPilotage",
            listOf(
                "M12 3c.7 0 1 .6 1 1.5v4.9l9.4.6c.4 0 .6.3.6.6v.5c0 .3-.2.6-.6.6l-9.4.4v6.5l2.4.4c.3.1.5.3.5.6v.3c0 .3-.3.5-.6.5H8.7c-.3 0-.6-.2-.6-.5v-.3c0-.3.2-.5.5-.6l2.4-.4v-6.5l-9.4-.4c-.4 0-.6-.3-.6-.6v-.5c0-.3.2-.6.6-.6l9.4-.6V4.5c0-.9.3-1.5 1-1.5z",
            ),
        )
        /** Silhouette de personne : Mes vols est la page profil du pilote. */
        val MesVols = glyph(
            "tabMesVols",
            listOf(
                "M12 3a4.3 4.3 0 1 1 0 8.6a4.3 4.3 0 1 1 0-8.6z",
                "M3.8 19.9c0-3.9 3.7-6.6 8.2-6.6s8.2 2.7 8.2 6.6c0 .8-.6 1.3-1.3 1.3H5.1c-.7 0-1.3-.5-1.3-1.3z",
            ),
        )
        /** Cartes empilées : le fil. */
        val Feed = glyph(
            "tabFeed",
            listOf(
                "M6.2 8h11.6c1.3 0 2.2 1 2.2 2.2v8.6c0 1.3-1 2.2-2.2 2.2H6.2C5 21 4 20 4 18.8v-8.6C4 9 5 8 6.2 8z",
                "M7 4.2h10c.6 0 1 .4 1 1s-.4 1-1 1H7c-.6 0-1-.4-1-1s.4-1 1-1z",
            ),
        )
        /** Carré plein, coche évidée. */
        val Checklist = glyph(
            "tabChecklist",
            listOf(
                "M5.5 3h13A2.5 2.5 0 0 1 21 5.5v13a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 18.5v-13A2.5 2.5 0 0 1 5.5 3z" +
                    "M7.3 12.4l1.5-1.5 2.1 2.1 4.3-4.3 1.5 1.5-5.8 5.8z",
            ),
        )
        /** V19.1 — toque d'écolier (mortier) : le tutoriel. */
        val Tuto = glyph(
            "tabTuto",
            listOf("M12 3.2 23 8.6 12 14 1 8.6z", "M5.8 11.4v4.4c0 1.8 2.8 3.4 6.2 3.4s6.2-1.6 6.2-3.4v-4.4l-6.2 3z"),
            listOf("M21.2 9.6v5.6"),
            1.8f,
        )
        /** Carte pliée en trois volets pleins. */
        val Carte = glyph(
            "tabCarte",
            listOf("M2.5 6.6 8.2 4.3v13.4l-5.7 2.2z", "M9.7 4.3l4.6 2.2v13.4l-4.6-2.2z", "M15.8 6.5l5.7-2.2v13.4l-5.7 2.2z"),
        )
    }
}
