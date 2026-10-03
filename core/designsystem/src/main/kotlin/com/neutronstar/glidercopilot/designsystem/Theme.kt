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
import androidx.compose.ui.text.font.Font
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
    /** S15 — aplat de la couleur de marque (boutons principaux, sélection). Sombre : identique à [ok]. */
    val accentFill: Color = ok,
    val onAccentFill: Color = onAccent,
    /** S16 — trait des traces de vol dans les vignettes du profil et du fil (rouge demandé par JB). */
    val trace: Color = Color(0xFFE5383B),
    /*
     * V18.1 « New UI » — couleurs de catégorie à la manière d'Apple Santé : chaque carte porte la teinte de son
     * sujet (titre + pictogramme + graphique). Ciel = sélection et interactions des pages blanches (demande JB).
     */
    val sky: Color = Color(0xFF0A84FF),
    val sun: Color = Color(0xFFFF9500),
    val wind: Color = Color(0xFF30B0C7),
    val altitude: Color = Color(0xFF5E5CE6),
    val heart: Color = Color(0xFFFF375F),
    val mint: Color = Color(0xFF34C759),
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
 * Thème clair « social » (S15, demande JB) — tous les onglets sauf Pilotage, qui reste noir et inchangé.
 * Fond blanc, texte presque noir, gris neutres, vert GLIDY #b7f7a5 en aplat de marque (boutons, sélection),
 * vert foncé lisible pour les chiffres et le texte d'accent. Contrastes texte ≥ 4,5:1 sur blanc.
 */
val GlidyLightColors = GcColors(
    background = Color(0xFFF2F2F7),
    panel = Color(0xFFFFFFFF),
    control = Color(0xFFEEEEF0),
    controlOn = Color(0xFFE3E3E8),
    line = Color(0xFFD1D1D6),
    lineSoft = Color(0xFFE5E5EA),
    lineFaint = Color(0xFFF0F0F3),
    sunken = Color(0xFFF7F7FA),
    sunkenField = Color(0xFFFFFFFF),
    sunkenPlan = Color(0xFFF2F2F7),
    inkSoft = Color(0xFF1A1A1A),
    planText = Color(0xFF3A3A3C),
    inputLine = Color(0xFFCFCFCF),
    ink = Color(0xFF000000),
    dim = Color(0xFF6C6C70),
    faint = Color(0xFF8E8E93),
    cardTitle = Color(0xFF0F0F0F),
    ok = Color(0xFF1A7F37),
    warn = Color(0xFFC25E00),
    bad = Color(0xFFC25E00),
    danger = Color(0xFFD1242F),
    onAccent = Color(0xFFFFFFFF),
    route = Color(0xFF0A84FF),
    heading = Color(0xFF8A3FFC),
    air = Color(0xFF0A6ED8),
    finesseIdle = Color(0xFF8E8E93),
    statusOn = Color(0xFF1A7F37),
    statusOff = Color(0xFFD1242F),
    overlay = Color(0xF2F9F9F9),
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
    windLayer = Color(0xFF1A7F37),
    accentFill = Color(0xFF0A84FF),
    onAccentFill = Color(0xFFFFFFFF),
)

/*
 * V18.7 — Pilotage sur fond blanc (demande JB) : la charte sombre inversée. Encre noire pure pour les chiffres
 * importants (contraste maximal en plein soleil), vert et orange foncés pour garder le sens des couleurs
 * (marge positive / alerte) tout en restant lisibles sur blanc (contrastes ≥ 4,5:1).
 */
val GlidyFlightLightColors = GcColors(
    background = Color(0xFFFFFFFF),
    panel = Color(0xFFFFFFFF),
    control = Color(0xFFEFEFF2),
    controlOn = Color(0xFFDCDCE2),
    line = Color(0xFFB8B8BF),
    lineSoft = Color(0xFFD6D6DB),
    lineFaint = Color(0xFFE8E8EC),
    sunken = Color(0xFFF6F6F8),
    sunkenField = Color(0xFFFFFFFF),
    sunkenPlan = Color(0xFFF0F0F3),
    inkSoft = Color(0xFF111111),
    planText = Color(0xFF222222),
    inputLine = Color(0xFFB0B0B0),
    ink = Color(0xFF000000),
    dim = Color(0xFF3C3C43),
    faint = Color(0xFF5E5E64),
    cardTitle = Color(0xFF111111),
    ok = Color(0xFF0E7A2E),
    warn = Color(0xFFC2410C),
    bad = Color(0xFFC2410C),
    danger = Color(0xFFC62828),
    onAccent = Color(0xFFFFFFFF),
    route = Color(0xFF6A1FB0),
    heading = Color(0xFF7B2CBF),
    air = Color(0xFF0B5CAD),
    finesseIdle = Color(0xFF4A4A50),
    statusOn = Color(0xFF0E7A2E),
    statusOff = Color(0xFFD1242F),
    overlay = Color(0xEBFFFFFF),
    terrainTop = Color(0xFF8DBF7F),
    terrainBottom = Color(0xFF4E8B57),
    mapLow = Color(0xFFF4F1E4),
    mapHigh = Color(0xFFFBF9F1),
    varioStops = listOf(
        -3.0 to Color(0xFF1E6B35),
        -1.0 to Color(0xFF3E9A54),
        0.0 to Color(0xFF8E8E93),
        0.6 to Color(0xFFE07B00),
        1.8 to Color(0xFFC2410C),
        3.5 to Color(0xFFB3261E),
    ),
    windLayer = Color(0xFF0E7A2E),
    accentFill = Color(0xFF0E7A2E),
    onAccentFill = Color(0xFFFFFFFF),
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
    /** V18.1 — Inter (SIL OFL 1.1, licenses/INTER-FONT-LICENSE.txt) dans toute l'application, Pilotage compris. */
    val inter: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
    val ui: FontFamily = inter
    val mono: FontFamily = inter
    val numbers: FontFamily = inter
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
    /*
     * V18.1 — échelle typographique d'Apple (Human Interface Guidelines, iOS, taille par défaut) :
     * Large Title 34 · Title 1 28 · Title 2 22 · Title 3 20 · Headline 17 semi-gras · Body 17 · Callout 16 ·
     * Subheadline 15 · Footnote 13 · Caption 1 12 · Caption 2 11.
     */
    val largeTitle: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp),
    val title1: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    val title2: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    val title3: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp),
    val headline: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
    val callout: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp),
    val subhead: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    val footnote: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp),
    val caption1: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontSize = 12.sp, lineHeight = 16.sp),
    val caption2: TextStyle = TextStyle(fontFamily = GcFonts.ui, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.06.sp),
    /** Grand chiffre d'une carte (« 62 BPM » d'Apple Santé) : Title 1 en chiffres tabulaires. */
    val metric: TextStyle = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
) {
    /** Applique la couleur d'encre du thème aux styles de l'échelle Apple. */
    internal fun inked(c: GcColors): GcType = copy(
        largeTitle = largeTitle.copy(color = c.ink), title1 = title1.copy(color = c.ink), title2 = title2.copy(color = c.ink),
        title3 = title3.copy(color = c.ink), headline = headline.copy(color = c.ink), callout = callout.copy(color = c.ink),
        subhead = subhead.copy(color = c.ink), footnote = footnote.copy(color = c.dim), caption1 = caption1.copy(color = c.dim),
        caption2 = caption2.copy(color = c.dim), metric = metric.copy(color = c.ink),
    )
}

private fun gcType(c: GcColors, social: Boolean = false): GcType = if (social) socialType(c) else GcType(
    eyebrow = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.15.sp, color = c.dim),
    body = TextStyle(fontFamily = GcFonts.ui, fontSize = 14.sp, color = c.ink),
    bodySmall = TextStyle(fontFamily = GcFonts.ui, fontSize = 12.sp, color = c.dim),
    mono = TextStyle(fontFamily = GcFonts.mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = c.ink, fontFeatureSettings = "tnum"),
    monoSmall = TextStyle(fontFamily = GcFonts.mono, fontSize = 11.sp, color = c.dim, fontFeatureSettings = "tnum"),
    title = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.6).sp, color = c.ink),
    kpi = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, color = c.ink, fontFeatureSettings = "tnum"),
    giant = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.Medium, fontSize = 50.sp, letterSpacing = (-2.2).sp, color = c.ok, fontFeatureSettings = "tnum"),
).inked(c)

/**
 * Typographie des pages blanches. V18.1 : échelle d'Apple — titres d'écran en Large Title (34), texte courant en
 * Body (17), texte secondaire en Subheadline (15), libellés en Footnote (13), chiffres en Title 1 tabulaire.
 */
private fun socialType(c: GcColors) = GcType(
    eyebrow = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp, color = c.dim),
    body = TextStyle(fontFamily = GcFonts.ui, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp, color = c.ink),
    bodySmall = TextStyle(fontFamily = GcFonts.ui, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp, color = c.dim),
    mono = TextStyle(fontFamily = GcFonts.mono, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = c.ink, fontFeatureSettings = "tnum"),
    monoSmall = TextStyle(fontFamily = GcFonts.mono, fontSize = 13.sp, color = c.dim, fontFeatureSettings = "tnum"),
    title = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp, color = c.ink),
    kpi = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, color = c.ink, fontFeatureSettings = "tnum"),
    giant = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.Bold, fontSize = 48.sp, letterSpacing = (-1).sp, color = c.ink, fontFeatureSettings = "tnum"),
).inked(c)

val LocalGcColors = staticCompositionLocalOf { GlidyColors }
/** S15 — vrai dans le thème clair « social » (tous les onglets sauf Pilotage). */
val LocalGcSocial = staticCompositionLocalOf { false }
val LocalGcType = staticCompositionLocalOf { gcType(GlidyColors) }

object Gc {
    val colors: GcColors @Composable get() = LocalGcColors.current
    val type: GcType @Composable get() = LocalGcType.current
    val social: Boolean @Composable get() = LocalGcSocial.current
}

/** Titre d'écran : en capitales dans la charte sombre v8, en casse normale dans le thème social. */
@Composable
fun gcHeading(text: String): String = if (Gc.social) text else text.uppercase()

/** V18.1 — typographie Material (champs, menus, boutons système) en Inter, tailles Apple. */
fun interTypography(): Typography {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = GcFonts.ui)
    return base.copy(
        displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(), displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(), headlineMedium = base.headlineMedium.inter(), headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter(), titleMedium = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = TextStyle(fontFamily = GcFonts.ui, fontSize = 17.sp), bodyMedium = TextStyle(fontFamily = GcFonts.ui, fontSize = 15.sp),
        bodySmall = TextStyle(fontFamily = GcFonts.ui, fontSize = 13.sp),
        labelLarge = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    )
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
 * V18.7 — thème de l'onglet Pilotage. [light] (par défaut) : fond blanc [GlidyFlightLightColors], même
 * typographie de vol que la charte sombre (tailles et graisses inchangées, seules les couleurs s'inversent).
 * Sinon : la charte sombre v8 d'origine, strictement inchangée ([GlidyTheme]).
 */
@Composable
fun GlidyFlightTheme(light: Boolean, content: @Composable () -> Unit) {
    if (!light) {
        GlidyTheme(content)
        return
    }
    val c = GlidyFlightLightColors
    val scheme = lightColorScheme(
        primary = c.ok, onPrimary = c.onAccent, secondary = c.route, tertiary = c.air,
        background = c.background, onBackground = c.ink, surface = c.control, onSurface = c.ink,
        surfaceVariant = c.control, onSurfaceVariant = c.dim, outline = c.line, error = c.bad,
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
 * Sous-thème appliqué à une partie de l'écran (Prévol, Check-lists, Carte, Mes vols — jamais Pilotage,
 * qui reste noir). [light] bascule vers le thème social blanc [GlidyLightColors] (S15, par défaut) ;
 * sinon la charte sombre habituelle, strictement inchangée.
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
    CompositionLocalProvider(LocalGcColors provides c, LocalGcType provides gcType(c, social = light), LocalGcSocial provides light) {
        MaterialTheme(colorScheme = scheme, typography = interTypography(), content = content)
    }
}
