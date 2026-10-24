package com.neutronstar.glidy.myflights

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.shadow
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
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
import com.neutronstar.glidercopilot.designsystem.GcLegalLinks
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.GcSectionTitle
import com.neutronstar.glidercopilot.designsystem.GcThemeToggleButton
import com.neutronstar.glidercopilot.designsystem.GcTraceThumbnail
import com.neutronstar.glidercopilot.designsystem.metricText
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.social.ExperienceLevel
import com.neutronstar.glidy.social.PilotProfile
import com.neutronstar.glidy.social.Usernames
import com.neutronstar.glidy.social.totalFlightMinutes
import java.util.Locale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap

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
    sharingEnabled: Boolean = true,
    onEditProfile: () -> Unit,
    onEditIdentity: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = Gc.colors
    val social = Gc.social
    // V21 (comme iOS) : photo du pilote dans la couverture, choisie avec le sélecteur de photos (sans autorisation)
    val context = androidx.compose.ui.platform.LocalContext.current
    var photo by remember { mutableStateOf(PilotPhoto.load(context)) }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) PilotPhoto.save(context, uri)?.let { photo = it }
    }
    val onPhoto = { pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // « Gérer mon profil » : replié par défaut, ouvert d'office tant que le profil n'est pas créé
    var managing by rememberSaveable { mutableStateOf(profile.isEmpty) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().testTag("flight-list"),
        // V18.1 pages blanches : marges iOS de 16 dp, tuiles espacées de 8 dp
        contentPadding = PaddingValues(start = if (social) 16.dp else 12.dp, end = if (social) 16.dp else 12.dp, top = 0.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(if (social) 8.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(
                profile = profile,
                flights = flights,
                photo = photo,
                onPhoto = onPhoto,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ManageCard(
                open = managing, onToggle = { managing = !managing }, profileEmpty = profile.isEmpty,
                onEditProfile = onEditProfile, onEditIdentity = onEditIdentity, onPhoto = onPhoto,
                onDeleteAccount = onDeleteAccount, account = account, accountActions = accountActions,
            )
        }
        if (notice != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(notice, style = Gc.type.bodySmall.copy(color = c.warn), modifier = Modifier.testTag("notice"))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val shared = flights.count { it.isPublic }
            val count = when {
                !sharingEnabled -> "${flights.size} vol${if (flights.size > 1) "s" else ""}"
                shared > 0 -> "$shared sur le fil"
                else -> "Rien de partagé"
            }
            if (social) {
                // V18.1 : titre de section groupée façon iOS
                GcSectionTitle("Vols", trailing = { Text(count, style = Gc.type.footnote) })
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vols", style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), modifier = Modifier.weight(1f))
                    Text(count, style = Gc.type.bodySmall)
                }
            }
        }
        if (flights.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyGrid(onLoadDemo, onImport) }
        }
        items(flights, key = { it.id }) { flight ->
            FlightTile(flight, sharingEnabled, onClick = { onFlightSelected(flight) }, onToggleShare = { onToggleShare(flight) })
        }
        // V21 (comme iOS) : sous les vols, ajouter un vol venu d'un autre instrument
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp).clip(RoundedCornerShape(14.dp)).background(c.sky.copy(alpha = 0.14f))
                    .clickable(role = Role.Button, onClick = onImport).testTag("import-igc"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GcIcons.Import, contentDescription = null, tint = c.sky, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importer un IGC", style = Gc.type.headline.copy(color = c.sky))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (sharingEnabled) "Fichiers IGC gardés sur ce téléphone. Un vol n'apparaît sur le fil que si vous le partagez. " +
                    "Enregistreur non approuvé : sans valeur pour un badge."
                else "Fichiers IGC gardés sur ce téléphone (sauvegarde en ligne facultative). " +
                    "Enregistreur non approuvé : sans valeur pour un badge.",
                style = if (social) Gc.type.footnote.copy(color = c.faint) else Gc.type.bodySmall.copy(color = c.faint),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // V19 — confidentialité, conditions et suppression du compte toujours accessibles (Google Play)
        item(span = { GridItemSpan(maxLineSpan) }) { GcLegalLinks(Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun ProfileHeader(
    profile: PilotProfile,
    flights: List<FlightCardUi>,
    photo: android.graphics.Bitmap?,
    onPhoto: () -> Unit,
) {
    val c = Gc.colors
    val social = Gc.social
    val real = flights.filterNot { it.isExample }
    val carnetSeconds = real.sumOf { it.durationSeconds }
    val totalMinutes = totalFlightMinutes(profile, carnetSeconds)
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = if (social) 6.dp else 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val title = profile.displayName.ifBlank { "Mon profil" }
        if (social) {
            // V21 (comme iOS) : nom en haut à gauche, photo intégrée en bas à gauche (touchable pour la changer),
            // pseudo, club et niveau à côté ; plus d'avatar à cheval sur le bord
            Box(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(22.dp))) {
                Image(
                    painterResource(R.drawable.wg_flights_header), contentDescription = null,
                    contentScale = ContentScale.Crop, alignment = Alignment.Center, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
                Text(
                    title,
                    style = Gc.type.title1.copy(color = Color.White, shadow = Shadow(Color(0x66000000), blurRadius = 10f)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 12.dp, end = 18.dp),
                )
                Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(128.dp).clickable(role = Role.Button, onClickLabel = "Changer la photo de profil", onClick = onPhoto)) {
                        if (photo != null) Image(
                            photo.asImageBitmap(), contentDescription = "Photo du pilote", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape).border(3.dp, Color.White, CircleShape),
                        ) else Avatar(profile.initials, Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.BottomEnd).size(30.dp).background(c.sky, CircleShape), contentAlignment = Alignment.Center) {
                            Text("📷", style = Gc.type.caption1)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val shadow = Shadow(Color(0x80000000), blurRadius = 6f)
                        if (profile.username.isNotBlank()) Text("@${profile.username}", style = Gc.type.subhead.copy(color = Color.White, fontWeight = FontWeight.SemiBold, shadow = shadow), maxLines = 1)
                        if (profile.club.isNotBlank()) Text(profile.club, style = Gc.type.caption1.copy(color = Color.White.copy(alpha = 0.85f), shadow = shadow), maxLines = 1)
                        profile.experience?.let {
                            Text(it.label, style = Gc.type.caption1.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.background(c.sky.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        } else {
            // ligne du haut : pseudo (ou titre) + interrupteur de thème + menu du profil
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = Gc.type.title.copy(fontSize = 22.sp),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                GcThemeToggleButton(Modifier.padding(start = 8.dp))

            }
        }
        val distanceKm = formatKm(real.sumOf { it.distanceMeters ?: 0L })
        if (social) {
            if (profile.bio.isNotBlank()) Text(profile.bio, style = Gc.type.subhead)
            GcCard {
                Row(Modifier.fillMaxWidth()) {
                    ProfileStat(real.size.toString(), "Vols", c.sky, Modifier.weight(1f))
                    ProfileStat(formatHours(totalMinutes).let { if ('h' in it) it else "$it h" }, "Heures", c.mint, Modifier.weight(1f))
                    ProfileStat("$distanceKm km", "Distance", c.sun, Modifier.weight(1f))
                }
                if (profile.minutesBeforeApp > 0) {
                    Text("Dont ${profile.minutesBeforeApp / 60} h avant Wind Glider", style = Gc.type.footnote.copy(color = c.faint))
                }
            }
        } else {
            // avatar + chiffres (codes des réseaux sociaux)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.initials, Modifier.size(78.dp))
                Spacer(Modifier.width(14.dp))
                Stat(real.size.toString(), "Vols", Modifier.weight(1f))
                Stat(formatHours(totalMinutes), "Heures", Modifier.weight(1f))
                Stat(distanceKm, "Km", Modifier.weight(1f))
            }
            // identité
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    profile.displayName.ifBlank { "Pilote Wind Glider" },
                    style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                )
                if (profile.bio.isNotBlank()) Text(profile.bio, style = Gc.type.body.copy(fontSize = 14.sp))
                val line = listOfNotNull(profile.club.takeIf(String::isNotBlank)).joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = Gc.type.bodySmall)
                profile.experience?.let { GcPill(it.label, c.ok, Modifier.padding(top = 4.dp)) }
                if (profile.minutesBeforeApp > 0) {
                    Text(
                        "Dont ${profile.minutesBeforeApp / 60} h avant Wind Glider",
                        style = Gc.type.bodySmall.copy(color = c.faint, fontSize = 12.sp),
                    )
                }
            }
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

/** V20 — avatar illustré du pilote (casquette, veste), cerclé de blanc. */
@Composable
private fun Avatar(@Suppress("UNUSED_PARAMETER") initials: String, modifier: Modifier) {
    val c = Gc.colors
    Image(
        painterResource(R.drawable.wg_avatar),
        contentDescription = "Avatar du pilote",
        contentScale = ContentScale.Crop,
        modifier = modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(c.panel).border(3.dp, Color.White, CircleShape),
    )
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Gc.type.kpi.copy(fontSize = 19.sp), maxLines = 1)
        Text(label, style = Gc.type.bodySmall.copy(fontSize = 12.sp))
    }
}

/** V18.1 pages blanches : chiffre clé à la manière d'Apple Santé (libellé teinté au-dessus, unité petite et grise). */
@Composable
private fun ProfileStat(value: String, label: String, tint: Color, modifier: Modifier) {
    val number = Gc.type.metric.copy(fontSize = 24.sp, lineHeight = 30.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = Gc.type.footnote.copy(color = tint, fontWeight = FontWeight.SemiBold), maxLines = 1)
        Text(metricText(value, number, Gc.colors.faint), style = number, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun EmptyGrid(onLoadDemo: (() -> Unit)?, onImport: () -> Unit) {
    val c = Gc.colors
    GcCard(title = "Carnet vide", icon = GcIcons.MesVols, tint = c.sky) {
        Text("Votre carnet est vide", style = Gc.type.body.copy(fontWeight = FontWeight.SemiBold))
        Text(
            "Vos vols Wind Glider apparaissent ici après l'atterrissage. Vous pouvez aussi importer un fichier IGC.",
            style = if (Gc.social) Gc.type.subhead.copy(color = c.dim) else Gc.type.bodySmall,
        )
        if (onLoadDemo != null) {
            GcButton("Charger le vol d'exemple", onClick = onLoadDemo, primary = true, modifier = Modifier.testTag("load-demo"))
        }
    }
}

/** Tuile de la grille : vignette carrée (fond clair, trace rouge), date, durée · distance, icône de partage. */
@Composable
private fun FlightTile(flight: FlightCardUi, sharingEnabled: Boolean, onClick: () -> Unit, onToggleShare: () -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    // V18.1 pages blanches : tuile = carte blanche aux coins de 12 dp, vignette inscrite (rayon 8 dp + marge 4 dp)
    val card = if (social) Modifier.clip(RoundedCornerShape(12.dp)).background(c.panel) else Modifier
    Column(
        Modifier
            .testTag("flight-card-${flight.id}")
            .semantics { contentDescription = "Vol du ${flight.date}, ${flight.duration}, ${flight.distance}" }
            .then(card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(if (social) 4.dp else 0.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            GcTraceThumbnail(flight.route, Modifier.fillMaxSize())
            if (sharingEnabled && !flight.isExample && flight.localState == LocalFileState.AVAILABLE) {
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
        Spacer(Modifier.height(if (social) 6.dp else 5.dp))
        Column(Modifier.padding(start = if (social) 4.dp else 0.dp, end = if (social) 4.dp else 0.dp, bottom = if (social) 4.dp else 0.dp)) {
            Text(
                flight.shortDate,
                style = if (social) Gc.type.footnote.copy(color = c.ink, fontWeight = FontWeight.SemiBold)
                else Gc.type.bodySmall.copy(color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${flight.duration} · ${flight.distance}",
                style = if (social) Gc.type.caption2.copy(fontFeatureSettings = "tnum") else Gc.type.bodySmall.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    val social = Gc.social
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
            TextButton(onClick = onCancel) { Text("Annuler", style = Gc.type.body.copy(color = if (social) c.route else c.ink)) }
            Text(
                if (identityOnly) "Nom et pseudo" else "Modifier le profil",
                style = if (social) Gc.type.headline else Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSave(candidate) }, enabled = problems.isEmpty(), modifier = Modifier.testTag("save-profile")) {
                Text("OK", style = Gc.type.body.copy(color = if (!problems.isEmpty()) c.faint else if (social) c.route else c.ok, fontWeight = FontWeight.Bold))
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
                            ?: "Unique sur Wind Glider, réservé à l'activation du compte en ligne.",
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
                        EditField(hours, { hours = it.filter(Char::isDigit).take(6) }, "Heures de vol avant Wind Glider", keyboard = KeyboardType.Number, tag = "field-hours")
                        Text(
                            "Ajoutées au total d'heures du profil.",
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
    val social = Gc.social
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
            // V18.1 pages blanches : champ blanc sur fond groupé, focus en bleu ciel (sombre : inchangé, panneau noir)
            focusedBorderColor = if (social) c.route else c.ink, unfocusedBorderColor = c.inputLine, cursorColor = if (social) c.route else c.ink,
            focusedTextColor = c.ink, unfocusedTextColor = c.ink,
            focusedLabelColor = c.dim, unfocusedLabelColor = c.dim,
            focusedContainerColor = c.panel, unfocusedContainerColor = c.panel,
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
                (if (hasOnlineAccount) "Ton compte, tes vols en ligne et ton profil seront effacés définitivement. "
                else "Ton profil pilote sera effacé de ce téléphone. ") +
                    "Les vols de ce téléphone sont conservés.",
                style = Gc.type.body.copy(color = c.dim),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-delete-account")) {
                Text("Supprimer", style = Gc.type.body.copy(color = c.danger, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Annuler", style = Gc.type.body.copy(color = if (Gc.social) c.route else c.ink, fontWeight = FontWeight.Bold)) }
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

/** V20 — encre bleu nuit de l'univers « Wind Glider » (icônes posées sur les illustrations). */
private val WgInk = Color(0xFF1D3550)

/** « Gérer mon profil » (comme iOS) : un toucher déplie profil, photo, sauvegarde en ligne et suppression du compte. */
@Composable
private fun ManageCard(
    open: Boolean, onToggle: () -> Unit, profileEmpty: Boolean,
    onEditProfile: () -> Unit, onEditIdentity: () -> Unit, onPhoto: () -> Unit, onDeleteAccount: () -> Unit,
    account: AccountCardState?, accountActions: AccountActions?,
) {
    val c = Gc.colors
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    GcCard {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) "Replier" else "Déplier", onClick = onToggle).testTag("manage-profile"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GcIcons.MesVols, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Gérer mon profil", style = Gc.type.headline, modifier = Modifier.weight(1f))
            Icon(GcIcons.ChevronDown, contentDescription = null, tint = c.dim, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation })
        }
        androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (profileEmpty) Text("Nom, pseudo, club et niveau. Tout reste sur ce téléphone tant que tu ne partages rien.", style = Gc.type.subhead.copy(color = c.dim))
                ManageButton(if (profileEmpty) "Créer mon profil pilote" else "Modifier mon profil", primary = profileEmpty, tag = "edit-profile", onClick = onEditProfile)
                ManageButton("Changer la photo", onClick = onPhoto)
                ManageButton("Modifier nom et pseudo", onClick = onEditIdentity)
                // sauvegarde en ligne et suppression du compte (comme iOS)
                if (account != null) AccountCard(account, accountActions)
                Text("Supprimer le compte", style = Gc.type.subhead.copy(color = c.danger),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onDeleteAccount).padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ManageButton(text: String, primary: Boolean = false, tag: String? = null, onClick: () -> Unit) {
    val c = Gc.colors
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(if (primary) c.sky else c.sky.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Gc.type.headline.copy(color = if (primary) Color.White else c.sky)) }
}
