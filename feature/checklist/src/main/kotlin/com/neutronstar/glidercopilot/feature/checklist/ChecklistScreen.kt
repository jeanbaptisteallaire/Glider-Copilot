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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcThemeToggleButton
import com.neutronstar.glidercopilot.designsystem.gcHeading
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
 * Page Check-lists. V18.1 « New UI » : Inter et échelle typographique d'Apple dans les deux thèmes ; pages
 * blanches façon iOS (Large Title, fond groupé gris, chaque check-list dans une carte blanche arrondie, bleu ciel
 * pour les cases et la sélection — jeton [route], vert GLIDY en thème sombre). Le contenu vient de [Checklists].
 */
@Composable
fun ChecklistScreen(store: ChecklistStore, modifier: Modifier = Modifier) {
    val c = Gc.colors
    val social = Gc.social
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val checked by store.checked.collectAsState(initial = emptySet())
    var brief by remember { mutableStateOf<CableBriefInput?>(null) }
    LaunchedEffect(store) { if (brief == null) brief = store.brief.first() }
    val plan = CablePlan.from(brief ?: CableBriefInput())
    val open = remember { mutableStateMapOf<String, Boolean>().apply { Checklists.all.forEach { put(it.id, it.openByDefault) } } }

    Column(
        modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
    ) {
        // En-tête de page : Large Title + bascule clair/sombre (les codes CRIS, TVBCR… figurent déjà sur les cartes)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(gcHeading(Checklists.PAGE_TITLE), style = if (social) Gc.type.largeTitle else Gc.type.title, modifier = Modifier.weight(1f))
            if (!social) {
                Text(Checklists.PAGE_TAG, style = Gc.type.caption2.copy(color = c.dim, textAlign = TextAlign.End))
            }
            GcThemeToggleButton(Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.height(12.dp))
        // Encadré d'introduction (filet orange : rappel de priorité du manuel de vol)
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(if (social) 14.dp else 0.dp)).background(if (social) c.panel else c.sunken),
        ) {
            Box(Modifier.width(if (social) 4.dp else 3.dp).fillMaxHeight().background(c.warn))
            Text(
                Checklists.INTRO,
                style = Gc.type.subhead.copy(color = c.dim),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                Checklists.RESET_NOTE,
                style = Gc.type.footnote.copy(color = c.faint),
                modifier = Modifier.weight(1f),
            )
            Text(
                Checklists.RESET_BUTTON,
                style = Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold, color = if (social) c.route else c.ink),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (social) Modifier.background(c.route.copy(alpha = 0.12f)) else Modifier.border(1.dp, c.line, RoundedCornerShape(10.dp)))
                    .clickable(role = Role.Button) {
                        scope.launch { store.clearChecks() }
                        Toast.makeText(context, Checklists.RESET_TOAST, Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(if (social) 4.dp else 10.dp))

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
            if (!social && index == Checklists.all.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.lineSoft)
        }

        Text(
            Checklists.SOURCE,
            style = Gc.type.caption1.copy(color = c.faint),
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = if (social) 4.dp else 0.dp, end = if (social) 4.dp else 0.dp),
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
    val social = Gc.social
    val progress = list.progress(checked)
    // thème social : carte blanche arrondie sur fond groupé ; thème sombre : liste à filets de la maquette v8
    val cardModifier = if (social) {
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel).padding(horizontal = 12.dp)
    } else {
        Modifier.fillMaxWidth().background(c.background)
    }
    Column(cardModifier) {
        if (!social) HorizontalDivider(thickness = 1.dp, color = c.lineSoft)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(onClickLabel = if (expanded) "Replier" else "Déplier", onClick = onToggleOpen)
                .padding(horizontal = 4.dp, vertical = 11.dp)
                .semantics { stateDescription = if (expanded) "déplié" else "replié" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(width = 58.dp, height = 44.dp)
                    .background(if (social) c.route.copy(alpha = 0.12f) else c.control, RoundedCornerShape(12.dp))
                    .then(if (expanded && !social) Modifier.border(1.dp, c.route.copy(alpha = 0.33f), RoundedCornerShape(12.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    list.code,
                    style = Gc.type.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.04.em, color = c.route),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(list.title, style = Gc.type.headline)
                Text(list.subtitle, style = Gc.type.footnote.copy(color = c.dim))
            }
            val done = if (social) c.mint else c.ok
            Text(
                progress.toString(),
                style = Gc.type.caption1.copy(
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontFeatureSettings = "tnum",
                    color = if (progress.complete) (if (social) c.onAccentFill else c.onAccent) else c.dim,
                ),
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .background(if (progress.complete) done else c.control, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 16.dp)) {
                list.blocks.forEach { block ->
                    when (block) {
                        is Block.Subtitle -> Text(
                            block.text.uppercase(),
                            style = Gc.type.footnote.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em, color = if (social) c.dim else c.route),
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
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
        HorizontalDivider(thickness = if (Gc.social) 0.5.dp else 1.dp, color = if (Gc.social) c.lineSoft else c.lineFaint)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .clickable(role = Role.Checkbox) { onChange(!isChecked) }
                .semantics { contentDescription = listOfNotNull(item.bold, text).joinToString(" "); stateDescription = if (isChecked) "coché" else "non coché" }
                .padding(horizontal = 2.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CheckBoxMark(isChecked, Modifier.padding(top = 1.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        item.bold?.let { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(it) }; append(" ") }
                        append(text)
                    },
                    style = Gc.type.subhead.copy(color = color, textDecoration = deco),
                )
                item.small?.let {
                    Text(
                        it,
                        style = Gc.type.footnote.copy(color = c.dim, textDecoration = deco),
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
    // case cochée : aplat bleu ciel (vert GLIDY en sombre), coche blanche ; vide : contour gris
    val shape = RoundedCornerShape(if (Gc.social) 6.dp else 4.dp)
    val tick = if (Gc.social) c.onAccentFill else c.onAccent
    Box(
        modifier.size(22.dp).then(
            if (on) Modifier.background(if (Gc.social) c.accentFill else c.ok, shape)
            else Modifier.border(1.5.dp, if (Gc.social) c.faint else c.dim, shape),
        ),
    ) {
        if (on) Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val stroke = 2.4.dp.toPx()
            drawLine(tick, Offset(w * 0.24f, w * 0.52f), Offset(w * 0.43f, w * 0.70f), stroke, StrokeCap.Round)
            drawLine(tick, Offset(w * 0.43f, w * 0.70f), Offset(w * 0.77f, w * 0.31f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun CableBriefBlock(brief: CableBriefInput, plan: CablePlan, onBrief: (CableBriefInput) -> Unit) {
    val c = Gc.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BriefField("Piste / QFU", brief.qfu, { onBrief(brief.copy(qfu = it)) }, Modifier.weight(1f), numeric = true)
            BriefField("Demi-tour dès", brief.turn, { onBrief(brief.copy(turn = it)) }, Modifier.weight(1f), numeric = true, unit = "m sol")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BriefField("Posé devant sous", brief.ahead, { onBrief(brief.copy(ahead = it)) }, Modifier.weight(1f), numeric = true, unit = "m sol")
            BriefField("Dégagement", brief.field, { onBrief(brief.copy(field = it)) }, Modifier.weight(1f))
        }
        BriefField("Menace", brief.threat, { onBrief(brief.copy(threat = it)) }, Modifier.fillMaxWidth())
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(c.sunkenPlan, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        plan.rows.forEach { row ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.warn)) { append(row.lead) }
                    append(" ")
                    append(row.text)
                },
                style = Gc.type.footnote.copy(color = c.planText, lineHeight = 20.sp),
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
        modifier.border(1.dp, if (focused) c.route else c.lineSoft, RoundedCornerShape(10.dp)).background(c.sunkenField, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = Gc.type.caption1.copy(color = c.dim), maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            interactionSource = interaction,
            textStyle = Gc.type.callout.copy(fontWeight = FontWeight.Medium, color = c.ink, fontFeatureSettings = "tnum"),
            cursorBrush = SolidColor(c.route),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = 3.dp)) { inner() }
                    HorizontalDivider(thickness = 1.dp, color = if (focused) c.route else c.inputLine)
                }
            },
        )
        unit?.let { Text(it, style = Gc.type.caption1.copy(color = c.dim)) }
    }
}
