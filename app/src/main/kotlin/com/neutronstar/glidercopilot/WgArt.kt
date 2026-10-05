package com.neutronstar.glidercopilot

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.neutronstar.glidercopilot.designsystem.GcFonts

/*
 * V20 « Wind Glider » — outils de l'univers illustré.
 * Les illustrations (format ~9:16 à 9:21) sont posées en bas de l'écran ; sur un téléphone plus haut, le ciel est
 * prolongé par sa couleur unie, avec un fondu doux sur le bord de l'image : aucune bande visible.
 */

/** Illustration plein écran ancrée en bas, ciel prolongé vers le haut si l'écran est plus haut que l'image. */
@Composable
internal fun ScenicBackground(
    @DrawableRes image: Int,
    imageWidth: Int,
    imageHeight: Int,
    sky: Color,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(sky)) {
        val drawnH = maxWidth * (imageHeight.toFloat() / imageWidth)
        if (drawnH >= maxHeight) {
            Image(
                painterResource(image), contentDescription = null, contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize().then(imageModifier),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(drawnH).align(Alignment.BottomCenter).then(imageModifier)) {
                Image(painterResource(image), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
                // fondu ciel → image sur 72 dp : la jonction disparaît
                Box(Modifier.fillMaxWidth().height(72.dp).background(Brush.verticalGradient(listOf(sky, sky.copy(alpha = 0f)))))
            }
        }
    }
}

/** Titre de marque : Inter en capitales, lettres espacées, blanc avec une ombre très douce (lisible sur le ciel). */
internal fun brandTitleStyle(size: TextUnit = 58.sp, color: Color = Color.White) = TextStyle(
    fontFamily = GcFonts.ui,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    lineHeight = size * 1.12f,
    letterSpacing = size * 0.06f,
    textAlign = TextAlign.Center,
    color = color,
    shadow = Shadow(Color(0x40102030), blurRadius = 18f),
)

internal const val APP_NAME_CAPS = "WIND GLIDER"

/** Bouton de l'univers « Wind Glider » : carte blanche aux coins doux, texte bleu nuit, ombre légère. */
@Composable
internal fun WgButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String = "", enabled: Boolean = true) {
    androidx.compose.foundation.layout.Box(
        modifier
            .height(56.dp)
            .then(androidx.compose.ui.Modifier.shadow(10.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp), ambientColor = Color(0x33102030), spotColor = Color(0x33102030)))
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text,
            style = TextStyle(fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 0.2.sp, color = WG_INK),
        )
    }
}

internal val WG_INK = Color(0xFF1D3550)

/*
 * V20.2 — couverture animée : l'animation HTML de JB (« Session 20.1/animation » : océan et nuage peints qui glissent,
 * deux planeurs biplaces, touches de gouache, 10 images/s) jouée telle quelle dans une WebView, depuis les assets
 * (`assets/cover/`, calques en WebP). Fond #073E60 identique à celui de l'animation : aucun flash au chargement.
 * Les touchers ne sont pas transmis à la page (elle n'a aucune commande).
 */
internal val COVER_SEA = Color(0xFF073E60)

@android.annotation.SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun CoverAnimation(modifier: Modifier = Modifier) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val context = androidx.compose.ui.platform.LocalContext.current
    val web = androidx.compose.runtime.remember {
        // V20.2b : les calques ne s'affichaient pas en file:///android_asset (fond bleu uni sur téléphone) —
        // chargement par WebViewAssetLoader (origine https locale), comme le rejeu 3D.
        val assets = androidx.webkit.WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        android.webkit.WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(0xFF073E60.toInt())
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(
                    view: android.webkit.WebView,
                    request: android.webkit.WebResourceRequest,
                ): android.webkit.WebResourceResponse? = assets.shouldInterceptRequest(request.url)
            }
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isFocusable = false
            setOnTouchListener { _, _ -> true }
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            loadUrl("https://appassets.androidplatform.net/assets/cover/index.html")
        }
    }
    androidx.compose.runtime.DisposableEffect(lifecycle, web) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> web.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> web.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); web.stopLoading(); web.destroy() }
    }
    Box(modifier.fillMaxSize().background(COVER_SEA)) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * V20.2 — titre de marque d'après la maquette de JB : « WIND » au-dessus de « GLIDER », lignes resserrées, G et R
 * légèrement plus grands, et de chaque côté de GLIDER trois traits en « ailes de pilote ». Inter Medium, capitales.
 */
@Composable
internal fun BrandTitle(size: TextUnit = 46.sp, color: Color = Color.White, modifier: Modifier = Modifier) {
    val base = TextStyle(
        fontFamily = GcFonts.ui, fontWeight = FontWeight.Medium, fontSize = size, lineHeight = size,
        letterSpacing = size * 0.07f, color = color, shadow = Shadow(Color(0x40001A2A), blurRadius = 16f),
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
        ),
    )
    val big = base.copy(fontSize = size * 1.16f, lineHeight = size * 1.16f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val wingW = with(density) { (size * 0.95f).toDp() }
    val wingH = with(density) { (size * 0.6f).toDp() }
    androidx.compose.foundation.layout.Column(
        modifier.semantics { contentDescription = "Wind Glider" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text("WIND", style = base)
        androidx.compose.foundation.layout.Spacer(Modifier.height(with(density) { (size * 0.16f).toDp() }))
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            PilotWing(left = true, color = color, modifier = Modifier.size(wingW, wingH))
            androidx.compose.foundation.layout.Spacer(Modifier.width(with(density) { (size * 0.12f).toDp() }))
            androidx.compose.material3.Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(big.toSpanStyle().copy(baselineShift = androidx.compose.ui.text.style.BaselineShift(-0.07f))) { append("G") }
                    append("LIDE")
                    withStyle(big.toSpanStyle().copy(letterSpacing = 0.sp, baselineShift = androidx.compose.ui.text.style.BaselineShift(-0.07f))) { append("R") }
                },
                style = base,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(with(density) { (size * 0.12f).toDp() }))
            PilotWing(left = false, color = color, modifier = Modifier.size(wingW, wingH))
        }
    }
}

/** Trois traits d'aile, du plus long (haut) au plus court (bas), extrémité extérieure biseautée. */
@Composable
private fun PilotWing(left: Boolean, color: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val t = size.height * 0.2f // épaisseur ≈ fût des lettres
        val gap = (size.height - 3 * t) / 2f
        val slant = t * 0.9f
        for (i in 0 until 3) {
            val len = size.width * (1f - 0.17f * i)
            val y = i * (t + gap)
            val path = androidx.compose.ui.graphics.Path().apply {
                if (left) {
                    moveTo(size.width - len, y); lineTo(size.width, y); lineTo(size.width, y + t); lineTo(size.width - len + slant, y + t)
                } else {
                    moveTo(0f, y); lineTo(len, y); lineTo(len - slant, y + t); lineTo(0f, y + t)
                }
                close()
            }
            drawPath(path, color)
        }
    }
}

/** V20.2 — apparition en fondu (et légère montée) après [delayMs] : rythme de la couverture animée. */
@Composable
internal fun rememberFadeIn(delayMs: Long, durationMs: Int = 650): androidx.compose.runtime.State<Float> {
    val a = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs)
        a.animateTo(1f, androidx.compose.animation.core.tween(durationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    return a.asState()
}

/** Modificateur d'apparition : opacité [p] et montée de 10 dp. */
internal fun Modifier.fadeUp(p: Float): Modifier = this.then(
    Modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 10.dp.toPx() },
)
