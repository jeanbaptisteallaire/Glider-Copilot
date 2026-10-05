package com.neutronstar.glidercopilot

import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcButton
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GlidyAdaptiveTheme
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * V19.1 — page « spiral est en développement » (une fois, après la connexion) et tutoriel illustré (FR/EN).
 * Textes au plus court, une capture d'écran réelle par étape (recadrages des captures CI, drawable-nodpi/tuto_*).
 * Le tutoriel se termine par un champ de commentaire libre enregistré dans Supabase (table feedback, écriture seule).
 */

/** Version de la page d'information : l'incrémenter pour la remontrer à tous après une mise à jour. */
internal const val DEV_NOTICE_VERSION = 1

/** Langue effective : choix enregistré, sinon français si le téléphone est en français, sinon anglais. */
internal fun guideLang(saved: String?): String = saved ?: if (Locale.getDefault().language == "fr") "fr" else "en"

/** Petit bouton globe « FR / EN » en haut des pages. */
@Composable
private fun LanguageButton(lang: String, dark: Boolean = false, onToggle: () -> Unit) {
    val c = Gc.colors
    val ink = if (dark) Color.White else c.ink
    Row(
        Modifier.clip(CircleShape).background(if (dark) Color.White.copy(alpha = 0.16f) else c.control)
            .clickable(role = Role.Button, onClickLabel = if (lang == "fr") "English" else "Français", onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics { contentDescription = "Langue / Language" }
            .testTag("guide-language"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GcIcons.Language, contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (lang == "fr") "FR" else "EN", style = Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold, color = ink))
    }
}

// ------------------------------------------------------------------------------------------------
// Page « en développement »
// ------------------------------------------------------------------------------------------------

@Composable
internal fun DevNoticeScreen(lang: String, onLanguage: (String) -> Unit, onContinue: () -> Unit) {
    GlidyAdaptiveTheme(light = true) {
        val c = Gc.colors
        val fr = lang == "fr"
        Column(
            Modifier.fillMaxSize().background(c.panel).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                LanguageButton(lang) { onLanguage(if (fr) "en" else "fr") }
            }
            Spacer(Modifier.height(56.dp))
            Image(painterResource(R.drawable.spiral_logo), contentDescription = null, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                if (fr) "spiral est en cours de développement" else "spiral is still in development",
                style = Gc.type.largeTitle,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (fr) "Nous aimerions avoir vos retours sur les fonctions actuelles, et sur celles que vous aimeriez avoir."
                else "We'd love your feedback on the current features, and on the ones you would like to have.",
                style = Gc.type.body.copy(color = c.ink),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (fr) "Dites-le-nous dans les commentaires du Play Store, ou en bas du tutoriel."
                else "Tell us in the Play Store reviews, or at the bottom of the tutorial.",
                style = Gc.type.body.copy(color = c.dim),
            )
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(c.accentFill)
                    .clickable(role = Role.Button, onClick = onContinue)
                    .semantics { contentDescription = "Continuer" }
                    .testTag("dev-notice-continue"),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (fr) "Continuer" else "Continue", style = Gc.type.headline.copy(color = c.onAccentFill))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Tutoriel
// ------------------------------------------------------------------------------------------------

/** Zone mise en valeur sur une capture pleine page (pixels de l'image, largeur 1080). */
private data class Spot(@DrawableRes val image: Int, val imageHeight: Int, val l: Float, val t: Float, val r: Float, val b: Float)

private data class Step(val spot: Spot, val titleFr: String, val textFr: String, val titleEn: String, val textEn: String)

private enum class Part(val fr: String, val en: String) { PREVOL("Prévol", "Pre-flight"), PILOTAGE("Pilotage", "Flight"), MES_VOLS("Mes vols", "My flights") }

private val STEPS: Map<Part, List<Step>> = linkedMapOf(
    Part.PREVOL to listOf(
        Step(Spot(R.drawable.tour_prevol, 4060, 460f, 40f, 1070f, 155f),
            "Votre terrain", "Touchez le terrain en haut de la page pour choisir votre club. La météo suit.",
            "Your airfield", "Tap the airfield at the top of the page to pick your club. The weather follows."),
        Step(Spot(R.drawable.tour_prevol, 4060, 30f, 204f, 1050f, 690f),
            "Carte hors ligne", "Téléchargez la zone où vous volez : la carte fonctionne ensuite sans réseau.",
            "Offline map", "Download the area you fly in: the map then works without network."),
        Step(Spot(R.drawable.tour_prevol, 4060, 30f, 3296f, 1050f, 3620f),
            "Appairez votre planeur", "Saisissez l'immatriculation de votre FLARM. Quand il y a du réseau, sa position OGN valide celle du téléphone.",
            "Pair your glider", "Enter your FLARM registration. With network, its OGN position cross-checks the phone's."),
        Step(Spot(R.drawable.tour_prevol, 4060, 30f, 3630f, 1050f, 3782f),
            "Décollage détecté", "Activez « Détec. auto. décollage » : chrono et trace démarrent seuls dès 50 km/h.",
            "Take-off detection", "Turn on take-off detection: timer and track start by themselves above 50 km/h."),
    ),
    Part.PILOTAGE to listOf(
        Step(Spot(R.drawable.tour_pilotage, 2210, 15f, 10f, 500f, 320f),
            "Marge de sécurité", "L'altitude en plus (vert) ou en moins (orange) pour rejoindre le terrain avec 300 m de réserve. SÉCU : altitude nécessaire.",
            "Safety margin", "Height above (green) or below (orange) what you need to reach the airfield with 300 m to spare. SÉCU: height needed."),
        Step(Spot(R.drawable.tour_pilotage, 2210, 0f, 360f, 1080f, 600f),
            "Coupe jusqu'au terrain", "Le relief entre vous et l'aérodrome choisi, avec votre plané. Un obstacle sur la route apparaît en rouge.",
            "Profile to the airfield", "The terrain between you and the chosen airfield, with your glide. An obstacle on the way shows in red."),
        Step(Spot(R.drawable.tour_pilotage, 2210, 680f, 15f, 1060f, 210f),
            "Finesse", "Choisissez la finesse des calculs. Plus elle est basse, plus la marge est prudente.",
            "Glide ratio", "Pick the glide ratio used in the computations. The lower it is, the more cautious the margin."),
        Step(Spot(R.drawable.tour_terrain, 2210, 290f, 210f, 1050f, 1520f),
            "Choix du terrain", "Touchez le terrain pour en changer. AUTO : votre club, sinon le meilleur terrain rejoignable. En rouge : hors de portée.",
            "Airfield choice", "Tap the airfield to change it. AUTO: your club, otherwise the best reachable field. Red: out of reach."),
        Step(Spot(R.drawable.tour_pilotage, 2210, 20f, 625f, 285f, 745f),
            "Orientation de la carte", "1er bouton : carte qui suit le planeur (AUTO) ou libre. 2e bouton : AUTO, nord en haut (N↑) ou route en haut (RTE).",
            "Map orientation", "1st button: map following the glider (AUTO) or free. 2nd button: AUTO, north up (N↑) or track up (RTE)."),
        Step(Spot(R.drawable.tour_pilotage, 2210, 20f, 1505f, 175f, 1710f),
            "Origine des données", "GPS : position. BARO : baromètre du téléphone pour l'altitude et le vario. DATA : réseau (météo, trafic). Point vert = actif.",
            "Data sources", "GPS: position. BARO: phone barometer for altitude and vario. DATA: network (weather, traffic). Green dot = active."),
        Step(Spot(R.drawable.tour_pilotage, 2210, 440f, 1320f, 640f, 1520f),
            "Pompes partagées", "Les planeurs qui spiralent autour de vous (réseau OGN) révèlent les pompes : un cercle coloré selon la montée.",
            "Shared thermals", "Gliders circling around you (OGN network) reveal thermals: a circle coloured by climb rate."),
        Step(Spot(R.drawable.tour_pilotage, 2210, 0f, 1830f, 920f, 2030f),
            "Vario", "Baromètre + accéléromètre. Vert en descente, orange puis rouge en montée. Moyennes spirale, pompe et jour. Haut-parleur : son du vario.",
            "Vario", "Barometer + accelerometer. Green when sinking, orange then red when climbing. Circle, thermal and day averages. Speaker: vario sound."),
        Step(Spot(R.drawable.tour_pilotage, 2210, 930f, 1930f, 1045f, 2020f),
            "Replier le vario", "La flèche replie le vario pour agrandir la carte.",
            "Fold the vario", "The arrow folds the vario to enlarge the map."),
    ),
    Part.MES_VOLS to listOf(
        Step(Spot(R.drawable.tour_rec, 2210, 460f, 1720f, 815f, 1805f),
            "Traces automatiques", "Avec la détection du décollage, la trace s'enregistre dès que le planeur vole et s'arrête à l'atterrissage, tant que l'app reste ouverte. Sinon : interrupteur REC.",
            "Automatic tracks", "With take-off detection, the track records as soon as the glider flies and stops after landing, as long as the app stays open. Otherwise: REC switch."),
        Step(Spot(R.drawable.tour_vols, 2210, 20f, 1520f, 1060f, 2030f),
            "Votre carnet", "Chaque vol est rangé dans Mes vols, sur votre téléphone.",
            "Your logbook", "Every flight is stored in My flights, on your phone."),
        Step(Spot(R.drawable.tour_3d, 2210, 120f, 560f, 980f, 1480f),
            "Revoir en 3D", "Ouvrez un vol puis « Rejeu 3D » : votre trace sur le relief, spirales comprises.",
            "Replay in 3D", "Open a flight, then « Rejeu 3D »: your track over the terrain, thermals included."),
    ),
)

/*
 * V19.1b — visite guidée façon « coach marks » : la vraie page (capture pleine page, données d'exemple, affichage
 * immédiat) avec un voile noir à 50 % et un cadre bleu clair lumineux qui vient entourer chaque zone d'intérêt.
 * Le cadre glisse d'une zone à l'autre, la page défile toute seule (Prévol) et change de page (Pilotage, Mes vols).
 * Texte en blanc sur le voile, au-dessus ou au-dessous de la zone. Dernière étape : « Votre avis » (Supabase).
 */
private val GLOW = Color(0xFF5AC8FA)

/** [onSendFeedback] : vrai si le commentaire a été enregistré. [onClose] : fin ou « Passer ». */
@Composable
internal fun TutorialScreen(
    lang: String,
    onLanguage: (String) -> Unit,
    onSendFeedback: suspend (String, String) -> Boolean,
    onClose: () -> Unit,
) {
    GlidyAdaptiveTheme(light = true) {
        val fr = lang == "fr"
        val flat = remember { STEPS.flatMap { (part, steps) -> steps.map { part to it } } }
        var index by rememberSaveable { mutableIntStateOf(0) }
        BackHandler { if (index > 0) index-- else onClose() }
        if (index >= flat.size) {
            FeedbackPage(fr, lang, onLanguage, onSendFeedback, onBack = { index = flat.size - 1 }, onDone = onClose)
        } else {
            val (part, step) = flat[index]
            SpotlightPage(
                step = step, part = part, fr = fr, lang = lang, onLanguage = onLanguage,
                position = index + 1, total = flat.size,
                onPrevious = { if (index > 0) index-- },
                onNext = { index++ },
                onSkip = onClose,
            )
        }
    }
}

@Composable
private fun SpotlightPage(
    step: Step, part: Part, fr: Boolean, lang: String, onLanguage: (String) -> Unit,
    position: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit,
) {
    val spot = step.spot
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.White).testTag("tutorial"),
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val scale = wPx / 1080f
        val viewport = hPx / scale // hauteur visible, en pixels d'image
        val bottomReserve = with(density) { 150.dp.toPx() } / scale
        val topReserve = with(density) { 40.dp.toPx() } / scale
        // défilement : la zone vers 40 % de l'écran, sans laisser de vide inutile
        val cy = (spot.t + spot.b) / 2f
        val maxScroll = spot.imageHeight - viewport + bottomReserve
        val target = (cy - viewport * 0.4f).coerceIn(-topReserve, maxOf(-topReserve, maxScroll))
        val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 140f)
        val scroll by animateFloatAsState(target, spec, label = "défilement")
        val l by animateFloatAsState(spot.l, spec, label = "g")
        val t by animateFloatAsState(spot.t, spec, label = "h")
        val r by animateFloatAsState(spot.r, spec, label = "d")
        val b by animateFloatAsState(spot.b, spec, label = "b")
        val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
            0.55f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
        )

        // la page (capture pleine largeur), en fondu quand on change de page
        Crossfade(spot.image to spot.imageHeight, animationSpec = tween(350), label = "page") { (img, imgH) ->
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Image(
                    painterResource(img),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .height(with(density) { (imgH * scale).toDp() })
                        .offset { IntOffset(0, (-scroll * scale).roundToInt()) },
                )
            }
        }

        // voile 50 % + découpe + cadre lumineux
        val pad = with(density) { 6.dp.toPx() }
        val left = l * scale - pad
        val top = (t - scroll) * scale - pad
        val right = r * scale + pad
        val bottom = (b - scroll) * scale + pad
        val radius = with(density) { 14.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val hole = RoundRect(left, top, right, bottom, CornerRadius(radius))
            val veil = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(hole)
            }
            drawPath(veil, Color.Black.copy(alpha = 0.5f))
            val stroke = 3.dp.toPx()
            for (k in 3 downTo 1) {
                drawRoundRect(
                    GLOW.copy(alpha = 0.16f * pulse), Offset(left - k * stroke, top - k * stroke),
                    Size(right - left + 2 * k * stroke, bottom - top + 2 * k * stroke), CornerRadius(radius + k * stroke),
                    style = Stroke(width = stroke * 1.4f),
                )
            }
            drawRoundRect(GLOW.copy(alpha = 0.75f + 0.25f * pulse), Offset(left, top), Size(right - left, bottom - top), CornerRadius(radius), style = Stroke(width = stroke))
        }

        // texte en blanc sur le voile, du côté de la zone où il reste le plus de place
        val spaceBelow = hPx - with(density) { 120.dp.toPx() } - bottom
        val below = spaceBelow >= top
        val textBlock: @Composable () -> Unit = {
            // bulle sombre derrière le texte blanc : lisible même au-dessus d'une page chargée
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${if (fr) part.fr else part.en} · $position / $total",
                    style = Gc.type.footnote.copy(color = GLOW, fontWeight = FontWeight.SemiBold),
                )
                Text(if (fr) step.titleFr else step.titleEn, style = Gc.type.title2.copy(color = Color.White))
                Text(if (fr) step.textFr else step.textEn, style = Gc.type.body.copy(color = Color.White.copy(alpha = 0.92f)))
            }
        }
        if (below) {
            Box(Modifier.fillMaxWidth().padding(top = with(density) { (bottom + 18.dp.toPx()).toDp() })) { textBlock() }
        } else {
            Box(
                Modifier.fillMaxWidth().height(with(density) { (top - 18.dp.toPx()).coerceAtLeast(0f).toDp() }),
                contentAlignment = Alignment.BottomStart,
            ) { textBlock() }
        }

        // commandes, en bas, sur le voile
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (fr) "Passer" else "Skip",
                style = Gc.type.subhead.copy(color = Color.White.copy(alpha = 0.8f)),
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 10.dp).testTag("tour-skip"),
            )
            LanguageButton(lang, dark = true) { onLanguage(if (fr) "en" else "fr") }
            Spacer(Modifier.weight(1f))
            if (position > 1) {
                TourButton(if (fr) "Précédent" else "Back", filled = false, onClick = onPrevious)
            }
            TourButton(if (fr) "Suivant" else "Next", filled = true, onClick = onNext, tag = "tour-next")
        }
    }
}

@Composable
private fun TourButton(text: String, filled: Boolean, onClick: () -> Unit, tag: String = "") {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .then(if (filled) Modifier.background(GLOW) else Modifier.border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(22.dp)))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Gc.type.headline.copy(color = if (filled) Color(0xFF00243A) else Color.White))
    }
}

/** Dernière étape : commentaire libre, sur page blanche. */
@Composable
private fun FeedbackPage(
    fr: Boolean, lang: String, onLanguage: (String) -> Unit, onSend: suspend (String, String) -> Boolean,
    onBack: () -> Unit, onDone: () -> Unit,
) {
    val c = Gc.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("tutorial-feedback"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (fr) "C'est tout !" else "That's it!", style = Gc.type.largeTitle, modifier = Modifier.weight(1f))
            LanguageButton(lang) { onLanguage(if (fr) "en" else "fr") }
        }
        FeedbackCard(fr, onSend)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GcButton(if (fr) "Revoir" else "Back", onClick = onBack, modifier = Modifier.weight(1f), fontSize = 15f)
            GcButton(if (fr) "Terminer" else "Done", onClick = onDone, primary = true, modifier = Modifier.weight(1f).testTag("tour-done"), fontSize = 15f)
        }
        Spacer(Modifier.height(12.dp))
    }
}


@Composable
private fun FeedbackCard(fr: Boolean, onSend: suspend (String, String) -> Boolean) {
    val c = Gc.colors
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf(0) } // 0 saisie, 1 envoi, 2 envoyé, 3 échec
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (fr) "Votre avis" else "Your feedback", style = Gc.type.title3)
        Text(
            if (fr) "Ce qui marche, ce qui manque, ce que vous aimeriez avoir : écrivez-nous."
            else "What works, what's missing, what you'd like to have: write to us.",
            style = Gc.type.subhead.copy(color = c.dim),
        )
        if (state == 2) {
            Text(if (fr) "Merci ! Message envoyé." else "Thank you! Message sent.", style = Gc.type.headline.copy(color = c.ok))
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("feedback-text"),
                placeholder = { Text(if (fr) "Votre commentaire…" else "Your comment…", style = Gc.type.body.copy(color = c.faint)) },
                textStyle = Gc.type.body,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.route, unfocusedBorderColor = c.inputLine, cursorColor = c.route,
                    focusedTextColor = c.ink, unfocusedTextColor = c.ink,
                ),
            )
            GcButton(
                if (state == 1) (if (fr) "Envoi…" else "Sending…") else (if (fr) "Envoyer" else "Send"),
                onClick = {
                    state = 1
                    scope.launch {
                        val ok = onSend(text, if (fr) "fr" else "en")
                        state = if (ok) 2 else 3
                        if (ok) text = ""
                    }
                },
                primary = true,
                enabled = state != 1 && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("feedback-send"),
                fontSize = 15f,
            )
            if (state == 3) {
                Text(
                    if (fr) "Envoi impossible (réseau ?). Réessayez plus tard." else "Could not send (network?). Please try again later.",
                    style = Gc.type.footnote.copy(color = c.warn),
                )
            }
        }
        Text(
            if (fr) "Message anonyme, lié à votre compte si vous êtes connecté. Ne mettez pas d'informations personnelles."
            else "Anonymous message, linked to your account if you are signed in. Please don't include personal information.",
            style = Gc.type.footnote.copy(color = c.faint),
        )
    }
}
