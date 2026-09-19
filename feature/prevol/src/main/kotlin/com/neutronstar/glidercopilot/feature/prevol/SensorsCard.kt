package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcKpi
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.GcSwitch
import kotlinx.coroutines.flow.StateFlow

/** Capteurs du téléphone et vols enregistrés, fournis par le moteur de vol de l'app. */
interface SensorsSource {
    val sensors: StateFlow<SensorsUi>
    fun setSound(on: Boolean)
    fun setVoice(on: Boolean)
    fun testSound()
    fun testVoice()
    fun share(file: IgcFileUi)
}

data class IgcFileUi(val name: String, val detail: String, val path: String)

data class SensorsUi(
    val running: Boolean = false,
    val replay: Boolean = false,
    /** Source du vario en ce moment : « Vario baro+accél. », « Vario OGN · 6 s », « Vario indisponible ». */
    val sourceLabel: String = "Moteur de vol à l'arrêt",
    val sourceOk: Boolean = false,
    val baroPresent: Boolean = false,
    val baroHz: String = "—",
    val baroNoise: String = "—",
    val accelHz: String = "—",
    val gpsAccuracy: String = "—",
    val altitudeLine: String = "",
    val flightLine: String = "",
    val soundOn: Boolean = false,
    val voiceOn: Boolean = true,
    val testing: Boolean = false,
    val flights: List<IgcFileUi> = emptyList(),
    /** Journaux de calibration (V7.3) : données brutes baro/accél./GPS d'un vol d'essai, à transmettre pour affiner le filtre. */
    val calibrations: List<IgcFileUi> = emptyList(),
)

/** Carte « Capteurs & vols » : qualité du baromètre, accéléromètre et GPS, essais son et voix, traces IGC. */
@Composable
internal fun SensorsCard(ui: SensorsUi, source: SensorsSource) {
    val c = Gc.colors
    GcCard(
        title = "Capteurs & vols",
        trailing = { GcPill(if (ui.replay) "Rejeu" else if (ui.sourceOk) "Prêt" else if (ui.running) "Dégradé" else "À l'arrêt", if (ui.replay) c.warn else if (ui.sourceOk) c.ok else c.dim) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(if (ui.sourceOk) c.statusOn else c.statusOff, CircleShape))
            Text(ui.sourceLabel, style = TextStyle(fontSize = 11.sp, color = c.dim))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GcKpi(ui.baroHz, "Baro", Modifier.weight(1f), color = if (ui.baroPresent) c.ink else c.warn)
            GcKpi(ui.baroNoise, "Bruit", Modifier.weight(1f))
            GcKpi(ui.accelHz, "Accél.", Modifier.weight(1f))
            GcKpi(ui.gpsAccuracy, "GPS", Modifier.weight(1f))
        }
        if (ui.altitudeLine.isNotEmpty()) Text(ui.altitudeLine, style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = c.dim))
        if (ui.flightLine.isNotEmpty()) Text(ui.flightLine, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Son du vario", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink))
                Text("Bips en montée, grave sous −1,7 m/s, continue écran éteint", style = TextStyle(fontSize = 10.5.sp, color = c.dim))
            }
            GcSwitch(ui.soundOn, source::setSound, Modifier.semantics { contentDescription = "Son du vario" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Annonces vocales", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink))
                Text("Décollage, atterrissage, perte du baromètre", style = TextStyle(fontSize = 10.5.sp, color = c.dim))
            }
            GcSwitch(ui.voiceOn, source::setVoice, Modifier.semantics { contentDescription = "Annonces vocales" })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GcButton(if (ui.testing) "Essai en cours…" else "Essai du son", source::testSound, Modifier.weight(1f), enabled = !ui.testing)
            GcButton("Essai de la voix", source::testVoice, Modifier.weight(1f))
        }
        Text("Vols enregistrés (IGC)", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink), modifier = Modifier.padding(top = 4.dp))
        if (ui.flights.isEmpty()) {
            Text("Aucun vol pour l'instant : la trace démarre au décollage détecté.", style = TextStyle(fontSize = 11.sp, color = c.dim))
        }
        ui.flights.take(5).forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.ink), maxLines = 1)
                    Text(f.detail, style = TextStyle(fontSize = 10.5.sp, color = c.dim), maxLines = 1)
                }
                Text(
                    "Partager",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.ok),
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Partager ${f.name}") { source.share(f) }.padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }
        if (ui.calibrations.isNotEmpty()) {
            Text("Journaux de calibration", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink), modifier = Modifier.padding(top = 4.dp))
            ui.calibrations.take(5).forEach { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.ink), maxLines = 1)
                        Text(f.detail, style = TextStyle(fontSize = 10.5.sp, color = c.dim), maxLines = 1)
                    }
                    Text(
                        "Partager",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.ok),
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Partager ${f.name}") { source.share(f) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
            Text(
                "Enregistrés depuis la bascule « calib » de Pilotage : baromètre, accélération verticale et GPS bruts, " +
                    "à transmettre après un vol d'essai pour affiner le filtre du vario — jamais utilisés en vol.",
                style = TextStyle(fontSize = 9.5.sp, lineHeight = 13.sp, color = c.faint),
            )
        }
        Text(
            "Vario : filtre de Kalman baromètre + accéléromètre ; sans baromètre, montée OGN de mon planeur en secours (retard de plusieurs secondes). " +
                "IGC d'enregistreur non approuvé : trace indicative, sans valeur pour un badge.",
            style = TextStyle(fontSize = 9.5.sp, lineHeight = 13.sp, color = c.faint),
        )
    }
}
