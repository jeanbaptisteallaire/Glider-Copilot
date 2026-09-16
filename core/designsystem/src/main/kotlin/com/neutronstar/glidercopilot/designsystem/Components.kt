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

/** Carte Prévol v8 : fond noir, simple filet supérieur, titre en capitales. */
@Composable
fun GcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Gc.colors
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

/** Pastille : texte coloré sur noir, bordure teintée. */
@Composable
fun GcPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val c = Gc.colors
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
        Text(label.uppercase(), style = Gc.type.eyebrow.copy(fontSize = 9.sp, letterSpacing = 0.6.sp))
    }
}

/** Bouton v8 : « primary » vert plein, sinon contour gris sur noir. */
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = if (primary) null else BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) c.ok else c.panel,
            contentColor = if (primary) c.onAccent else c.ink,
            disabledContainerColor = c.control,
            disabledContentColor = c.faint,
        ),
    ) { Text(text, style = Gc.type.body.copy(fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = if (primary) c.onAccent else c.ink)) }
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
