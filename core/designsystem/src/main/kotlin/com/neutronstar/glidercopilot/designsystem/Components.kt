package com.neutronstar.glidercopilot.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Gc.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.panel, RoundedCornerShape(14.dp))
            .border(1.dp, accent ?: c.line, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null || trailing != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (title != null) Text(title.uppercase(), style = Gc.type.eyebrow)
                trailing?.invoke()
            }
        }
        content()
    }
}

@Composable
fun GcPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Gc.type.eyebrow.copy(color = color, letterSpacing = Gc.type.eyebrow.letterSpacing),
        modifier = modifier
            .background(Gc.colors.chip, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
fun GcKpi(value: String, label: String, modifier: Modifier = Modifier, color: Color = Gc.colors.ink) {
    Column(modifier) {
        Text(value, style = Gc.type.kpi.copy(color = color))
        Text(label.uppercase(), style = Gc.type.eyebrow.copy(fontSize = Gc.type.eyebrow.fontSize))
    }
}
