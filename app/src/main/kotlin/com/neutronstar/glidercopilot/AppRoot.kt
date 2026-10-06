package com.neutronstar.glidercopilot

import android.Manifest
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.neutronstar.glidy.feed.FeedApp
import com.neutronstar.glidercopilot.designsystem.GcThemeToggle
import com.neutronstar.glidercopilot.designsystem.LocalGcThemeToggle
import com.neutronstar.glidercopilot.designsystem.GcFonts
import androidx.compose.runtime.CompositionLocalProvider
import com.neutronstar.glidercopilot.designsystem.GlidyFlightTheme
import com.neutronstar.glidercopilot.designsystem.GcLegalLinks
import com.neutronstar.glidercopilot.designsystem.GlidyFlightLightColors
import com.neutronstar.glidercopilot.designsystem.GlidyAdaptiveTheme
import com.neutronstar.glidercopilot.designsystem.GlidyColors
import com.neutronstar.glidercopilot.designsystem.GlidyLightColors
import androidx.compose.material3.HorizontalDivider
import com.neutronstar.glidercopilot.feature.checklist.ChecklistScreen
import com.neutronstar.glidercopilot.carto.MapStyle
import com.neutronstar.glidercopilot.feature.flight.FlightMapConfig
import com.neutronstar.glidercopilot.feature.flight.FlightScreen
import com.neutronstar.glidercopilot.feature.flight.TrafficMapScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolScreen
import com.neutronstar.glidercopilot.feature.prevol.PrevolViewModel
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.myflights.MyFlightsApp
import com.neutronstar.glidy.replay3d.Replay3dScreen
import kotlinx.coroutines.launch

private const val DISCLAIMER_VERSION = 1

/** Onglets de la maquette v8, dans l'ordre du vol : préparer, vérifier, piloter. */
/** S18 Lite : seuls Prévol (météo + cartes), Pilotage et Mes vols. Édition complète : tous les onglets. */
/** V19.1 : onglet Tuto tout à gauche, dans les deux éditions. */
private val visibleTabs: List<Tab> get() = if (BuildConfig.LITE) listOf(Tab.TUTO, Tab.PREVOL, Tab.PILOTAGE, Tab.MES_VOLS) else Tab.entries

/** V20 : libellés dans la langue choisie (les mêmes que les cartes du menu d'accueil). */
private enum class Tab(val label: String, val labelEn: String) {
    TUTO("Tuto", "Tuto"), FEED("Feed", "Feed"), PREVOL("Prévol", "Pre-flight"), CHECKLIST("Check-lists", "Checklists"),
    PILOTAGE("Pilotage", "Flight"), CARTE("Carte", "Map"), MES_VOLS("Mes vols", "My flights");
    fun label(lang: String) = if (lang == "fr") label else labelEn
}

/** V20 : la page « Avant de voler » s'affiche une fois par lancement de l'app (pas à chaque changement d'onglet). */
private object PilotNoticeSession { var shown = false }

@Composable
fun AppRoot(container: AppContainer) {
    // V18.3 : page d'accueil Wind Glider (connexion Google ou invité), affichée à chaque ouverture tant qu'aucun compte
    // n'est connecté ; avec un compte, elle sert d'écran de lancement.
    var splashDone by rememberSaveable { mutableStateOf(false) }
    if (!splashDone) {
        WelcomeScreen(container.cloud, onContinue = { splashDone = true })
        return
    }
    val ack by container.prefs.acknowledgedDisclaimer.collectAsState(initial = -1)
    val notice by container.prefs.devNoticeSeen.collectAsState(initial = -1)
    val savedLang by container.prefs.guideLanguage.collectAsState(initial = null)
    // V20.1 : tutoriel obligatoire par profil — invité une fois par téléphone, puis chaque NOUVEAU compte connecté
    val tutorialSeen by container.prefs.tutorialSeen.collectAsState(initial = null)
    val account by container.cloud.state.collectAsState()
    val tutorialKey = tutorialKeyFor(account.email)
    val liveForTuto by container.flight.live.collectAsState()
    val flyingNow = liveForTuto.snapshot?.recording == true
    val tutorialDone: Boolean? = tutorialSeen?.let { seen -> tutorialKey in seen || flyingNow }
    val scope = rememberCoroutineScope()
    when {
        ack == -1 || notice == -1 || tutorialDone == null -> Box(Modifier.fillMaxSize().background(Gc.colors.background))
        // V19.1 : page « Wind Glider est en développement », une seule fois, juste après la connexion
        notice < DEV_NOTICE_VERSION -> DevNoticeScreen(
            lang = guideLang(savedLang),
            onLanguage = { l -> scope.launch { container.prefs.setGuideLanguage(l) } },
            onContinue = { scope.launch { container.prefs.setDevNoticeSeen(DEV_NOTICE_VERSION) } },
        )
        ack < DISCLAIMER_VERSION -> Disclaimer { scope.launch { container.prefs.acknowledgeDisclaimer(DISCLAIMER_VERSION) } }
        // V20 : tutoriel complet et obligatoire au premier lancement (ni « Passer », ni retour système)
        tutorialDone == false -> TutorialScreen(
            lang = guideLang(savedLang),
            onLanguage = { l -> scope.launch { container.prefs.setGuideLanguage(l) } },
            onSendFeedback = { text, lang -> container.cloud.sendFeedback(text, lang) },
            onClose = { scope.launch { container.prefs.markTutorialSeen(tutorialKey) } },
            mandatory = true,
        )
        else -> {
            // V18.5 : menu d'accueil Wind Glider à chaque ouverture ; le retour système y ramène depuis les onglets
            var section by rememberSaveable { mutableStateOf<Tab?>(null) }
            val current = section
            if (current == null) {
                val prevolVm: PrevolViewModel = viewModel(factory = PrevolViewModel.Factory(container.weather, container.clubs, container.glider))
                HomeMenuScreen(
                    prevol = prevolVm,
                    lang = guideLang(savedLang),
                    onPrevol = { section = Tab.PREVOL },
                    onPilotage = { section = Tab.PILOTAGE },
                    onMyFlights = { section = Tab.MES_VOLS },
                    onTutorial = { section = Tab.TUTO },
                )
            } else {
                MainScaffold(container, startTab = current, onHome = { section = null }, startTour = current == Tab.TUTO)
            }
        }
    }
}

@Composable
private fun MainScaffold(container: AppContainer, startTab: Tab, onHome: () -> Unit, startTour: Boolean = false) {
    val c = Gc.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(startTab) }
    // V19.1b : onglet à retrouver en quittant le tutoriel (null : ouvert depuis le menu → retour au menu)
    var tutorialReturn by rememberSaveable { mutableStateOf<Tab?>(null) }
    // V18.5 : retour système → menu d'accueil (les retours internes, rejeu 3D par exemple, restent prioritaires)
    BackHandler(onBack = onHome)
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
    // V18.5 : retour au menu d'accueil depuis Pilotage → service arrêté, sauf vol en cours d'enregistrement
    val recordingNow by androidx.compose.runtime.rememberUpdatedState(recording)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (!recordingNow) FlightService.stop(context) }
    }
    val activeMap by container.carto.active.collectAsState()
    val club by container.clubs.selectedClub.collectAsState(initial = null)
    val lightMode by container.prefs.lightMode.collectAsState(initial = true)
    // Pilotage reste toujours sombre ; la Carte suit le mode clair (V7.2). Deux rendus du même pack.
    val flightMapDark = remember(activeMap, club?.id) { buildFlightMapConfig(activeMap, club, mapPalette(GlidyColors)) }
    // V18.7 — carte claire façon carte VFR papier (Pilotage et Carte en thème clair)
    val flightMapLight = remember(activeMap, club?.id) { buildFlightMapConfig(activeMap, club, paperChartPalette()) }
    val flightMap = if (lightMode) flightMapLight else flightMapDark

    // Localisation demandée une seule fois : club le plus proche et pastille GPS. Refus = club par défaut.
    val asked by container.prefs.locationAsked.collectAsState(initial = true)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.location.refresh()
        // GPS accordé après le démarrage du moteur : on relance pour s'abonner
        container.flight.stop(); container.flight.start()
    }
    // V19 (Google Play) : information claire avant la demande système — à quoi sert la position, quand elle est lue,
    // qu'elle reste sur le téléphone. Aucune demande de localisation en arrière-plan (ACCESS_BACKGROUND_LOCATION).
    var locationInfo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(asked) {
        if (!asked && !context.hasLocationPermission()) {
            locationInfo = true
        } else if (!asked) {
            scope.launch { container.prefs.setLocationAsked() }
        }
    }
    if (locationInfo) {
        LocationDisclosure(
            onContinue = {
                locationInfo = false
                scope.launch { container.prefs.setLocationAsked() }
                val perms = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
                    if (android.os.Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
                launcher.launch(perms.toTypedArray())
            },
            onLater = {
                locationInfo = false
                scope.launch { container.prefs.setLocationAsked() }
            },
        )
    }

    // S10 — rejeu 3D d'un vol du carnet : plein écran, barre d'onglets masquée, retour système = retour au carnet
    var replayed by remember { mutableStateOf<ArchivedFlight?>(null) }
    LaunchedEffect(Unit) {
        if (!container.flights.openReplayOnStart) return@LaunchedEffect
        container.flights.openReplayOnStart = false
        tab = Tab.MES_VOLS
        repeat(20) {
            val newest = runCatching { container.flights.repository.listFlights() }.getOrNull()
                ?.maxByOrNull { it.summary?.startedAt?.toEpochMilli() ?: Long.MIN_VALUE }
            if (newest != null) { replayed = newest; return@LaunchedEffect }
            kotlinx.coroutines.delay(500)
        }
    }
    // S15 — thème social blanc ; V18.7 : Pilotage passe aussi sur fond blanc (accord JB), barre d'onglets claire
    val social = lightMode && replayed == null
    SystemBarsFor(social)
    val replay = replayed
    if (replay != null) {
        BackHandler { replayed = null }
        Replay3dScreen(
            flight = replay,
            onBack = { replayed = null },
            modifier = Modifier.fillMaxSize(),
            loadTrack = { id -> container.flights.repository.loadTrack(id) },
        )
        return
    }

    // V20 : onglet Tuto = page illustrée (tutoriel, avis) ; tutoriel et avis en plein écran, barre d'onglets masquée.
    // Ouvert depuis la case « Tutoriel » du menu : le tutoriel démarre directement et la fermeture ramène au menu.
    val savedLang by container.prefs.guideLanguage.collectAsState(initial = null)
    val lang = guideLang(savedLang)
    val setLang: (String) -> Unit = { l -> scope.launch { container.prefs.setGuideLanguage(l) } }
    var tour by rememberSaveable { mutableStateOf(startTour) }
    var feedbackOnly by rememberSaveable { mutableStateOf(false) }
    if (tab == Tab.TUTO && tour) {
        TutorialScreen(
            lang = lang,
            onLanguage = setLang,
            onSendFeedback = { text, l -> container.cloud.sendFeedback(text, l) },
            onClose = { tour = false; if (startTour && tutorialReturn == null) onHome() },
        )
        return
    }
    if (tab == Tab.TUTO && feedbackOnly) {
        FeedbackScreen(lang, setLang, onSend = { text, l -> container.cloud.sendFeedback(text, l) }, onDone = { feedbackOnly = false })
        return
    }
    // V20 : « Avant de voler » (téléphone fixé, accord de l'instructeur), une fois par lancement
    var pilotNotice by remember { mutableStateOf(!PilotNoticeSession.shown) }
    if (tab == Tab.PILOTAGE && pilotNotice) {
        PilotNoticeScreen(lang, setLang) { PilotNoticeSession.shown = true; pilotNotice = false }
        return
    }

    Column(Modifier.fillMaxSize().background(if (social) (if (tab == Tab.PILOTAGE) GlidyFlightLightColors.background else GlidyLightColors.background) else c.background).statusBarsPadding()) {
        // S15 : l'interrupteur clair/sombre est dans l'en-tête de chaque écran (jamais sur Pilotage)
        val themeToggle = remember(lightMode) { GcThemeToggle(lightMode) { on -> scope.launch { container.prefs.setLightMode(on) } } }
        Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalGcThemeToggle provides themeToggle) {
            when (tab) {
                Tab.FEED -> GlidyAdaptiveTheme(lightMode) { FeedApp(container.social) }
                Tab.TUTO -> TutoHomeScreen(lang, setLang, onTutorial = { tour = true }, onFeedback = { feedbackOnly = true })
                Tab.PREVOL -> GlidyAdaptiveTheme(lightMode) { PrevolScreen(prevolVm, container.carto, container.ogn, container.flight, lite = BuildConfig.LITE) }
                Tab.CHECKLIST -> GlidyAdaptiveTheme(lightMode) { ChecklistScreen(container.checklist) }
                Tab.PILOTAGE -> GlidyFlightTheme(lightMode) { FlightScreen(status, map = flightMap, traffic = traffic, live = live, controls = container.flight) }
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
                Tab.MES_VOLS -> GlidyAdaptiveTheme(lightMode) {
                    val account by container.cloud.state.collectAsState()
                    val cloudChanges by container.cloud.changes.collectAsState()
                    LaunchedEffect(Unit) { container.cloud.onShown() }
                    MyFlightsApp(
                        account = account,
                        accountActions = container.cloud,
                        refreshSignal = cloudChanges,
                        profileStore = container.profile,
                        sharingEnabled = !BuildConfig.LITE,
                        repository = container.flights.repository,
                        shareGateway = container.flights.shareGateway,
                        completedFlightGateway = container.flights.completedGateway,
                        demoFlight = { FlightArchiveHost.DEMO_NAME to context.assets.open(FlightArchiveHost.DEMO_ASSET) },
                        onReplay3d = { f ->
                            // pas de rejeu 3D pendant un vol enregistré : GPU et batterie restent au pilotage
                            if (recording) Toast.makeText(context, "Rejeu 3D après l'atterrissage", Toast.LENGTH_LONG).show()
                            else replayed = f
                        },
                    )
                }
            }
        } }
        // barre d'onglets : blanche en thème clair (V18.7 : Pilotage compris) ; noire en thème sombre
        GlidyAdaptiveTheme(social) {
            val bar = Gc.colors
            Column(Modifier.fillMaxWidth().background(if (social) bar.overlay else bar.background)) {
                if (social) HorizontalDivider(thickness = 0.5.dp, color = bar.line)
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp).height(62.dp).padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visibleTabs.forEach { t ->
                        TabButton(t.label(lang), icon(t), t == tab, Modifier.weight(1f)) { if (t == Tab.TUTO) tutorialReturn = tab; tab = t }
                    }
                }
            }
        }
    }
}

private fun icon(t: Tab): ImageVector = when (t) {
    Tab.FEED -> GcIcons.Tab.Feed
    Tab.PREVOL -> GcIcons.Tab.Prevol
    Tab.CHECKLIST -> GcIcons.Tab.Checklist
    Tab.TUTO -> GcIcons.Tab.Tuto
    Tab.PILOTAGE -> GcIcons.Tab.Pilotage
    Tab.CARTE -> GcIcons.Tab.Carte
    Tab.MES_VOLS -> GcIcons.Tab.MesVols
}

@Composable
private fun TabButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    // V18.1 : bleu ciel sélectionné / gris Apple sinon ; sous Pilotage (sombre) : vert / gris, inchangé
    val color = if (social) (if (selected) c.route else c.faint) else if (selected) c.route else c.dim
    Column(
        modifier.fillMaxHeight().clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(label, style = TextStyle(fontFamily = GcFonts.ui, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = color))
    }
}

/**
 * S15 — icônes de la barre d'état foncées sur les onglets blancs. Ailleurs (Pilotage, rejeu 3D) : le réglage
 * d'origine de la fenêtre est rétabli, pour que « En vol » reste exactement comme avant.
 */
@Composable
private fun SystemBarsFor(light: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    val initialStatus = remember(window) { controller.isAppearanceLightStatusBars }
    val initialNav = remember(window) { controller.isAppearanceLightNavigationBars }
    SideEffect {
        controller.isAppearanceLightStatusBars = if (light) true else initialStatus
        controller.isAppearanceLightNavigationBars = if (light) true else initialNav
    }
}

/** V19 — divulgation de la localisation (règles Google Play « Prominent disclosure »), avant la boîte système. */
@Composable
private fun LocationDisclosure(onContinue: () -> Unit, onLater: () -> Unit) {
    GlidyAdaptiveTheme(light = true) {
        val c = Gc.colors
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Position GPS", style = Gc.type.headline) },
            text = {
                Text(
                    "Wind Glider utilise la position de votre téléphone pour trouver le terrain le plus proche et, en vol, pour " +
                        "calculer vario, marge de sécurité, distance au terrain et enregistrer votre trace IGC.\n\n" +
                        "Pendant un vol, la position continue d'être lue écran éteint ; une notification permanente " +
                        "l'indique et le service s'arrête quand vous quittez Pilotage (hors vol enregistré).\n\n" +
                        "La position reste sur ce téléphone. Elle n'est envoyée en ligne que dans les vols que vous " +
                        "choisissez de sauvegarder avec un compte. Les notifications servent à afficher ce service de vol.",
                    style = Gc.type.subhead.copy(color = c.dim),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onContinue, modifier = Modifier.testTag("location-continue")) {
                    Text("Continuer", style = Gc.type.headline.copy(color = c.route))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onLater) {
                    Text("Plus tard", style = Gc.type.subhead.copy(color = c.dim))
                }
            },
            containerColor = c.panel,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

/** V20 — avertissement de sécurité (premier lancement) sur la couverture illustrée, texte sur carte claire. */
@Composable
private fun Disclaimer(onAccept: () -> Unit) {
    GlidyAdaptiveTheme(light = true) {
        val c = Gc.colors
        Box(Modifier.fillMaxSize()) {
            CoverAnimation() // V20.2 : même couverture animée que l'ouverture
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.94f))
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    Text(APP_NAME_CAPS, style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 3.sp, color = c.brandInk))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Aide secondaire au vol à voile. Ne remplace ni le vario, ni le calculateur, ni le FLARM, ni une navigation certifiée. " +
                            "Instruments de bord et veille extérieure priment. Le pilote reste seul responsable.",
                        style = Gc.type.body.copy(lineHeight = 22.sp, color = c.brandInk),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Données météo : PRECOG — precog-api.com (Source : Météo-France). Trafic : OGN (ODbL). Estimations thermiques calculées dans l'app : à confronter au ciel.",
                        style = Gc.type.footnote.copy(color = c.brandInk.copy(alpha = 0.7f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    GcLegalLinks(color = c.route, withDeletion = false)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.brandInk, contentColor = androidx.compose.ui.graphics.Color.White),
                    ) { Text("J'ai compris", style = Gc.type.headline.copy(color = androidx.compose.ui.graphics.Color.White)) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
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

/**
 * V18.7 — carte claire inspirée des cartes aéronautiques VFR papier (OACI 1:500 000) et des applications de
 * navigation à fond clair : terrain crème, forêts vert pâle, eau bleu clair, routes rouge brique, courbes brunes,
 * relief ombré chaud. Les familles d'espaces aériens restent séparées : contrôlés en bleu (trait plein), zones
 * réglementées en rouge-magenta (tireté), information en vert ; terrains en magenta, cap de retour en violet.
 */
private fun paperChartPalette() = com.neutronstar.glidercopilot.carto.MapPalette(
    background = "#F4F1E4", earth = "#F4F1E4", wood = "#CFE3BF", water = "#A9D3F0", waterLine = "#5EA9DD",
    roadMajor = "#D46A4F", roadMinor = "#D9C3A0", label = "#2B2B2B", halo = "#FFFFFF",
    contour = "#8A6D3B", hillShadow = "#6B5A3A", hillHighlight = "#FFFFFF",
    controlled = "#1F5FBF", restricted = "#C0185A", information = "#2E8B57", other = "#6C6C70",
    airport = "#B3127C", navaid = "#1F5FBF", route = "#6A1FB0", glider = "#111111", traffic = "#0B5CAD",
    contourOpacity = 0.25, contourIndexOpacity = 0.45, airspaceFillOpacity = 0.07, airspaceLineWidth = 2.0,
    hillshadeExaggeration = 0.4,
)

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
