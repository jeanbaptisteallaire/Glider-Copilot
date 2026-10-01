package com.neutronstar.glidercopilot.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Carte Prévol v8 : fond noir, simple filet supérieur, titre en capitales.
 * V18.1 pages blanches (Apple Santé) : carte blanche aux coins de 14 dp sur fond groupé gris, titre coloré à la
 * teinte du sujet ([tint], pictogramme [icon] facultatif), [trailing] à droite (heure, pastille, chevron).
 */
@Composable
fun GcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    icon: ImageVector? = null,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Gc.colors
    if (Gc.social) {
        val t = tint ?: accent ?: c.cardTitle
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null || trailing != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = t, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    if (title != null) Text(
                        title.socialCase(),
                        style = Gc.type.subhead.copy(color = t, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    ) else Spacer(Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
            content()
        }
        return
    }
    Column(modifier.fillMaxWidth().background(c.panel)) {
        HorizontalDivider(thickness = 1.dp, color = accent ?: c.lineSoft)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (title != null || trailing != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    if (title != null) Text(
                        title.uppercase(),
                        style = Gc.type.eyebrow.copy(color = c.cardTitle, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.4.sp),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** Section groupée façon iOS : titre de section (Title 3) au-dessus d'une ou plusieurs cartes. */
@Composable
fun GcSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = Gc.type.title3.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** « CARNET · CE TÉLÉPHONE » → « Carnet · ce téléphone » (titres écrits en capitales dans les écrans). */
internal fun String.socialCase(): String {
    val letters = filter { it.isLetter() }
    if (letters.isEmpty() || letters.any { it.isLowerCase() }) return this
    // les sigles courts (OGN, IGC, QNH, FLARM…) restent en capitales
    return split(" ").joinToString(" ") { w -> if (w.length in 2..5 && w.all { it.isUpperCase() || !it.isLetter() } && w in ACRONYMS) w else w.lowercase() }
        .replaceFirstChar { it.titlecase() }
}

private val ACRONYMS = setOf("OGN", "IGC", "QNH", "QFE", "FLARM", "GPS", "VFR", "OACI", "FFVP", "SIA", "AIP", "UTC", "TMA", "CTR", "3D", "IGN", "EOX")

/** Pastille : texte coloré sur noir, bordure teintée. Thème social : aplat pâle de la couleur, sans bordure. */
@Composable
fun GcPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val c = Gc.colors
    if (Gc.social) {
        val neutral = color == c.dim || color == c.faint || color == c.ink
        Text(
            text,
            style = Gc.type.caption1.copy(color = if (neutral) c.dim else color, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            modifier = modifier
                .background(if (neutral) c.control else color.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        return
    }
    val border = if (color == c.dim || color == c.faint || color == c.ink) c.line else color.copy(alpha = 0.33f)
    Text(
        text,
        style = Gc.type.eyebrow.copy(color = color, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.5.sp),
        maxLines = 1,
        modifier = modifier
            .background(c.panel, RoundedCornerShape(50))
            .border(1.dp, border, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Chiffre clé. V18.1 pages blanches (Apple Santé) : libellé au-dessus, en couleur de catégorie ou gris, puis la
 * valeur dont les unités sont plus petites et grises (« 62 BPM », « 1 h 08 », « 128 km »).
 */
@Composable
fun GcKpi(value: String, label: String, modifier: Modifier = Modifier, color: Color = Gc.colors.ink, labelColor: Color? = null) {
    if (Gc.social) {
        val c = Gc.colors
        Column(modifier) {
            Text(label.socialCase(), style = Gc.type.footnote.copy(color = labelColor ?: c.dim, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(metricText(value, Gc.type.kpi.copy(color = if (color == c.ok) c.ink else color), c.faint), maxLines = 1)
        }
        return
    }
    Column(modifier) {
        Text(value, style = Gc.type.kpi.copy(color = color))
        Text(label.uppercase(), style = Gc.type.eyebrow.copy(fontSize = 9.sp, letterSpacing = 0.6.sp))
    }
}

/**
 * « 1 h 08 » → chiffres au style [number], lettres (unités) à 60 % de la taille et en [unitColor] : le rendu
 * « 62 BPM » d'Apple Santé, utilisable pour toute valeur mesurée.
 */
fun metricText(value: String, number: TextStyle, unitColor: Color): AnnotatedString = buildAnnotatedString {
    val unit = SpanStyle(fontSize = number.fontSize * 0.62f, color = unitColor, fontWeight = FontWeight.SemiBold)
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        val isNum = ch.isDigit() || ((ch == ',' || ch == '.' || ch == ':' || ch == '+' || ch == '−' || ch == '-') && i + 1 < value.length && value[i + 1].isDigit())
        val start = i
        if (isNum) {
            while (i < value.length && (value[i].isDigit() || value[i] in ",.:+−-")) i++
            withStyle(number.toSpanStyle()) { append(value.substring(start, i)) }
        } else {
            while (i < value.length && !value[i].isDigit() && !(value[i] in "+−-" && i + 1 < value.length && value[i + 1].isDigit())) i++
            withStyle(unit) { append(value.substring(start, i)) }
        }
    }
}

/**
 * Bouton v8 : « primary » vert plein, sinon contour gris sur noir.
 * Thème social : principal en aplat vert GLIDY (texte foncé), secondaire gris clair plein, coins 12 dp.
 */
@Composable
fun GcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    fontSize: Float = 12f,
) {
    val c = Gc.colors
    val social = Gc.social
    // V18.1 : principal = bleu ciel plein ; secondaire = teinté (texte bleu sur bleu pâle), comme iOS
    val container = when { primary && social -> c.accentFill; primary -> c.ok; social -> c.route.copy(alpha = 0.12f); else -> c.panel }
    val content = when { primary && social -> c.onAccentFill; primary -> c.onAccent; social -> c.route; else -> c.ink }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (social) 44.dp else 40.dp),
        shape = RoundedCornerShape(if (social) 12.dp else 10.dp),
        contentPadding = PaddingValues(horizontal = if (social) 16.dp else 12.dp, vertical = 8.dp),
        border = if (primary || social) null else BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = c.control,
            disabledContentColor = c.faint,
        ),
    ) {
        Text(
            if (social) text.socialCase() else text,
            style = if (social) Gc.type.headline.copy(fontSize = 16.sp, color = if (!enabled) c.faint else content)
            else Gc.type.body.copy(fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = content),
        )
    }
}

@Composable
fun GcSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Gc.colors
    val on = if (Gc.social) c.mint else c.ok
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = if (Gc.social) c.panel else c.onAccent,
            checkedTrackColor = on,
            checkedBorderColor = on,
            uncheckedThumbColor = c.dim,
            uncheckedTrackColor = c.control,
            uncheckedBorderColor = c.line,
        ),
    )
}

/**
 * S15 — interrupteur clair/sombre intégré à l'en-tête de chaque écran (Prévol, Check-lists, Carte, Mes vols),
 * au lieu d'une pastille flottante qui recouvrait le contenu. Fourni par l'app via [LocalGcThemeToggle] ;
 * absent (null) → rien n'est affiché (tests, aperçus, Pilotage).
 */
@Immutable
data class GcThemeToggle(val light: Boolean, val onToggle: (Boolean) -> Unit)

val LocalGcThemeToggle = staticCompositionLocalOf<GcThemeToggle?> { null }

@Composable
fun GcThemeToggleButton(modifier: Modifier = Modifier) {
    val toggle = LocalGcThemeToggle.current ?: return
    val c = Gc.colors
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(c.control)
            .clickable(role = Role.Switch, onClickLabel = if (toggle.light) "Mode sombre" else "Mode clair") { toggle.onToggle(!toggle.light) }
            .semantics { contentDescription = "Mode clair"; stateDescription = if (toggle.light) "activé" else "désactivé" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (toggle.light) GcIcons.DarkMode else GcIcons.LightMode, contentDescription = null, tint = c.ink, modifier = Modifier.size(17.dp))
    }
}
