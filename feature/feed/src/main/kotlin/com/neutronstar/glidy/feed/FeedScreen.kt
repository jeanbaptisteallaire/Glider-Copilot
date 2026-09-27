package com.neutronstar.glidy.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GcKpi
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.GcThemeToggleButton
import com.neutronstar.glidercopilot.designsystem.GcTraceThumbnail
import com.neutronstar.glidy.social.FeedFlight
import com.neutronstar.glidy.social.FeedPilot
import com.neutronstar.glidy.social.SocialRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * S17 — onglet Feed (premier onglet) : recherche de pilotes, suivre, grille 3 colonnes des vols des pilotes
 * suivis avec défilement infini. Vignette claire + trace rouge, distance et durée dessous (demande JB).
 */

@Composable
fun FeedApp(repository: SocialRepository) {
    val factory = remember(repository) { FeedViewModelFactory(repository) }
    val vm: FeedViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsState()
    val selected = state.selected
    if (selected != null) {
        BackHandler(onBack = vm::close)
        FeedFlightDetail(
            flight = selected,
            following = selected.pilot.id in state.following,
            onBack = vm::close,
            onToggleFollow = { vm.toggleFollow(selected.pilot.id) },
        )
        return
    }
    FeedScreen(
        state = state,
        onQueryChange = vm::onQueryChange,
        onToggleFollow = vm::toggleFollow,
        onOpen = vm::open,
        onLoadMore = vm::loadMore,
    )
}

@Composable
fun FeedScreen(
    state: FeedUiState,
    onQueryChange: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onOpen: (FeedFlight) -> Unit,
    onLoadMore: () -> Unit,
) {
    val c = Gc.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Feed", style = Gc.type.title, modifier = Modifier.weight(1f))
            GcThemeToggleButton()
        }
        SearchBar(state.query, onQueryChange, Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(10.dp))
        when {
            state.query.isNotBlank() -> SearchResults(state, onToggleFollow)
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.ok, strokeWidth = 2.dp)
            }
            else -> FeedGrid(state, onToggleFollow, onOpen, onLoadMore)
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    val c = Gc.colors
    Row(
        modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(c.control).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GcIcons.Search, contentDescription = null, tint = c.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Rechercher un pilote (nom ou @pseudo)", style = Gc.type.body.copy(color = c.faint, fontSize = 15.sp), maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Gc.type.body.copy(fontSize = 15.sp),
                cursorBrush = SolidColor(c.ink),
                modifier = Modifier.fillMaxWidth().testTag("feed-search")
                    .semantics { contentDescription = "Rechercher un pilote" },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Effacer") { onChange("") },
                contentAlignment = Alignment.Center,
            ) { Icon(GcIcons.Close, contentDescription = "Effacer la recherche", tint = c.dim, modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable
private fun SearchResults(state: FeedUiState, onToggleFollow: (String) -> Unit) {
    val c = Gc.colors
    LazyColumn(
        Modifier.fillMaxSize().testTag("feed-results"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.results.isEmpty()) {
            item { Text("Aucun pilote trouvé pour « ${state.query.trim()} ».", style = Gc.type.bodySmall.copy(color = c.dim), modifier = Modifier.padding(8.dp)) }
        }
        items(state.results, key = { it.id }) { p -> PilotRow(p, p.id in state.following, onToggleFollow) }
    }
}

@Composable
private fun FeedGrid(
    state: FeedUiState,
    onToggleFollow: (String) -> Unit,
    onOpen: (FeedFlight) -> Unit,
    onLoadMore: () -> Unit,
) {
    val c = Gc.colors
    val grid = rememberLazyGridState()
    // défilement infini : page suivante quand il reste moins de 2 lignes (6 vignettes) à afficher
    val nearEnd by remember {
        derivedStateOf {
            val info = grid.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = grid,
        modifier = Modifier.fillMaxSize().testTag("feed-grid"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Pilotes et vols d'exemple : le vrai fil arrivera avec les comptes en ligne.",
                style = Gc.type.bodySmall.copy(color = c.faint, fontSize = 12.sp),
            )
        }
        if (state.following.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ton fil est vide", style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp))
                    Text("Suis des pilotes pour voir leurs vols ici.", style = Gc.type.bodySmall)
                }
            }
        }
        // peu d'abonnements : suggestions en tête du fil, pour le remplir en quelques gestes
        if (state.suggestions.isNotEmpty() && state.following.size < FEW_FOLLOWS) {
            suggestionsBlock(state, onToggleFollow)
        }
        items(state.flights, key = { it.id }) { f -> FeedTile(f) { onOpen(f) } }
        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.ok, strokeWidth = 2.dp)
                }
            }
        }
        if (state.suggestions.isNotEmpty() && state.following.size >= FEW_FOLLOWS && state.endReached) {
            suggestionsBlock(state, onToggleFollow)
        }
        if (state.endReached && state.flights.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Tu as tout vu ✈", style = Gc.type.bodySmall.copy(color = c.faint), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private const val FEW_FOLLOWS = 3

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.suggestionsBlock(
    state: FeedUiState,
    onToggleFollow: (String) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            "Pilotes à suivre",
            style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    items(state.suggestions, key = { "s-" + it.id }, span = { GridItemSpan(maxLineSpan) }) { p ->
        PilotRow(p, p.id in state.following, onToggleFollow)
    }
}

@Composable
private fun FeedTile(flight: FeedFlight, onClick: () -> Unit) {
    val c = Gc.colors
    Column(
        Modifier
            .testTag("feed-tile")
            .semantics { contentDescription = "Vol de ${flight.pilot.displayName}, ${formatKm(flight.distanceMeters)}, ${formatDuration(flight.durationSeconds)}" }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        GcTraceThumbnail(flight.route, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(5.dp))
        Text(
            "${formatKm(flight.distanceMeters)} · ${formatDuration(flight.durationSeconds)}",
            style = Gc.type.bodySmall.copy(color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            maxLines = 1,
        )
        Text("@${flight.pilot.username}", style = Gc.type.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PilotRow(pilot: FeedPilot, following: Boolean, onToggleFollow: (String) -> Unit) {
    val c = Gc.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("pilot-${pilot.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(pilot.initials, Modifier.size(46.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pilot.displayName, style = Gc.type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("@${pilot.username}", pilot.experience?.label).joinToString(" · "),
                style = Gc.type.bodySmall.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(pilot.club, style = Gc.type.bodySmall.copy(fontSize = 12.sp, color = c.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FollowButton(following) { onToggleFollow(pilot.id) }
    }
}

@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val c = Gc.colors
    Box(
        Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (following) c.control else c.accentFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (following) "Suivi" else "Suivre",
            style = Gc.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (following) c.ink else c.onAccentFill),
        )
    }
}

@Composable
private fun Avatar(initials: String, modifier: Modifier) {
    val c = Gc.colors
    Box(modifier.clip(CircleShape).background(c.accentFill), contentAlignment = Alignment.Center) {
        Text(initials, style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.onAccentFill))
    }
}

/** Détail d'un vol du fil (démonstration : pas de fichier IGC, donc pas de rejeu 3D pour ces vols). */
@Composable
private fun FeedFlightDetail(flight: FeedFlight, following: Boolean, onBack: () -> Unit, onToggleFollow: () -> Unit) {
    val c = Gc.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.background).testTag("feed-detail"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Retour au fil", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(GcIcons.ChevronDown, contentDescription = "Retour", tint = c.ink, modifier = Modifier.size(22.dp).rotateLeft()) }
                Text("Vol", style = Gc.type.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(flight.pilot.initials, Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(flight.pilot.displayName, style = Gc.type.body.copy(fontWeight = FontWeight.SemiBold))
                    Text("@${flight.pilot.username} · ${flight.site}", style = Gc.type.bodySmall.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FollowButton(following, onToggleFollow)
            }
        }
        item { GcTraceThumbnail(flight.route, Modifier.fillMaxWidth().aspectRatio(1f)) }
        item {
            Row(Modifier.fillMaxWidth()) {
                GcKpi(formatDuration(flight.durationSeconds), "Durée", Modifier.weight(1f), color = c.ok)
                GcKpi(formatKm(flight.distanceMeters), "Distance", Modifier.weight(1f))
                GcKpi("${flight.maxAltitudeMeters} m", "Alt. max", Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(DATE.format(flight.startedAt.atZone(ZoneId.systemDefault())), style = Gc.type.bodySmall)
                if (flight.pilot.isDemo) GcPill("Exemple", c.warn)
            }
        }
        if (flight.pilot.bio.isNotBlank()) item { Text(flight.pilot.bio, style = Gc.type.body) }
    }
}

private fun Modifier.rotateLeft(): Modifier = this.rotate(90f)

internal fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    return if (h == 0L) "$m min" else "%d h %02d".format(Locale.FRANCE, h, m)
}

internal fun formatKm(meters: Long): String =
    if (meters >= 100_000) "%.0f km".format(Locale.FRANCE, meters / 1000.0) else "%.1f km".format(Locale.FRANCE, meters / 1000.0)

private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
