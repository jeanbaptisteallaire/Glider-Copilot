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
    /** V18.6 : édition Lite → pas de « Suivi & debug » (outil de mise au point). */
    showFollow: Boolean = true,
) {
    val c = Gc.colors
    val social = Gc.social
    val pill: Pair<String, androidx.compose.ui.graphics.Color> = when {
        ui.paired == null -> "Non appairé" to c.dim
        ui.status is PairingStatus.NotFound || ui.status is PairingStatus.NotTracked -> ui.paired to c.warn
        else -> ui.paired to c.ok
    }
    GcCard(title = "Planeur du jour · FLARM", tint = c.mint, trailing = { GcPill(pill.first, pill.second) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = ui.input,
                onValueChange = onInput,
                singleLine = true,
                textStyle = cardStyle(TextRole.Strong, 14.sp, c.ink, FontWeight.Bold).copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                cursorBrush = SolidColor(if (social) c.route else c.ok),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onValidate() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Immatriculation du planeur" },
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = if (social) 44.dp else 40.dp)
                            .then(fieldFrame(social))
                            .padding(horizontal = if (social) 12.dp else 9.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (ui.input.isEmpty()) Text("Immatriculation, ex. F-CJAB", style = cardStyle(TextRole.Body, 13.sp, c.faint), maxLines = 1)
                        inner()
                    }
                },
            )
            GcButton("Valider", onValidate, primary = true)
            GcButton("Annuler", onCancel)
        }
        ui.message?.let { Text(it, style = cardStyle(TextRole.Secondary, 10.5.sp, c.warn)) }
        val (dot, text) = statusLine(ui)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.size(if (dot == DotState.OK) 13.dp else 7.dp)
                    .background(if (dot == DotState.OK) c.ok.copy(alpha = 0.11f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(7.dp).background(when (dot) { DotState.OK -> c.ok; DotState.WARN -> c.warn; DotState.IDLE -> c.faint }, CircleShape))
            }
            Text(text, style = cardStyle(TextRole.Secondary, 10.5.sp, c.dim, darkLineHeight = 14.sp))
        }
        val others = ui.recent.filter { it != ui.paired }
        if (others.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Récents", style = cardStyle(TextRole.Secondary, 10.5.sp, c.dim))
                others.forEach { r ->
                    Text(
                        r,
                        style = cardStyle(TextRole.Secondary, 11.sp, c.ink, FontWeight.Bold).copy(letterSpacing = 0.6.sp),
                        modifier = Modifier
                            .then(chipFrame(social, on = false))
                            .clickable(role = Role.Button, onClickLabel = "Appairer $r") { onPickRecent(r) }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Détec. auto. décollage", style = cardStyle(TextRole.Strong, 13.sp, c.ink, FontWeight.Bold))
                Text("Chrono auto dès 50 km/h", style = cardStyle(TextRole.Secondary, 10.5.sp, c.dim))
            }
            GcSwitch(ui.autoTakeoff, onAutoTakeoff, Modifier.semantics { contentDescription = "Détection automatique du décollage" })
        }
        if (showFollow) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Suivi & debug", style = cardStyle(TextRole.Strong, 13.sp, if (ui.followOn) c.warn else c.ink, FontWeight.Bold))
                Text("Calculs sur un planeur du club en vol (FLARM via OGN)", style = cardStyle(TextRole.Secondary, 10.5.sp, c.dim))
            }
            GcSwitch(ui.followOn, onFollow, Modifier.semantics { contentDescription = "Suivi et debug d'un planeur en vol" })
        }
        if (showFollow && ui.followOn) FollowBlock(ui, onFollowInput, onFollowValidate, onFollowPick)
    }
}

/** Planeurs du club LFNL souvent en vol, proposés en raccourci pour le suivi. */
private val FOLLOW_SUGGESTIONS = listOf("F-CGXB", "F-CEIQ")

@Composable
private fun FollowBlock(ui: PairingUi, onInput: (String) -> Unit, onValidate: () -> Unit, onPick: (String) -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (social) Modifier.background(c.warn.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, c.warn.copy(alpha = 0.33f), RoundedCornerShape(10.dp)),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = ui.followInput,
                onValueChange = onInput,
                singleLine = true,
                textStyle = cardStyle(TextRole.Strong, 14.sp, c.ink, FontWeight.Bold).copy(fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp),
                cursorBrush = SolidColor(c.warn),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onValidate() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Immatriculation du planeur suivi" },
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = if (social) 44.dp else 40.dp).then(fieldFrame(social))
                            .padding(horizontal = if (social) 12.dp else 9.dp, vertical = 7.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (ui.followInput.isEmpty()) Text("Planeur en vol, ex. F-CGXB", style = cardStyle(TextRole.Body, 13.sp, c.faint), maxLines = 1)
                        inner()
                    }
                },
            )
            GcButton("Suivre", onValidate)
        }
        ui.followMessage?.let { Text(it, style = cardStyle(TextRole.Secondary, 10.5.sp, c.warn)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Club", style = cardStyle(TextRole.Secondary, 10.5.sp, c.dim))
            FOLLOW_SUGGESTIONS.forEach { r ->
                Text(
                    r,
                    style = cardStyle(TextRole.Secondary, 11.sp, if (r == ui.followReg) c.warn else c.ink, FontWeight.Bold).copy(letterSpacing = 0.6.sp),
                    modifier = Modifier.then(
                        if (social) chipFrame(true, on = r == ui.followReg)
                        else Modifier.border(1.dp, if (r == ui.followReg) c.warn else c.line, RoundedCornerShape(50)),
                    )
                        .clickable(role = Role.Button, onClickLabel = "Suivre $r") { onPick(r) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        val status = when (val s = ui.followStatus) {
            PairingStatus.None -> "Choisissez un planeur."
            PairingStatus.Checking -> "Recherche de ${ui.followReg} dans la base OGN…"
            is PairingStatus.Paired -> "${ui.followReg} · ${s.device} · " + (ui.followLine ?: "en attente d'une trame OGN")
            is PairingStatus.NotFound -> "${ui.followReg} absent de la base OGN : suivi impossible."
            PairingStatus.NotTracked -> "Le propriétaire de ${ui.followReg} refuse le suivi OGN : choix respecté."
            PairingStatus.Unverified -> "Base OGN injoignable : nouvel essai au prochain réseau."
        }
        Text(status, style = cardStyle(TextRole.Secondary, 10.5.sp, if (ui.followLine != null) c.ok else c.dim, darkLineHeight = 14.sp))
        Text(
            "Pilotage affiche ce planeur au lieu du téléphone (étiquette SUIVI). OGN a quelques secondes de retard : outil de mise au point, pas d'aide au vol.",
            style = cardStyle(TextRole.Fine, 9.5.sp, c.faint, darkLineHeight = 13.sp),
        )
    }
}

private enum class DotState { OK, WARN, IDLE }

private fun statusLine(ui: PairingUi): Pair<DotState, String> = when (val s = ui.status) {
    PairingStatus.None -> DotState.IDLE to "Saisissez l’immatriculation du FLARM."
    PairingStatus.Checking -> DotState.IDLE to "Recherche de ${ui.paired} dans la base OGN…"
    is PairingStatus.Paired -> DotState.OK to "FLARM appairé · ${s.device} · ${s.source}."
    is PairingStatus.NotFound -> DotState.WARN to "Absente de la base OGN (${s.source}) : le trafic ne reconnaîtra pas ce planeur."
    PairingStatus.NotTracked -> DotState.WARN to "Suivi OGN refusé par le propriétaire : pas d’appairage, choix respecté."
    PairingStatus.Unverified -> DotState.IDLE to "Enregistrée · base OGN injoignable, vérification au prochain réseau."
}

/** V18.1 — cadre d'un champ de saisie : gris plein arrondi (iOS) sur pages blanches, filet gris en sombre. */
@Composable
private fun fieldFrame(social: Boolean): Modifier {
    val c = Gc.colors
    return if (social) Modifier.background(c.control, RoundedCornerShape(10.dp))
    else Modifier.border(1.dp, c.line, RoundedCornerShape(9.dp))
}

/** V18.1 — pastille d'immatriculation : aplat gris (orange pâle si choisie) sur pages blanches, filet en sombre. */
@Composable
private fun chipFrame(social: Boolean, on: Boolean): Modifier {
    val c = Gc.colors
    return if (social) Modifier.background(if (on) c.warn.copy(alpha = 0.14f) else c.control, RoundedCornerShape(50))
    else Modifier.border(1.dp, c.line, RoundedCornerShape(50))
}
