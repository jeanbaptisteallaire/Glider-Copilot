package com.neutronstar.glidy.myflights

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GcKpi
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.vario
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.CompletedFlightGateway
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightShareGateway
import com.neutronstar.glidy.flightarchive.GeoPoint
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.social.PilotProfile
import com.neutronstar.glidy.social.ProfileStore
import java.io.InputStream
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Mes vols — charte GLIDY (S11) : tout vient de core/designsystem (Gc.colors, Gc.type, GcCard, GcKpi, GcPill,
 * GcButton). Aucune couleur ni police codée ici ; mode clair suivi via GlidyAdaptiveTheme côté application.
 */

data class FlightCardUi(
    val id: String,
    val day: String,
    val date: String,
    val place: String,
    val glider: String,
    val duration: String,
    val distance: String,
    val maxAltitude: String,
    val gain: String,
    val fileName: String,
    val pointCount: String,
    val route: List<Pair<Float, Float>>,
    val altitudes: List<Float>,
    val durationSeconds: Long,
    val distanceMeters: Long?,
    val localState: LocalFileState,
    val minimumAltitude: String,
    val fileSize: String,
    val fingerprint: String,
    val pilot: String,
    val maxAltitudeMeters: Int? = null,
    /** S16 : date courte des tuiles (« 19 sept. 2026 »). */
    val shortDate: String = date,
    /** S16 : vol partagé sur le fil (icône cochée). */
    val isPublic: Boolean = false,
) {
    /** Vol synthétique fourni avec l'app (jamais un vrai vol). */
    val isExample: Boolean get() = fileName.startsWith("exemple-", ignoreCase = true)
}

/**
 * [demoFlight] : ouvre le vol synthétique d'exemple embarqué par l'application hôte (nom de fichier + flux),
 * proposé quand le carnet est vide. null = bouton masqué (app autonome, tests).
 */
@Composable
fun MyFlightsApp(
    repository: FlightArchiveRepository,
    shareGateway: FlightShareGateway,
    completedFlightGateway: CompletedFlightGateway? = null,
    onReplay3d: (ArchivedFlight) -> Unit = {},
    demoFlight: (() -> Pair<String, InputStream>)? = null,
    account: AccountCardState? = null,
    accountActions: AccountActions? = null,
    /** Incrémenté par l'hôte quand le carnet a changé hors de cet écran (vol archivé, restauration cloud). */
    refreshSignal: Int = 0,
    /** S16 — stockage du profil pilote fourni par l'hôte (null = profil en mémoire). */
    profileStore: ProfileStore? = null,
) {
    val factory = remember(repository, shareGateway, completedFlightGateway, profileStore) {
        MyFlightsViewModelFactory(repository, shareGateway, completedFlightGateway, profileStore)
    }
    val viewModel: MyFlightsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val context = LocalContext.current
    // S10 (GLIDY) : l'onglet peut rester ouvert pendant qu'un vol est archivé en tâche de fond → relire à chaque affichage
    LaunchedEffect(refreshSignal) { viewModel.reload() }
    // retour système depuis le détail d'un vol → liste du carnet (et non sortie de l'app)
    BackHandler(enabled = (state as? MyFlightsUiState.Ready)?.selectedFlightId != null) { viewModel.closeDetail() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = context.displayName(uri) ?: "vol-importe.igc"
            viewModel.importIgc(fileName, context.contentResolver.openInputStream(uri))
        }
    }

    MyFlightsScreen(
        state = state,
        onRetry = viewModel::refresh,
        onImport = { importer.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
        onFlightSelected = { viewModel.selectFlight(it.id) },
        onBack = viewModel::closeDetail,
        onShare = viewModel::shareFlight,
        onDeleteRequest = viewModel::requestDelete,
        onDeleteCancel = viewModel::cancelDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        onReplay3d = onReplay3d,
        account = account,
        accountActions = accountActions,
        onLoadDemo = demoFlight?.let { open ->
            {
                val (name, stream) = runCatching(open).getOrNull() ?: ("" to null)
                viewModel.importIgc(name, stream)
            }
        },
        profile = profile,
        onToggleShare = viewModel::toggleShare,
        onSaveProfile = viewModel::saveProfile,
        onDeleteAccount = {
            viewModel.deleteProfile()
            // compte en ligne connecté : il est supprimé aussi (S12 : fichiers, lignes et compte Supabase)
            if (account?.email != null) accountActions?.deleteAccount()
        },
    )
}

/** S16 — écran d'édition ouvert au-dessus de la page profil. */
private enum class ProfileEdit { FULL, IDENTITY }

@Composable
fun MyFlightsScreen(
    state: MyFlightsUiState,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onFlightSelected: (ArchivedFlight) -> Unit,
    onBack: () -> Unit,
    onShare: (FlightId) -> Unit = {},
    onDeleteRequest: (FlightId) -> Unit = {},
    onDeleteCancel: () -> Unit = {},
    onDeleteConfirm: () -> Unit = {},
    onReplay3d: (ArchivedFlight) -> Unit = {},
    onLoadDemo: (() -> Unit)? = null,
    account: AccountCardState? = null,
    accountActions: AccountActions? = null,
    profile: PilotProfile = PilotProfile(),
    onToggleShare: (FlightId) -> Unit = {},
    onSaveProfile: (PilotProfile) -> Unit = {},
    onDeleteAccount: () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf<ProfileEdit?>(null) }
    var confirmDeleteAccount by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Gc.colors.background)) {
        when (state) {
            MyFlightsUiState.Loading -> LoadingScreen()
            is MyFlightsUiState.Error -> ErrorScreen(state.message, onRetry)
            is MyFlightsUiState.Ready -> {
                val selectedFlight = state.flights.firstOrNull { it.id == state.selectedFlightId }
                if (selectedFlight != null) {
                    FlightDetailScreen(
                        flight = selectedFlight.toCardUi(),
                        notice = state.notice,
                        onToggleShare = { onToggleShare(selectedFlight.id) },
                        onBack = onBack,
                        onShare = { onShare(selectedFlight.id) },
                        onDelete = { onDeleteRequest(selectedFlight.id) },
                        onReplay3d = { onReplay3d(selectedFlight) },
                    )
                } else if (editing != null) {
                    ProfileEditScreen(
                        initial = profile,
                        identityOnly = editing == ProfileEdit.IDENTITY,
                        onCancel = { editing = null },
                        onSave = { onSaveProfile(it); editing = null },
                    )
                } else {
                    ProfileScreen(
                        profile = profile,
                        flights = state.flights.map(ArchivedFlight::toCardUi),
                        notice = state.notice,
                        account = account,
                        accountActions = accountActions,
                        onImport = onImport,
                        onLoadDemo = onLoadDemo,
                        onFlightSelected = { card ->
                            state.flights.firstOrNull { it.id.value == card.id }?.let(onFlightSelected)
                        },
                        onToggleShare = { card -> onToggleShare(FlightId(card.id)) },
                        onEditProfile = { editing = ProfileEdit.FULL },
                        onEditIdentity = { editing = ProfileEdit.IDENTITY },
                        onDeleteAccount = { confirmDeleteAccount = true },
                    )
                }
                if (confirmDeleteAccount) {
                    DeleteAccountDialog(
                        hasOnlineAccount = account?.email != null,
                        onCancel = { confirmDeleteAccount = false },
                        onConfirm = { confirmDeleteAccount = false; onDeleteAccount() },
                    )
                }
                val pendingDelete = state.flights.firstOrNull { it.id == state.pendingDeleteFlightId }
                if (pendingDelete != null) {
                    DeleteFlightDialog(
                        fileName = pendingDelete.file.fileName,
                        onCancel = onDeleteCancel,
                        onConfirm = onDeleteConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().testTag("loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Gc.colors.ok, strokeWidth = 2.dp)
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp).testTag("error"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Carnet indisponible", style = Gc.type.title.copy(fontSize = 22.sp))
        Spacer(Modifier.height(10.dp))
        Text(message, style = Gc.type.body.copy(color = Gc.colors.dim))
        Spacer(Modifier.height(22.dp))
        GcButton("RÉESSAYER", onClick = onRetry, primary = true)
    }
}

@Composable
private fun FileStateBadge(state: LocalFileState) {
    val label = when (state) {
        LocalFileState.MISSING -> "FICHIER MANQUANT"
        LocalFileState.INVALID -> "FICHIER INVALIDE"
        LocalFileState.RECORDING -> "ENREGISTREMENT EN COURS"
        LocalFileState.AVAILABLE -> "LOCAL"
    }
    GcPill(label, if (state == LocalFileState.AVAILABLE) Gc.colors.dim else Gc.colors.warn)
}

@Composable
private fun FlightDetailScreen(
    flight: FlightCardUi,
    notice: String?,
    onToggleShare: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReplay3d: () -> Unit,
) {
    val c = Gc.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).testTag("back").clickable(role = Role.Button, onClickLabel = "Retour au carnet", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GcIcons.ChevronDown, contentDescription = "Retour", tint = c.ink, modifier = Modifier.size(22.dp).rotate(90f))
            }
            Text("DÉTAIL DU VOL", style = Gc.type.eyebrow.copy(color = c.cardTitle, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 1.4.sp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("flight-detail"),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (Gc.social) flight.date else flight.date.uppercase(), style = Gc.type.title.copy(fontSize = 24.sp))
                    Text("${flight.day} · ${flight.place}", style = Gc.type.bodySmall)
                }
            }
            item {
                GcCard(title = "Trace", trailing = { GcPill(if (flight.isExample) "EXEMPLE SYNTHÉTIQUE" else "IGC TÉLÉPHONE", if (flight.isExample) c.warn else c.dim) }) {
                    RoutePreview(route = flight.route, modifier = Modifier.fillMaxWidth().aspectRatio(1.6f))
                    Row(Modifier.fillMaxWidth()) {
                        GcKpi(flight.duration, "Durée", Modifier.weight(1f), color = c.ok)
                        GcKpi(flight.distance, "Distance", Modifier.weight(1f))
                        GcKpi(flight.maxAltitude, "Alt. max", Modifier.weight(1f))
                    }
                    GcButton(
                        "REVOIR LE VOL EN 3D",
                        onClick = onReplay3d,
                        primary = true,
                        enabled = flight.localState == LocalFileState.AVAILABLE && flight.route.size >= 2,
                        modifier = Modifier.fillMaxWidth().testTag("open-replay-3d"),
                        fontSize = 13f,
                    )
                }
            }
            item {
                GcCard(title = "Profil d'altitude", trailing = { GcPill("GPS · VARIO", c.dim) }) {
                    AltitudeChart(flight.altitudes, flight.durationSeconds)
                }
            }
            item {
                GcCard(title = "Informations", trailing = { FileStateBadge(flight.localState) }) {
                    InformationPanel(flight)
                }
            }
            if (notice != null) {
                item { Text(notice, style = Gc.type.bodySmall.copy(color = c.warn)) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!flight.isExample && flight.localState == LocalFileState.AVAILABLE) {
                        GcButton(
                            if (flight.isPublic) "Partagé sur le fil ✓ · retirer" else "Partager sur le fil GLIDY",
                            onClick = onToggleShare,
                            primary = !flight.isPublic,
                            modifier = Modifier.fillMaxWidth().testTag("share-feed"),
                            fontSize = 13f,
                        )
                    }
                    GcButton(
                        "Partager le fichier IGC",
                        onClick = onShare,
                        enabled = flight.localState != LocalFileState.MISSING,
                        modifier = Modifier.fillMaxWidth().testTag("share-igc"),
                        fontSize = 13f,
                    )
                    DangerButton("Supprimer de ce téléphone", onDelete, Modifier.fillMaxWidth().testTag("delete-flight"))
                }
            }
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Gc.colors
    Box(
        modifier.height(40.dp).border(1.dp, c.danger.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Gc.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.danger)) }
}

@Composable
private fun DeleteFlightDialog(
    fileName: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = Gc.colors
    AlertDialog(
        modifier = Modifier.testTag("delete-confirmation"),
        onDismissRequest = onCancel,
        title = { Text("Supprimer ce vol ?", style = Gc.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) },
        text = {
            Text(
                "Le fichier $fileName et son index local seront supprimés de ce téléphone. " +
                    "Les autres vols ne seront pas modifiés.",
                style = Gc.type.body.copy(color = c.dim),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-delete")) {
                Text("SUPPRIMER", style = Gc.type.body.copy(color = c.danger, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-delete")) {
                Text("ANNULER", style = Gc.type.body.copy(color = c.ink, fontWeight = FontWeight.Bold))
            }
        },
        containerColor = c.control,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun InformationPanel(flight: FlightCardUi) {
    val c = Gc.colors
    Column {
        listOf(
            "Planeur" to flight.glider,
            "Pilote" to flight.pilot,
            "Altitude minimale" to flight.minimumAltitude,
            "Dénivelé positif" to flight.gain,
            "Points valides" to flight.pointCount,
            "Fichier source" to flight.fileName,
            "Taille" to flight.fileSize,
            "Empreinte" to flight.fingerprint,
            "État local" to flight.localState.userLabel(),
        ).forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = c.lineSoft)
            InfoRow(label, value)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = Gc.type.eyebrow, modifier = Modifier.weight(0.42f))
        Text(value, style = Gc.type.body.copy(fontSize = 13.sp), modifier = Modifier.weight(0.58f))
    }
}

/** Vignette de trace : trait vert de la charte, départ en blanc, arrivée en orange. */
@Composable
private fun RoutePreview(route: List<Pair<Float, Float>>, modifier: Modifier) {
    val c = Gc.colors
    Canvas(modifier = modifier.background(c.sunken, RoundedCornerShape(10.dp)).border(1.dp, c.lineFaint, RoundedCornerShape(10.dp)).padding(8.dp)) {
        for (index in 1..3) {
            val fraction = index / 4f
            drawLine(c.lineFaint, Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height), 1f)
            drawLine(c.lineFaint, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), 1f)
        }
        if (route.size > 1) {
            val path = Path()
            route.forEachIndexed { index, point ->
                val x = point.first * size.width
                val y = point.second * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c.route, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
            val start = route.first()
            val end = route.last()
            drawCircle(c.ink, radius = 4.5f, center = Offset(start.first * size.width, start.second * size.height))
            drawCircle(c.warn, radius = 4.5f, center = Offset(end.first * size.width, end.second * size.height))
        }
    }
}

/** Profil d'altitude coloré avec l'échelle vario GLIDY (vert en descente, orange en montée). */
@Composable
private fun AltitudeChart(altitudes: List<Float>, durationSeconds: Long) {
    val c = Gc.colors
    Column {
        Canvas(
            modifier = Modifier.fillMaxWidth().height(140.dp)
                .background(c.sunken, RoundedCornerShape(10.dp)).padding(12.dp),
        ) {
            val minimum = altitudes.minOrNull() ?: 0f
            val maximum = altitudes.maxOrNull() ?: 1f
            val span = (maximum - minimum).coerceAtLeast(1f)
            repeat(3) { index ->
                val y = size.height * index / 2f
                drawLine(c.lineSoft, Offset(0f, y), Offset(size.width, y), 1f)
            }
            if (altitudes.size > 1) {
                val step = durationSeconds.coerceAtLeast(1).toDouble() / (altitudes.size - 1)
                fun pos(i: Int) = Offset(
                    i.toFloat() / (altitudes.size - 1) * size.width,
                    size.height - ((altitudes[i] - minimum) / span * size.height),
                )
                for (i in 1 until altitudes.size) {
                    val ms = (altitudes[i] - altitudes[i - 1]) / step
                    drawLine(c.vario(ms), pos(i - 1), pos(i), strokeWidth = 3.5f, cap = StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DÉPART · ${altitudes.firstOrNull()?.toInt() ?: "—"} m", style = Gc.type.eyebrow)
            Text("PLAFOND ${altitudes.maxOrNull()?.toInt() ?: "—"} m", style = Gc.type.eyebrow.copy(color = c.ok))
            Text("ARRIVÉE · ${altitudes.lastOrNull()?.toInt() ?: "—"} m", style = Gc.type.eyebrow)
        }
    }
}

private fun ArchivedFlight.toCardUi(): FlightCardUi {
    val summary = summary
    val startedAt = summary?.startedAt?.atZone(ZoneId.systemDefault())
    val durationSeconds = if (summary == null) 0L else Duration.between(summary.startedAt, summary.endedAt).seconds
    val gliderLabel = listOfNotNull(gliderType, gliderId).filter(String::isNotBlank).joinToString(" · ")
        .ifBlank { "Planeur non renseigné" }
    return FlightCardUi(
        id = id.value,
        day = startedAt?.format(DAY_FORMATTER) ?: "Fichier",
        date = startedAt?.format(DATE_FORMATTER) ?: "À vérifier",
        place = pilot?.takeIf(String::isNotBlank)?.let { "Pilote · $it" } ?: "Vol IGC importé",
        glider = gliderLabel,
        duration = formatDuration(durationSeconds),
        distance = summary?.distanceMeters?.let(::formatDistance) ?: "—",
        maxAltitude = summary?.maximumAltitudeMeters?.let { formatInteger(it) + " m" } ?: "—",
        gain = summary?.positiveGainMeters?.let { "+${formatInteger(it)} m" } ?: "—",
        fileName = file.fileName,
        pointCount = formatInteger(summary?.validPointCount ?: 0),
        route = normalizeTrack(previewTrack),
        altitudes = altitudeProfileMeters.map(Int::toFloat),
        durationSeconds = durationSeconds,
        distanceMeters = summary?.distanceMeters,
        localState = localState,
        minimumAltitude = summary?.minimumAltitudeMeters?.let { formatInteger(it) + " m" } ?: "—",
        fileSize = formatFileSize(file.sizeBytes),
        fingerprint = file.sha256.take(10) + "…",
        pilot = pilot?.takeIf(String::isNotBlank) ?: "Non renseigné",
        maxAltitudeMeters = summary?.maximumAltitudeMeters,
        shortDate = startedAt?.format(SHORT_DATE_FORMATTER) ?: "À vérifier",
        isPublic = isPublic,
    )
}

private fun normalizeTrack(points: List<GeoPoint>): List<Pair<Float, Float>> {
    if (points.isEmpty()) return emptyList()
    val minimumLatitude = points.minOf(GeoPoint::latitude)
    val maximumLatitude = points.maxOf(GeoPoint::latitude)
    val minimumLongitude = points.minOf(GeoPoint::longitude)
    val maximumLongitude = points.maxOf(GeoPoint::longitude)
    val latitudeSpan = (maximumLatitude - minimumLatitude).coerceAtLeast(0.000001)
    val longitudeSpan = (maximumLongitude - minimumLongitude).coerceAtLeast(0.000001)
    return points.map { point ->
        val x = 0.08f + ((point.longitude - minimumLongitude) / longitudeSpan).toFloat() * 0.84f
        val y = 0.08f + (1f - ((point.latitude - minimumLatitude) / latitudeSpan).toFloat()) * 0.84f
        x to y
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds.coerceAtLeast(0) / 3600
    val minutes = totalSeconds.coerceAtLeast(0) % 3600 / 60
    return if (hours == 0L) "${minutes} min" else "%d h %02d".format(Locale.FRANCE, hours, minutes)
}

private fun formatDistance(meters: Long): String = when {
    meters >= 100_000 -> "%.0f km".format(Locale.FRANCE, meters / 1000.0)
    meters >= 10_000 -> "%.1f km".format(Locale.FRANCE, meters / 1000.0)
    else -> "%.1f km".format(Locale.FRANCE, meters / 1000.0)
}

private fun formatInteger(value: Int): String = "%,d".format(Locale.FRANCE, value).replace('\u202f', ' ')

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f Mo".format(Locale.FRANCE, bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.0f Ko".format(Locale.FRANCE, bytes / 1_024.0)
    else -> "$bytes octets"
}

private fun LocalFileState.userLabel(): String = when (this) {
    LocalFileState.AVAILABLE -> "Disponible sur cet appareil"
    LocalFileState.RECORDING -> "Enregistrement en cours"
    LocalFileState.MISSING -> "Fichier manquant"
    LocalFileState.INVALID -> "Fichier invalide · diagnostic conservé"
}

private fun Context.displayName(uri: Uri): String? = contentResolver.query(
    uri,
    arrayOf(OpenableColumns.DISPLAY_NAME),
    null,
    null,
    null,
)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
}

private val DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE)
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)
