package com.neutronstar.glidercopilot.feature.flight

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neutronstar.glidercopilot.carto.MapStyle
import com.neutronstar.glidercopilot.domain.LatLon
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow

/** Carte hors ligne à afficher : style MapLibre complet (pack installé) et terrain de référence. */
data class FlightMapConfig(
    val styleJson: String,
    val styleKey: String,
    val fieldId: String,
    val field: LatLon,
    val attribution: String,
    /** Altitude du terrain (openAIP), null si inconnue. */
    val fieldElevationM: Int? = null,
)

/** Position démo du planeur et trace colorée, en coordonnées géographiques. */
data class GeoFrame(
    val glider: LatLon,
    val headingDeg: Double,
    /** Segments (début, fin, couleur). */
    val trace: List<Triple<LatLon, LatLon, Color>>,
    val field: LatLon,
)

/** Pilote la caméra depuis les boutons superposés à la carte. */
@Stable
class MapController {
    internal var map: MapLibreMap? = null
    var follow by mutableStateOf(true)
    var zoom by mutableDoubleStateOf(DEFAULT_ZOOM)
        internal set
    var latitude by mutableDoubleStateOf(44.0)
        internal set

    fun zoomIn() { map?.animateCamera(CameraUpdateFactory.zoomIn(), 250) }
    fun zoomOut() { map?.animateCamera(CameraUpdateFactory.zoomOut(), 250) }
    fun recenter() { follow = true }

    /** Échelle graphique : distance ronde et longueur en dp (MapLibre : 512 dp par tuile au niveau 0). */
    fun scale(maxDp: Float = 70f): Pair<String, Float> {
        val metersPerDp = 40_075_016.686 * cos(Math.toRadians(latitude)) / (512.0 * 2.0.pow(zoom))
        val maxMeters = metersPerDp * maxDp
        val nice = listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10_000.0, 20_000.0, 50_000.0).lastOrNull { it <= maxMeters } ?: 50.0
        val label = if (nice >= 1000) String.format(Locale.FRANCE, "%.0f km", nice / 1000) else "${nice.toInt()} m"
        return label to (nice / metersPerDp).toFloat()
    }

    companion object { const val DEFAULT_ZOOM = 10.4 }
}

@Composable
internal fun LiveMap(config: FlightMapConfig, frame: GeoFrame, controller: MapController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            controller.map = null
            mapView.onDestroy()
        }
    }

    LaunchedEffect(config.styleKey) {
        style = null
        mapView.getMapAsync { map ->
            controller.map = map
            map.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
                isTiltGesturesEnabled = false
            }
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(frame.glider.lat, frame.glider.lon)).zoom(controller.zoom).build()
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) controller.follow = false
            }
            map.addOnCameraMoveListener {
                controller.zoom = map.cameraPosition.zoom
                controller.latitude = map.cameraPosition.target?.latitude ?: controller.latitude
            }
            map.setStyle(Style.Builder().fromJson(config.styleJson)) { s ->
                s.addImage(MapStyle.IMG_GLIDER, gliderBitmap())
                style = s
            }
        }
    }

    SideEffect {
        val s = style ?: return@SideEffect
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_GLIDER)?.setGeoJson(gliderJson(frame))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_TRACE)?.setGeoJson(traceJson(frame))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_ROUTE)?.setGeoJson(line(frame.glider, frame.field))
        if (controller.follow) controller.map?.moveCamera(CameraUpdateFactory.newLatLng(LatLng(frame.glider.lat, frame.glider.lon)))
    }

    AndroidView(factory = { mapView }, modifier = modifier.semantics { contentDescription = "Carte hors ligne vue de dessus" })
}

private fun gliderJson(f: GeoFrame) =
    """{"type":"Feature","properties":{"heading":${f.headingDeg}},"geometry":{"type":"Point","coordinates":[${f.glider.lon},${f.glider.lat}]}}"""

private fun line(a: LatLon, b: LatLon) =
    """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[[${a.lon},${a.lat}],[${b.lon},${b.lat}]]}}"""

private fun traceJson(f: GeoFrame): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    f.trace.forEachIndexed { i, (a, b, color) ->
        if (i > 0) append(',')
        append("""{"type":"Feature","properties":{"color":"${hex(color)}"},"geometry":{"type":"LineString","coordinates":[[${a.lon},${a.lat}],[${b.lon},${b.lat}]]}}""")
    }
    append("]}")
}

internal fun hex(c: Color): String = String.format("#%06X", c.toArgb() and 0xFFFFFF)

/** Silhouette de planeur vue de dessus, nez vers le haut (la couche la tourne selon le cap). */
private fun gliderBitmap(size: Int = 72): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val k = size / 24f
    fun paint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; strokeWidth = width * k; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
    }
    val halo = paint(android.graphics.Color.BLACK, 4.2f)
    val body = paint(android.graphics.Color.WHITE, 2.4f)
    for (p in listOf(halo, body)) {
        cv.drawLine(1.5f * k, 10f * k, 22.5f * k, 10f * k, p)      // ailes
        cv.drawLine(12f * k, 4f * k, 12f * k, 20f * k, p)          // fuselage
        cv.drawLine(8.5f * k, 20f * k, 15.5f * k, 20f * k, p)      // empennage
    }
    return bmp
}
