package com.neutronstar.glidercopilot.designsystem

import androidx.compose.foundation.BorderStroke
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
 * Thème social (S15) : filet très léger, titre en casse normale, gras, comme une section de réseau social.
 */
@Composable
fun GcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Gc.colors
    val social = Gc.social
    Column(modifier.fillMaxWidth().background(c.panel)) {
        HorizontalDivider(thickness = 1.dp, color = accent ?: if (social) c.lineFaint else c.lineSoft)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = if (social) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (social) 10.dp else 9.dp),
        ) {
            if (title != null || trailing != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    if (title != null) Text(
                        if (social) title.socialCase() else title.uppercase(),
                        style = if (social) Gc.type.body.copy(color = c.cardTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.2).sp)
                        else Gc.type.eyebrow.copy(color = c.cardTitle, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.4.sp),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
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
            style = Gc.type.eyebrow.copy(color = if (neutral) c.dim else color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.1.sp),
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

@Composable
fun GcKpi(value: String, label: String, modifier: Modifier = Modifier, color: Color = Gc.colors.ink) {
    Column(modifier) {
        Text(value, style = Gc.type.kpi.copy(color = color))
        if (Gc.social) Text(label.socialCase(), style = Gc.type.eyebrow.copy(fontSize = 12.sp))
        else Text(label.uppercase(), style = Gc.type.eyebrow.copy(fontSize = 9.sp, letterSpacing = 0.6.sp))
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
    val container = when { primary && social -> c.accentFill; primary -> c.ok; social -> c.control; else -> c.panel }
    val content = when { primary && social -> c.onAccentFill; primary -> c.onAccent; else -> c.ink }
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
            style = Gc.type.body.copy(fontSize = (if (social) fontSize + 2f else fontSize).sp, fontWeight = if (social) FontWeight.SemiBold else FontWeight.Bold, color = if (social && !enabled) c.faint else content),
        )
    }
}

@Composable
fun GcSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Gc.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.ok,
            checkedBorderColor = c.ok,
            uncheckedThumbColor = c.dim,
            uncheckedTrackColor = c.control,
            uncheckedBorderColor = c.line,
        ),
    )
}
