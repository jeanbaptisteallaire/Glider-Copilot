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

/** Autre aéronef du réseau OGN à dessiner. */
data class TrafficMark(
    val position: LatLon,
    val trackDeg: Double?,
    val label: String,
    val altitudeM: Double?,
    val circling: Boolean,
    /** Adresse radio : identifiant stable pour retrouver l'aéronef touché sur la carte. */
    val id: String = "",
    /** Deux caractères affichés dans la pastille de la carte des aéronefs. */
    val shortLabel: String = "",
    val typeLabel: String = "Aéronef",
    val speedKmh: Double? = null,
    val climbMs: Double? = null,
    val ageS: Long = 0,
)

/** Pompe détectée dans les spirales du réseau. */
data class ThermalMark(val position: LatLon, val climbMs: Double, val aircraftCount: Int, val ageMinutes: Long)

/** Mon planeur vu par OGN (secours) : position, altitude, montée et retard mesurés. */
data class OwnOgn(val position: LatLon, val trackDeg: Double?, val altitudeM: Double?, val climbMs: Double?, val latencyS: Double?, val ageS: Long)

/** Données réseau affichées en vol. [replay] : rejeu d'un enregistrement anonymisé, signalé à l'écran. */
data class FlightTraffic(
    val aircraft: List<TrafficMark> = emptyList(),
    val thermals: List<ThermalMark> = emptyList(),
    val own: OwnOgn? = null,
    val live: Boolean = false,
    val replay: Boolean = false,
)

/** Cœur de pompe estimé, à dessiner dans la vue centrage. */
data class CoreMark(val position: LatLon, val confidence: Double, val label: String)

/** Position du planeur et trace colorée, en coordonnées géographiques. */
data class GeoFrame(
    val glider: LatLon,
    val headingDeg: Double,
    /** Segments (début, fin, couleur). */
    val trace: List<Triple<LatLon, LatLon, Color>>,
    val field: LatLon,
    /** En spirale : la carte passe en vue centrage (nord en haut, trace recalée du vent). */
    val circling: Boolean = false,
    /** Trace recalée de la dérive du vent (vue centrage). */
    val airTrace: List<Triple<LatLon, LatLon, Color>> = emptyList(),
    val core: CoreMark? = null,
)

/** Orientation de la carte : AUTO (route en haut, nord en haut en spirale), nord en haut, route en haut. */
enum class MapOrientation { AUTO, NORTH, TRACK }

/** Pilote la caméra depuis les boutons superposés à la carte. */
@Stable
class MapController {
    internal var map: MapLibreMap? = null
    var follow by mutableStateOf(true)
    var orientation by mutableStateOf(MapOrientation.AUTO)
        private set
    /** Vue centrage active : spirale et orientation AUTO. */
    var centering by mutableStateOf(false)
        internal set

    fun cycleOrientation() {
        orientation = when (orientation) {
            MapOrientation.AUTO -> MapOrientation.NORTH
            MapOrientation.NORTH -> MapOrientation.TRACK
            MapOrientation.TRACK -> MapOrientation.AUTO
        }
        follow = true
    }

    val orientationLabel: String get() = when (orientation) {
        MapOrientation.AUTO -> "AUTO"
        MapOrientation.NORTH -> "N↑"
        MapOrientation.TRACK -> "RTE"
    }
    var zoom by mutableDoubleStateOf(DEFAULT_ZOOM)
        internal set
    /** Zoom réellement affiché (vue centrage comprise) : sert à l'échelle graphique. */
    var shownZoom by mutableDoubleStateOf(DEFAULT_ZOOM)
        internal set
    var latitude by mutableDoubleStateOf(44.0)
        internal set

    fun zoomIn() { map?.animateCamera(CameraUpdateFactory.zoomIn(), 250) }
    fun zoomOut() { map?.animateCamera(CameraUpdateFactory.zoomOut(), 250) }
    fun recenter() { follow = true }

    /** Échelle graphique : distance ronde et longueur en dp (MapLibre : 512 dp par tuile au niveau 0). */
    fun scale(maxDp: Float = 70f): Pair<String, Float> {
        val metersPerDp = 40_075_016.686 * cos(Math.toRadians(latitude)) / (512.0 * 2.0.pow(shownZoom))
        val maxMeters = metersPerDp * maxDp
        val nice = listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10_000.0, 20_000.0, 50_000.0).lastOrNull { it <= maxMeters } ?: 50.0
        val label = if (nice >= 1000) String.format(Locale.FRANCE, "%.0f km", nice / 1000) else "${nice.toInt()} m"
        return label to (nice / metersPerDp).toFloat()
    }

    companion object {
        const val DEFAULT_ZOOM = 10.4
        /** Vue centrage : ~250 m de large sur un téléphone, de quoi voir le cercle et le cœur. */
        const val CENTERING_ZOOM = 15.2
    }
}

@Composable
internal fun LiveMap(
    config: FlightMapConfig,
    frame: GeoFrame,
    controller: MapController,
    traffic: FlightTraffic,
    thermalColor: (Double) -> Color,
    modifier: Modifier = Modifier,
    /** Carte des aéronefs : aucune couche de pilotage, pastilles à deux lettres, caméra libre. */
    trafficOnly: Boolean = false,
    onAircraftTap: ((String) -> Unit)? = null,
) {
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
            if (onAircraftTap != null) {
                // tolérance de la taille d'un doigt (22 dp autour du point touché), pas de la pastille dessinée
                val slop = 22f * context.resources.displayMetrics.density
                map.addOnMapClickListener { point ->
                    val p = map.projection.toScreenLocation(point)
                    val box = android.graphics.RectF(p.x - slop, p.y - slop, p.x + slop, p.y + slop)
                    val hit = map.queryRenderedFeatures(box, MapStyle.LAYER_TRAFFIC, MapStyle.LAYER_TRAFFIC_DOTS)
                        .firstNotNullOfOrNull { f -> f.getStringProperty("id") }
                    if (hit != null) onAircraftTap(hit)
                    hit != null
                }
            }
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
                // le zoom choisi par le pilote n'est mémorisé que pour ses propres gestes : la vue centrage zoome sans l'écraser
                if (!controller.centering) controller.zoom = map.cameraPosition.zoom
                controller.shownZoom = map.cameraPosition.zoom
                controller.latitude = map.cameraPosition.target?.latitude ?: controller.latitude
            }
            map.setStyle(Style.Builder().fromJson(config.styleJson)) { s ->
                s.addImage(MapStyle.IMG_GLIDER, gliderBitmap())
                s.addImage(MapStyle.IMG_TRAFFIC, trafficBitmap())
                s.addImage(MapStyle.IMG_RUNWAY, runwayBitmap())
                s.addImage(MapStyle.IMG_DOT, dotBitmap())
                style = s
            }
        }
    }

    SideEffect {
        val s = style ?: return@SideEffect
        val centering = frame.circling && controller.orientation == MapOrientation.AUTO
        controller.centering = centering
        if (trafficOnly) {
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_TRAFFIC_DOTS)?.setGeoJson(trafficJson(traffic.aircraft))
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_TRAFFIC)?.setGeoJson(EMPTY_JSON)
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_GLIDER)?.setGeoJson(EMPTY_JSON)
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_TRACE)?.setGeoJson(EMPTY_JSON)
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_AIR_TRACE)?.setGeoJson(EMPTY_JSON)
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_CORE)?.setGeoJson(EMPTY_JSON)
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_ROUTE)?.setGeoJson(EMPTY_JSON)
            // aucune aide au vol sur cet onglet : ni pompes du réseau, ni route, ni trace
            s.getSourceAs<GeoJsonSource>(MapStyle.SRC_THERMALS)?.setGeoJson(EMPTY_JSON)
            return@SideEffect
        }
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_GLIDER)?.setGeoJson(gliderJson(frame))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_TRACE)?.setGeoJson(traceJson(if (centering) emptyList() else frame.trace))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_AIR_TRACE)?.setGeoJson(traceJson(if (centering) frame.airTrace else emptyList()))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_CORE)?.setGeoJson(coreJson(if (centering) frame.core else null))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_ROUTE)?.setGeoJson(line(frame.glider, frame.field))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_TRAFFIC)?.setGeoJson(trafficJson(traffic.aircraft))
        s.getSourceAs<GeoJsonSource>(MapStyle.SRC_THERMALS)?.setGeoJson(thermalsJson(traffic.thermals, thermalColor))
        val map = controller.map
        if (controller.follow && map != null) {
            // route en haut en transition, nord en haut en spirale (une carte qui tourne à 14°/s est illisible)
            val bearing = when {
                centering || controller.orientation == MapOrientation.NORTH -> 0.0
                else -> frame.headingDeg
            }
            val zoom = if (centering) maxOf(controller.zoom, MapController.CENTERING_ZOOM) else controller.zoom
            // en route en haut, le planeur est placé au tiers bas de l'écran ; centré en vue centrage
            val padTop = if (centering || bearing == 0.0) 0.0 else mapView.height * 0.34
            val cp = CameraPosition.Builder()
                .target(LatLng(frame.glider.lat, frame.glider.lon))
                .bearing(bearing)
                .zoom(zoom)
                .padding(0.0, padTop, 0.0, 0.0)
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cp))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.semantics { contentDescription = "Carte hors ligne vue de dessus" })
}

private fun gliderJson(f: GeoFrame) =
    """{"type":"Feature","properties":{"heading":${f.headingDeg}},"geometry":{"type":"Point","coordinates":[${f.glider.lon},${f.glider.lat}]}}"""

private fun line(a: LatLon, b: LatLon) =
    """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[[${a.lon},${a.lat}],[${b.lon},${b.lat}]]}}"""

private fun coreJson(core: CoreMark?): String {
    if (core == null) return """{"type":"FeatureCollection","features":[]}"""
    return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"confidence":${core.confidence},"label":"${esc(core.label)}"},"geometry":{"type":"Point","coordinates":[${core.position.lon},${core.position.lat}]}}]}"""
}

private fun traceJson(segments: List<Triple<LatLon, LatLon, Color>>): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    segments.forEachIndexed { i, (a, b, color) ->
        if (i > 0) append(',')
        append("""{"type":"Feature","properties":{"color":"${hex(color)}"},"geometry":{"type":"LineString","coordinates":[[${a.lon},${a.lat}],[${b.lon},${b.lat}]]}}""")
    }
    append("]}")
}

internal fun hex(c: Color): String = String.format("#%06X", c.toArgb() and 0xFFFFFF)

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

private const val EMPTY_JSON = """{"type":"FeatureCollection","features":[]}"""

private fun trafficJson(list: List<TrafficMark>): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    list.forEachIndexed { i, a ->
        if (i > 0) append(',')
        append(
            """{"type":"Feature","properties":{"id":"${esc(a.id)}","track":${a.trackDeg ?: 0.0},"label":"${esc(a.label)}",""" +
                """"short":"${esc(a.shortLabel)}","climb":${a.climbMs ?: 0.0},"circling":${a.circling}},""" +
                """"geometry":{"type":"Point","coordinates":[${a.position.lon},${a.position.lat}]}}""",
        )
    }
    append("]}")
}

/** Pastille ronde à deux lettres de la carte des aéronefs. */
private fun dotBitmap(size: Int = 42): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val k = size / 42f
    val r = size / 2f
    cv.drawCircle(r, r, r - 2.5f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(232, 24, 30, 32) })
    cv.drawCircle(r, r, r - 2.5f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3.5f * k
    })
    return bmp
}

/** Piste de terrain : bande claire bordée de noir, tournée par la couche selon l'orientation réelle. */
private fun runwayBitmap(size: Int = 96): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val k = size / 96f
    val rect = android.graphics.RectF(size / 2f - 7f * k, 8f * k, size / 2f + 7f * k, size - 8f * k)
    // liseré sombre puis bande claire : lisible sur le relief comme sur les espaces aériens
    cv.drawRoundRect(rect, 3f * k, 3f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 6f * k
    })
    cv.drawRoundRect(rect, 3f * k, 3f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    return bmp
}

private fun thermalsJson(list: List<ThermalMark>, color: (Double) -> Color): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    list.forEachIndexed { i, t ->
        if (i > 0) append(',')
        val fresh = (1.0 - t.ageMinutes / 45.0).coerceIn(0.35, 1.0)
        val label = String.format(Locale.FRANCE, "%+.1f", t.climbMs) + if (t.aircraftCount > 1) " ×${t.aircraftCount}" else ""
        append("""{"type":"Feature","properties":{"color":"${hex(color(t.climbMs))}","count":${t.aircraftCount},"fresh":$fresh,"label":"$label"},"geometry":{"type":"Point","coordinates":[${t.position.lon},${t.position.lat}]}}""")
    }
    append("]}")
}

/** Flèche blanche cernée de noir, pointe vers le haut (tournée selon la route). */
private fun trafficBitmap(size: Int = 48): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val k = size / 24f
    val path = android.graphics.Path().apply {
        moveTo(12f * k, 3f * k); lineTo(19f * k, 20f * k); lineTo(12f * k, 16f * k); lineTo(5f * k, 20f * k); close()
    }
    cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f * k; strokeJoin = Paint.Join.ROUND })
    cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL })
    return bmp
}

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
