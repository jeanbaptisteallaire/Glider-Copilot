package com.neutronstar.glidercopilot.feature.flight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcColors
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.vario
import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** État des sources affiché sur la carte (pastilles GPS, BARO, DATA de la maquette v8). */
data class FlightStatus(val gps: Boolean = false, val baro: Boolean = false, val data: Boolean = false)

/** Terrain de référence affiché (OACI du club), fourni par la carte ; LFNL par défaut. */
private val LocalFieldId = androidx.compose.runtime.staticCompositionLocalOf { "LFNL" }
private const val DEFAULT_FIELD_ELEV = 183.0
/** Altitude du terrain de référence (openAIP quand la carte est installée). */
private val LocalFieldElev = androidx.compose.runtime.staticCompositionLocalOf { DEFAULT_FIELD_ELEV }
private const val FIELD_DIST_KM = 7.4
private const val FIELD_BRG = 285.0
private const val ARRIVAL_MARGIN = 300.0
private const val CEILING = 1880.0

/**
 * Écran Pilotage — mise en page v8 : marge de sécurité, profil de retour rétractable, carte avec états
 * GPS/BARO/DATA et chrono, vario simplifié sans conseil (valeur, barre, moyennes) avec son et repli.
 * Dès qu'une source réelle existe (baro, GPS, OGN), plus aucune valeur de démonstration n'est affichée ;
 * le signal simulé ne sert qu'aux aperçus sans capteur. Relief du profil et vent : session 6.
 */
@Composable
fun FlightScreen(
    status: FlightStatus,
    modifier: Modifier = Modifier,
    map: FlightMapConfig? = null,
    traffic: FlightTraffic = FlightTraffic(),
    live: FlightLive = FlightLive(),
    controls: FlightControls? = null,
) {
    val c = Gc.colors
    var t by remember { mutableDoubleStateOf(0.0) }
    var finesse by rememberSaveable { mutableIntStateOf(20) }
    var pendingRaise by remember { mutableStateOf<Int?>(null) }
    var profileOpen by rememberSaveable { mutableStateOf(true) }
    var varioOpen by rememberSaveable { mutableStateOf(true) }
    var localSound by rememberSaveable { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Sample>() }
    val tone = remember { VarioTone() }
    val controller = remember { MapController() }
    val snap = live.snapshot
    val real = live.hasData
    /** Signal simulé seulement dans les aperçus sans moteur de vol ; dans l'app, le mode démo est une bascule explicite. */
    val showDemo = !real && controls == null
    val waiting = !real && !showDemo
    val soundOn = if (controls != null) live.soundOn else localSound

    LaunchedEffect(showDemo) {
        if (!showDemo) return@LaunchedEffect
        while (true) {
            delay(250)   // 4 Hz : suffisant pour l'affichage, sobre en batterie
            t += 0.25
        }
    }
    LaunchedEffect(pendingRaise) {
        if (pendingRaise != null) {
            delay(3000)
            pendingRaise = null
        }
    }

    // Signal de démonstration (aperçu sans capteur) : spirale dans une pompe de ~1,5 m/s.
    val demoV = 1.4 + 1.3 * sin(t * 2 * Math.PI / 26) + 0.3 * sin(t * 1.7)
    val ownOgn = traffic.own?.takeIf { it.ageS <= 60 }
    val v: Double? = when {
        real -> snap!!.climbMs
        ownOgn?.climbMs != null -> ownOgn.climbMs
        showDemo -> demoV
        else -> null
    }
    val ognDelay = ownOgn?.let { o -> (o.latencyS ?: 0.0) + o.ageS }
    val varioLabel = when {
        real -> varioSourceLabel(snap, ognDelay)
        ownOgn?.climbMs != null -> varioSourceLabel(FlightSnapshotOgn, ognDelay)
        showDemo -> "Vario m/s (démo)"
        else -> varioSourceLabel(snap, ognDelay)
    }
    val alt: Double? = when {
        real -> snap!!.altitudeM
        showDemo -> 1250.0 + 40 * sin(t / 30)
        else -> null
    }
    val fieldElev = map?.fieldElevationM?.toDouble() ?: DEFAULT_FIELD_ELEV
    val gliderPos = when {
        real && live.gpsFresh -> snap?.gps?.position
        ownOgn != null -> ownOgn.position
        else -> null
    }
    LaunchedEffect(finesse, controls) { controls?.setFinesse(finesse) }
    val safety = live.safety?.takeIf { real }
    val result = safety?.choice?.result
    val field = result?.field?.position ?: map?.field
    val distKm = result?.distanceKm ?: if (gliderPos != null && field != null) Geo.distanceKm(gliderPos, field) else FIELD_DIST_KM
    val brg = result?.bearingDeg ?: if (gliderPos != null && field != null) Geo.bearingDeg(gliderPos, field) else FIELD_BRG
    val distKnown = result != null || (gliderPos != null && field != null) || showDemo
    val fieldElevShown = result?.field?.elevationM ?: fieldElev
    val need = result?.requiredM ?: (fieldElev + ARRIVAL_MARGIN + (if (distKnown) distKm * 1000 / finesse else 0.0))
    val marge = result?.let { r -> alt?.let { it - r.requiredM } } ?: alt?.takeIf { distKnown }?.let { it - need }
    val trend: Double? = when {
        real -> snap!!.avgSpiralMs
        showDemo -> 40 / 30.0 * cos(t / 30)
        else -> null
    }
    val trendNote = when {
        showDemo -> "démo"
        !real -> if (live.mode == OwnshipMode.FOLLOW) "en attente d'OGN" else "en attente du GPS"
        safety?.projectedMarginM != null -> "2 min : ${signed(safety.projectedMarginM!!)} m · ${altitudeRefLabel(snap!!.altitudeRef)}"
        else -> altitudeRefLabel(snap!!.altitudeRef)
    }
    val fieldLabel = result?.field?.code ?: map?.fieldId ?: "LFNL"
    var picking by remember { mutableStateOf(false) }
    val clock = if (real) snap!!.now.epochSecond.toDouble() else t

    LaunchedEffect(clock.toLong()) {
        val a = alt ?: return@LaunchedEffect
        history.add(Sample(clock, a, need, v ?: 0.0))
        while (history.isNotEmpty() && clock - history.first().t > 300) history.removeAt(0)
        while (history.isNotEmpty() && history.first().t > clock) history.removeAt(0)
    }
    // son : porté par le moteur de vol (actif aussi écran éteint) ; localement seulement en aperçu
    tone.vario = v ?: 0.0
    DisposableEffect(soundOn, varioOpen, controls) {
        if (controls == null && soundOn && varioOpen) tone.start() else tone.stop()
        onDispose { tone.stop() }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalFieldId provides fieldLabel, LocalFieldElev provides fieldElevShown) {
    Column(modifier.fillMaxSize().background(c.background)) {
        SafetyZone(
            marge, alt, need.takeIf { !waiting }, trend, trendNote, finesse, pendingRaise, distKm.takeIf { distKnown }, brg,
            auto = safety?.choice?.auto ?: true,
            windLabel = safety?.wind?.let { w -> "vent ${((w.fromDeg / 10).roundToInt() * 10) % 360}°/${w.speedKmh.roundToInt()}" } ?: if (showDemo) "vent ✓" else "vent —",
            onPickField = if (controls != null && live.fieldChoices.isNotEmpty()) ({ picking = true }) else null,
            picker = {
                androidx.compose.material3.DropdownMenu(expanded = picking, onDismissRequest = { picking = false }, containerColor = c.panel) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("AUTO · terrain du club, sinon le meilleur rejoignable", style = TextStyle(fontSize = 13.sp, color = c.ok)) },
                        onClick = { controls?.selectField(null); picking = false },
                    )
                    live.fieldChoices.take(8).forEach { f ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    listOfNotNull(
                                        f.code.takeIf { it != f.name },
                                        f.name.take(26),
                                        f.distanceKm?.let { km(it) },
                                        f.marginM?.let { "${signed(it)} m" },
                                    ).joinToString(" · "),
                                    style = TextStyle(fontSize = 13.sp, color = if ((f.marginM ?: 0.0) >= 0) c.ink else c.bad),
                                )
                            },
                            onClick = { controls?.selectField(f.id); picking = false },
                        )
                    }
                }
            },
        ) { f ->
            when {
                f == finesse -> Unit
                f < finesse -> { finesse = f; pendingRaise = null }
                pendingRaise == f -> { finesse = f; pendingRaise = null }
                else -> pendingRaise = f
            }
        }
        ReturnProfile(profileOpen, { profileOpen = !profileOpen }, alt, need, result?.effectiveFinesse ?: finesse.toDouble(), marge, distKm, brg, result, ceiling = if (showDemo) CEILING else null, waiting = waiting)
        Box(Modifier.weight(1f).heightIn(min = 200.dp).fillMaxWidth().background(c.background)) {
            if (map != null) {
                val frame = when {
                    real && live.gpsFresh -> liveFrame(snap!!, field ?: map.field)
                    ownOgn != null -> GeoFrame(ownOgn.position, ownOgn.trackDeg ?: 0.0, emptyList(), map.field)
                    showDemo -> demoFrame(map.field, t)
                    else -> GeoFrame(map.field, 0.0, emptyList(), map.field)
                }
                LiveMap(map, frame, controller, traffic, { c.vario(it) }, Modifier.fillMaxSize())
            } else {
                if (real && snap?.gps != null) TraceOnlyMap(snap) else if (showDemo) DemoMap(t, v ?: 0.0) else Box(Modifier.fillMaxSize().background(c.mapLow))
            }
            val tag = when {
                live.mode == OwnshipMode.DEMO -> "DÉMO · planeur simulé · trafic OGN réel"
                live.mode == OwnshipMode.FOLLOW -> "SUIVI ${live.followLabel ?: ""} · " + (snap?.gpsAgeS?.let { "FLARM via OGN · ${it.roundToInt()} s" } ?: "en attente d'une trame OGN")
                map == null -> "Carte hors ligne à télécharger dans Prévol"
                live.replay -> "REJEU VOL · capteurs simulés" + if (traffic.replay) " · OGN rejoué" else ""
                real && live.gpsFresh -> null
                ownOgn != null -> "Position OGN de mon planeur"
                real -> "Position GPS indisponible"
                traffic.replay && showDemo -> "REJEU OGN · planeur simulé"
                showDemo -> "DÉMO · planeur simulé"
                else -> "Position GPS en attente"
            }
            val shown = if (snap != null) status.copy(gps = live.gpsFresh, baro = live.baroActive) else status
            val nearTraffic = gliderPos?.let { p -> traffic.aircraft.count { Geo.distanceKm(p, it.position) <= 15.0 } }
            MapOverlays(
                shown, snap?.flightSeconds ?: 0, tag, map, controller, traffic, chronoAction(live, controls),
                wind = safety?.wind?.let { it.fromDeg to it.speedKmh },
                capBanner = if (result != null && marge != null && marge < 0 && !(result.distanceKm < 1.5 || (result.distanceKm < 3.0 && result.arrivalAglM > -50 && result.relief == null) || (snap?.gps?.groundSpeedKmh ?: 99.0) < 30)) "CAP TERRAIN · ${result.field.code} ${result.bearingDeg.roundToInt()}° · ${km(result.distanceKm)}" + (if (result.relief != null) " · RELIEF" else "") else null,
                demoOn = live.demo,
                onDemo = controls?.let { ctl -> { ctl.setDemo(!live.demo) } },
                nearTraffic = if (live.mode == OwnshipMode.DEMO) nearTraffic else null,
            )
        }
        VarioPanel(
            open = varioOpen,
            soundOn = soundOn,
            v = v,
            label = varioLabel,
            averages = when {
                real -> Triple(snap!!.avgSpiralMs, snap.avgThermalMs, snap.avgDayMs)
                showDemo -> Triple(1.4, 1.2, 1.0)
                else -> Triple(null, null, null)
            },
            history = history,
            alt = alt,
            need = need,
            ceiling = if (showDemo) CEILING else null,
            onSound = { if (controls != null) controls.setSound(!soundOn) else localSound = !localSound },
            onToggle = { varioOpen = !varioOpen },
        )
    }
    }
}

private val FlightSnapshotOgn = com.neutronstar.glidercopilot.domain.flight.FlightSnapshot(java.time.Instant.EPOCH, source = com.neutronstar.glidercopilot.domain.flight.VarioSource.OGN)

/** Chrono manuel quand la détection automatique est coupée : un appui lance, un autre arrête. */
private fun chronoAction(live: FlightLive, controls: FlightControls?): (() -> Unit)? {
    if (controls == null || live.autoTakeoff || live.snapshot == null) return null
    val s = live.snapshot
    return if (s.recording) controls::stopChrono else controls::startChrono
}

/** Planeur réel : position GPS, route, trace des 4 dernières minutes colorée par le vario. */
@Composable
private fun liveFrame(s: com.neutronstar.glidercopilot.domain.flight.FlightSnapshot, field: LatLon): GeoFrame {
    val c = Gc.colors
    val pts = s.trace.takeLast(240)
    val trace = pts.zipWithNext().map { (a, b) -> Triple(a.position, b.position, c.vario(b.climbMs)) }
    val g = s.gps!!
    return GeoFrame(g.position, g.trackDeg ?: 0.0, trace, field)
}

private data class Sample(val t: Double, val alt: Double, val need: Double, val v: Double)

// ---------------------------------------------------------------- zone sécurité

@Composable
private fun SafetyZone(marge: Double?, alt: Double?, need: Double?, trend: Double?, trendNote: String, finesse: Int, pending: Int?, distKm: Double?, brg: Double,
    auto: Boolean = true,
    windLabel: String = "vent ✓",
    onPickField: (() -> Unit)? = null,
    picker: @Composable () -> Unit = {},
    onFinesse: (Int) -> Unit,
) {
    val c = Gc.colors
    val below = (marge ?: 0.0) < 0
    val col = when { marge == null -> c.dim; below -> c.bad; else -> c.ok }
    // V7.1 : trois colonnes sur la largeur d'un téléphone (360 dp). Les tailles sont calées pour que
    // la marge ne soit jamais tronquée et qu'ALT/SÉCU ne déborde pas : marge 44 sp, distance 34 sp,
    // colonne de droite 128 dp, espacement 9 dp.
    Row(
        Modifier.fillMaxWidth().background(c.background).padding(start = 18.dp, end = 18.dp, top = 5.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Marge de sécurité", style = eyebrow(c).copy(color = col), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    marge?.let { signed(it) } ?: "—",
                    style = Gc.type.giant.copy(color = col, fontSize = 44.sp, lineHeight = 48.sp),
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.semantics { contentDescription = marge?.let { "Marge de sécurité ${signed(it)} mètres" } ?: "Marge de sécurité indisponible, altitude inconnue" },
                )
                Text("m", style = Gc.type.body.copy(color = col, fontSize = 17.sp), modifier = Modifier.padding(start = 3.dp, bottom = 7.dp))
            }
            // l'une sous l'autre : côte à côte, « SÉCU » passait à la ligne sans sa valeur
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 5.dp)) {
                AltValue("ALT", alt?.let { grouped(it.roundToInt()) } ?: "—")
                AltValue("SÉCU", need?.let { grouped(it.roundToInt()) } ?: "—")
            }
            if (UiMask.SHOW_TREND_LINE) {
                Text("tendance ${trend?.let { signed1(it) } ?: "—"} m/s · $trendNote", style = TextStyle(fontSize = 10.sp, color = c.dim), maxLines = 1, modifier = Modifier.padding(top = 9.dp))
            }
        }
        // distance au terrain : deuxième information fondamentale, au centre et en grand (V7.1)
        Column(
            Modifier.width(68.dp).padding(top = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Distance", style = eyebrow(c), maxLines = 1)
            Text(
                distKm?.let { String.format(Locale.FRANCE, "%.1f", it) } ?: "—",
                style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.ok, letterSpacing = (-1.2).sp, lineHeight = 38.sp),
                maxLines = 1, softWrap = false,
                modifier = Modifier.padding(top = 2.dp)
                    .semantics { contentDescription = distKm?.let { "Distance au terrain ${km(it)}" } ?: "Distance au terrain inconnue" },
            )
            Text("km", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ok))
        }
        Column(Modifier.width(128.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Finesse", style = eyebrow(c), maxLines = 1)
                Text(
                    if (pending != null) "encore : F$pending" else "+${ARRIVAL_MARGIN.toInt()} m · $windLabel",
                    style = TextStyle(fontSize = 8.sp, color = if (pending != null) c.warn else c.dim), maxLines = 1,
                )
            }
            Row(Modifier.fillMaxWidth().background(c.control, RoundedCornerShape(13.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(20, 15, 10).forEach { f ->
                    val selected = f == finesse
                    val isPending = pending == f
                    Row(
                        Modifier.weight(1f).height(44.dp)
                            .background(if (selected) c.controlOn else Color.Transparent, RoundedCornerShape(10.dp))
                            .then(if (isPending) Modifier.border(1.5.dp, c.warn, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable(role = Role.Button) { onFinesse(f) }
                            .semantics { contentDescription = "Finesse de sécurité $f"; stateDescription = if (selected) "sélectionnée" else "" },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tc = when { selected -> c.ok; isPending -> c.warn; else -> c.finesseIdle }
                        Text("F", style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Medium, color = tc), modifier = Modifier.padding(top = 4.dp, end = 1.dp))
                        Text("$f", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = tc))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .then(if (onPickField != null) Modifier.clickable(role = Role.Button, onClickLabel = "Choisir le terrain", onClick = onPickField) else Modifier)
                    .padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                picker()
                // la distance est passée au centre de l'écran (V7.1) : terrain et cap tiennent sur une ligne
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (auto) "AUTO" else "MANU", style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = c.route), modifier = Modifier.background(c.background, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 3.dp))
                    Text(LocalFieldId.current, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (distKm != null) "${brg.roundToInt()}°" else "position ?",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ok, letterSpacing = (-0.3).sp),
                        maxLines = 1, softWrap = false,
                    )
                    Icon(GcIcons.ChevronDown, contentDescription = null, tint = c.ink, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AltValue(label: String, value: String) {
    val c = Gc.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Medium, color = c.dim, letterSpacing = 0.25.sp), modifier = Modifier.padding(bottom = 2.dp))
        Text(value, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, letterSpacing = (-0.3).sp, fontFeatureSettings = "tnum"))
    }
}

private fun eyebrow(c: GcColors) = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = c.dim, letterSpacing = 0.15.sp)

// ---------------------------------------------------------------- profil de retour

@Composable
private fun ReturnProfile(
    open: Boolean, onToggle: () -> Unit, alt: Double?, need: Double, finesse: Double, marge: Double?, distKm: Double, brg: Double,
    result: com.neutronstar.glidercopilot.domain.safety.GlideResult? = null,
    ceiling: Double? = CEILING,
    waiting: Boolean = false,
) {
    val c = Gc.colors
    if (!UiMask.SHOW_PROFILE_GRAPH && !UiMask.SHOW_PROFILE_HEADER) return
    Column(Modifier.fillMaxWidth().background(c.background)) {
        HorizontalDivider(thickness = 1.dp, color = c.ok.copy(alpha = 0.11f))
        if (UiMask.SHOW_PROFILE_HEADER) {
            Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Profil de retour au terrain", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.dim), modifier = Modifier.weight(1f))
                Row(
                    Modifier.fillMaxHeight36().widthIn(min = 86.dp)
                        .clickable(role = Role.Button, onClickLabel = if (open) "Réduire le profil" else "Déployer le profil", onClick = onToggle)
                        .semantics { stateDescription = if (open) "déployé" else "réduit" }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (open) "Réduire" else "Déployer", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.ok))
                    Spacer(Modifier.width(8.dp))
                    Icon(if (open) GcIcons.ChevronUp else GcIcons.ChevronDown, contentDescription = null, tint = c.ok, modifier = Modifier.size(12.dp))
                }
            }
        }
        if (open && waiting) {
            Text(
                "Coupe du terrain dès la première position (GPS ou OGN)",
                style = TextStyle(fontSize = 11.sp, color = c.dim),
                modifier = Modifier.fillMaxWidth().height(88.dp).padding(start = 24.dp, top = 30.dp),
            )
        } else if (open && UiMask.SHOW_PROFILE_GRAPH) ProfileCanvas(alt, need, finesse, marge ?: 0.0, distKm, brg, result, ceiling)
        HorizontalDivider(thickness = 1.dp, color = c.ok.copy(alpha = 0.11f))
    }
}

private fun Modifier.fillMaxHeight36() = this.height(36.dp)

/** Relief de démonstration le long de la route vers le terrain (m), s en km depuis le planeur. */
private fun demoElevation(s: Double): Double =
    330 + 260 * exp(-((s - 2.6) * (s - 2.6)) / 0.9) + 90 * sin(s * 3.1) * exp(-s / 5) - 12 * s + 25 * sin(s * 11.0)

@Composable
private fun ProfileCanvas(
    altOrNull: Double?, need: Double, finesse: Double, marge: Double, distKm: Double, brg: Double,
    result: com.neutronstar.glidercopilot.domain.safety.GlideResult? = null,
    CEILING: Double? = null,
) {
    val realRelief = result?.takeIf { it.terrainKnown }?.profile
    val alt = altOrNull ?: need
    val c = Gc.colors
    val tm = rememberTextMeasurer()
    val FIELD_ID = LocalFieldId.current
    val FIELD_ELEV = LocalFieldElev.current
    Canvas(Modifier.fillMaxWidth().height(88.dp).semantics { contentDescription = "Coupe du terrain vers $FIELD_ID" }) {
        val w = size.width
        val h = size.height
        val d = distKm.coerceAtLeast(0.3)
        val dx = d * 1.06 + 0.4
        val n = 110
        val prof = DoubleArray(n + 1) { i ->
            val s = dx * i / n
            when {
                realRelief != null -> {
                    if (s >= d) FIELD_ELEV else {
                        val k = ((s / d) * (realRelief.size - 1)).toInt().coerceIn(0, realRelief.size - 1)
                        realRelief[k].terrainM ?: FIELD_ELEV
                    }
                }
                s >= d -> FIELD_ELEV + (s - d) * 20
                else -> max(FIELD_ELEV, demoElevation(s))
            }
        }
        val clearance = if (realRelief != null) 100.0 else 40.0
        val tmax = prof.max()
        val tmin = prof.min()
        val top = maxOf(alt, need, tmax, CEILING?.let { min(it, alt + 400) } ?: (alt + 150)) + 120
        val bot = max(0.0, min(tmin, FIELD_ELEV) - 80)
        val pL = 36.dp.toPx(); val pR = 12.dp.toPx(); val pT = 16.dp.toPx(); val pB = 15.dp.toPx()
        fun x(s: Double) = (pL + s / dx * (w - pL - pR)).toFloat()
        fun y(a: Double) = (pT + (top - a) / (top - bot) * (h - pT - pB)).toFloat()
        val small = TextStyle(fontSize = 9.sp, color = c.dim)
        drawRect(c.background)
        // grille
        val step = if (top - bot > 1400) 500 else 250
        var a = (kotlin.math.ceil(bot / step) * step)
        while (a < top) {
            val yy = y(a)
            if (yy >= pT - 2 && yy <= h - pB) {
                drawLine(Color.White.copy(alpha = 0.06f), Offset(pL, yy), Offset(w - pR, yy), 1f)
                label(tm, grouped(a.toInt()), pL - 4.dp.toPx(), yy, small, TextAlign.End)
            }
            a += step
        }
        // plafond prévu
        if (CEILING != null && CEILING < top && CEILING > bot) {
            drawLine(c.ok, Offset(pL, y(CEILING)), Offset(w - pR, y(CEILING)), 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))
            label(tm, "plafond ${grouped(CEILING.toInt())}", pL + 16.dp.toPx(), y(CEILING) + 9.dp.toPx(), small.copy(color = c.ok), TextAlign.Start)
        }
        // relief
        val ground = Path().apply {
            moveTo(x(0.0), h)
            prof.forEachIndexed { i, e -> lineTo(x(dx * i / n), y(e)) }
            lineTo(x(dx), h); close()
        }
        drawPath(ground, Brush.verticalGradient(listOf(c.terrainTop, c.terrainBottom), startY = y(tmax), endY = h))
        // plancher de sécurité
        val arr = FIELD_ELEV + ARRIVAL_MARGIN
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        drawLine(c.warn, Offset(x(0.0), y(need)), Offset(x(d), y(arr)), 2.dp.toPx(), pathEffect = dash)
        drawLine(c.warn, Offset(x(d), y(arr)), Offset(x(d), y(FIELD_ELEV)), 1.5.dp.toPx())
        // plané en finesse de sécurité (rien si l'altitude est inconnue)
        fun gl(s: Double) = alt - s * 1000 / finesse
        val known = altOrNull != null
        var hit = -1.0
        for (i in 0..n) {
            val s = dx * i / n
            if (s > d) break
            if (s > 0.2 && s < d - 0.8 && gl(s) < prof[i] + clearance) { hit = s; break }
        }
        val end = if (hit >= 0) hit else d
        if (known) drawLine(c.ink, Offset(x(0.0), y(alt)), Offset(x(end), y(gl(end))), 2.dp.toPx())
        if (known && hit >= 0) {
            drawLine(c.bad, Offset(x(hit), y(gl(hit))), Offset(x(d), y(gl(d))), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            label(tm, "RELIEF", x(hit), y(gl(hit)) - 8.dp.toPx(), TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = c.bad), TextAlign.Center)
        }
        // drapeau terrain et arrivée
        val fx = x(d); val fy = y(FIELD_ELEV)
        drawPath(Path().apply { moveTo(fx, fy); lineTo(fx, fy - 14.dp.toPx()); lineTo(fx - 8.dp.toPx(), fy - 10.dp.toPx()); lineTo(fx, fy - 7.dp.toPx()); close() }, c.route)
        val arrH = gl(d) - FIELD_ELEV
        val arrY = (y(gl(d)) - 8.dp.toPx()).coerceIn(pT + 2, h - pB - 14.dp.toPx())
        if (known) label(tm, "arrivée ${signed(arrH)} m/sol", fx - 10.dp.toPx(), arrY, TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (arrH - ARRIVAL_MARGIN >= 0) c.ok else c.bad), TextAlign.End)
        label(tm, "+${ARRIVAL_MARGIN.toInt()}", fx - 4.dp.toPx(), y(arr) + 9.dp.toPx(), TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = c.warn), TextAlign.End)
        // planeur de profil
        val gx = x(0.0); val gy = y(alt)
        if (known) drawLine(c.ink, Offset(gx - 8.dp.toPx(), gy), Offset(gx + 10.dp.toPx(), gy + 1.dp.toPx()), 2.dp.toPx())
        if (known) drawPath(Path().apply { moveTo(gx - 8.dp.toPx(), gy); lineTo(gx - 10.dp.toPx(), gy - 6.dp.toPx()); lineTo(gx - 6.dp.toPx(), gy); close() }, c.ink)
        // accolade de marge
        if (known) drawLine(if (marge < 0) c.bad else c.ok, Offset(gx + 2.dp.toPx(), gy), Offset(gx + 2.dp.toPx(), y(need)), 3.dp.toPx())
        // légendes
        if (UiMask.SHOW_PROFILE_CAPTION) {
            label(tm, "COUPE → $FIELD_ID · ${brg.roundToInt()}° · F${oneDecimal(finesse)} eff. · " + if (realRelief != null) "relief Copernicus" else "relief schématique", pL, 7.dp.toPx(), TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c.inkSoft), TextAlign.Start)
        }
        val bottomY = h - 8.dp.toPx()
        label(tm, "0", x(0.0), bottomY, small, TextAlign.Start)
        label(tm, km(d / 2), x(d / 2), bottomY, small, TextAlign.Center)
        label(tm, "${km(d)} · $FIELD_ID ${FIELD_ELEV.toInt()} m", w - pR, bottomY, small, TextAlign.End)
    }
}

/** Texte centré verticalement sur [cy], ancré à gauche, au centre ou à droite de [x]. */
private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, cy: Float, style: TextStyle, align: TextAlign) {
    val r = tm.measure(text, style)
    val left = when (align) { TextAlign.End -> x - r.size.width; TextAlign.Center -> x - r.size.width / 2f; else -> x }
    drawText(r, topLeft = Offset(left, cy - r.size.height / 2f))
}

// ---------------------------------------------------------------- carte

/** Sans carte hors ligne : trace réelle des dernières minutes autour du planeur, sans fond (échelle 500 m). */
@Composable
private fun TraceOnlyMap(s: com.neutronstar.glidercopilot.domain.flight.FlightSnapshot) {
    val c = Gc.colors
    val g = s.gps ?: return
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Trace du vol sans carte" }) {
        drawRect(c.mapLow)
        val step = 40.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(Color.White.copy(alpha = 0.06f), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
        var gy = 0f
        while (gy < size.height) { drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }
        val cx = size.width / 2; val cy = size.height / 2
        val pxPerKm = 60.dp.toPx() / 0.5f
        val cosLat = cos(Math.toRadians(g.position.lat))
        fun p(ll: LatLon) = Offset(
            cx + ((ll.lon - g.position.lon) * 111.32 * cosLat * pxPerKm).toFloat(),
            cy - ((ll.lat - g.position.lat) * 111.32 * pxPerKm).toFloat(),
        )
        s.trace.takeLast(300).zipWithNext().forEach { (a, b) -> drawLine(c.vario(b.climbMs), p(a.position), p(b.position), 3.dp.toPx(), StrokeCap.Round) }
        drawGlider(cx, cy, (g.trackDeg ?: 0.0).toFloat(), c.ink)
    }
}

@Composable
private fun DemoMap(t: Double, v: Double) {
    val c = Gc.colors
    val FIELD_ID = LocalFieldId.current
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Carte vue de dessus, démonstration" }) {
        drawRect(c.mapLow)
        val step = 40.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(Color.White.copy(alpha = 0.06f), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
        var gy = 0f
        while (gy < size.height) { drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }
        val cx = size.width / 2
        val cy = size.height / 2
        val r = 46.dp.toPx()
        // terrain de référence et route
        val field = Offset(cx - size.width * 0.34f, cy + size.height * 0.28f)
        drawLine(c.route, Offset(cx, cy), field, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
        drawCircle(c.route, 5.dp.toPx(), field)
        label(tm, FIELD_ID, field.x + 9.dp.toPx(), field.y, TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = c.route), TextAlign.Start)
        // trace colorée par le vario sur la dernière spirale
        val a0 = t * 2 * Math.PI / 26
        val seg = 48
        for (k in 0 until seg) {
            val a1 = a0 - k * 0.12
            val a2 = a0 - (k + 1) * 0.12
            val vv = 1.4 + 1.3 * sin(a1) + 0.3 * sin((t - k * 0.5) * 1.7)
            val drift = k * 0.6f
            drawLine(
                c.vario(vv).copy(alpha = 1f - k / seg.toFloat() * 0.7f),
                Offset(cx + (r * cos(a1)).toFloat() + drift, cy + (r * sin(a1)).toFloat() - drift * 0.4f),
                Offset(cx + (r * cos(a2)).toFloat() + drift + 0.6f, cy + (r * sin(a2)).toFloat() - (drift + 0.6f) * 0.4f),
                3.dp.toPx(), StrokeCap.Round,
            )
        }
        // planeur
        val px = cx + (r * cos(a0)).toFloat()
        val py = cy + (r * sin(a0)).toFloat()
        val heading = Math.toDegrees(a0).toFloat() + 180f
        drawGlider(px, py, heading, c.ink)
        drawCircle(c.vario(v), 3.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))
    }
}

private fun DrawScope.drawGlider(x: Float, y: Float, headingDeg: Float, color: Color) {
    val k = 1.dp.toPx()
    val rad = Math.toRadians(headingDeg.toDouble())
    fun p(dx: Float, dy: Float) = Offset(x + (dx * cos(rad) - dy * sin(rad)).toFloat() * k, y + (dx * sin(rad) + dy * cos(rad)).toFloat() * k)
    drawLine(color, p(-11f, 0f), p(11f, 0f), 2.4f * k, StrokeCap.Round)
    drawLine(color, p(0f, -6f), p(0f, 9f), 2.4f * k, StrokeCap.Round)
    drawLine(color, p(-4f, 9f), p(4f, 9f), 2f * k, StrokeCap.Round)
}

@Composable
private fun BoxScope.MapOverlays(
    status: FlightStatus,
    flightSeconds: Long,
    tag: String?,
    map: FlightMapConfig?,
    controller: MapController,
    traffic: FlightTraffic,
    onChrono: (() -> Unit)?,
    wind: Pair<Double, Double>? = 300.0 to 18.0,
    capBanner: String? = null,
    demoOn: Boolean = false,
    onDemo: (() -> Unit)? = null,
    nearTraffic: Int? = null,
) {
    val c = Gc.colors
    // sous la sécurité : cap vers le terrain retenu, bien visible
    if (capBanner != null) Text(
        capBanner,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onAccent),
        maxLines = 1,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp).background(c.bad, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics { contentDescription = capBanner },
    )
    // bascule discrète du mode démo (coin haut droit)
    if (onDemo != null) Text(
        if (demoOn) "DÉMO" else "démo",
        style = TextStyle(fontSize = 9.sp, fontWeight = if (demoOn) FontWeight.Bold else FontWeight.Medium, color = if (demoOn) c.onAccent else c.faint, letterSpacing = 0.4.sp),
        modifier = Modifier.align(Alignment.TopEnd).padding(end = 13.dp, top = 12.dp)
            .background(if (demoOn) c.warn else c.overlay, RoundedCornerShape(50))
            .clickable(role = Role.Switch, onClickLabel = if (demoOn) "Quitter le mode démo" else "Mode démo") { onDemo() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .semantics { contentDescription = "Mode démo"; stateDescription = if (demoOn) "activé" else "désactivé" },
    )
    // origine de la position affichée (démonstration, rejeu, secours)
    if (tag != null) Text(
        tag,
        style = TextStyle(fontSize = 9.sp, color = c.faint),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).background(c.overlay, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
    // légende trafic OGN (maquette v8 : .traffic-key)
    if (map != null && (traffic.live || traffic.replay)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("OGN ") }
                append("${traffic.aircraft.size} aéronef" + (if (traffic.aircraft.size > 1) "s" else "") + (nearTraffic?.let { " ($it à 15 km)" } ?: "") + " · ${traffic.thermals.size} pompe" + (if (traffic.thermals.size > 1) "s" else ""))
            },
            style = TextStyle(fontSize = 9.sp, color = c.cardTitle),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 62.dp)
                .background(c.overlay, RoundedCornerShape(10.dp)).border(1.dp, c.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
    // orientation
    RoundTool(
        if (controller.follow) "AUTO" else "LIBRE",
        Modifier.align(Alignment.TopStart).padding(start = 13.dp, top = 10.dp),
        if (controller.follow) "Carte centrée sur le planeur" else "Recentrer la carte sur le planeur",
        onClick = controller::recenter,
    )
    // vent
    Column(
        Modifier.align(Alignment.TopEnd).padding(end = 13.dp, top = 45.dp).width(44.dp)
            .background(c.background, RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(alpha = 0.035f), RoundedCornerShape(20.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { contentDescription = wind?.let { "Vent estimé ${it.first.roundToInt()} degrés ${it.second.roundToInt()} kilomètres heure" } ?: "Vent pas encore estimé : spiralez" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (wind != null) {
            Icon(GcIcons.WindArrow, contentDescription = null, tint = c.route, modifier = Modifier.size(20.dp).rotate(wind.first.toFloat() + 180f))
            Text("${((wind.first / 10).roundToInt() * 10) % 360}°\n${wind.second.roundToInt()}", style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, color = c.ink, textAlign = TextAlign.Center))
        } else {
            Text("vent\n—", style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, color = c.dim, textAlign = TextAlign.Center))
        }
    }
    // échelle vario
    Column(Modifier.align(Alignment.TopEnd).padding(end = 17.dp, top = 141.dp).width(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("+4", style = TextStyle(fontSize = 9.sp, color = c.dim))
        Box(Modifier.size(width = 4.dp, height = 51.dp).background(Brush.verticalGradient(c.varioStops.reversed().map { it.second }), RoundedCornerShape(2.dp)))
        Text("−3", style = TextStyle(fontSize = 9.sp, color = c.dim))
    }
    // zoom
    Column(Modifier.align(Alignment.BottomEnd).padding(end = 13.dp, bottom = 15.dp).background(c.background, RoundedCornerShape(22.dp))) {
        Text("+", style = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Light, color = c.ink, textAlign = TextAlign.Center), modifier = Modifier.size(44.dp).clickable(role = Role.Button, onClick = controller::zoomIn).padding(top = 6.dp).semantics { contentDescription = "Zoom avant" })
        Text("−", style = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Light, color = c.ink, textAlign = TextAlign.Center), modifier = Modifier.size(44.dp).clickable(role = Role.Button, onClick = controller::zoomOut).padding(top = 6.dp).semantics { contentDescription = "Zoom arrière" })
    }
    // échelle de distance
    val (scaleLabel, scaleDp) = if (map != null) controller.scale() else "500 m" to 60f
    Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 12.dp)) {
        Text(scaleLabel, style = TextStyle(fontSize = 9.sp, color = c.dim))
        Canvas(Modifier.padding(top = 2.dp).size(width = scaleDp.dp, height = 5.dp)) {
            val s = 1.5.dp.toPx()
            drawLine(c.dim, Offset(0f, size.height), Offset(size.width, size.height), s)
            drawLine(c.dim, Offset(s / 2, 0f), Offset(s / 2, size.height), s)
            drawLine(c.dim, Offset(size.width - s / 2, 0f), Offset(size.width - s / 2, size.height), s)
        }
    }
    // états GPS · BARO · DATA
    Column(
        Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 42.dp).semantics(mergeDescendants = true) {
            contentDescription = "État du vol et des données : GPS ${onOff(status.gps)}, BARO ${onOff(status.baro)}, DATA ${onOff(status.data)}"
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusBox("GPS", status.gps)
        StatusBox("BARO", status.baro)
        StatusBox("DATA", status.data)
    }
    // attribution des données de carte
    if (map != null) {
        Text(
            map.attribution,
            style = TextStyle(fontSize = 7.sp, color = c.faint),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
        )
    }
    // chrono de vol
    Box(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).height(21.dp).widthIn(min = 50.dp)
            .background(c.overlay, RoundedCornerShape(7.dp))
            .then(if (onChrono != null) Modifier.clickable(role = Role.Button, onClickLabel = "Lancer ou arrêter le chrono", onClick = onChrono) else Modifier)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "Chronomètre de vol ${chrono(flightSeconds)}" },
        contentAlignment = Alignment.Center,
    ) { Text(chrono(flightSeconds) + if (onChrono != null) " ⏯" else "", style = TextStyle(fontSize = 9.sp, color = Color.White, letterSpacing = 0.18.sp)) }
}

private fun onOff(b: Boolean) = if (b) "actif" else "inactif"

@Composable
private fun StatusBox(label: String, on: Boolean) {
    val c = Gc.colors
    Row(
        Modifier.height(21.dp).widthIn(min = 50.dp).background(c.overlay, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(if (on) c.statusOn else c.statusOff, CircleShape))
        Text(label, style = TextStyle(fontSize = 8.75.sp, color = Color.White, letterSpacing = 0.18.sp))
    }
}

@Composable
private fun RoundTool(text: String, modifier: Modifier, description: String, onClick: () -> Unit = {}) {
    val c = Gc.colors
    Box(
        modifier.size(44.dp).background(c.background, CircleShape).border(1.dp, Color.White.copy(alpha = 0.035f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.ink)) }
}

/** Point à [distKm] de [from] au relèvement [bearingDeg] (approximation locale, suffisante à quelques km). */
private fun destination(from: LatLon, bearingDeg: Double, distKm: Double): LatLon {
    val b = Math.toRadians(bearingDeg)
    return LatLon(from.lat + distKm * cos(b) / 111.32, from.lon + distKm * sin(b) / (111.32 * cos(Math.toRadians(from.lat))))
}

/** Planeur démo spiralant à FIELD_DIST_KM du terrain de référence, trace colorée par le vario simulé. */
@Composable
private fun demoFrame(field: LatLon, t: Double): GeoFrame {
    val c = Gc.colors
    val center = destination(field, (FIELD_BRG + 180) % 360, FIELD_DIST_KM)
    val r = 0.18
    fun at(a: Double, driftKm: Double): LatLon {
        val p = destination(center, 300.0 - 180.0, driftKm)   // la spirale dérive sous le vent (vent du 300°)
        return LatLon(p.lat + r * sin(a) / 111.32, p.lon + r * cos(a) / (111.32 * cos(Math.toRadians(p.lat))))
    }
    val a0 = t * 2 * Math.PI / 26
    val trace = (0 until 60).map { k ->
        val a1 = a0 - k * 0.12
        val a2 = a0 - (k + 1) * 0.12
        val vv = 1.4 + 1.3 * sin(a1) + 0.3 * sin((t - k * 0.5) * 1.7)
        Triple(at(a1, -k * 0.004), at(a2, -(k + 1) * 0.004), c.vario(vv))
    }
    val heading = (Math.toDegrees(kotlin.math.atan2(-sin(a0), cos(a0))) + 360) % 360
    return GeoFrame(at(a0, 0.0), heading, trace, field)
}

// ---------------------------------------------------------------- vario

@Composable
private fun VarioPanel(
    open: Boolean,
    soundOn: Boolean,
    v: Double?,
    label: String,
    averages: Triple<Double?, Double?, Double?>,
    history: List<Sample>,
    alt: Double?,
    need: Double,
    ceiling: Double? = CEILING,
    onSound: () -> Unit,
    onToggle: () -> Unit,
) {
    val c = Gc.colors
    Column(
        Modifier.fillMaxWidth().background(c.background, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 14.dp, top = if (open) 11.dp else 7.dp, bottom = if (open) 6.dp else 7.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            if (open) {
                Column(Modifier.width(92.dp)) {
                    Text(label, style = eyebrow(c), modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
                    Text(
                        v?.let { signed1(it) } ?: "—",
                        style = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp, color = v?.let { c.vario(it) } ?: c.dim, fontFeatureSettings = "tnum"),
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.semantics { contentDescription = v?.let { "Vario ${signed1(it)} mètres par seconde" } ?: "Vario indisponible" },
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    VarioBar(v)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avg("Spirale", averages.first?.let { signed1(it) } ?: "—", Modifier.weight(1f))
                        Avg("Pompe", averages.second?.let { signed1(it) } ?: "—", Modifier.weight(1f))
                        Avg("Jour", averages.third?.let { signed1(it) } ?: "—", Modifier.weight(1f))
                    }
                }
            } else Spacer(Modifier.weight(1f))
            VarioActions(open, soundOn, onSound, onToggle)
        }
        if (open) {
            if (UiMask.SHOW_ALTITUDE_HISTORY) {
                HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.047f))
                AltitudeStrip(history, alt ?: history.lastOrNull()?.alt ?: need, need, ceiling)
            }
        }
    }
}

@Composable
private fun VarioActions(open: Boolean, soundOn: Boolean, onSound: () -> Unit, onToggle: () -> Unit) {
    val c = Gc.colors
    @Composable
    fun ActionButton(pressed: Boolean, description: String, state: String?, onClick: () -> Unit, content: @Composable () -> Unit) {
        Box(
            Modifier.width(40.dp).heightIn(min = 30.dp)
                .background(if (pressed) c.control else c.sunkenPlan, RoundedCornerShape(10.dp))
                .border(1.dp, if (pressed) c.ok.copy(alpha = 0.4f) else c.line, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description; if (state != null) stateDescription = state },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
    val sound: @Composable () -> Unit = {
        ActionButton(soundOn, if (soundOn) "Couper le son du vario" else "Activer le son du vario", if (soundOn) "son actif" else "son coupé", onSound) {
            Icon(if (soundOn) GcIcons.Sound else GcIcons.SoundOff, contentDescription = null, tint = if (soundOn) c.ok else c.dim, modifier = Modifier.size(18.dp))
        }
    }
    val toggle: @Composable () -> Unit = {
        ActionButton(false, if (open) "Masquer le vario" else "Redéployer le vario", null, onToggle) {
            Icon(if (open) GcIcons.ChevronDown else GcIcons.ChevronUp, contentDescription = null, tint = c.dim, modifier = Modifier.size(18.dp))
        }
    }
    if (open) Column(Modifier.width(42.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { sound(); toggle() }
    else Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { sound(); toggle() }
}

@Composable
private fun Avg(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Gc.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = TextStyle(fontSize = 9.sp, color = c.dim), maxLines = 1)
        Text(value, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp, color = c.ink, fontFeatureSettings = "tnum"), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun VarioBar(v: Double?) {
    val c = Gc.colors
    Canvas(Modifier.fillMaxWidth().height(15.dp)) {
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2
        drawRoundRect(
            Brush.horizontalGradient(
                *arrayOf(0f to c.varioStops[0].second, 0.3f to c.varioStops[1].second, 0.5f to c.varioStops[2].second, 0.62f to c.varioStops[3].second, 0.8f to c.varioStops[4].second, 1f to c.varioStops[5].second),
            ),
            topLeft = Offset(0f, top), size = Size(size.width, barH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2),
        )
        drawLine(c.dim, Offset(size.width / 2, top - 3.dp.toPx()), Offset(size.width / 2, top + barH + 3.dp.toPx()), 1f)
        if (v == null) return@Canvas
        val x = ((v.coerceIn(-5.0, 5.0) + 5) / 10 * size.width).toFloat()
        drawRoundRect(c.background, Offset(x - 4.dp.toPx(), 0f), Size(8.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawRoundRect(c.ink, Offset(x - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
    }
}

@Composable
private fun AltitudeStrip(history: List<Sample>, alt: Double, need: Double, CEILING: Double?) {
    val c = Gc.colors
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(38.dp).semantics { contentDescription = "Altitude sur les 5 dernières minutes" }) {
        if (history.size < 2) return@Canvas
        val now = history.last().t
        val win = 300.0
        var lo = history.minOf { min(it.alt, it.need) }
        var hi = history.maxOf { it.alt }
        hi = max(hi, CEILING?.let { min(it, hi + 300) } ?: hi)
        lo -= 40; hi += 40
        val pL = 4.dp.toPx(); val pR = 78.dp.toPx()
        fun x(tt: Double) = (pL + (1 - (now - tt) / win) * (size.width - pL - pR)).toFloat()
        fun y(a: Double) = (2.dp.toPx() + (hi - a) / (hi - lo) * (size.height - 4.dp.toPx())).toFloat()
        if (CEILING != null && CEILING < hi) drawLine(c.ok, Offset(pL, y(CEILING)), Offset(size.width - pR, y(CEILING)), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
        for (i in 1 until history.size) {
            drawLine(c.bad, Offset(x(history[i - 1].t), y(history[i - 1].need)), Offset(x(history[i].t), y(history[i].need)), 1.4.dp.toPx())
            drawLine(c.vario(history[i].v), Offset(x(history[i - 1].t), y(history[i - 1].alt)), Offset(x(history[i].t), y(history[i].alt)), 2.4.dp.toPx(), StrokeCap.Round)
        }
        val le = history.last()
        drawCircle(c.ink, 2.6.dp.toPx(), Offset(x(le.t), y(le.alt)))
        val lx = size.width - pR + 6.dp.toPx()
        label(tm, "alt ${grouped(alt.roundToInt())} m", lx, size.height * 0.5f, TextStyle(fontSize = 9.sp, color = c.ink), TextAlign.Start)
        if (CEILING != null) label(tm, "plaf. ${grouped(CEILING.toInt())}", lx, 8.dp.toPx(), TextStyle(fontSize = 9.sp, color = c.ok), TextAlign.Start)
        label(tm, "sécu ${grouped(need.roundToInt())}", lx, size.height - 7.dp.toPx(), TextStyle(fontSize = 9.sp, color = c.bad), TextAlign.Start)
        label(tm, "−5 min", pL + 2.dp.toPx(), 8.dp.toPx(), TextStyle(fontSize = 9.sp, color = c.dim), TextAlign.Start)
    }
}

// ---------------------------------------------------------------- formats

private fun signed(v: Double): String = (if (v >= 0) "+" else "−") + grouped(abs(v).roundToInt())
private fun signed1(v: Double): String = (if (v >= 0) "+" else "−") + String.format(Locale.FRANCE, "%.1f", abs(v))
private fun oneDecimal(v: Double): String = String.format(Locale.FRANCE, "%.1f", v)
private fun km(v: Double): String = String.format(Locale.FRANCE, "%.1f km", v)
private fun grouped(n: Int): String = "%,d".format(Locale.ROOT, n).replace(',', ' ')
fun chrono(seconds: Long): String = "${seconds / 3600} h ${"%02d".format((seconds / 60) % 60)}"
