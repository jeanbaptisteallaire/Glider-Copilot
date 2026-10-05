package com.neutronstar.glidercopilot

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.neutronstar.glidercopilot.designsystem.GcLegal
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcFonts
import com.neutronstar.glidercopilot.designsystem.GlidyAdaptiveTheme
import kotlinx.coroutines.delay

/**
 * V20 — couverture « Wind Glider » : illustration (prairie, planeur, manche à air, lever de soleil), titre en Inter
 *   capitales espacées, deux boutons blancs. Ciel prolongé en haut sur les écrans très hauts (ScenicBackground).
 * - Animation : l'illustration « respire » (zoom de 4 % sur 20 s), à peine perceptible.
 * - Parallaxe : le doigt posé décale très légèrement l'illustration et le titre ; tout revient au relâché.
 * - Sans compte connecté : « Continuer avec Google » (si configuré) et « Continuer en invité », à chaque ouverture.
 *   Compte déjà connecté : la page sert d'écran de lancement (1,6 s) puis l'app s'ouvre.
 */
@Composable
internal fun WelcomeScreen(cloud: CloudHost, onContinue: () -> Unit) {
    val account by cloud.state.collectAsState()
    // décidé une fois à l'ouverture : connecté → simple écran de lancement
    val signedInAtStart = remember { account.email != null }
    var leaving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LightStatusBar()

    LaunchedEffect(signedInAtStart) {
        if (signedInAtStart) { delay(1600); onContinue() }
    }
    // connexion Google réussie depuis cette page : on entre dans l'app
    LaunchedEffect(account.email) {
        if (!signedInAtStart && account.email != null && !leaving) { leaving = true; delay(400); onContinue() }
    }

    val density = LocalDensity.current
    // V20 : respiration très lente de l'illustration (zoom de 4 % sur 20 s), à peine perceptible
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(durationMillis = 20_000, easing = FastOutSlowInEasing)) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val parallax by animateOffsetAsState(
        targetValue = touch,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "parallaxe",
    )

    GlidyAdaptiveTheme(light = true) {
        val c = Gc.colors
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(c.brandSky)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        fun follow(p: Offset) {
                            touch = Offset(
                                ((p.x / size.width) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f,
                                ((p.y / size.height) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f,
                            )
                        }
                        follow(down.position)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            follow(change.position)
                        }
                        touch = Offset.Zero
                    }
                },
        ) {
            val h = constraints.maxHeight.toFloat()
            val shift = with(density) { 8.dp.toPx() }
            // V20 « Wind Glider » : couverture illustrée (prairie, planeur, manche à air), ciel prolongé en haut
            ScenicBackground(
                R.drawable.wg_cover, 941, 1954, c.brandSky,
                imageModifier = Modifier.graphicsLayer {
                    val z = 1.04f + 0.04f * rise.value
                    scaleX = z
                    scaleY = z
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    translationX = -parallax.x * shift
                    translationY = -parallax.y * shift
                },
            )

            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(with(density) { (h * 0.09f).toDp() }))
                Text(
                    "WIND\nGLIDER",
                    style = brandTitleStyle(),
                    modifier = Modifier.graphicsLayer { translationX = parallax.x * shift * 0.5f },
                )
                Spacer(Modifier.weight(1f))
                if (!signedInAtStart) {
                    account.message?.takeIf { !account.busy && account.email == null }?.let { msg ->
                        Text(
                            msg,
                            style = Gc.type.footnote.copy(color = c.brandInk, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth(0.78f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (account.configured && account.googleAvailable) {
                        WelcomeButton(
                            text = "Continuer avec Google",
                            enabled = !account.busy && !leaving,
                            leading = { GoogleMark(busy = account.busy) },
                        ) { (context as? Activity)?.let { cloud.signInWithGoogle(it) } }
                        Spacer(Modifier.height(16.dp))
                    }
                    WelcomeButton(text = "Continuer en invité", enabled = !leaving) { leaving = true; onContinue() }
                    Spacer(Modifier.height(18.dp))
                    LegalNotice()
                    Spacer(Modifier.height(with(density) { (h * 0.06f).toDp() }))
                }
            }
        }
    }
}

/** V20 — bouton de la couverture : carte blanche aux coins doux, texte bleu nuit (maquette « Wind Glider »). */
@Composable
private fun WelcomeButton(text: String, enabled: Boolean, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = Gc.colors
    Box(
        Modifier
            .fillMaxWidth(0.74f)
            .height(56.dp)
            .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x33102030), spotColor = Color(0x33102030))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Text(text, style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 0.2.sp, color = if (enabled) c.brandInk else c.dim))
        }
    }
}

/**
 * Logo « G » officiel, fourni par Google Play Services (googleg_standard_color_18), conformément à la charte
 * « Sign in with Google ». Référencé par son nom : la ressource est conservée par res/raw/keep.xml.
 */
@SuppressLint("DiscouragedApi")
@Composable
private fun GoogleMark(busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Gc.colors.route)
        return
    }
    val context = LocalContext.current
    val id = remember { context.resources.getIdentifier("googleg_standard_color_18", "drawable", context.packageName) }
    if (id != 0) Image(painterResource(id), contentDescription = null, modifier = Modifier.size(22.dp))
}

/** Icônes de barre d'état sombres sur la photo claire ; réglage d'origine rétabli en quittant la page. */
@Composable
private fun LightStatusBar() {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val nav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false // V20 : icônes blanches sur le ciel bleu
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = nav
        }
    }
}

/** V19 — mention légale sous les boutons (V20 : texte blanc discret sur la prairie). */
@Composable
private fun LegalNotice() {
    val uri = LocalUriHandler.current
    val style = TextStyle(fontFamily = GcFonts.ui, fontSize = 12.sp, color = Color.White, shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), blurRadius = 8f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Conditions",
            style = style.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.clickable(role = Role.Button) { uri.openUri(GcLegal.TERMS) }.padding(vertical = 8.dp, horizontal = 6.dp),
        )
        Text("·", style = style)
        Text(
            "Confidentialité",
            style = style.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.clickable(role = Role.Button) { uri.openUri(GcLegal.PRIVACY) }.padding(vertical = 8.dp, horizontal = 6.dp),
        )
    }
}
