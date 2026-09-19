package com.neutronstar.glidercopilot.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * CHARTE GLIDY — maquette « planeur-pilotage-prevol-v8 » (session 2).
 * Fond noir intégral, accent vert #b7f7a5, alerte orange #ff9f43, police système.
 * Toute la charte vit dans ce fichier : aucun écran ne code une couleur ou une police.
 */

@Immutable
data class GcColors(
    val background: Color,
    val panel: Color,
    /** Fond des commandes groupées (finesse, pastilles, badges de check-list). */
    val control: Color,
    /** Commande sélectionnée. */
    val controlOn: Color,
    val line: Color,
    val lineSoft: Color,
    val lineFaint: Color,
    /** Fonds légèrement relevés des check-lists (encadré, champs, plan de rupture). */
    val sunken: Color,
    val sunkenField: Color,
    val sunkenPlan: Color,
    val inkSoft: Color,
    val planText: Color,
    val inputLine: Color,
    val ink: Color,
    val dim: Color,
    val faint: Color,
    val cardTitle: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val danger: Color,
    val onAccent: Color,
    val route: Color,
    /** Mauve aéronautique (V7.2) : cap et vecteur de retour au terrain, jamais utilisé ailleurs. */
    val heading: Color,
    val air: Color,
    val finesseIdle: Color,
    val statusOn: Color,
    val statusOff: Color,
    val overlay: Color,
    val terrainTop: Color,
    val terrainBottom: Color,
    val mapLow: Color,
    val mapHigh: Color,
    /** Échelle vario v8 : vert en descente, gris à zéro, orange puis rouge en montée (m/s → couleur). */
    val varioStops: List<Pair<Double, Color>>,
    val windLayer: Color,
)

val GlidyColors = GcColors(
    background = Color(0xFF000000),
    panel = Color(0xFF000000),
    control = Color(0xFF171717),
    controlOn = Color(0xFF292929),
    line = Color(0xFF383838),
    lineSoft = Color(0xFF292929),
    lineFaint = Color(0xFF202020),
    sunken = Color(0xFF0B0B0B),
    sunkenField = Color(0xFF080808),
    sunkenPlan = Color(0xFF111111),
    inkSoft = Color(0xFFEEEEEE),
    planText = Color(0xFFDDDDDD),
    inputLine = Color(0xFF444444),
    ink = Color(0xFFF5F5F5),
    dim = Color(0xFFC4C4C4),
    faint = Color(0xFFADADAD),
    cardTitle = Color(0xFFD8D8D8),
    ok = Color(0xFFB7F7A5),
    warn = Color(0xFFFF9F43),
    bad = Color(0xFFFF9F43),
    danger = Color(0xFFFF8078),
    onAccent = Color(0xFF061008),
    route = Color(0xFFB7F7A5),
    heading = Color(0xFFC77DFF),
    air = Color(0xFF69C8FF),
    finesseIdle = Color(0xFFC1D3BB),
    statusOn = Color(0xFF68E37F),
    statusOff = Color(0xFFFF4D5E),
    overlay = Color(0xE6000000),
    terrainTop = Color(0xFF579567),
    terrainBottom = Color(0xFF244F32),
    mapLow = Color(0xFF1A1A1A),
    mapHigh = Color(0xFF313131),
    varioStops = listOf(
        -3.0 to Color(0xFF2D7043),
        -1.0 to Color(0xFF7DC77E),
        0.0 to Color(0xFFBEBEBE),
        0.6 to Color(0xFFFFBD70),
        1.8 to Color(0xFFFF9130),
        3.5 to Color(0xFFFF8078),
    ),
    windLayer = Color(0xFFB7F7A5),
)

/*
 * Mode clair (V7.2) — Prévol, Check-lists et Carte uniquement ; Pilotage reste noir, obligatoire (demande JB).
 * Fond gris/blanc doux, texte quasi noir, style « Apple » (cf. system gray palette iOS).
 */
val GlidyLightColors = GcColors(
    background = Color(0xFFF2F2F7),
    panel = Color(0xFFFFFFFF),
    control = Color(0xFFEDEDF2),
    controlOn = Color(0xFFE0E0E8),
    line = Color(0xFFD8D8DE),
    lineSoft = Color(0xFFE4E4EA),
    lineFaint = Color(0xFFEDEDF2),
    sunken = Color(0xFFF7F7FA),
    sunkenField = Color(0xFFFFFFFF),
    sunkenPlan = Color(0xFFF0F0F5),
    inkSoft = Color(0xFF1C1C1E),
    planText = Color(0xFF3A3A3C),
    inputLine = Color(0xFFC7C7CC),
    ink = Color(0xFF1C1C1E),
    dim = Color(0xFF6E6E73),
    faint = Color(0xFF8E8E93),
    cardTitle = Color(0xFF3A3A3C),
    ok = Color(0xFF2E9E44),
    warn = Color(0xFFE07E00),
    bad = Color(0xFFE07E00),
    danger = Color(0xFFE0342A),
    onAccent = Color(0xFFFFFFFF),
    route = Color(0xFF2E9E44),
    heading = Color(0xFF8A3FFC),
    air = Color(0xFF0A6ED8),
    finesseIdle = Color(0xFF8E8E93),
    statusOn = Color(0xFF2E9E44),
    statusOff = Color(0xFFE0342A),
    overlay = Color(0xE6FFFFFF),
    terrainTop = Color(0xFFBFE3C4),
    terrainBottom = Color(0xFF8FCB98),
    mapLow = Color(0xFFEDEDF2),
    mapHigh = Color(0xFFFAFAFC),
    varioStops = listOf(
        -3.0 to Color(0xFF2D7043),
        -1.0 to Color(0xFF7DC77E),
        0.0 to Color(0xFFBEBEBE),
        0.6 to Color(0xFFFFBD70),
        1.8 to Color(0xFFFF9130),
        3.5 to Color(0xFFFF8078),
    ),
    windLayer = Color(0xFF2E9E44),
)

/** Couleur interpolée sur l'échelle vario de la charte. */
fun GcColors.vario(ms: Double): Color {
    val s = varioStops
    if (ms <= s.first().first) return s.first().second
    for (k in 1 until s.size) {
        val (v1, c1) = s[k]
        val (v0, c0) = s[k - 1]
        if (ms <= v1) return lerp(c0, c1, ((ms - v0) / (v1 - v0)).toFloat())
    }
    return s.last().second
}

object GcFonts {
    /** Police système (Roboto sur Android), équivalent de -apple-system de la maquette. */
    val ui: FontFamily = FontFamily.SansSerif
    val mono: FontFamily = FontFamily.SansSerif
    val numbers: FontFamily = FontFamily.SansSerif
}

@Immutable
data class GcType(
    val eyebrow: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val mono: TextStyle,
    val monoSmall: TextStyle,
    val title: TextStyle,
    val kpi: TextStyle,
    val giant: TextStyle,
)

private fun gcType(c: GcColors) = GcType(
    eyebrow = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.15.sp, color = c.dim),
    body = TextStyle(fontFamily = GcFonts.ui, fontSize = 14.sp, color = c.ink),
    bodySmall = TextStyle(fontFamily = GcFonts.ui, fontSize = 12.sp, color = c.dim),
    mono = TextStyle(fontFamily = GcFonts.mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = c.ink, fontFeatureSettings = "tnum"),
    monoSmall = TextStyle(fontFamily = GcFonts.mono, fontSize = 11.sp, color = c.dim, fontFeatureSettings = "tnum"),
    title = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.6).sp, color = c.ink),
    kpi = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, color = c.ink, fontFeatureSettings = "tnum"),
    giant = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.Medium, fontSize = 50.sp, letterSpacing = (-2.2).sp, color = c.ok, fontFeatureSettings = "tnum"),
)

val LocalGcColors = staticCompositionLocalOf { GlidyColors }
val LocalGcType = staticCompositionLocalOf { gcType(GlidyColors) }

object Gc {
    val colors: GcColors @Composable get() = LocalGcColors.current
    val type: GcType @Composable get() = LocalGcType.current
}

@Composable
fun GlidyTheme(content: @Composable () -> Unit) {
    val c = GlidyColors
    val scheme = darkColorScheme(
        primary = c.ok,
        onPrimary = c.onAccent,
        secondary = c.route,
        tertiary = c.air,
        background = c.background,
        onBackground = c.ink,
        surface = c.control,
        onSurface = c.ink,
        surfaceVariant = c.control,
        onSurfaceVariant = c.dim,
        outline = c.line,
        error = c.bad,
    )
    val typography = Typography(
        bodyLarge = TextStyle(fontFamily = GcFonts.ui, fontSize = 15.sp),
        bodyMedium = TextStyle(fontFamily = GcFonts.ui, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = GcFonts.ui, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        labelMedium = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
        titleMedium = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    )
    CompositionLocalProvider(LocalGcColors provides c, LocalGcType provides gcType(c)) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/**
 * Sous-thème appliqué à une partie de l'écran (V7.2 : Prévol, Check-lists, Carte — jamais Pilotage,
 * qui reste noir). [light] bascule vers [GlidyLightColors] ; sinon la charte sombre habituelle.
 */
@Composable
fun GlidyAdaptiveTheme(light: Boolean, content: @Composable () -> Unit) {
    val c = if (light) GlidyLightColors else GlidyColors
    val scheme = if (light) {
        lightColorScheme(
            primary = c.ok, onPrimary = c.onAccent, secondary = c.route, tertiary = c.air,
            background = c.background, onBackground = c.ink, surface = c.panel, onSurface = c.ink,
            surfaceVariant = c.control, onSurfaceVariant = c.dim, outline = c.line, error = c.bad,
        )
    } else {
        darkColorScheme(
            primary = c.ok, onPrimary = c.onAccent, secondary = c.route, tertiary = c.air,
            background = c.background, onBackground = c.ink, surface = c.control, onSurface = c.ink,
            surfaceVariant = c.control, onSurfaceVariant = c.dim, outline = c.line, error = c.bad,
        )
    }
    CompositionLocalProvider(LocalGcColors provides c, LocalGcType provides gcType(c)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
