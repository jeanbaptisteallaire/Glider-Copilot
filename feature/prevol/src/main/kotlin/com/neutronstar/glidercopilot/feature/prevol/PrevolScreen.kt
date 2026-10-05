package com.neutronstar.glidercopilot.feature.prevol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GcKpi
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.metricText
import com.neutronstar.glidercopilot.designsystem.gcHeading
import com.neutronstar.glidercopilot.designsystem.GcThemeToggleButton
import com.neutronstar.glidercopilot.domain.DayQuality
import com.neutronstar.glidercopilot.domain.LiftType
import com.neutronstar.glidercopilot.precog.DayWeather
import com.neutronstar.glidercopilot.precog.HourWeather

@Composable
fun PrevolScreen(
    viewModel: PrevolViewModel,
    mapSource: OfflineMapSource,
    ognSource: OgnNetworkSource,
    sensorsSource: SensorsSource,
    modifier: Modifier = Modifier,
    /** S18 Lite : seulement météo + cartes hors ligne (sans planeur FLARM, OGN, capteurs). */
    lite: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val map by mapSource.state.collectAsStateWithLifecycle()
    val ogn by ognSource.network.collectAsStateWithLifecycle()
    val sensors by sensorsSource.sensors.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val c = Gc.colors
    val social = Gc.social
    // V18.1 : couleur d'interaction = bleu ciel sur pages blanches, vert de la charte en sombre
    val action = if (social) c.route else c.ok

    Column(modifier.fillMaxSize().background(c.background)) {
        Header(state, onPickClub = { picking = true }, onRefresh = viewModel::refresh)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = if (social) PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)
            else PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!lite) item {
                PairingCard(
                    ui = state.pairing,
                    onInput = viewModel::onRegistrationInput,
                    onValidate = viewModel::validateRegistration,
                    onCancel = viewModel::cancelRegistration,
                    onPickRecent = viewModel::pickRecent,
                    onAutoTakeoff = viewModel::setAutoTakeoff,
                    onFollow = viewModel::setFollow,
                    onFollowInput = viewModel::onFollowInput,
                    onFollowValidate = viewModel::validateFollow,
                    onFollowPick = viewModel::pickFollow,
                )
            }
            item { OfflineMapCard(map, mapSource::download, mapSource::cancel, mapSource::refreshCatalog) }
            if (!lite) item { OgnNetworkCard(ogn) }
            if (!lite) item { SensorsCard(sensors, sensorsSource) }
            val day = state.day
            when {
                day != null -> dayItems(day, state, viewModel::selectHour)
                state.loading -> item {
                    Centered { CircularProgressIndicator(color = action); Spacer(Modifier.size(12.dp)); Text("Chargement de la météo…", style = Gc.type.bodySmall) }
                }
                state.error != null -> item {
                    Centered {
                        Text(state.error!!, style = Gc.type.body.copy(color = c.warn))
                        TextButton(onClick = viewModel::refresh) { Text("Réessayer", color = action) }
                    }
                }
                state.club == null -> item {
                    Centered {
                        Text("Choisissez un club pour la météo du terrain.", style = Gc.type.body)
                        TextButton(onClick = { picking = true }) { Text("Choisir un club", color = action) }
                    }
                }
            }
            item { AirspacesCard(map) }
            item { NotamCard(state.club) }
            // V18.6 : en Lite, appairage FLARM (immatriculation → trafic OGN) et détection auto du décollage, en bas
            if (lite) item {
                PairingCard(
                    ui = state.pairing,
                    onInput = viewModel::onRegistrationInput,
                    onValidate = viewModel::validateRegistration,
                    onCancel = viewModel::cancelRegistration,
                    onPickRecent = viewModel::pickRecent,
                    onAutoTakeoff = viewModel::setAutoTakeoff,
                    showFollow = false,
                )
            }
        }
    }
    if (picking) {
        ClubPicker(state, onDismiss = { picking = false }, onPick = { viewModel.selectClub(it); picking = false })
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}

/** En-tête v8 : titre à gauche, date et terrain à droite (le terrain ouvre le choix du club). */
@Composable
private fun Header(state: PrevolUiState, onPickClub: () -> Unit, onRefresh: () -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    Row(
        Modifier.fillMaxWidth().background(c.background)
            .padding(start = 16.dp, end = 4.dp, top = if (social) 12.dp else 10.dp, bottom = if (social) 8.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // V18.1 : grand titre gras façon Apple Santé sur pages blanches
        Text(gcHeading("Prévol"), style = if (social) Gc.type.largeTitle else Gc.type.title, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                state.day?.let { Fmt.day.format(it.date) } ?: " ",
                style = if (social) Gc.type.footnote else Gc.type.bodySmall.copy(fontSize = 10.sp),
                maxLines = 1,
            )
            Row(Modifier.clickable(onClickLabel = "Changer de club", onClick = onPickClub), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.club?.let { cl -> listOfNotNull(cl.airfieldIcao, cl.shortName ?: cl.name).joinToString(" · ") } ?: "Choisir un club",
                    style = if (social) Gc.type.subhead.copy(color = c.route, fontWeight = FontWeight.SemiBold)
                    else Gc.type.bodySmall.copy(fontSize = 11.sp, color = c.route, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                Icon(GcIcons.ChevronDown, contentDescription = "Changer de club", tint = c.route, modifier = Modifier.padding(start = 3.dp).size(if (social) 14.dp else 12.dp))
            }
        }
        GcThemeToggleButton(Modifier.padding(start = 8.dp))
        IconButton(onClick = onRefresh, enabled = !state.loading) {
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), color = if (social) c.route else c.ok, strokeWidth = 2.dp)
            else Icon(Icons.Filled.Refresh, contentDescription = "Actualiser", tint = c.ink)
        }
    }
}

private fun LazyListScope.dayItems(day: DayWeather, state: PrevolUiState, onSelectHour: (Int) -> Unit) {
    val hour = day.hours.getOrNull(state.selectedHour) ?: day.hours.first()
    item { DaySummaryCard(day, state.selectedHour, onSelectHour) }
    item { HourCard(day, hour, state.selectedHour, onSelectHour) }
    item { WindCard(hour) }
    item { VigilanceCard(day) }
    item { SourcesCard(day, state) }
    item {
        val social = Gc.social
        Text(
            "Estimations de l'app sur le profil ARPEGE (maille ~10 km) : plafond par parcelle adiabatique, " +
                "base des cumulus par Espy, force par w*. Aide secondaire, à confronter au ciel et aux consignes du club.",
            style = if (social) Gc.type.footnote.copy(color = Gc.colors.faint) else Gc.type.bodySmall.copy(color = Gc.colors.faint),
            modifier = if (social) Modifier.padding(horizontal = 16.dp) else Modifier,
        )
    }
}

@Composable
private fun DaySummaryCard(day: DayWeather, selected: Int, onSelectHour: (Int) -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    val s = day.summary
    val qColor = when (s.quality) {
        DayQuality.NONE -> c.faint
        DayQuality.WEAK -> c.warn
        DayQuality.AVERAGE -> c.ink
        DayQuality.GOOD, DayQuality.EXCELLENT -> c.ok
    }
    GcCard(
        title = "La journée",
        icon = GcIcons.Prevol,
        tint = c.sun,
        trailing = { GcPill("Thermique · ${s.quality.label}", qColor) },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GcKpi(s.triggerTime?.let(Fmt::hm) ?: "—", "Déclenchement", Modifier.weight(1f), labelColor = c.sun)
            GcKpi(s.best?.let { Fmt.m(it.ceilingMslM) } ?: "—", s.best?.let { "Plafond ${Fmt.hour(it.validTime)}" } ?: "Plafond", Modifier.weight(1.2f), labelColor = c.altitude)
            GcKpi(s.endTime?.let(Fmt::hm) ?: "—", "Fin", Modifier.weight(1f), labelColor = c.sun)
        }
        CeilingChart(day.hours, selected, onSelectHour)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(chartClimbColor(c, 0.4, social), "faible")
            Legend(chartClimbColor(c, 1.2, social), "moyen")
            Legend(chartClimbColor(c, 2.4, social), "fort")
            Box(Modifier.size(7.dp).background(cloudBaseColor(c, social), CircleShape))
            Text("base Cu", style = if (social) Gc.type.caption1 else Gc.type.monoSmall)
        }
        if (s.overdevelopmentRisk) Text("⚠ CAPE élevée sous cumulus : surdéveloppement possible", style = Gc.type.bodySmall.copy(color = c.warn))
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    val social = Gc.social
    Row(verticalAlignment = Alignment.CenterVertically) {
        // V18.1 : pastille en gélule sur pages blanches
        if (social) Box(Modifier.size(width = 7.dp, height = 12.dp).background(color, RoundedCornerShape(50)))
        else Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, style = if (social) Gc.type.caption1 else Gc.type.monoSmall)
    }
}

@Composable
private fun HourCard(day: DayWeather, hour: HourWeather, selected: Int, onSelectHour: (Int) -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    val a = hour.analysis
    GcCard(title = "Conditions à ${Fmt.hm(a.validTime)}", icon = GcIcons.Prevol, tint = c.sun) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(day.hours) { i, h ->
                val on = i == selected
                // V18.1 : sélecteur d'heure en gélules (bleu plein = choisie) sur pages blanches
                val chip = if (social) Modifier.clip(RoundedCornerShape(50)).background(if (on) c.route else c.control)
                else Modifier
                    .background(if (on) c.controlOn else c.background, RoundedCornerShape(8.dp))
                    .border(1.dp, if (on) c.ok else c.line, RoundedCornerShape(8.dp))
                Text(
                    Fmt.hour(h.analysis.validTime),
                    style = if (social) Gc.type.subhead.copy(color = if (on) c.onAccentFill else c.ink, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                    else Gc.type.mono.copy(color = if (on) c.ok else c.dim),
                    modifier = chip
                        .clickable { onSelectHour(i) }
                        .padding(horizontal = if (social) 12.dp else 10.dp, vertical = 6.dp),
                )
            }
        }
        val nature = when (a.liftType) {
            LiftType.NONE -> "Pas d'ascendance exploitable"
            LiftType.BLUE -> "Thermique pur (ciel bleu)"
            LiftType.CUMULUS -> "Thermique sous cumulus"
        }
        val capped = if (a.dryTopCapped && a.liftType == LiftType.BLUE) " (≥ sommet du profil)" else ""
        if (social) {
            // V18.1 : chiffres clés en grand (Apple Santé), mêmes valeurs et mêmes unités que les lignes du thème sombre
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GcKpi(Fmt.m(a.ceilingMslM), "Plafond", Modifier.weight(1f), labelColor = c.altitude)
                GcKpi(a.cloudBaseMslM?.let { Fmt.m(it) } ?: "—", "Base Cu", Modifier.weight(1f), labelColor = c.altitude)
                GcKpi("~${Fmt.ms(a.climbMs)}", "Montée", Modifier.weight(1f), labelColor = c.sun)
            }
            StatRow("Plafond sol", Fmt.m(a.ceilingAglM) + capped)
            StatRow("Nature", nature)
            StatRow("Force estimée", "w* ${Fmt.ms(a.wStarMs)}")
        } else {
            StatRow("Plafond", "${Fmt.m(a.ceilingMslM)}  ·  ${Fmt.m(a.ceilingAglM)} sol" + capped)
            StatRow("Nature", nature)
            StatRow("Base des cumulus", a.cloudBaseMslM?.let { Fmt.m(it) } ?: "—")
            StatRow("Force estimée", "w* ${Fmt.ms(a.wStarMs)}  ·  montée ~${Fmt.ms(a.climbMs)}", valueColor = climbColor(c, a.climbMs))
        }
        StatRow("Nébulosité", a.totalCloudPct?.let { "${it.toInt()} %" + (hour.surface.lowCloudPct?.let { l -> " (basse ${l.toInt()} %)" } ?: "") } ?: "—")
        if ((hour.surface.lowCloudPct ?: 0.0) >= 85.0) {
            Text("⚠ Couche basse prévue presque couverte : ascendances sans doute étouffées, base à vérifier au ciel.", style = Gc.type.bodySmall.copy(color = c.warn))
        }
        StatRow("CAPE", a.capeJkg?.let { "${it.toInt()} J/kg" } ?: "—")
        StatRow("Sol du modèle", Fmt.m(a.groundAltitudeM) + (day.gridDistanceKm?.let { " · maille à ${"%.1f".format(it)} km" } ?: ""))
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Gc.colors.ink) {
    if (Gc.social) {
        // V18.1 : ligne de liste iOS — libellé gris à gauche, valeur à droite
        Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Gc.type.subhead.copy(color = Gc.colors.dim), modifier = Modifier.width(128.dp))
            Text(
                value,
                style = Gc.type.subhead.copy(color = valueColor, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", textAlign = TextAlign.End),
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Gc.type.bodySmall, modifier = Modifier.width(128.dp))
        Text(value, style = Gc.type.mono.copy(color = valueColor, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun WindCard(hour: HourWeather) {
    val c = Gc.colors
    val social = Gc.social
    GcCard(title = "Vent par tranches · ${Fmt.hm(hour.analysis.validTime)}", icon = GcIcons.WindArrow, tint = c.wind) {
        if (hour.winds.isEmpty()) {
            Text("Vent en altitude indisponible", style = Gc.type.bodySmall)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WindRose(hour.winds)
            Column(verticalArrangement = Arrangement.spacedBy(if (social) 6.dp else 4.dp)) {
                hour.winds.forEachIndexed { i, w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (social) Box(Modifier.size(width = 12.dp, height = 5.dp).background(windLayerColor(c, i, social = true), RoundedCornerShape(50)))
                        else Box(Modifier.size(width = 10.dp, height = 4.dp).background(windLayerColor(c, i)))
                        Spacer(Modifier.width(6.dp))
                        Text(w.label, style = if (social) Gc.type.footnote else Gc.type.monoSmall, modifier = Modifier.width(56.dp))
                        val v = "${Fmt.deg(w.fromDeg)} / ${Fmt.kmh(w.speedKmh)}"
                        if (social) Text(metricText(v, Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), c.faint), maxLines = 1)
                        else Text(v, style = Gc.type.mono)
                    }
                }
                Text("altitudes QNH · d'où vient le vent", style = if (social) Gc.type.caption1.copy(color = c.faint) else Gc.type.monoSmall.copy(color = c.faint))
            }
        }
    }
}

@Composable
private fun VigilanceCard(day: DayWeather) {
    val c = Gc.colors
    val v = day.vigilance
    val color = when (v?.colorId) { 1 -> c.ok; 2 -> c.warn; 3 -> c.warn; 4 -> c.danger; else -> c.faint }
    GcCard(
        title = "Vigilance Météo-France",
        tint = c.heart,
        trailing = { GcPill(v?.let { "${it.colorLabel} · dépt ${it.departement}" } ?: "indisponible", color) },
    ) {
        if (v == null) Text("Bulletin indisponible.", style = Gc.type.bodySmall)
        else if (v.phenomena.isEmpty()) Text("Aucun phénomène au-dessus du vert.", style = Gc.type.bodySmall)
        else v.phenomena.forEach { Text("• $it", style = Gc.type.body) }
    }
}

@Composable
private fun SourcesCard(day: DayWeather, state: PrevolUiState) {
    val c = Gc.colors
    GcCard(title = "Sources") {
        day.sources.forEach { s ->
            val stale = s.isStale(state.now)
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (stale) c.warn else c.ok, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(s.product, style = (if (Gc.social) Gc.type.subhead else Gc.type.body).copy(color = if (stale) c.dim else c.ink))
                }
                Text(
                    listOfNotNull(
                        s.attribution,
                        s.referenceTime?.let { "réseau ${Fmt.hm(it)}" },
                        "reçu ${Fmt.hm(s.fetchedAt)}",
                        if (s.offline) "hors ligne" else null,
                        if (stale) "périmé" else null,
                    ).joinToString(" · "),
                    style = (if (Gc.social) Gc.type.footnote else Gc.type.monoSmall).copy(color = if (stale) c.warn else c.dim),
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ClubPicker(state: PrevolUiState, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Gc.colors
    val social = Gc.social
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (social) c.panel else c.control,
        title = { Text("Club", style = if (social) Gc.type.title2 else Gc.type.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.clubs, key = { it.id }) { club ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onPick(club.id) }.padding(vertical = 8.dp),
                    ) {
                        Text(club.name, style = Gc.type.body.copy(color = if (club.id == state.club?.id) (if (social) c.route else c.ok) else c.ink))
                        Text(listOfNotNull(club.airfieldIcao, club.city, club.postcode.take(2)).joinToString(" · "), style = Gc.type.monoSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/**
 * V18.5 — aperçu de la journée pour le menu d'accueil Wind Glider : déclenchement, plafond, fin et graphique des plafonds
 * heure par heure, d'après la météo prévue du terrain choisi (même ViewModel que l'onglet Prévol).
 */
@Composable
fun PrevolDayPreview(viewModel: PrevolViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Gc.colors
    val day = state.day
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            day != null -> {
                val s = day.summary
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GcKpi(s.triggerTime?.let(Fmt::hm) ?: "—", "Déclenchement", Modifier.weight(1f), labelColor = c.sun)
                    GcKpi(s.best?.let { Fmt.m(it.ceilingMslM) } ?: "—", s.best?.let { "Plafond ${Fmt.hour(it.validTime)}" } ?: "Plafond", Modifier.weight(1.2f), labelColor = c.altitude)
                    GcKpi(s.endTime?.let(Fmt::hm) ?: "—", "Fin", Modifier.weight(1f), labelColor = c.sun)
                }
                CeilingChart(day.hours, state.selectedHour) { }
                Text(
                    listOfNotNull(state.club?.name, "ARPEGE · Météo-France").joinToString(" · "),
                    style = Gc.type.caption1.copy(color = c.faint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.route)
                Spacer(Modifier.width(10.dp))
                Text("Chargement de la météo…", style = Gc.type.subhead.copy(color = c.dim))
            }
            state.error != null -> Text(state.error!!, style = Gc.type.subhead.copy(color = c.warn))
            else -> Text("Choisissez un club dans Prévol pour la météo du terrain.", style = Gc.type.subhead.copy(color = c.dim))
        }
    }
}
