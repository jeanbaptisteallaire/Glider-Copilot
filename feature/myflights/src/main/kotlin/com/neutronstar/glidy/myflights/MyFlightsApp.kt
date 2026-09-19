package com.neutronstar.glidy.myflights

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.GeoPoint
import com.neutronstar.glidy.flightarchive.IgcParseError
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val Ink = Color(0xFF141414)
private val Graphite = Color(0xFF55575B)
private val Quiet = Color(0xFF8C8F94)
private val CanvasGray = Color(0xFFF3F4F5)
private val LineGray = Color(0xFFE2E3E5)
private val White = Color(0xFFFFFFFF)

private val GlidyLightColors = lightColorScheme(
    primary = Ink,
    onPrimary = White,
    background = White,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = CanvasGray,
    onSurfaceVariant = Graphite,
    outline = LineGray,
)

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
)

@Composable
fun MyFlightsApp(repository: FlightArchiveRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archivedFlights by remember { mutableStateOf<List<ArchivedFlight>?>(null) }
    var selectedFlightId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        repository.reconcile()
        archivedFlights = repository.listFlights()
    }

    LaunchedEffect(repository) { refresh() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val fileName = context.displayName(uri) ?: "vol-importe.igc"
                val result = context.contentResolver.openInputStream(uri)?.use { source ->
                    repository.importIgc(fileName, source)
                } ?: ImportIgcResult.Failed("Le fichier ne peut pas être ouvert")
                notice = result.userMessage()
                archivedFlights = repository.listFlights()
            }
        }
    }

    val flights = archivedFlights?.map(ArchivedFlight::toCardUi).orEmpty()
    val selectedFlight = flights.firstOrNull { it.id == selectedFlightId }
    MaterialTheme(colorScheme = GlidyLightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = White) {
            when {
                selectedFlight != null -> FlightDetailScreen(
                    flight = selectedFlight,
                    onBack = { selectedFlightId = null },
                )
                archivedFlights == null -> LoadingScreen()
                else -> FlightsListScreen(
                    flights = flights,
                    notice = notice,
                    onImport = { importer.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                    onFlightSelected = { selectedFlightId = it.id },
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
    }
}

@Composable
private fun FlightsListScreen(
    flights: List<FlightCardUi>,
    notice: String?,
    onImport: () -> Unit,
    onFlightSelected: (FlightCardUi) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                BrandHeader()
                Spacer(Modifier.height(34.dp))
                Text(
                    text = "Mes vols",
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.2).sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Votre carnet de vol, disponible hors ligne.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Graphite,
                )
                Spacer(Modifier.height(24.dp))
                ArchiveSummary(flights)
                if (notice != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(color = CanvasGray, shape = RoundedCornerShape(14.dp)) {
                        Text(
                            notice,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            color = Graphite,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("RÉCENTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("${flights.size} VOLS", fontSize = 12.sp, color = Quiet, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (flights.isEmpty()) {
            item { EmptyArchive() }
        }
        items(items = flights, key = { it.id }) { flight ->
            FlightCard(
                flight = flight,
                onClick = { onFlightSelected(flight) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
            )
        }
        item {
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    contentColor = White,
                ),
            ) {
                Text("IMPORTER UN FICHIER IGC", fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        }
    }
}

@Composable
private fun EmptyArchive() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        color = CanvasGray,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = White, shape = CircleShape) {
                Text("IGC", modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text("Votre carnet est vide", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(
                "Importez une trace IGC. Elle restera disponible sur cet appareil, même hors ligne.",
                color = Graphite,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("GLIDY", fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.6.sp)
        Surface(color = CanvasGray, shape = RoundedCornerShape(50)) {
            Text(
                "CARNET LOCAL",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 10.sp,
                color = Graphite,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
        }
    }
}

@Composable
private fun ArchiveSummary(flights: List<FlightCardUi>) {
    val totalSeconds = flights.sumOf { it.durationSeconds }
    val totalDistance = flights.sumOf { it.distanceMeters ?: 0L }
    Surface(color = Ink, shape = RoundedCornerShape(22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SummaryValue(formatDuration(totalSeconds), "TEMPS DE VOL", White)
            SummaryDivider()
            SummaryValue(formatDistance(totalDistance), "DISTANCE", White)
            SummaryDivider()
            SummaryValue(flights.size.toString(), "VOLS", White)
        }
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.width(1.dp).height(42.dp).background(Color(0xFF424242)))
}

@Composable
private fun SummaryValue(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(label, color = color.copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun FlightCard(flight: FlightCardUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CanvasGray),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RoutePreview(route = flight.route, modifier = Modifier.size(92.dp), dark = false)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(flight.day.uppercase(), fontSize = 10.sp, color = Quiet, letterSpacing = 1.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(flight.date, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        flight.place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Graphite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(color = White, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            flight.glider,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            fontSize = 11.sp,
                            color = Graphite,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text("›", fontSize = 28.sp, color = Quiet)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = LineGray)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallMetric(flight.duration, "DURÉE")
                SmallMetric(flight.distance, "DISTANCE")
                SmallMetric(flight.maxAltitude, "ALT. MAX")
                SmallMetric(flight.gain, "DÉNIVELÉ")
            }
        }
    }
}

@Composable
private fun SmallMetric(value: String, label: String) {
    Column {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 8.sp, color = Quiet, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun FlightDetailScreen(flight: FlightCardUi, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp).clickable(onClick = onBack),
                    color = CanvasGray,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", fontSize = 32.sp, fontWeight = FontWeight.Light)
                    }
                }
                Text("DÉTAIL DU VOL", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Surface(modifier = Modifier.size(48.dp), color = CanvasGray, shape = CircleShape) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("•••", fontSize = 14.sp, letterSpacing = 1.sp)
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(flight.date, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                Spacer(Modifier.height(5.dp))
                Text(flight.place, color = Graphite, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(22.dp))
                Surface(color = Ink, shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        RoutePreview(
                            route = flight.route,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.65f),
                            dark = true,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SummaryValue(flight.duration, "DURÉE", White)
                            SummaryValue(flight.distance, "DISTANCE", White)
                            SummaryValue(flight.maxAltitude, "ALTITUDE MAX", White)
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                SectionTitle("PROFIL D'ALTITUDE", "GPS")
                Spacer(Modifier.height(12.dp))
                AltitudeChart(flight.altitudes)
                Spacer(Modifier.height(26.dp))
                SectionTitle("INFORMATIONS", "LOCAL")
                Spacer(Modifier.height(12.dp))
                InformationPanel(flight)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = CanvasGray,
                        disabledContentColor = Graphite,
                    ),
                ) {
                    Text("REVOIR EN 3D · ÉTUDE À VENIR", fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, qualifier: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(qualifier, fontSize = 10.sp, color = Quiet, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun InformationPanel(flight: FlightCardUi) {
    Surface(color = CanvasGray, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            InfoRow("Planeur", flight.glider)
            HorizontalDivider(color = LineGray)
            InfoRow("Dénivelé positif", flight.gain)
            HorizontalDivider(color = LineGray)
            InfoRow("Points valides", flight.pointCount)
            HorizontalDivider(color = LineGray)
            InfoRow("Fichier source", flight.fileName)
            HorizontalDivider(color = LineGray)
            InfoRow("Sauvegarde", "Sur cet appareil")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Graphite, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RoutePreview(route: List<Pair<Float, Float>>, modifier: Modifier, dark: Boolean) {
    val background = if (dark) Color(0xFF202124) else White
    val grid = if (dark) Color(0xFF343538) else LineGray
    val routeColor = if (dark) White else Ink
    Canvas(modifier = modifier.background(background, RoundedCornerShape(16.dp)).padding(10.dp)) {
        for (index in 1..3) {
            val fraction = index / 4f
            drawLine(grid, Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height), 1f)
            drawLine(grid, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), 1f)
        }
        if (route.size > 1) {
            val path = Path()
            route.forEachIndexed { index, point ->
                val x = point.first * size.width
                val y = point.second * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, routeColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            val start = route.first()
            val end = route.last()
            drawCircle(routeColor, radius = 5f, center = Offset(start.first * size.width, start.second * size.height))
            drawCircle(background, radius = 3f, center = Offset(start.first * size.width, start.second * size.height))
            drawCircle(routeColor, radius = 5f, center = Offset(end.first * size.width, end.second * size.height))
        }
    }
}

@Composable
private fun AltitudeChart(altitudes: List<Float>) {
    Column {
        Canvas(
            modifier = Modifier.fillMaxWidth().height(150.dp)
                .background(CanvasGray, RoundedCornerShape(20.dp)).padding(18.dp),
        ) {
            val minimum = altitudes.minOrNull() ?: 0f
            val maximum = altitudes.maxOrNull() ?: 1f
            val span = (maximum - minimum).coerceAtLeast(1f)
            repeat(3) { index ->
                val y = size.height * index / 2f
                drawLine(LineGray, Offset(0f, y), Offset(size.width, y), 1f)
            }
            val path = Path()
            altitudes.forEachIndexed { index, altitude ->
                val x = index.toFloat() / (altitudes.size - 1).coerceAtLeast(1) * size.width
                val y = size.height - ((altitude - minimum) / span * size.height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Ink, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DÉPART", fontSize = 9.sp, color = Quiet, letterSpacing = 0.7.sp)
            Text("ARRIVÉE", fontSize = 9.sp, color = Quiet, letterSpacing = 0.7.sp)
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

private fun ImportIgcResult.userMessage(): String = when (this) {
    is ImportIgcResult.Imported -> "Vol importé et archivé sur cet appareil."
    is ImportIgcResult.Duplicate -> "Ce vol est déjà présent dans le carnet."
    is ImportIgcResult.Invalid -> when (error) {
        IgcParseError.EmptyFile -> "Le fichier IGC est vide."
        IgcParseError.MissingDateHeader -> "Le fichier IGC ne contient aucune date."
        is IgcParseError.InvalidDateHeader -> "La date du fichier IGC est invalide."
        IgcParseError.NoValidFix -> "Le fichier IGC ne contient aucun point GPS valide."
    }
    ImportIgcResult.TooLarge -> "Le fichier dépasse la limite de 64 Mo."
    is ImportIgcResult.Failed -> "Import impossible : $reason"
}

private val DAY_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE)
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
