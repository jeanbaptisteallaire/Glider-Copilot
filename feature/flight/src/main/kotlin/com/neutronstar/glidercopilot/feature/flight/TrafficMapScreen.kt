package com.neutronstar.glidercopilot.feature.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.vario
import com.neutronstar.glidercopilot.domain.Geo
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Onglet Carte : la même carte aéronautique hors ligne que Pilotage, sans aucune fonction de vol.
 * Tous les aéronefs reçus du réseau OGN sont affichés par une pastille portant les deux derniers caractères
 * de leur immatriculation (ou de leur adresse radio quand la base ne la publie pas). Un appui ouvre leur fiche.
 */
@Composable
fun TrafficMapScreen(
    map: FlightMapConfig?,
    traffic: FlightTraffic,
    networkLabel: String,
    modifier: Modifier = Modifier,
    /** « Suivre » : met cet aéronef au centre de Pilotage et y bascule (V7.1). */
    onFollow: ((TrafficMark) -> Unit)? = null,
) {
    val c = Gc.colors
    // zoom d'ouverture : tout le sud de la France, soit le rayon de 250 km du filtre OGN
    val controller = remember { MapController().apply { zoom = 6.2; shownZoom = 6.2; follow = false } }
    var selected by remember { mutableStateOf<String?>(null) }
    val chosen = traffic.aircraft.firstOrNull { it.id == selected }

    Column(modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("CARTE · AÉRONEFS EN VOL", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = c.dim))
                Text(networkLabel, style = TextStyle(fontSize = 10.5.sp, color = c.faint), maxLines = 1)
            }
            Text(
                "${traffic.aircraft.size}",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, color = if (traffic.aircraft.isEmpty()) c.dim else c.ok),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (map != null) {
                LiveMap(
                    config = map,
                    frame = GeoFrame(map.field, 0.0, emptyList(), map.field),
                    controller = controller,
                    traffic = traffic,
                    thermalColor = { c.vario(it) },
                    modifier = Modifier.fillMaxSize(),
                    trafficOnly = true,
                    onAircraftTap = { id -> selected = id },
                )
            } else {
                Box(Modifier.fillMaxSize().background(c.mapLow), contentAlignment = Alignment.Center) {
                    Text("Carte hors ligne à télécharger dans Prévol", style = TextStyle(fontSize = 12.sp, color = c.dim))
                }
            }
            // zoom : même commande que Pilotage
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 13.dp, bottom = 15.dp).background(c.background, RoundedCornerShape(22.dp))) {
                Text(
                    "+", style = TextStyle(fontSize = 23.sp, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    modifier = Modifier.size(44.dp).clickable(role = Role.Button, onClick = controller::zoomIn).padding(top = 6.dp)
                        .semantics { contentDescription = "Zoom avant" },
                )
                Text(
                    "−", style = TextStyle(fontSize = 23.sp, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    modifier = Modifier.size(44.dp).clickable(role = Role.Button, onClick = controller::zoomOut).padding(top = 6.dp)
                        .semantics { contentDescription = "Zoom arrière" },
                )
            }
            if (map != null) {
                Text(
                    map.attribution + " · Open Glider Network (ODbL)",
                    style = TextStyle(fontSize = 7.sp, color = c.faint),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
                )
            }
            chosen?.let { a ->
                AircraftCard(
                    a, map?.field, map?.fieldId,
                    onFollow = onFollow?.let { f -> { f(a); selected = null } },
                    onClose = { selected = null },
                )
            }
        }
    }
}

/** Fiche de l'aéronef touché : type, altitude, vitesse, montée, âge de la dernière trame. */
@Composable
private fun BoxScope.AircraftCard(
    a: TrafficMark,
    field: com.neutronstar.glidercopilot.domain.LatLon?,
    fieldId: String?,
    onFollow: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val c = Gc.colors
    Column(
        Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 58.dp)
            .widthIn(max = 340.dp)
            .background(c.overlay, RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Fermer la fiche", onClick = onClose),
            ) {
                Text(
                    a.fullLabel.ifBlank { a.label.substringBefore(' ') }.ifBlank { a.typeLabel },
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink),
                    maxLines = 1,
                )
                Text(a.typeLabel + if (a.circling) " · en spirale" else "", style = TextStyle(fontSize = 10.5.sp, color = c.dim))
            }
            if (onFollow != null) {
                Text(
                    "SUIVRE",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = c.onAccent),
                    modifier = Modifier
                        .background(c.ok, RoundedCornerShape(9.dp))
                        .clickable(role = Role.Button, onClickLabel = "Suivre cet aéronef dans Pilotage", onClick = onFollow)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .semantics { contentDescription = "Suivre ${a.fullLabel.ifBlank { a.typeLabel }}" },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Value("ALT", a.altitudeM?.let { "${it.roundToInt()} m" } ?: "—")
            Value("VITESSE", a.speedKmh?.let { "${it.roundToInt()} km/h" } ?: "—")
            Value("VARIO", a.climbMs?.let { String.format(Locale.FRANCE, "%+.1f", it) } ?: "—")
        }
        val dist = if (field != null) Geo.distanceKm(field, a.position) else null
        Text(
            listOfNotNull(
                dist?.let { String.format(Locale.FRANCE, "%.0f km de %s", it, fieldId ?: "terrain") },
                "vu il y a ${a.ageS} s",
            ).joinToString(" · "),
            style = TextStyle(fontSize = 10.5.sp, color = c.faint),
        )
    }
}

@Composable
private fun Value(label: String, value: String) {
    val c = Gc.colors
    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, color = c.dim))
        Text(value, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink), maxLines = 1)
    }
}

