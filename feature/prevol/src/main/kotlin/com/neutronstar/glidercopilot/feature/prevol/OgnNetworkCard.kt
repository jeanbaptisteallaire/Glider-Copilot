package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcKpi
import com.neutronstar.glidercopilot.designsystem.GcPill
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** État du réseau OGN en direct, fourni par l'app. */
interface OgnNetworkSource {
    val network: StateFlow<OgnNetworkUi>
}

data class OgnNetworkUi(
    val statusLabel: String = "Réseau OGN non connecté",
    val connected: Boolean = false,
    val replay: Boolean = false,
    val radiusKm: Int = 100,
    val aircraftCount: Int = 0,
    val thermalCount: Int = 0,
    /** Cadence mesurée : intervalle médian entre deux trames d'un même aéronef (s). */
    val medianIntervalS: Double? = null,
    /** Retard médian réception − émission (s). */
    val medianLatencyS: Double? = null,
    val frames: Long = 0,
    val ownLabel: String? = null,
    val ownLine: String? = null,
    val ownSeen: Boolean = false,
    /** Trames reçues la dernière minute et âge de la dernière : diagnostic de la connexion. */
    val framesPerMin: Int = 0,
    val lastFrameAgoS: Long? = null,
    /** Message d'alerte quand la connexion est ouverte mais muette (indicatif ou filtre refusé). */
    val warning: String? = null,
    /**
     * État de l'annuaire des immatriculations (DDB), à vérifier sans avoir à voler (S8, correctif) :
     * si absent, tout le trafic autour de toi s'affichera par son adresse radio, pas d'immatriculation.
     */
    val ddbStatus: String = "Base des immatriculations (DDB) : jamais chargée",
)

private fun s1(v: Double?) = v?.let { String.format(Locale.FRANCE, "%.1f s", it) } ?: "—"

/** Carte « Réseau OGN » : connexion, trafic et pompes autour du terrain, mon planeur, cadence et retard mesurés. */
@Composable
internal fun OgnNetworkCard(ui: OgnNetworkUi) {
    val c = Gc.colors
    GcCard(
        title = "Réseau OGN · ${ui.radiusKm} km",
        trailing = { GcPill(if (ui.replay) "Rejeu" else if (ui.connected) "En direct" else "Hors ligne", if (ui.replay) c.warn else if (ui.connected) c.ok else c.dim) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(if (ui.warning != null) c.warn else if (ui.connected) c.statusOn else c.statusOff, CircleShape))
            Text(ui.statusLabel, style = TextStyle(fontSize = 11.sp, color = c.dim))
        }
        ui.warning?.let { Text(it, style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = c.warn)) }
        if (ui.connected) {
            Text(
                "${ui.framesPerMin} trames/min" + (ui.lastFrameAgoS?.let { " · dernière il y a $it s" } ?: " · aucune trame encore"),
                style = TextStyle(fontSize = 10.5.sp, color = c.faint),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GcKpi("${ui.aircraftCount}", "Aéronefs", Modifier.weight(1f))
            GcKpi("${ui.thermalCount}", "Pompes", Modifier.weight(1f), color = if (ui.thermalCount > 0) c.ok else c.ink)
            GcKpi(s1(ui.medianIntervalS), "Cadence", Modifier.weight(1f))
            GcKpi(s1(ui.medianLatencyS), "Retard", Modifier.weight(1f))
        }
        if (ui.ownLabel != null) {
            Column(Modifier.padding(top = 2.dp)) {
                Text("Mon planeur · ${ui.ownLabel}", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (ui.ownSeen) c.ok else c.ink))
                Text(ui.ownLine ?: "Pas encore reçu depuis la connexion.", style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = c.dim))
            }
        }
        Text(ui.ddbStatus, style = TextStyle(fontSize = 10.5.sp, color = c.faint))
        Text(
            (if (ui.replay) "Rejeu d'un enregistrement OGN anonymisé (démonstration). " else "") +
                "Open Glider Network (ODbL) · furtifs, « no-tracking » et refus DDB ignorés · rien n'est conservé au-delà de 30 min. " +
                "Le vario OGN arrive avec retard : secours uniquement.",
            style = TextStyle(fontSize = 9.5.sp, lineHeight = 13.sp, color = c.faint),
        )
    }
}
