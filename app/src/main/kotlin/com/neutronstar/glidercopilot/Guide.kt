package com.neutronstar.glidercopilot

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
private fun LanguageButton(lang: String, onToggle: () -> Unit) {
    val c = Gc.colors
    Row(
        Modifier.clip(CircleShape).background(c.control)
            .clickable(role = Role.Button, onClickLabel = if (lang == "fr") "English" else "Français", onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics { contentDescription = "Langue / Language" }
            .testTag("guide-language"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GcIcons.Language, contentDescription = null, tint = c.ink, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (lang == "fr") "FR" else "EN", style = Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold))
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

private data class Step(@DrawableRes val image: Int, val titleFr: String, val textFr: String, val titleEn: String, val textEn: String)

private enum class Part(val fr: String, val en: String) { PREVOL("Prévol", "Pre-flight"), PILOTAGE("Pilotage", "Flight"), MES_VOLS("Mes vols", "My flights") }

private val STEPS: Map<Part, List<Step>> = linkedMapOf(
    Part.PREVOL to listOf(
        Step(R.drawable.tuto_prevol_club,
            "Votre terrain", "Touchez le terrain en haut de la page pour choisir votre club. La météo suit.",
            "Your airfield", "Tap the airfield at the top of the page to pick your club. The weather follows."),
        Step(R.drawable.tuto_prevol_carte,
            "Carte hors ligne", "Téléchargez la zone où vous volez : la carte fonctionne ensuite sans réseau.",
            "Offline map", "Download the area you fly in: the map then works without network."),
        Step(R.drawable.tuto_prevol_flarm,
            "Appairez votre planeur", "Saisissez l'immatriculation de votre FLARM. Quand il y a du réseau, sa position OGN valide celle du téléphone.",
            "Pair your glider", "Enter your FLARM registration. With network, its OGN position cross-checks the phone's."),
        Step(R.drawable.tuto_prevol_decollage,
            "Décollage détecté", "Activez « Détec. auto. décollage » : chrono et trace démarrent seuls dès 50 km/h.",
            "Take-off detection", "Turn on take-off detection: timer and track start by themselves above 50 km/h."),
    ),
    Part.PILOTAGE to listOf(
        Step(R.drawable.tuto_pilot_marge,
            "Marge de sécurité", "L'altitude en plus (vert) ou en moins (orange) pour rejoindre le terrain avec 300 m de réserve. SÉCU : altitude nécessaire.",
            "Safety margin", "Height above (green) or below (orange) what you need to reach the airfield with 300 m to spare. SÉCU: height needed."),
        Step(R.drawable.tuto_pilot_coupe,
            "Coupe jusqu'au terrain", "Le relief entre vous et l'aérodrome choisi, avec votre plané. Un obstacle sur la route apparaît en rouge.",
            "Profile to the airfield", "The terrain between you and the chosen airfield, with your glide. An obstacle on the way shows in red."),
        Step(R.drawable.tuto_pilot_finesse,
            "Finesse", "Choisissez la finesse des calculs. Plus elle est basse, plus la marge est prudente.",
            "Glide ratio", "Pick the glide ratio used in the computations. The lower it is, the more cautious the margin."),
        Step(R.drawable.tuto_pilot_terrain,
            "Choix du terrain", "Touchez le terrain pour en changer. AUTO : votre club, sinon le meilleur terrain rejoignable. En rouge : hors de portée.",
            "Airfield choice", "Tap the airfield to change it. AUTO: your club, otherwise the best reachable field. Red: out of reach."),
        Step(R.drawable.tuto_pilot_orientation,
            "Orientation de la carte", "1er bouton : carte qui suit le planeur (AUTO) ou libre. 2e bouton : AUTO, nord en haut (N↑) ou route en haut (RTE).",
            "Map orientation", "1st button: map following the glider (AUTO) or free. 2nd button: AUTO, north up (N↑) or track up (RTE)."),
        Step(R.drawable.tuto_pilot_sources,
            "Origine des données", "GPS : position. BARO : baromètre du téléphone pour l'altitude et le vario. DATA : réseau (météo, trafic). Point vert = actif.",
            "Data sources", "GPS: position. BARO: phone barometer for altitude and vario. DATA: network (weather, traffic). Green dot = active."),
        Step(R.drawable.tuto_pilot_pompes,
            "Pompes partagées", "Les planeurs qui spiralent autour de vous (réseau OGN) révèlent les pompes : un cercle coloré selon la montée.",
            "Shared thermals", "Gliders circling around you (OGN network) reveal thermals: a circle coloured by climb rate."),
        Step(R.drawable.tuto_pilot_vario,
            "Vario", "Baromètre + accéléromètre. Vert en descente, orange puis rouge en montée. Moyennes spirale, pompe et jour. Haut-parleur : son du vario.",
            "Vario", "Barometer + accelerometer. Green when sinking, orange then red when climbing. Circle, thermal and day averages. Speaker: vario sound."),
        Step(R.drawable.tuto_pilot_replier,
            "Replier le vario", "La flèche replie le vario pour agrandir la carte.",
            "Fold the vario", "The arrow folds the vario to enlarge the map."),
    ),
    Part.MES_VOLS to listOf(
        Step(R.drawable.tuto_vols_rec,
            "Traces automatiques", "Avec la détection du décollage, la trace s'enregistre dès que le planeur vole et s'arrête à l'atterrissage, tant que l'app reste ouverte. Sinon : interrupteur REC.",
            "Automatic tracks", "With take-off detection, the track records as soon as the glider flies and stops after landing, as long as the app stays open. Otherwise: REC switch."),
        Step(R.drawable.tuto_vols_carnet,
            "Votre carnet", "Chaque vol est rangé dans Mes vols, sur votre téléphone.",
            "Your logbook", "Every flight is stored in My flights, on your phone."),
        Step(R.drawable.tuto_vols_3d,
            "Revoir en 3D", "Ouvrez un vol puis « Rejeu 3D » : votre trace sur le relief, spirales comprises.",
            "Replay in 3D", "Open a flight, then « Rejeu 3D »: your track over the terrain, thermals included."),
    ),
)

/** [onSendFeedback] : vrai si le commentaire a été enregistré. */
@Composable
internal fun TutorialScreen(lang: String, onLanguage: (String) -> Unit, onSendFeedback: suspend (String, String) -> Boolean) {
    GlidyAdaptiveTheme(light = true) {
        val c = Gc.colors
        val fr = lang == "fr"
        val list = rememberLazyListState()
        val scope = rememberCoroutineScope()
        // index de la première carte de chaque partie (en-tête + chips = 1 élément)
        val partStart = remember {
            var i = 1
            STEPS.mapValues { (_, steps) -> i.also { i += steps.size + 1 } }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().background(c.background).testTag("tutorial"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (fr) "Tutoriel" else "Tutorial", style = Gc.type.largeTitle, modifier = Modifier.weight(1f))
                        LanguageButton(lang) { onLanguage(if (fr) "en" else "fr") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        STEPS.keys.forEach { part ->
                            Text(
                                if (fr) part.fr else part.en,
                                style = Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold, color = c.route),
                                modifier = Modifier.clip(CircleShape).background(c.panel)
                                    .clickable(role = Role.Button) { scope.launch { list.animateScrollToItem(partStart.getValue(part)) } }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            STEPS.forEach { (part, steps) ->
                item(key = "title-$part") {
                    Text(if (fr) part.fr else part.en, style = Gc.type.title2, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
                }
                items(steps, key = { it.image }) { step -> StepCard(step, fr) }
            }
            item(key = "feedback") { FeedbackCard(fr, onSendFeedback) }
        }
    }
}

@Composable
private fun StepCard(step: Step, fr: Boolean) {
    val c = Gc.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.panel)) {
        Image(
            painterResource(step.image),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).background(c.sunken)
                .border(0.5.dp, c.lineSoft, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (fr) step.titleFr else step.titleEn, style = Gc.type.headline)
            Text(if (fr) step.textFr else step.textEn, style = Gc.type.subhead.copy(color = c.dim))
        }
    }
}

@Composable
private fun FeedbackCard(fr: Boolean, onSend: suspend (String, String) -> Boolean) {
    val c = Gc.colors
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf(0) } // 0 saisie, 1 envoi, 2 envoyé, 3 échec
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp)).background(c.panel).padding(16.dp),
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
