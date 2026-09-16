package com.neutronstar.glidercopilot.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * CHARTE PROVISOIRE — issue de la maquette HTML.
 * Toute la charte vit dans ce fichier : couleurs, familles, tailles. La nouvelle charte de JB
 * (fin de session 1) remplacera ces jetons sans toucher aux écrans.
 */

@Immutable
data class GcColors(
    val background: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val ink: Color,
    val dim: Color,
    val faint: Color,
    val chip: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val magenta: Color,
    val route: Color,
    val air: Color,
    val terrain: Color,
)

val CockpitColors = GcColors(
    background = Color(0xFF0B1117),
    panel = Color(0xFF111922),
    panel2 = Color(0xFF16202A),
    line = Color(0xFF22303C),
    ink = Color(0xFFE6EDF2),
    dim = Color(0xFF8B9BA8),
    faint = Color(0xFF5E6D7B),
    chip = Color(0xFF1A2531),
    ok = Color(0xFF6FE08A),
    warn = Color(0xFFF5A524),
    bad = Color(0xFFFF4D5E),
    magenta = Color(0xFFE24BD6),
    route = Color(0xFF4FC3F7),
    air = Color(0xFF6AA8FF),
    terrain = Color(0xFF8A7650),
)

object GcFonts {
    val ui = FontFamily(Font(R.font.b612_regular, FontWeight.Normal), Font(R.font.b612_bold, FontWeight.Bold))
    val mono = FontFamily(Font(R.font.b612mono_regular, FontWeight.Normal), Font(R.font.b612mono_bold, FontWeight.Bold))
    val numbers = FontFamily(
        Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlowcondensed_bold, FontWeight.Bold),
    )
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
    eyebrow = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp, color = c.dim),
    body = TextStyle(fontFamily = GcFonts.ui, fontSize = 14.sp, color = c.ink),
    bodySmall = TextStyle(fontFamily = GcFonts.ui, fontSize = 12.sp, color = c.dim),
    mono = TextStyle(fontFamily = GcFonts.mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink),
    monoSmall = TextStyle(fontFamily = GcFonts.mono, fontSize = 11.sp, color = c.dim),
    title = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = 0.6.sp, color = c.ink),
    kpi = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = c.ink),
    giant = TextStyle(fontFamily = GcFonts.numbers, fontWeight = FontWeight.Bold, fontSize = 72.sp, color = c.ok),
)

val LocalGcColors = staticCompositionLocalOf { CockpitColors }
val LocalGcType = staticCompositionLocalOf { gcType(CockpitColors) }

object Gc {
    val colors: GcColors @Composable get() = LocalGcColors.current
    val type: GcType @Composable get() = LocalGcType.current
}

@Composable
fun GliderCopilotTheme(content: @Composable () -> Unit) {
    val c = CockpitColors
    val scheme = darkColorScheme(
        primary = c.ok,
        onPrimary = Color(0xFF06110A),
        secondary = c.route,
        tertiary = c.magenta,
        background = c.background,
        onBackground = c.ink,
        surface = c.panel,
        onSurface = c.ink,
        surfaceVariant = c.panel2,
        onSurfaceVariant = c.dim,
        outline = c.line,
        error = c.bad,
    )
    val typography = Typography(
        bodyLarge = TextStyle(fontFamily = GcFonts.ui, fontSize = 15.sp),
        bodyMedium = TextStyle(fontFamily = GcFonts.ui, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = GcFonts.ui, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 13.sp),
        labelMedium = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 11.sp),
        titleMedium = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    )
    CompositionLocalProvider(LocalGcColors provides c, LocalGcType provides gcType(c)) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
