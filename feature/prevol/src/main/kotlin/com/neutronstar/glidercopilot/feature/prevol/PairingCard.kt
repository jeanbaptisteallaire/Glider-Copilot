package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.GcSwitch

/** Carte « Planeur du jour · FLARM » de la maquette v8 : appairage par immatriculation et détection de décollage. */
@Composable
internal fun PairingCard(
    ui: PairingUi,
    onInput: (String) -> Unit,
    onValidate: () -> Unit,
    onCancel: () -> Unit,
    onPickRecent: (String) -> Unit,
    onAutoTakeoff: (Boolean) -> Unit,
    onFollow: (Boolean) -> Unit = {},
    onFollowInput: (String) -> Unit = {},
    onFollowValidate: () -> Unit = {},
    onFollowPick: (String) -> Unit = {},
) {
    val c = Gc.colors
    val pill: Pair<String, androidx.compose.ui.graphics.Color> = when {
        ui.paired == null -> "Non appairé" to c.dim
        ui.status is PairingStatus.NotFound || ui.status is PairingStatus.NotTracked -> ui.paired to c.warn
        else -> ui.paired to c.ok
    }
    GcCard(title = "Planeur du jour · FLARM", trailing = { GcPill(pill.first, pill.second) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = ui.input,
                onValueChange = onInput,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = c.ink),
                cursorBrush = SolidColor(c.ok),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onValidate() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Immatriculation du planeur" },
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            .border(1.dp, c.line, RoundedCornerShape(9.dp))
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (ui.input.isEmpty()) Text("Immatriculation, ex. F-CJAB", style = TextStyle(fontSize = 13.sp, color = c.faint), maxLines = 1)
                        inner()
                    }
                },
            )
            GcButton("Valider", onValidate, primary = true)
            GcButton("Annuler", onCancel)
        }
        ui.message?.let { Text(it, style = TextStyle(fontSize = 10.5.sp, color = c.warn)) }
        val (dot, text) = statusLine(ui)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.size(if (dot == DotState.OK) 13.dp else 7.dp)
                    .background(if (dot == DotState.OK) c.ok.copy(alpha = 0.11f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(7.dp).background(when (dot) { DotState.OK -> c.ok; DotState.WARN -> c.warn; DotState.IDLE -> c.faint }, CircleShape))
            }
            Text(text, style = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, color = c.dim))
        }
        val others = ui.recent.filter { it != ui.paired }
        if (others.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Récents", style = TextStyle(fontSize = 10.5.sp, color = c.dim))
                others.forEach { r ->
                    Text(
                        r,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = c.ink),
                        modifier = Modifier
                            .border(1.dp, c.line, RoundedCornerShape(50))
                            .clickable(role = Role.Button, onClickLabel = "Appairer $r") { onPickRecent(r) }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Détec. auto. décollage", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink))
                Text("Chrono automatique au-dessus de 50 km/h", style = TextStyle(fontSize = 10.5.sp, color = c.dim))
            }
            GcSwitch(ui.autoTakeoff, onAutoTakeoff, Modifier.semantics { contentDescription = "Détection automatique du décollage" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Suivi & debug", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (ui.followOn) c.warn else c.ink))
                Text("Sécurité et trajectoire calculées sur un planeur du club déjà en vol (FLARM via OGN)", style = TextStyle(fontSize = 10.5.sp, color = c.dim))
            }
            GcSwitch(ui.followOn, onFollow, Modifier.semantics { contentDescription = "Suivi et debug d'un planeur en vol" })
        }
        if (ui.followOn) FollowBlock(ui, onFollowInput, onFollowValidate, onFollowPick)
    }
}

/** Planeurs du club LFNL souvent en vol, proposés en raccourci pour le suivi. */
private val FOLLOW_SUGGESTIONS = listOf("F-CGXB", "F-CEIQ")

@Composable
private fun FollowBlock(ui: PairingUi, onInput: (String) -> Unit, onValidate: () -> Unit, onPick: (String) -> Unit) {
    val c = Gc.colors
    Column(
        Modifier.fillMaxWidth().border(1.dp, c.warn.copy(alpha = 0.33f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = ui.followInput,
                onValueChange = onInput,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = c.ink),
                cursorBrush = SolidColor(c.warn),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onValidate() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Immatriculation du planeur suivi" },
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).border(1.dp, c.line, RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (ui.followInput.isEmpty()) Text("Planeur en vol, ex. F-CGXB", style = TextStyle(fontSize = 13.sp, color = c.faint), maxLines = 1)
                        inner()
                    }
                },
            )
            GcButton("Suivre", onValidate)
        }
        ui.followMessage?.let { Text(it, style = TextStyle(fontSize = 10.5.sp, color = c.warn)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Club", style = TextStyle(fontSize = 10.5.sp, color = c.dim))
            FOLLOW_SUGGESTIONS.forEach { r ->
                Text(
                    r,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = if (r == ui.followReg) c.warn else c.ink),
                    modifier = Modifier.border(1.dp, if (r == ui.followReg) c.warn else c.line, RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClickLabel = "Suivre $r") { onPick(r) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        val status = when (val s = ui.followStatus) {
            PairingStatus.None -> "Choisissez le planeur à suivre."
            PairingStatus.Checking -> "Recherche de ${ui.followReg} dans la base OGN…"
            is PairingStatus.Paired -> "${ui.followReg} · ${s.device} · " + (ui.followLine ?: "pas encore reçu : en attente d'une trame OGN")
            is PairingStatus.NotFound -> "${ui.followReg} absent de la base OGN : suivi impossible."
            PairingStatus.NotTracked -> "Le propriétaire de ${ui.followReg} refuse le suivi OGN : choix respecté."
            PairingStatus.Unverified -> "Base OGN injoignable : nouvel essai au prochain réseau."
        }
        Text(status, style = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, color = if (ui.followLine != null) c.ok else c.dim))
        Text(
            "Pilotage affiche alors ce planeur à la place du téléphone (étiquette SUIVI). Données OGN en retard de quelques secondes : outil de mise au point, pas d'aide au vol.",
            style = TextStyle(fontSize = 9.5.sp, lineHeight = 13.sp, color = c.faint),
        )
    }
}

private enum class DotState { OK, WARN, IDLE }

private fun statusLine(ui: PairingUi): Pair<DotState, String> = when (val s = ui.status) {
    PairingStatus.None -> DotState.IDLE to "Saisissez l’immatriculation pour appairer le FLARM."
    PairingStatus.Checking -> DotState.IDLE to "Recherche de ${ui.paired} dans la base OGN…"
    is PairingStatus.Paired -> DotState.OK to "FLARM appairé · ${s.device} · ${s.source}."
    is PairingStatus.NotFound -> DotState.WARN to "Immatriculation absente de la base OGN (${s.source}) : le trafic ne pourra pas reconnaître ce planeur."
    PairingStatus.NotTracked -> DotState.WARN to "Le propriétaire refuse le suivi OGN de ce boîtier : pas d’appairage, choix respecté."
    PairingStatus.Unverified -> DotState.IDLE to "Immatriculation enregistrée · base OGN injoignable, vérification au prochain réseau."
}
