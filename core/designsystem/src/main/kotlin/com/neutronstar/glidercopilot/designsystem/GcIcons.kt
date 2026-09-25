package com.neutronstar.glidercopilot.designsystem

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
}
