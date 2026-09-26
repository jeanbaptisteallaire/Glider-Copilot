package com.neutronstar.glidy.replay3d

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.IgcTrackPoint
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import java.time.Duration
import org.json.JSONArray
import org.json.JSONObject

private const val REPLAY_URL = "https://appassets.androidplatform.net/assets/replay3d/index.html"

/**
 * Rejeu 3D d'un vol du carnet. [loadTrack] relit la trace complète du fichier IGC (GLIDY S11) ; à défaut,
 * l'aperçu de 512 points de l'index sert de repli. Charte : noir, vert, police système (jetons GLIDY).
 */
@Composable
fun Replay3dScreen(
    flight: ArchivedFlight,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loadTrack: suspend (FlightId) -> List<IgcTrackPoint>? = { null },
) {
    val c = Gc.colors
    val density = LocalDensity.current
    val insetTop = with(density) { WindowInsets.statusBars.getTop(this).toDp().value }
    val insetBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
    val payload by produceState<String?>(initialValue = null, flight.id) {
        value = withContext(Dispatchers.Default) {
            val track = runCatching { loadTrack(flight.id) }.getOrNull()
            val full = track?.takeIf { it.size >= 2 }
                ?.let { runCatching { Replay3dTrackPayload.from(flight, it).takeIf { p -> p.latitudes.size >= 2 }?.toJson() }.getOrNull() }
            val json = full ?: Replay3dPayload.from(flight).toJson()
            json.put("insets", JSONObject().put("top", insetTop).put("bottom", insetBottom))
            json.toString()
        }
    }
    var webView: WebView? by remember { mutableStateOf(null) }
    var engineMessage by remember { mutableStateOf("Lecture de la trace IGC…") }

    DisposableEffect(Unit) {
        onDispose {
            webView?.run {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("AndroidReplay")
                destroy()
            }
            webView = null
        }
    }

    Box(modifier.fillMaxSize().background(c.background)) {
        val json = payload
        if (json != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().testTag("replay-3d-webview"),
                factory = { context ->
                    createReplayWebView(
                        context = context,
                        payload = json,
                        onStatus = { engineMessage = it },
                    ).also { created -> webView = created }
                },
            )
        }

        Box(
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp)
                .size(48.dp)
                .background(c.overlay, CircleShape)
                .border(1.dp, c.line, CircleShape)
                .testTag("close-replay-3d")
                .clickable(onClickLabel = "Fermer le rejeu 3D", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GcIcons.ChevronDown, contentDescription = "Retour", tint = c.ink, modifier = Modifier.size(22.dp).rotate(90f))
        }

        // état du moteur : affiché seulement tant qu'il n'est pas prêt, ou en cas d'erreur
        if (!engineMessage.startsWith("REJEU 3D")) {
            Text(
                text = engineMessage,
                style = Gc.type.bodySmall.copy(color = if (engineMessage.startsWith("ERREUR")) c.warn else c.dim),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)
                    .background(c.overlay, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createReplayWebView(
    context: android.content.Context,
    payload: String,
    onStatus: (String) -> Unit,
): WebView {
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()
    return WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportZoom(false)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        // rendu WebGL continu (60 i/s visé) : le processus de rendu ne doit pas être dépriorisé tant que l'écran est visible
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        addJavascriptInterface(ReplayJavascriptBridge(this, onStatus), "AndroidReplay")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            @Deprecated("Legacy Android callback")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(Uri.parse(url))

            override fun onPageFinished(view: WebView, url: String) {
                if (url == REPLAY_URL) {
                    view.evaluateJavascript("window.GlidyReplay.loadFlight($payload)", null)
                }
            }
        }
        loadUrl(REPLAY_URL)
    }
}

private class ReplayJavascriptBridge(
    private val webView: WebView,
    private val onStatus: (String) -> Unit,
) {
    @JavascriptInterface
    fun onJavascriptReady(@Suppress("UNUSED_PARAMETER") value: String) {
        webView.post { onStatus("MOTEUR 3D · PRÊT") }
    }

    @JavascriptInterface
    fun onReady(value: String) {
        webView.post { onStatus("REJEU 3D · $value") }
    }

    @JavascriptInterface
    fun onError(value: String) {
        webView.post { onStatus("ERREUR 3D · $value") }
    }

    @JavascriptInterface
    fun onMapWarning(@Suppress("UNUSED_PARAMETER") value: String) = Unit
}

/**
 * Charge du rejeu à pleine résolution (S11) : colonnes lat/lon/alt/t (s depuis le premier point), altitude
 * GNSS (baro en repli). Au-delà de [MAX_POINTS], simplification de Douglas-Peucker à 2,5 m qui garde les virages.
 */
internal data class Replay3dTrackPayload(
    val title: String,
    val latitudes: DoubleArray,
    val longitudes: DoubleArray,
    val altitudes: IntArray,
    val seconds: LongArray,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("lat", JSONArray().apply { latitudes.forEach { put(Math.round(it * 1e6) / 1e6) } })
        put("lon", JSONArray().apply { longitudes.forEach { put(Math.round(it * 1e6) / 1e6) } })
        put("alt", JSONArray().apply { altitudes.forEach { put(it) } })
        put("t", JSONArray().apply { seconds.forEach { put(it) } })
    }

    companion object {
        const val MAX_POINTS = 20_000

        fun from(flight: ArchivedFlight, track: List<IgcTrackPoint>): Replay3dTrackPayload {
            val usable = track.filter { (it.gpsAltitudeMeters ?: it.pressureAltitudeMeters) != null }
            val kept = if (usable.size > MAX_POINTS) simplify(usable, 2.5) else usable
            val t0 = kept.firstOrNull()?.timestamp?.epochSecond ?: 0L
            return Replay3dTrackPayload(
                title = flight.file.fileName,
                latitudes = DoubleArray(kept.size) { kept[it].latitude },
                longitudes = DoubleArray(kept.size) { kept[it].longitude },
                altitudes = IntArray(kept.size) { kept[it].gpsAltitudeMeters ?: kept[it].pressureAltitudeMeters ?: 0 },
                seconds = LongArray(kept.size) { kept[it].timestamp.epochSecond - t0 },
            )
        }

        /** Douglas-Peucker itératif en mètres (projection locale + altitude). */
        internal fun simplify(points: List<IgcTrackPoint>, toleranceM: Double): List<IgcTrackPoint> {
            if (points.size < 3) return points
            val lat0 = Math.toRadians(points.first().latitude)
            val x = DoubleArray(points.size) { Math.toRadians(points[it].longitude) * 6_371_000.0 * cos(lat0) }
            val y = DoubleArray(points.size) { Math.toRadians(points[it].latitude) * 6_371_000.0 }
            val z = DoubleArray(points.size) { (points[it].gpsAltitudeMeters ?: points[it].pressureAltitudeMeters ?: 0).toDouble() }
            val keep = BooleanArray(points.size).also { it[0] = true; it[points.size - 1] = true }
            val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.size - 1) }
            while (stack.isNotEmpty()) {
                val (a, b) = stack.removeLast()
                if (b - a < 2) continue
                val dx = x[b] - x[a]; val dy = y[b] - y[a]; val dz = z[b] - z[a]
                val len2 = dx * dx + dy * dy + dz * dz
                var worst = -1.0; var index = -1
                for (i in a + 1 until b) {
                    val px = x[i] - x[a]; val py = y[i] - y[a]; val pz = z[i] - z[a]
                    val u = if (len2 > 0) ((px * dx + py * dy + pz * dz) / len2).coerceIn(0.0, 1.0) else 0.0
                    val ex = px - u * dx; val ey = py - u * dy; val ez = pz - u * dz
                    val d = ex * ex + ey * ey + ez * ez
                    if (d > worst) { worst = d; index = i }
                }
                if (index >= 0 && worst > toleranceM * toleranceM) {
                    keep[index] = true
                    stack.add(a to index); stack.add(index to b)
                }
            }
            return points.filterIndexed { i, _ -> keep[i] }
        }
    }
}

internal data class ReplayPointPayload(val latitude: Double, val longitude: Double, val altitudeMeters: Int)

internal data class Replay3dPayload(
    val title: String,
    val durationSeconds: Long,
    val points: List<ReplayPointPayload>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("durationSeconds", durationSeconds.coerceAtLeast(1L))
        put("points", JSONArray().apply {
            points.forEach { point ->
                put(JSONObject().apply {
                    put("lat", point.latitude)
                    put("lon", point.longitude)
                    put("alt", point.altitudeMeters)
                })
            }
        })
    }

    companion object {
        fun from(flight: ArchivedFlight): Replay3dPayload {
            val fallbackAltitude = flight.summary?.minimumAltitudeMeters ?: 0
            val points = flight.previewTrack.mapIndexed { index, point ->
                ReplayPointPayload(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitudeMeters = flight.altitudeProfileMeters.getOrNull(index) ?: fallbackAltitude,
                )
            }
            val duration = flight.summary?.let { Duration.between(it.startedAt, it.endedAt).seconds } ?: 1L
            return Replay3dPayload(
                title = flight.file.fileName,
                durationSeconds = duration.coerceAtLeast(1L),
                points = points,
            )
        }
    }
}
