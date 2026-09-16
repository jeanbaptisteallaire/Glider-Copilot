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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GcKpi
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.domain.DayQuality
import com.neutronstar.glidercopilot.domain.LiftType
import com.neutronstar.glidercopilot.precog.DayWeather
import com.neutronstar.glidercopilot.precog.HourWeather

@Composable
fun PrevolScreen(viewModel: PrevolViewModel, mapSource: OfflineMapSource, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val map by mapSource.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val c = Gc.colors

    Column(modifier.fillMaxSize().background(c.background)) {
        Header(state, onPickClub = { picking = true }, onRefresh = viewModel::refresh)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PairingCard(
                    ui = state.pairing,
                    onInput = viewModel::onRegistrationInput,
                    onValidate = viewModel::validateRegistration,
                    onCancel = viewModel::cancelRegistration,
                    onPickRecent = viewModel::pickRecent,
                    onAutoTakeoff = viewModel::setAutoTakeoff,
                )
            }
            item { OfflineMapCard(map, mapSource::download, mapSource::cancel, mapSource::refreshCatalog) }
            val day = state.day
            when {
                day != null -> dayItems(day, state, viewModel::selectHour)
                state.loading -> item {
                    Centered { CircularProgressIndicator(color = c.ok); Spacer(Modifier.size(12.dp)); Text("Chargement de la météo…", style = Gc.type.bodySmall) }
                }
                state.error != null -> item {
                    Centered {
                        Text(state.error!!, style = Gc.type.body.copy(color = c.warn))
                        TextButton(onClick = viewModel::refresh) { Text("Réessayer", color = c.ok) }
                    }
                }
                state.club == null -> item {
                    Centered {
                        Text("Choisissez votre club pour obtenir la météo du terrain.", style = Gc.type.body)
                        TextButton(onClick = { picking = true }) { Text("Choisir un club", color = c.ok) }
                    }
                }
            }
            item { AirspacesCard(map) }
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
    Row(
        Modifier.fillMaxWidth().background(c.background).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("PRÉVOL", style = Gc.type.title, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(state.day?.let { Fmt.day.format(it.date) } ?: " ", style = Gc.type.bodySmall.copy(fontSize = 10.sp), maxLines = 1)
            Row(Modifier.clickable(onClickLabel = "Changer de club", onClick = onPickClub), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.club?.let { cl -> listOfNotNull(cl.airfieldIcao, cl.shortName ?: cl.name).joinToString(" · ") } ?: "Choisir un club",
                    style = Gc.type.bodySmall.copy(fontSize = 11.sp, color = c.route, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                Icon(GcIcons.ChevronDown, contentDescription = "Changer de club", tint = c.route, modifier = Modifier.padding(start = 3.dp).size(12.dp))
            }
        }
        IconButton(onClick = onRefresh, enabled = !state.loading) {
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), color = c.ok, strokeWidth = 2.dp)
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
        Text(
            "Estimations calculées dans l'app à partir du profil ARPEGE (maille ~10 km) : plafond par parcelle " +
                "adiabatique, base des cumulus par la formule d'Espy, force par la vitesse convective w*. " +
                "Aide à la décision secondaire, à confronter au ciel et aux consignes du club.",
            style = Gc.type.bodySmall.copy(color = Gc.colors.faint),
        )
    }
}

@Composable
private fun DaySummaryCard(day: DayWeather, selected: Int, onSelectHour: (Int) -> Unit) {
    val c = Gc.colors
    val s = day.summary
    val qColor = when (s.quality) {
        DayQuality.NONE -> c.faint
        DayQuality.WEAK -> c.warn
        DayQuality.AVERAGE -> c.ink
        DayQuality.GOOD, DayQuality.EXCELLENT -> c.ok
    }
    GcCard(title = "La journée en bref", trailing = { GcPill("Thermique · ${s.quality.label}", qColor) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GcKpi(s.triggerTime?.let(Fmt::hm) ?: "—", "Déclenchement", Modifier.weight(1f))
            GcKpi(s.best?.let { Fmt.m(it.ceilingMslM) } ?: "—", s.best?.let { "Plafond ${Fmt.hour(it.validTime)}" } ?: "Plafond", Modifier.weight(1.2f))
            GcKpi(s.endTime?.let(Fmt::hm) ?: "—", "Fin", Modifier.weight(1f))
        }
        CeilingChart(day.hours, selected, onSelectHour)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(climbColor(c, 0.4), "faible")
            Legend(climbColor(c, 1.2), "moyen")
            Legend(climbColor(c, 2.4), "fort")
            Box(Modifier.size(7.dp).background(c.ink, CircleShape))
            Text("base Cu", style = Gc.type.monoSmall)
        }
        if (s.overdevelopmentRisk) Text("⚠ CAPE élevée sous cumulus : risque de surdéveloppement", style = Gc.type.bodySmall.copy(color = c.warn))
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text, style = Gc.type.monoSmall)
    }
}

@Composable
private fun HourCard(day: DayWeather, hour: HourWeather, selected: Int, onSelectHour: (Int) -> Unit) {
    val c = Gc.colors
    val a = hour.analysis
    GcCard(title = "Conditions à ${Fmt.hm(a.validTime)}") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(day.hours) { i, h ->
                val on = i == selected
                Text(
                    Fmt.hour(h.analysis.validTime),
                    style = Gc.type.mono.copy(color = if (on) c.ok else c.dim),
                    modifier = Modifier
                        .background(if (on) c.controlOn else c.background, RoundedCornerShape(8.dp))
                        .border(1.dp, if (on) c.ok else c.line, RoundedCornerShape(8.dp))
                        .clickable { onSelectHour(i) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        val nature = when (a.liftType) {
            LiftType.NONE -> "Pas d'ascendance exploitable"
            LiftType.BLUE -> "Thermique pur (ciel bleu)"
            LiftType.CUMULUS -> "Thermique sous cumulus"
        }
        StatRow("Plafond", "${Fmt.m(a.ceilingMslM)}  ·  ${Fmt.m(a.ceilingAglM)} sol" + if (a.dryTopCapped && a.liftType == LiftType.BLUE) " (≥ sommet du profil)" else "")
        StatRow("Nature", nature)
        StatRow("Base des cumulus", a.cloudBaseMslM?.let { Fmt.m(it) } ?: "—")
        StatRow("Force estimée", "w* ${Fmt.ms(a.wStarMs)}  ·  montée ~${Fmt.ms(a.climbMs)}", valueColor = climbColor(c, a.climbMs))
        StatRow("Nébulosité", a.totalCloudPct?.let { "${it.toInt()} %" + (hour.surface.lowCloudPct?.let { l -> " (basse ${l.toInt()} %)" } ?: "") } ?: "—")
        if ((hour.surface.lowCloudPct ?: 0.0) >= 85.0) {
            Text("⚠ Couche nuageuse basse prévue presque couverte : ascendances probablement étouffées, base réelle à vérifier au ciel.", style = Gc.type.bodySmall.copy(color = c.warn))
        }
        StatRow("CAPE", a.capeJkg?.let { "${it.toInt()} J/kg" } ?: "—")
        StatRow("Sol du modèle", Fmt.m(a.groundAltitudeM) + (day.gridDistanceKm?.let { " · maille à ${"%.1f".format(it)} km" } ?: ""))
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Gc.colors.ink) {
    Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Gc.type.bodySmall, modifier = Modifier.width(128.dp))
        Text(value, style = Gc.type.mono.copy(color = valueColor, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun WindCard(hour: HourWeather) {
    GcCard(title = "Vent par tranches · ${Fmt.hm(hour.analysis.validTime)}") {
        if (hour.winds.isEmpty()) {
            Text("Vent en altitude indisponible", style = Gc.type.bodySmall)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WindRose(hour.winds)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hour.winds.forEachIndexed { i, w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 10.dp, height = 4.dp).background(windLayerColor(Gc.colors, i)))
                        Spacer(Modifier.width(6.dp))
                        Text(w.label, style = Gc.type.monoSmall, modifier = Modifier.width(56.dp))
                        Text("${Fmt.deg(w.fromDeg)} / ${Fmt.kmh(w.speedKmh)}", style = Gc.type.mono)
                    }
                }
                Text("altitudes QNH · d'où vient le vent", style = Gc.type.monoSmall.copy(color = Gc.colors.faint))
            }
        }
    }
}

@Composable
private fun VigilanceCard(day: DayWeather) {
    val c = Gc.colors
    val v = day.vigilance
    val color = when (v?.colorId) { 1 -> c.ok; 2 -> c.warn; 3 -> c.warn; 4 -> c.danger; else -> c.faint }
    GcCard(title = "Vigilance Météo-France", trailing = { GcPill(v?.let { "${it.colorLabel} · dépt ${it.departement}" } ?: "indisponible", color) }) {
        if (v == null) Text("Bulletin non disponible pour le moment.", style = Gc.type.bodySmall)
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
                    Text(s.product, style = Gc.type.body.copy(color = if (stale) c.dim else c.ink))
                }
                Text(
                    listOfNotNull(
                        s.attribution,
                        s.referenceTime?.let { "réseau ${Fmt.hm(it)}" },
                        "reçu ${Fmt.hm(s.fetchedAt)}",
                        if (s.offline) "hors ligne" else null,
                        if (stale) "périmé" else null,
                    ).joinToString(" · "),
                    style = Gc.type.monoSmall.copy(color = if (stale) c.warn else c.dim),
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ClubPicker(state: PrevolUiState, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Gc.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.control,
        title = { Text("Club", style = Gc.type.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.clubs, key = { it.id }) { club ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onPick(club.id) }.padding(vertical = 8.dp),
                    ) {
                        Text(club.name, style = Gc.type.body.copy(color = if (club.id == state.club?.id) c.ok else c.ink))
                        Text(listOfNotNull(club.airfieldIcao, club.city, club.postcode.take(2)).joinToString(" · "), style = Gc.type.monoSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
