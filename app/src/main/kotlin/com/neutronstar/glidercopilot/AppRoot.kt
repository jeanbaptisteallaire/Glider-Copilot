package com.neutronstar.glidercopilot

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.feature.checklist.ChecklistScreen
import com.neutronstar.glidercopilot.carto.MapStyle
import com.neutronstar.glidercopilot.feature.flight.FlightMapConfig
import com.neutronstar.glidercopilot.feature.flight.FlightScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolViewModel
import kotlinx.coroutines.launch

private const val DISCLAIMER_VERSION = 1

/** Onglets de la maquette v8, dans l'ordre du vol : préparer, vérifier, piloter. */
private enum class Tab(val label: String) { PREVOL("Prévol"), CHECKLIST("Check-lists"), PILOTAGE("Pilotage") }

@Composable
fun AppRoot(container: AppContainer) {
    val ack by container.prefs.acknowledgedDisclaimer.collectAsState(initial = -1)
    val scope = rememberCoroutineScope()
    when {
        ack == -1 -> Box(Modifier.fillMaxSize().background(Gc.colors.background))
        ack < DISCLAIMER_VERSION -> Disclaimer { scope.launch { container.prefs.acknowledgeDisclaimer(DISCLAIMER_VERSION) } }
        else -> MainScaffold(container)
    }
}

@Composable
private fun MainScaffold(container: AppContainer) {
    val c = Gc.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.PREVOL) }
    val prevolVm: PrevolViewModel = viewModel(factory = PrevolViewModel.Factory(container.weather, container.clubs, container.glider))
    val status = rememberFlightStatus()
    val activeMap by container.carto.active.collectAsState()
    val club by container.clubs.selectedClub.collectAsState(initial = null)
    val palette = rememberMapPalette()
    val flightMap = remember(activeMap, club?.id) {
        val m = activeMap ?: return@remember null
        val field = club?.position ?: m.pack.center
        FlightMapConfig(
            styleJson = MapStyle.build(m.pack, m.dir, m.aeroText, palette),
            styleKey = "${m.pack.id}-${m.pack.version}",
            fieldId = club?.airfieldIcao ?: "TERRAIN",
            field = field,
            attribution = "© OpenStreetMap · openAIP · Copernicus",
            fieldElevationM = club?.airfieldIcao?.let { icao -> m.aero.airports.firstOrNull { it.icao == icao }?.elevationM },
        )
    }

    // Localisation demandée une seule fois : club le plus proche et pastille GPS. Refus = club par défaut.
    val asked by container.prefs.locationAsked.collectAsState(initial = true)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.location.refresh()
    }
    LaunchedEffect(asked) {
        if (!asked && !context.hasLocationPermission()) {
            container.prefs.setLocationAsked()
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (!asked) {
            scope.launch { container.prefs.setLocationAsked() }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.PREVOL -> PrevolScreen(prevolVm, container.carto)
                Tab.CHECKLIST -> ChecklistScreen(container.checklist)
                Tab.PILOTAGE -> FlightScreen(status, map = flightMap)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(top = 8.dp).height(62.dp).padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                TabButton(t.label, icon(t), t == tab, Modifier.weight(1f)) { tab = t }
            }
        }
    }
}

private fun icon(t: Tab): ImageVector = when (t) {
    Tab.PREVOL -> GcIcons.Prevol
    Tab.CHECKLIST -> GcIcons.Checklist
    Tab.PILOTAGE -> GcIcons.Pilotage
}

@Composable
private fun TabButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Gc.colors
    val color = if (selected) c.route else c.dim
    Column(
        modifier.fillMaxHeight().clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
        Text(label, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color))
    }
}

@Composable
private fun Disclaimer(onAccept: () -> Unit) {
    val c = Gc.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text("GLIDY", style = Gc.type.giant.copy(color = c.ok, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(16.dp))
        Text(
            "Aide secondaire au vol à voile. L'instrumentation de bord et la veille extérieure priment. " +
                "Cette application ne remplace ni le vario, ni le calculateur, ni le FLARM, et n'est pas un moyen de navigation certifié. " +
                "Le pilote reste seul responsable de la conduite de son vol.",
            style = Gc.type.body.copy(lineHeight = 21.sp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Données : Météo-France via precog, Open Glider Network (ODbL). Les estimations thermiques sont calculées dans l'app et doivent être confrontées au ciel.",
            style = Gc.type.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.ok, contentColor = c.onAccent),
        ) { Text("J'ai compris", style = Gc.type.body.copy(color = c.onAccent, fontWeight = FontWeight.Bold)) }
    }
}

/** Couleurs de carte tirées de la charte GLIDY. */
@Composable
private fun rememberMapPalette(): com.neutronstar.glidercopilot.carto.MapPalette {
    val c = Gc.colors
    return remember(c) {
        fun h(color: androidx.compose.ui.graphics.Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)
        com.neutronstar.glidercopilot.carto.MapPalette(
            background = h(c.background), label = h(c.dim), halo = h(c.background),
            controlled = h(c.air), restricted = h(c.warn), information = h(c.ok), other = h(c.faint),
            airport = h(c.ok), navaid = h(c.dim), route = h(c.route), glider = h(c.ink),
        )
    }
}
