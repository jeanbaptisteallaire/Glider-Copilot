package com.neutronstar.glidercopilot

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GlidyAdaptiveTheme
import com.neutronstar.glidercopilot.designsystem.GlidyColors
import com.neutronstar.glidercopilot.designsystem.GlidyLightColors
import com.neutronstar.glidercopilot.feature.checklist.ChecklistScreen
import com.neutronstar.glidercopilot.carto.MapStyle
import com.neutronstar.glidercopilot.feature.flight.FlightMapConfig
import com.neutronstar.glidercopilot.feature.flight.FlightScreen
import com.neutronstar.glidercopilot.feature.flight.TrafficMapScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolViewModel
import kotlinx.coroutines.launch

private const val DISCLAIMER_VERSION = 1

/** Onglets de la maquette v8, dans l'ordre du vol : préparer, vérifier, piloter. */
private enum class Tab(val label: String) { PREVOL("Prévol"), CHECKLIST("Check-lists"), PILOTAGE("Pilotage"), CARTE("Carte") }

@Composable
fun AppRoot(container: AppContainer) {
    // écran d'accueil (V7.2) : logo et fond affichés ensemble, sans transition entre les deux, à chaque ouverture
    var splashDone by rememberSaveable { mutableStateOf(false) }
    if (!splashDone) {
        SplashScreen(onDone = { splashDone = true })
        return
    }
    val ack by container.prefs.acknowledgedDisclaimer.collectAsState(initial = -1)
    val scope = rememberCoroutineScope()
    when {
        ack == -1 -> Box(Modifier.fillMaxSize().background(Gc.colors.background))
        ack < DISCLAIMER_VERSION -> Disclaimer { scope.launch { container.prefs.acknowledgeDisclaimer(DISCLAIMER_VERSION) } }
        else -> MainScaffold(container)
    }
}

private val SPLASH_BLUE = Color(0xFF3E7DB5)

/** Écran d'accueil (V7.2) : fond bleu et logo écureuil affichés d'un bloc, nom et slogan dessous. */
@Composable
private fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1600)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().background(SPLASH_BLUE).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.splash_glidy),
            contentDescription = null,
            modifier = Modifier.size(200.dp).clip(androidx.compose.foundation.shape.CircleShape),
        )
        Spacer(Modifier.height(22.dp))
        Text("Glidy", style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-0.5).sp))
        Spacer(Modifier.height(4.dp))
        Text("Glide easy", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f), letterSpacing = 1.2.sp))
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
    val traffic by container.ogn.traffic.collectAsState()
    val ognUi by container.ogn.network.collectAsState()
    val live by container.flight.live.collectAsState()
    // réseau OGN et moteur de vol actifs tant que l'app est visible ; en arrière-plan seulement via le service de vol
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                container.ogn.start()
                container.flight.start()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && !FlightService.running) {
                container.ogn.stop()
                container.flight.stop()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // service de premier plan : écran Pilotage ouvert ou vol enregistré (lancé app visible, exigence Android 12+)
    val recording = live.snapshot?.recording == true
    LaunchedEffect(tab, recording) {
        if (tab == Tab.PILOTAGE || recording) FlightService.start(context, container.flight.replay)
        else FlightService.stop(context)
    }
    val activeMap by container.carto.active.collectAsState()
    val club by container.clubs.selectedClub.collectAsState(initial = null)
    val lightMode by container.prefs.lightMode.collectAsState(initial = false)
    // Pilotage reste toujours sombre ; la Carte suit le mode clair (V7.2). Deux rendus du même pack.
    val flightMapDark = remember(activeMap, club?.id) { buildFlightMapConfig(activeMap, club, mapPalette(GlidyColors)) }
    val flightMapLight = remember(activeMap, club?.id) { buildFlightMapConfig(activeMap, club, mapPalette(GlidyLightColors)) }
    val flightMap = flightMapDark

    // Localisation demandée une seule fois : club le plus proche et pastille GPS. Refus = club par défaut.
    val asked by container.prefs.locationAsked.collectAsState(initial = true)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.location.refresh()
        // GPS accordé après le démarrage du moteur : on relance pour s'abonner
        container.flight.stop(); container.flight.start()
    }
    LaunchedEffect(asked) {
        if (!asked && !context.hasLocationPermission()) {
            container.prefs.setLocationAsked()
            val perms = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
                if (android.os.Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            launcher.launch(perms.toTypedArray())
        } else if (!asked) {
            scope.launch { container.prefs.setLocationAsked() }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.PREVOL -> GlidyAdaptiveTheme(lightMode) { PrevolScreen(prevolVm, container.carto, container.ogn, container.flight) }
                Tab.CHECKLIST -> GlidyAdaptiveTheme(lightMode) { ChecklistScreen(container.checklist) }
                Tab.PILOTAGE -> FlightScreen(status, map = flightMap, traffic = traffic, live = live, controls = container.flight)
                Tab.CARTE -> GlidyAdaptiveTheme(lightMode) {
                    TrafficMapScreen(
                        map = if (lightMode) flightMapLight else flightMapDark, traffic = traffic, networkLabel = ognUi.statusLabel,
                        // « Suivre » : l'aéronef touché passe au centre de Pilotage, et l'app y bascule (V7.1)
                        onFollow = { a ->
                            scope.launch { container.glider.followAddress(a.id, a.fullLabel) }
                            tab = Tab.PILOTAGE
                        },
                    )
                }
            }
            // mode clair : un seul interrupteur, partagé par Prévol/Check-lists/Carte (V7.2). Jamais sur Pilotage.
            if (tab != Tab.PILOTAGE) {
                LightModeToggle(
                    lightMode,
                    onToggle = { on -> scope.launch { container.prefs.setLightMode(on) } },
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 10.dp),
                )
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
    Tab.CARTE -> GcIcons.Carte
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

/**
 * Interrupteur de mode clair (V7.2), commun à Prévol/Check-lists/Carte. Rendu en dehors du sous-thème
 * de l'onglet (pastille sombre fixe) pour rester lisible que le fond soit clair ou sombre.
 */
@Composable
private fun LightModeToggle(on: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color(0xCC1C1C1E), RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable(role = Role.Switch, onClickLabel = if (on) "Mode sombre" else "Mode clair") { onToggle(!on) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Mode clair"; stateDescription = if (on) "activé" else "désactivé" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(if (on) GcIcons.LightMode else GcIcons.DarkMode, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(if (on) "Clair" else "Sombre", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
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

/**
 * Palette de la carte hors ligne dérivée d'une charte donnée. Fonction pure (pas de dépendance à
 * Gc.colors) : V7.2 a besoin de deux rendus simultanés (Pilotage sombre, Carte claire ou sombre).
 */
private fun mapPalette(c: com.neutronstar.glidercopilot.designsystem.GcColors): com.neutronstar.glidercopilot.carto.MapPalette {
    fun h(color: androidx.compose.ui.graphics.Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)
    return com.neutronstar.glidercopilot.carto.MapPalette(
        background = h(c.background), label = h(c.dim), halo = h(c.background),
        controlled = h(c.air), restricted = h(c.warn), information = h(c.ok), other = h(c.faint),
        // V7.2 : vecteur de retour au terrain en mauve, charte aéronautique
        airport = h(c.ok), navaid = h(c.dim), route = h(c.heading), glider = h(c.ink),
    )
}

private fun buildFlightMapConfig(
    activeMap: ActiveMap?,
    club: com.neutronstar.glidercopilot.domain.Club?,
    palette: com.neutronstar.glidercopilot.carto.MapPalette,
): FlightMapConfig? {
    val m = activeMap ?: return null
    val field = club?.position ?: m.pack.center
    return FlightMapConfig(
        styleJson = MapStyle.build(
            m.pack, m.dir, m.aeroText, palette,
            runwaysGeoJson = com.neutronstar.glidercopilot.carto.RunwayGeoJson.build(m.aero.airports),
        ),
        styleKey = "${m.pack.id}-${m.pack.version}",
        fieldId = club?.airfieldIcao ?: "TERRAIN",
        field = field,
        attribution = "© OpenStreetMap · openAIP · Copernicus",
        fieldElevationM = club?.airfieldIcao?.let { icao -> m.aero.airports.firstOrNull { it.icao == icao }?.elevationM },
    )
}
