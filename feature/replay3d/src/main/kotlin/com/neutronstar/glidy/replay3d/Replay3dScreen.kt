package com.neutronstar.glidy.replay3d

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import java.time.Duration
import org.json.JSONArray
import org.json.JSONObject

private const val REPLAY_URL = "https://appassets.androidplatform.net/assets/replay3d/index.html"

@Composable
fun Replay3dScreen(
    flight: ArchivedFlight,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payload = remember(flight) { Replay3dPayload.from(flight).toJson().toString() }
    var webView: WebView? by remember { mutableStateOf(null) }
    var engineMessage by remember { mutableStateOf("Initialisation du moteur 3D") }

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

    Box(modifier.fillMaxSize().background(ComposeColor(0xFFE3E7E6))) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("replay-3d-webview"),
            factory = { context ->
                createReplayWebView(
                    context = context,
                    payload = payload,
                    onStatus = { engineMessage = it },
                ).also { created -> webView = created }
            },
        )

        Row(
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp).testTag("close-replay-3d").clickable(onClick = onBack),
                shape = CircleShape,
                color = ComposeColor(0xCC111719),
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("‹", color = ComposeColor.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                }
            }
            Surface(
                modifier = Modifier.padding(start = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = ComposeColor(0xBB111719),
            ) {
                Text(
                    text = engineMessage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = ComposeColor.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .5.sp,
                )
            }
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
        setBackgroundColor(Color.rgb(223, 229, 228))
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
