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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neutronstar.glidercopilot.designsystem.Gc
import com.neutronstar.glidercopilot.designsystem.GcCard
import com.neutronstar.glidercopilot.designsystem.GcIcons
import com.neutronstar.glidercopilot.designsystem.GcKpi
import com.neutronstar.glidercopilot.designsystem.GcPill
import com.neutronstar.glidercopilot.designsystem.GcSectionTitle
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
 * V18.1 « New UI » : Large Title, fond groupé gris, listes en cartes blanches arrondies, bleu ciel pour suivre
 * et la sélection (jetons route / accentFill : vert GLIDY en thème sombre), chiffres façon Apple Santé.
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
            Text("Feed", style = if (Gc.social) Gc.type.largeTitle else Gc.type.title, modifier = Modifier.weight(1f))
            GcThemeToggleButton()
        }
        SearchBar(state.query, onQueryChange, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        when {
            state.query.isNotBlank() -> SearchResults(state, onToggleFollow)
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.route, strokeWidth = 2.dp)
            }
            else -> FeedGrid(state, onToggleFollow, onOpen, onLoadMore)
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    val c = Gc.colors
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.control).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GcIcons.Search, contentDescription = null, tint = c.faint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Nom ou @pseudo", style = Gc.type.callout.copy(color = c.faint), maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Gc.type.callout,
                cursorBrush = SolidColor(c.route),
                modifier = Modifier.fillMaxWidth().testTag("feed-search")
                    .semantics { contentDescription = "Rechercher un pilote" },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Effacer") { onChange("") },
                contentAlignment = Alignment.Center,
            ) { Icon(GcIcons.Close, contentDescription = "Effacer la recherche", tint = c.faint, modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable
private fun SearchResults(state: FeedUiState, onToggleFollow: (String) -> Unit) {
    val c = Gc.colors
    LazyColumn(
        Modifier.fillMaxSize().testTag("feed-results"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        if (state.results.isEmpty()) {
            item { Text("Aucun résultat pour « ${state.query.trim()} »", style = Gc.type.subhead.copy(color = c.dim), modifier = Modifier.padding(8.dp)) }
        }
        itemsIndexed(state.results, key = { _, p -> p.id }) { i, p ->
            GroupedRow(i, state.results.size) { PilotRow(p, p.id in state.following, onToggleFollow) }
        }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Vols d'exemple en attendant les comptes en ligne.",
                style = Gc.type.footnote.copy(color = c.faint),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (state.following.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Fil vide", style = Gc.type.title3)
                    Text("Suis des pilotes pour voir leurs vols.", style = Gc.type.subhead.copy(color = c.dim))
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
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.route, strokeWidth = 2.dp)
                }
            }
        }
        if (state.suggestions.isNotEmpty() && state.following.size >= FEW_FOLLOWS && state.endReached) {
            suggestionsBlock(state, onToggleFollow)
        }
        if (state.endReached && state.flights.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Tu es à jour", style = Gc.type.footnote.copy(color = c.faint), modifier = Modifier.padding(top = 8.dp))
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
        GcSectionTitle("À suivre", Modifier.padding(bottom = 6.dp))
    }
    itemsIndexed(state.suggestions, key = { _, p -> "s-" + p.id }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { i, p ->
        GroupedRow(i, state.suggestions.size, Modifier.padding(bottom = if (i == state.suggestions.size - 1) 14.dp else 0.dp)) {
            PilotRow(p, p.id in state.following, onToggleFollow)
        }
    }
}

@Composable
private fun FeedTile(flight: FeedFlight, onClick: () -> Unit) {
    val c = Gc.colors
    Column(
        Modifier
            .testTag("feed-tile")
            .semantics { contentDescription = "Vol de ${flight.pilot.displayName}, ${formatKm(flight.distanceMeters)}, ${formatDuration(flight.durationSeconds)}" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(bottom = 14.dp),
    ) {
        GcTraceThumbnail(flight.route, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.height(6.dp))
        Text(
            "${formatKm(flight.distanceMeters)} · ${formatDuration(flight.durationSeconds)}",
            style = Gc.type.footnote.copy(color = c.ink, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("@${flight.pilot.username}", style = Gc.type.caption1.copy(color = c.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PilotRow(pilot: FeedPilot, following: Boolean, onToggleFollow: (String) -> Unit) {
    val c = Gc.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("pilot-${pilot.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(pilot.initials, Modifier.size(AVATAR))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pilot.displayName, style = Gc.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("@${pilot.username}", pilot.experience?.label).joinToString(" · "),
                style = Gc.type.footnote.copy(color = c.dim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(pilot.club, style = Gc.type.footnote.copy(color = c.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        FollowButton(following) { onToggleFollow(pilot.id) }
    }
}

@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val c = Gc.colors
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (following) c.control else c.accentFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (following) "Suivi" else "Suivre",
            style = Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold, color = if (following) c.ink else c.onAccentFill),
        )
    }
}

@Composable
private fun Avatar(initials: String, modifier: Modifier) {
    val c = Gc.colors
    Box(modifier.clip(CircleShape).background(c.accentFill), contentAlignment = Alignment.Center) {
        Text(initials, style = Gc.type.subhead.copy(fontWeight = FontWeight.SemiBold, color = c.onAccentFill))
    }
}

/** Détail d'un vol du fil (démonstration : pas de fichier IGC, donc pas de rejeu 3D pour ces vols). */
@Composable
private fun FeedFlightDetail(flight: FeedFlight, following: Boolean, onBack: () -> Unit, onToggleFollow: () -> Unit) {
    val c = Gc.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.background).testTag("feed-detail"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // barre de navigation iOS : chevron + titre précédent en bleu ciel (vert GLIDY en thème sombre)
            Row(
                Modifier.padding(top = 6.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClickLabel = "Retour au fil", onClick = onBack).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GcIcons.ChevronDown, contentDescription = "Retour", tint = c.route, modifier = Modifier.size(24.dp).rotateLeft())
                Text("Fil", style = Gc.type.body.copy(color = c.route))
            }
        }
        item {
            GcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(flight.pilot.initials, Modifier.size(AVATAR))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(flight.pilot.displayName, style = Gc.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${flight.pilot.username} · ${flight.site}", style = Gc.type.footnote.copy(color = c.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    FollowButton(following, onToggleFollow)
                }
            }
        }
        item { GcTraceThumbnail(flight.route, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))) }
        item {
            GcCard(
                title = DATE.format(flight.startedAt.atZone(ZoneId.systemDefault())).replaceFirstChar { it.titlecase(Locale.FRANCE) },
                trailing = { if (flight.pilot.isDemo) GcPill("Exemple", c.warn) },
            ) {
                Row(Modifier.fillMaxWidth()) {
                    GcKpi(formatDuration(flight.durationSeconds), "Durée", Modifier.weight(1f), labelColor = c.sun)
                    GcKpi(formatKm(flight.distanceMeters), "Distance", Modifier.weight(1f), labelColor = c.sky)
                    GcKpi("${flight.maxAltitudeMeters} m", "Alt. max", Modifier.weight(1f), labelColor = c.altitude)
                }
            }
        }
        if (flight.pilot.bio.isNotBlank()) item { GcCard { Text(flight.pilot.bio, style = Gc.type.body) } }
    }
}

/** Ligne d'une liste groupée iOS : carte blanche arrondie en tête et en fin de groupe, filet entre les lignes. */
@Composable
private fun GroupedRow(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Gc.colors
    val top: Dp = if (index == 0) 14.dp else 0.dp
    val bottom: Dp = if (index == count - 1) 14.dp else 0.dp
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom))
            .background(c.panel),
    ) {
        Box(Modifier.padding(horizontal = 16.dp)) { content() }
        if (index < count - 1) HorizontalDivider(Modifier.padding(start = 16.dp + AVATAR + 12.dp), thickness = 0.5.dp, color = c.lineSoft)
    }
}

private val AVATAR = 42.dp

private fun Modifier.rotateLeft(): Modifier = this.rotate(90f)

internal fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    return if (h == 0L) "$m min" else "%d h %02d".format(Locale.FRANCE, h, m)
}

internal fun formatKm(meters: Long): String =
    if (meters >= 100_000) "%.0f km".format(Locale.FRANCE, meters / 1000.0) else "%.1f km".format(Locale.FRANCE, meters / 1000.0)

private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
