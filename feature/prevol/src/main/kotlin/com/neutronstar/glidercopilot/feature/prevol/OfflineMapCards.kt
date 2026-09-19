package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.domain.aero.AirspaceFamily
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE).withZone(ZoneId.of("Europe/Paris"))

private fun mb(bytes: Long): String = "${(bytes / 1_000_000.0).roundToInt()} Mo"

/** Carte « Carte hors ligne » : région du club, taille, validité des données aéro, téléchargement. */
@Composable
internal fun OfflineMapCard(ui: OfflineMapUi, onDownload: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    val c = Gc.colors
    val pill: Pair<String, Color> = when {
        ui.progress != null -> "Téléchargement" to c.dim
        ui.installed != null && ui.aeroExpired -> "Aéro à mettre à jour" to c.warn
        ui.updateAvailable -> "Mise à jour" to c.warn
        ui.installed != null -> "Installée" to c.ok
        else -> "Non installée" to c.dim
    }
    val pack = ui.installed ?: ui.available
    GcCard(title = "Carte hors ligne", trailing = { GcPill(pill.first, pill.second) }) {
        when {
            pack != null -> {
                Text(pack.name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink))
                Text(
                    "Fond OpenStreetMap, relief et courbes, espaces aériens et terrains openAIP · ${mb((ui.available ?: pack).totalBytes)}",
                    style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = c.dim),
                )
                ui.installed?.aeroValidUntil?.let {
                    Text(
                        (if (ui.aeroExpired) "Données aéro périmées depuis le " else "Données aéro valables jusqu’au ") + DAY.format(it),
                        style = TextStyle(fontSize = 11.sp, color = if (ui.aeroExpired) c.warn else c.dim),
                    )
                }
            }
            ui.catalogLoading -> Text("Recherche de la carte de la région…", style = TextStyle(fontSize = 11.sp, color = c.dim))
            ui.outOfCoverage -> Text("Pas encore de carte pour ce terrain : les régions couvertes sont celles des clubs du sud de la France.", style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = c.dim))
            ui.catalogError != null -> Text("${ui.catalogError}. La carte se télécharge au sol, avec du réseau.", style = TextStyle(fontSize = 11.sp, color = c.warn))
        }
        ui.progress?.let { p ->
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.control)) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(6.dp).background(c.ok))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${(p * 100).roundToInt()} % · fichiers vérifiés un par un", style = TextStyle(fontSize = 11.sp, color = c.dim))
                GcButton("Annuler", onCancel)
            }
        }
        ui.downloadError?.let { Text("Échec : $it", style = TextStyle(fontSize = 11.sp, color = c.warn)) }
        if (ui.progress == null) {
            when {
                ui.available != null && (ui.installed == null || ui.updateAvailable) ->
                    GcButton(if (ui.installed == null) "Télécharger (${mb(ui.available.totalBytes)})" else "Mettre à jour (${mb(ui.available.totalBytes)})", onDownload, primary = true)
                ui.available == null && ui.installed == null && !ui.catalogLoading && ui.catalogError != null -> GcButton("Réessayer", onRetry)
            }
        }
        Text(
            "© OpenStreetMap (ODbL) · Protomaps · openAIP (CC BY-NC) · Copernicus DEM",
            style = TextStyle(fontSize = 9.5.sp, color = c.faint),
        )
    }
}

/** Espaces aériens autour du terrain du club, lus dans le pack installé. */
@Composable
internal fun AirspacesCard(ui: OfflineMapUi) {
    val c = Gc.colors
    if (ui.installed == null) return
    GcCard(
        title = "Espaces aériens · ${CartoText.RADIUS} km autour de ${ui.fieldLabel ?: "terrain"}",
        trailing = { GcPill("${ui.airspacesAround.size}", c.dim) },
    ) {
        if (ui.airspacesAround.isEmpty()) {
            Text("Aucun espace dans ce rayon selon openAIP.", style = TextStyle(fontSize = 11.sp, color = c.dim))
        }
        ui.airspacesAround.forEach { (a, d) ->
            val color = when (a.family) {
                AirspaceFamily.CONTROLLED -> c.air
                AirspaceFamily.RESTRICTED -> c.warn
                AirspaceFamily.INFORMATION -> c.ok
                AirspaceFamily.OTHER -> c.faint
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink), maxLines = 1)
                    Text(
                        listOfNotNull(a.typeLabel, a.classLetter?.let { "classe $it" }, if (a.byNotam) "activable NOTAM" else null).joinToString(" · "),
                        style = TextStyle(fontSize = 11.sp, color = c.dim),
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 170.dp)) {
                    Text(a.verticalLabel, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color, fontFeatureSettings = "tnum", textAlign = androidx.compose.ui.text.style.TextAlign.End), maxLines = 2)
                    Text(if (d == 0.0) "terrain dedans" else String.format(Locale.FRANCE, "à %.1f km", d), style = TextStyle(fontSize = 11.sp, color = c.dim))
                }
            }
        }
        Text(
            "openAIP" + (ui.aeroFetched?.let { " · données du ${DAY.format(it)}" } ?: "") + " · niveaux de vol en atmosphère standard. Vérifier SUP AIP et NOTAM avant le vol.",
            style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, color = c.faint),
        )
    }
}

/**
 * NOTAM du club (V7.2) : l'app n'interroge aucun flux NOTAM elle-même (aucune fonction de sécurité
 * ne doit dépendre du réseau) — cette carte donne un accès direct au site officiel DGAC pour le
 * terrain du club, seule source à jour et faisant foi avant le vol.
 */
@Composable
internal fun NotamCard(club: Club?) {
    val c = Gc.colors
    val uriHandler = LocalUriHandler.current
    GcCard(title = "NOTAM") {
        Text(
            club?.airfieldIcao?.let { "Terrain du club : ${club.displayName} ($it)" }
                ?: (club?.let { "Terrain du club : ${it.displayName}, code OACI inconnu" } ?: "Choisissez votre club pour préparer la recherche NOTAM."),
            style = TextStyle(fontSize = 12.sp, color = c.dim),
        )
        Text(
            "Les NOTAM ne sont pas rechargés dans l'app : consultez toujours la source officielle avant le vol.",
            style = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, color = c.faint),
        )
        GcButton(
            "Consulter les NOTAM (SOFIA-Briefing, DGAC)",
            onClick = { uriHandler.openUri("https://sofia-briefing.aviation-civile.gouv.fr/sofia/pages/notamsearchaero.html") },
            primary = true,
        )
    }
}

internal object CartoText { const val RADIUS = 15 }
