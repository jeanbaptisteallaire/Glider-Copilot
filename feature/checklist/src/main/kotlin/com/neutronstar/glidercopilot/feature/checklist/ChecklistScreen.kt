package com.neutronstar.glidercopilot.feature.checklist

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.domain.checklist.Block
import com.neutronstar.glidercopilot.domain.checklist.CableBriefInput
import com.neutronstar.glidercopilot.domain.checklist.CablePlan
import com.neutronstar.glidercopilot.domain.checklist.CheckItem
import com.neutronstar.glidercopilot.domain.checklist.Checklist
import com.neutronstar.glidercopilot.domain.checklist.Checklists
import com.neutronstar.glidercopilot.domain.checklist.progress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Persistance des cases et du briefing, fournie par l'app (locale aujourd'hui, compte demain). */
interface ChecklistStore {
    val checked: Flow<Set<String>>
    val brief: Flow<CableBriefInput>
    suspend fun setChecked(id: String, checked: Boolean)
    suspend fun clearChecks()
    suspend fun saveBrief(brief: CableBriefInput)
}

/**
 * Page Check-lists : copie de la maquette v8, toutes les polices agrandies de 30 %.
 * Les tailles CSS de la maquette sont conservées dans le code et multipliées par [SCALE].
 */
private const val SCALE = 1.3f
private fun fs(cssPx: Float): TextUnit = (cssPx * SCALE).sp

@Composable
fun ChecklistScreen(store: ChecklistStore, modifier: Modifier = Modifier) {
    val c = Gc.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val checked by store.checked.collectAsState(initial = emptySet())
    var brief by remember { mutableStateOf<CableBriefInput?>(null) }
    LaunchedEffect(store) { if (brief == null) brief = store.brief.first() }
    val plan = CablePlan.from(brief ?: CableBriefInput())
    val open = remember { mutableStateMapOf<String, Boolean>().apply { Checklists.all.forEach { put(it.id, it.openByDefault) } } }

    Column(
        modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
    ) {
        // En-tête de page
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                Checklists.PAGE_TITLE.uppercase(),
                style = TextStyle(fontSize = fs(30f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em, color = c.ink, lineHeight = fs(30f)),
            )
            Text(
                Checklists.PAGE_TAG,
                style = TextStyle(fontSize = fs(10f), color = c.dim, textAlign = TextAlign.End, lineHeight = fs(13f)),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Encadré d'introduction
        Row(Modifier.fillMaxWidth().padding(top = 2.dp).height(IntrinsicSize.Min).background(c.sunken)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.warn))
            Text(
                Checklists.INTRO,
                style = TextStyle(fontSize = fs(11f), lineHeight = fs(11f * 1.45f), color = c.dim),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                Checklists.RESET_NOTE,
                style = TextStyle(fontSize = fs(9f), lineHeight = fs(9f * 1.35f), color = c.faint),
                modifier = Modifier.weight(1f),
            )
            Text(
                Checklists.RESET_BUTTON,
                style = TextStyle(fontSize = fs(12f), fontWeight = FontWeight.Bold, color = c.ink),
                modifier = Modifier
                    .border(1.dp, c.line, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) {
                        scope.launch { store.clearChecks() }
                        Toast.makeText(context, Checklists.RESET_TOAST, Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        Checklists.all.forEachIndexed { index, list ->
            ChecklistCard(
                list = list,
                checked = checked,
                plan = plan,
                brief = brief,
                expanded = open[list.id] == true,
                onToggleOpen = { open[list.id] = open[list.id] != true },
                onCheck = { id, on -> scope.launch { store.setChecked(id, on) } },
                onBrief = { b -> brief = b; scope.launch { store.saveBrief(b) } },
            )
            if (index == Checklists.all.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.lineSoft)
        }

        Text(
            Checklists.SOURCE,
            style = TextStyle(fontSize = fs(9f), lineHeight = fs(9f * 1.45f), color = c.faint),
            modifier = Modifier.padding(top = 15.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ChecklistCard(
    list: Checklist,
    checked: Set<String>,
    plan: CablePlan,
    brief: CableBriefInput?,
    expanded: Boolean,
    onToggleOpen: () -> Unit,
    onCheck: (String, Boolean) -> Unit,
    onBrief: (CableBriefInput) -> Unit,
) {
    val c = Gc.colors
    val progress = list.progress(checked)
    Column(Modifier.fillMaxWidth().background(c.background)) {
        HorizontalDivider(thickness = 1.dp, color = c.lineSoft)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(onClickLabel = if (expanded) "Replier" else "Déplier", onClick = onToggleOpen)
                .padding(horizontal = 4.dp, vertical = 11.dp)
                .semantics { stateDescription = if (expanded) "déplié" else "replié" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(width = 60.dp, height = 47.dp)
                    .background(c.control, RoundedCornerShape(13.dp))
                    .then(if (expanded) Modifier.border(1.dp, c.ok.copy(alpha = 0.33f), RoundedCornerShape(13.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(list.code, style = TextStyle(fontSize = fs(13f), fontWeight = FontWeight.Bold, letterSpacing = 0.04.em, color = c.ok), maxLines = 1, softWrap = false)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(list.title, style = TextStyle(fontSize = fs(13f), fontWeight = FontWeight.Bold, color = c.ink))
                Text(list.subtitle, style = TextStyle(fontSize = fs(10f), lineHeight = fs(13f), color = c.dim))
            }
            Text(
                progress.toString(),
                style = TextStyle(
                    fontSize = fs(9f), textAlign = TextAlign.Center, fontFeatureSettings = "tnum",
                    color = if (progress.complete) c.onAccent else c.dim,
                ),
                modifier = Modifier
                    .widthIn(min = 57.dp)
                    .background(if (progress.complete) c.ok else c.control, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            )
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 16.dp)) {
                list.blocks.forEach { block ->
                    when (block) {
                        is Block.Subtitle -> Text(
                            block.text.uppercase(),
                            style = TextStyle(fontSize = fs(9f), fontWeight = FontWeight.Bold, letterSpacing = 0.12.em, color = c.ok),
                            modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
                        )
                        is Block.Items -> block.items.forEach { item ->
                            CheckRow(item, plan, item.id in checked) { onCheck(item.id, it) }
                        }
                        Block.CableBrief -> if (brief != null) CableBriefBlock(brief, plan, onBrief)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, plan: CablePlan, isChecked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Gc.colors
    val text = item.dynamic?.let(plan::textFor) ?: item.text
    val color = if (isChecked) c.dim else c.inkSoft
    val deco = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.lineFaint)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp)
                .clickable(role = Role.Checkbox) { onChange(!isChecked) }
                .semantics { contentDescription = listOfNotNull(item.bold, text).joinToString(" "); stateDescription = if (isChecked) "coché" else "non coché" }
                .padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CheckBoxMark(isChecked, Modifier.padding(top = 1.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        item.bold?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it) }; append(" ") }
                        append(text)
                    },
                    style = TextStyle(fontSize = fs(11.5f), lineHeight = fs(11.5f * 1.35f), color = color, textDecoration = deco),
                )
                item.small?.let {
                    Text(
                        it,
                        style = TextStyle(fontSize = fs(9.5f), lineHeight = fs(9.5f * 1.35f), color = c.dim, textDecoration = deco),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckBoxMark(on: Boolean, modifier: Modifier = Modifier) {
    val c = Gc.colors
    Box(
        modifier.size(22.dp).then(
            if (on) Modifier.background(c.ok, RoundedCornerShape(4.dp))
            else Modifier.border(1.5.dp, c.dim, RoundedCornerShape(4.dp)),
        ),
    ) {
        if (on) Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val stroke = 2.4.dp.toPx()
            drawLine(c.onAccent, Offset(w * 0.24f, w * 0.52f), Offset(w * 0.43f, w * 0.70f), stroke, StrokeCap.Round)
            drawLine(c.onAccent, Offset(w * 0.43f, w * 0.70f), Offset(w * 0.77f, w * 0.31f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun CableBriefBlock(brief: CableBriefInput, plan: CablePlan, onBrief: (CableBriefInput) -> Unit) {
    val c = Gc.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BriefField("Piste / QFU", brief.qfu, { onBrief(brief.copy(qfu = it)) }, Modifier.weight(1f), numeric = true)
            BriefField("Demi-tour envisagé dès", brief.turn, { onBrief(brief.copy(turn = it)) }, Modifier.weight(1f), numeric = true, unit = "m sol")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BriefField("Atterrissage devant sous", brief.ahead, { onBrief(brief.copy(ahead = it)) }, Modifier.weight(1f), numeric = true, unit = "m sol")
            BriefField("Terrain de dégagement", brief.field, { onBrief(brief.copy(field = it)) }, Modifier.weight(1f))
        }
        BriefField("Menace principale", brief.threat, { onBrief(brief.copy(threat = it)) }, Modifier.fillMaxWidth())
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(c.sunkenPlan, RoundedCornerShape(10.dp)).padding(10.dp),
    ) {
        plan.rows.forEach { row ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.warn)) { append(row.lead) }
                    append(" ")
                    append(row.text)
                },
                style = TextStyle(fontSize = fs(10.5f), lineHeight = fs(10.5f * 1.55f), color = c.planText),
            )
        }
    }
}

@Composable
private fun BriefField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, numeric: Boolean = false, unit: String? = null) {
    val c = Gc.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier.border(1.dp, c.lineSoft, RoundedCornerShape(10.dp)).background(c.sunkenField, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = TextStyle(fontSize = fs(9f), color = c.dim))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            interactionSource = interaction,
            textStyle = TextStyle(fontSize = fs(12f), fontWeight = FontWeight.Medium, color = c.ink),
            cursorBrush = SolidColor(c.ok),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = 3.dp)) { inner() }
                    HorizontalDivider(thickness = 1.dp, color = if (focused) c.ok else c.inputLine)
                }
            },
        )
        unit?.let { Text(it, style = TextStyle(fontSize = fs(9f), color = c.dim)) }
    }
}
