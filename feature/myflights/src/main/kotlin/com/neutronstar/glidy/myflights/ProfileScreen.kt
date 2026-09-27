package com.neutronstar.glidy.myflights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.GcThemeToggleButton
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.social.ExperienceLevel
import com.neutronstar.glidy.social.PilotProfile
import com.neutronstar.glidy.social.Usernames
import com.neutronstar.glidy.social.totalFlightMinutes
import java.util.Locale

/*
 * S16 — Mes vols devient la page profil du pilote : en-tête (avatar, nom, pseudo, bio, club, niveau, heures),
 * bouton Modifier, menu du profil, puis la grille des vols, 3 par ligne, comme le futur fil (S17).
 * Chaque tuile : vignette claire vue de dessus, trace rouge, date, durée, distance, icône de partage.
 */

@Composable
internal fun ProfileScreen(
    profile: PilotProfile,
    flights: List<FlightCardUi>,
    notice: String?,
    account: AccountCardState?,
    accountActions: AccountActions?,
    onImport: () -> Unit,
    onLoadDemo: (() -> Unit)?,
    onFlightSelected: (FlightCardUi) -> Unit,
    onToggleShare: (FlightCardUi) -> Unit,
    onEditProfile: () -> Unit,
    onEditIdentity: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = Gc.colors
    var showAccount by rememberSaveable { mutableStateOf(false) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().testTag("flight-list"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(
                profile = profile,
                flights = flights,
                onEditProfile = onEditProfile,
                onImport = onImport,
                onShowAccount = { showAccount = !showAccount },
                onEditIdentity = onEditIdentity,
                onDeleteAccount = onDeleteAccount,
            )
        }
        if (showAccount && account != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { AccountCard(account, accountActions) }
        }
        if (notice != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(notice, style = Gc.type.bodySmall.copy(color = c.warn), modifier = Modifier.testTag("notice"))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Vols", style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), modifier = Modifier.weight(1f))
                val shared = flights.count { it.isPublic }
                Text(
                    if (shared > 0) "$shared partagé${if (shared > 1) "s" else ""} sur le fil" else "Rien de partagé",
                    style = Gc.type.bodySmall,
                )
            }
        }
        if (flights.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyGrid(onLoadDemo, onImport) }
        }
        items(flights, key = { it.id }) { flight ->
            FlightTile(flight, onClick = { onFlightSelected(flight) }, onToggleShare = { onToggleShare(flight) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Carnet local : les fichiers IGC restent sur ce téléphone. Un vol n'apparaît sur le fil que si vous " +
                    "touchez son icône de partage. Enregistreur non approuvé, sans valeur pour un badge.",
                style = Gc.type.bodySmall.copy(color = c.faint),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: PilotProfile,
    flights: List<FlightCardUi>,
    onEditProfile: () -> Unit,
    onImport: () -> Unit,
    onShowAccount: () -> Unit,
    onEditIdentity: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = Gc.colors
    val real = flights.filterNot { it.isExample }
    val carnetSeconds = real.sumOf { it.durationSeconds }
    val totalMinutes = totalFlightMinutes(profile, carnetSeconds)
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ligne du haut : pseudo (ou titre) + interrupteur de thème + menu du profil
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (profile.username.isNotBlank()) "@${profile.username}" else "Mon profil",
                style = Gc.type.title.copy(fontSize = 22.sp),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GcThemeToggleButton(Modifier.padding(start = 8.dp))
            Box {
                Box(
                    Modifier.padding(start = 8.dp).size(34.dp).clip(CircleShape).background(c.control)
                        .clickable(role = Role.Button, onClickLabel = "Menu du profil") { menu = true }
                        .testTag("profile-menu"),
                    contentAlignment = Alignment.Center,
                ) { Icon(GcIcons.More, contentDescription = "Menu du profil", tint = c.ink, modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.panel) {
                    MenuEntry("Voir le compte") { menu = false; onShowAccount() }
                    MenuEntry("Modifier nom et pseudo") { menu = false; onEditIdentity() }
                    MenuEntry("Modifier le profil") { menu = false; onEditProfile() }
                    MenuEntry("Supprimer le compte", danger = true) { menu = false; onDeleteAccount() }
                }
            }
        }
        // avatar + chiffres (codes des réseaux sociaux)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.initials, Modifier.size(78.dp))
            Spacer(Modifier.width(14.dp))
            Stat(real.size.toString(), "Vols", Modifier.weight(1f))
            Stat(formatHours(totalMinutes), "Heures", Modifier.weight(1f))
            Stat(formatKm(real.sumOf { it.distanceMeters ?: 0L }), "Km", Modifier.weight(1f))
        }
        // identité
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                profile.displayName.ifBlank { "Pilote GLIDY" },
                style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            )
            if (profile.bio.isNotBlank()) Text(profile.bio, style = Gc.type.body.copy(fontSize = 14.sp))
            val line = listOfNotNull(profile.club.takeIf(String::isNotBlank)).joinToString(" · ")
            if (line.isNotBlank()) Text(line, style = Gc.type.bodySmall)
            profile.experience?.let { GcPill(it.label, c.ok, Modifier.padding(top = 4.dp)) }
            if (profile.minutesBeforeApp > 0) {
                Text(
                    "Dont ${profile.minutesBeforeApp / 60} h déclarées avant GLIDY",
                    style = Gc.type.bodySmall.copy(color = c.faint, fontSize = 12.sp),
                )
            }
        }
        if (profile.isEmpty) {
            GcCard(title = "Crée ton profil pilote") {
                Text(
                    "Nom, pseudo, club et niveau : ton profil sera la vitrine de tes vols sur le fil GLIDY. " +
                        "Tout reste sur ce téléphone tant que tu ne partages rien.",
                    style = Gc.type.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GcButton(
                if (profile.isEmpty) "Créer mon profil" else "Modifier",
                onClick = onEditProfile,
                primary = profile.isEmpty,
                modifier = Modifier.weight(1f).testTag("edit-profile"),
                fontSize = 13f,
            )
            GcButton("Importer un IGC", onClick = onImport, modifier = Modifier.weight(1f).testTag("import-igc"), fontSize = 13f)
        }
    }
}

@Composable
private fun MenuEntry(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Gc.colors
    DropdownMenuItem(
        text = { Text(text, style = Gc.type.body.copy(color = if (danger) c.danger else c.ink)) },
        onClick = onClick,
    )
}

@Composable
private fun Avatar(initials: String, modifier: Modifier) {
    val c = Gc.colors
    Box(
        modifier.clip(CircleShape).background(c.accentFill).border(2.dp, c.panel, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = Gc.type.title.copy(fontSize = 26.sp, color = c.onAccentFill, letterSpacing = 0.sp))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Gc.type.kpi.copy(fontSize = 19.sp), maxLines = 1)
        Text(label, style = Gc.type.bodySmall.copy(fontSize = 12.sp))
    }
}

@Composable
private fun EmptyGrid(onLoadDemo: (() -> Unit)?, onImport: () -> Unit) {
    GcCard(title = "Carnet vide") {
        Text("Votre carnet est vide", style = Gc.type.body.copy(fontWeight = FontWeight.SemiBold))
        Text(
            "Vos vols enregistrés par GLIDY apparaîtront ici après l'atterrissage. Vous pouvez aussi importer une trace IGC.",
            style = Gc.type.bodySmall,
        )
        if (onLoadDemo != null) {
            GcButton("Charger le vol d'exemple", onClick = onLoadDemo, primary = true, modifier = Modifier.testTag("load-demo"))
        }
    }
}

/** Tuile de la grille : vignette carrée (fond clair, trace rouge), date, durée · distance, icône de partage. */
@Composable
private fun FlightTile(flight: FlightCardUi, onClick: () -> Unit, onToggleShare: () -> Unit) {
    val c = Gc.colors
    Column(
        Modifier
            .testTag("flight-card-${flight.id}")
            .semantics { contentDescription = "Vol du ${flight.date}, ${flight.duration}, ${flight.distance}" }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            TraceThumbnail(flight.route, Modifier.fillMaxSize())
            if (!flight.isExample && flight.localState == LocalFileState.AVAILABLE) {
                ShareBadge(flight.isPublic, onToggleShare, Modifier.align(Alignment.TopEnd).padding(5.dp))
            } else if (flight.isExample) {
                Text(
                    "Exemple",
                    style = Gc.type.bodySmall.copy(fontSize = 10.sp, color = c.warn, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
                        .background(c.panel.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(flight.shortDate, style = Gc.type.bodySmall.copy(color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp), maxLines = 1)
        Text(
            "${flight.duration} · ${flight.distance}",
            style = Gc.type.bodySmall.copy(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShareBadge(shared: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val c = Gc.colors
    Box(
        modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (shared) c.accentFill else c.panel.copy(alpha = 0.92f))
            .border(1.dp, if (shared) c.accentFill else c.line, CircleShape)
            .clickable(role = Role.Switch, onClickLabel = if (shared) "Retirer du fil" else "Partager sur le fil", onClick = onToggle)
            .semantics { contentDescription = "Partage sur le fil"; stateDescription = if (shared) "partagé" else "privé" }
            .testTag("share-toggle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (shared) GcIcons.Check else GcIcons.Share,
            contentDescription = null,
            tint = if (shared) c.onAccentFill else c.ink,
            modifier = Modifier.size(15.dp),
        )
    }
}

/**
 * Vignette « carte claire vue de dessus » dessinée sur le téléphone, sans réseau : fond papier, quadrillage
 * léger façon carte, trace rouge [trace], départ marqué. (Fond de carte réel OpenFreeMap : avec le fil, S17.)
 */
@Composable
internal fun TraceThumbnail(route: List<Pair<Float, Float>>, modifier: Modifier) {
    val c = Gc.colors
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(c.sunken)) {
        val step = size.minDimension / 6f
        var x = step
        while (x < size.width) { drawLine(c.lineFaint, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = step
        while (y < size.height) { drawLine(c.lineFaint, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        if (route.size > 1) {
            val path = Path()
            route.forEachIndexed { i, p ->
                val px = p.first * size.width
                val py = p.second * size.height
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            val w = (size.minDimension / 55f).coerceIn(2f, 5f)
            drawPath(path, c.trace, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val s = route.first()
            drawCircle(c.panel, radius = w * 1.9f, center = Offset(s.first * size.width, s.second * size.height))
            drawCircle(c.trace, radius = w * 1.2f, center = Offset(s.first * size.width, s.second * size.height))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Édition du profil
// ------------------------------------------------------------------------------------------------

/** Écran « Modifier le profil » (plein écran). [identityOnly] : seulement nom et pseudo (entrée du menu). */
@Composable
internal fun ProfileEditScreen(
    initial: PilotProfile,
    identityOnly: Boolean,
    onCancel: () -> Unit,
    onSave: (PilotProfile) -> Unit,
) {
    val c = Gc.colors
    BackHandler(onBack = onCancel)
    var name by rememberSaveable { mutableStateOf(initial.displayName) }
    var username by rememberSaveable { mutableStateOf(initial.username.ifBlank { if (initial.displayName.isNotBlank()) Usernames.suggestFrom(initial.displayName) else "" }) }
    var bio by rememberSaveable { mutableStateOf(initial.bio) }
    var club by rememberSaveable { mutableStateOf(initial.club) }
    var level by rememberSaveable { mutableStateOf(initial.experience?.name) }
    var hours by rememberSaveable { mutableStateOf(if (initial.minutesBeforeApp > 0) (initial.minutesBeforeApp / 60).toString() else "") }

    val candidate = initial.copy(
        displayName = name.trim(),
        username = username,
        bio = bio.trim(),
        club = club.trim(),
        experience = level?.let { runCatching { ExperienceLevel.valueOf(it) }.getOrNull() },
        minutesBeforeApp = (hours.toLongOrNull() ?: 0L) * 60,
    )
    val problems = candidate.validate()

    Column(Modifier.fillMaxSize().testTag("profile-edit")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Annuler", style = Gc.type.body.copy(color = c.ink)) }
            Text(
                if (identityOnly) "Nom et pseudo" else "Modifier le profil",
                style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSave(candidate) }, enabled = problems.isEmpty(), modifier = Modifier.testTag("save-profile")) {
                Text("OK", style = Gc.type.body.copy(color = if (problems.isEmpty()) c.ok else c.faint, fontWeight = FontWeight.Bold))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Avatar(candidate.initials, Modifier.size(84.dp))
                }
            }
            item { EditField(name, { name = it.take(PilotProfile.MAX_NAME) }, "Nom affiché", tag = "field-name") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EditField(username, { username = Usernames.normalize(it).take(Usernames.MAX) }, "Pseudo", prefix = "@", tag = "field-username")
                    Text(
                        Usernames.problem(username)
                            ?: "Unique sur GLIDY : il sera réservé à l'activation de ton compte en ligne.",
                        style = Gc.type.bodySmall.copy(fontSize = 12.sp, color = if (Usernames.problem(username) != null) c.warn else c.faint),
                    )
                }
            }
            if (!identityOnly) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EditField(bio, { bio = it.take(PilotProfile.MAX_BIO) }, "Bio", singleLine = false, tag = "field-bio")
                        Text("${bio.length} / ${PilotProfile.MAX_BIO}", style = Gc.type.bodySmall.copy(fontSize = 12.sp, color = c.faint))
                    }
                }
                item { EditField(club, { club = it.take(PilotProfile.MAX_CLUB) }, "Club de référence", tag = "field-club") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Niveau", style = Gc.type.body.copy(fontWeight = FontWeight.SemiBold))
                        ExperienceLevel.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { lv ->
                                    Chip(lv.label, selected = level == lv.name, Modifier.weight(1f)) {
                                        level = if (level == lv.name) null else lv.name
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EditField(hours, { hours = it.filter(Char::isDigit).take(6) }, "Heures de vol avant GLIDY", keyboard = KeyboardType.Number, tag = "field-hours")
                        Text(
                            "Ajoutées aux vols de ton carnet pour le total d'heures du profil.",
                            style = Gc.type.bodySmall.copy(fontSize = 12.sp, color = c.faint),
                        )
                    }
                }
            }
            if (problems.isNotEmpty() && (name.isNotBlank() || username.isNotBlank())) {
                item { Text(problems.first(), style = Gc.type.bodySmall.copy(color = c.warn)) }
            }
            item {
                GcButton(
                    "Enregistrer",
                    onClick = { onSave(candidate) },
                    primary = true,
                    enabled = problems.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14f,
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Gc.colors
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.accentFill else c.control)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Gc.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) c.onAccentFill else c.ink),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EditField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    prefix: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    tag: String,
) {
    val c = Gc.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        label = { Text(label, style = Gc.type.bodySmall) },
        prefix = prefix?.let { p -> { Text(p, style = Gc.type.body.copy(color = c.dim)) } },
        textStyle = Gc.type.body,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.ink, unfocusedBorderColor = c.inputLine, cursorColor = c.ink,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink,
            focusedLabelColor = c.dim, unfocusedLabelColor = c.dim,
        ),
    )
}

/** Suppression du compte : profil de ce téléphone (+ compte en ligne s'il existe). Les vols restent. */
@Composable
internal fun DeleteAccountDialog(hasOnlineAccount: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = Gc.colors
    AlertDialog(
        modifier = Modifier.testTag("delete-account"),
        onDismissRequest = onCancel,
        title = { Text("Supprimer le compte ?", style = Gc.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) },
        text = {
            Text(
                (if (hasOnlineAccount) "Ton compte en ligne, tes vols sauvegardés en ligne et ton profil seront effacés définitivement. "
                else "Ton profil pilote (nom, pseudo, bio, club, niveau) sera effacé de ce téléphone. ") +
                    "Les vols présents sur ce téléphone sont conservés.",
                style = Gc.type.body.copy(color = c.dim),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-delete-account")) {
                Text("Supprimer", style = Gc.type.body.copy(color = c.danger, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Annuler", style = Gc.type.body.copy(color = c.ink, fontWeight = FontWeight.Bold)) }
        },
        containerColor = c.panel,
        shape = RoundedCornerShape(16.dp),
    )
}

/** Total d'heures du profil : « 152 » au-delà de 10 h, sinon « 1 h 53 » (ou « 3 h »). */
private fun formatHours(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 10 -> h.toString()
        m == 0L -> "$h h"
        else -> "%d h %02d".format(Locale.FRANCE, h, m)
    }
}

private fun formatKm(meters: Long): String = when {
    meters >= 10_000_000 -> "%.0fk".format(Locale.FRANCE, meters / 1_000_000.0)
    else -> "%.0f".format(Locale.FRANCE, meters / 1000.0)
}
