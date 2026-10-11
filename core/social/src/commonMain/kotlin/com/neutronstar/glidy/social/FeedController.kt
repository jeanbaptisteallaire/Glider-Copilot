package com.neutronstar.glidy.social

import com.neutronstar.glidy.common.zeroPad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val query: String = "",
    val results: List<FeedPilot> = emptyList(),
    val following: Set<String> = emptySet(),
    val suggestions: List<FeedPilot> = emptyList(),
    val flights: List<FeedFlight> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val selected: FeedFlight? = null,
) {
    val endReached: Boolean get() = nextCursor == null
}

/**
 * S17 — fil (commun Android / iOS) : recherche de pilotes (avec délai de frappe), suivre / ne plus suivre, grille
 * paginée (défilement infini par curseur). Android l'enveloppe dans FeedViewModel, iOS l'observe depuis Swift.
 */
class FeedController(private val repository: SocialRepository, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = mutable.asStateFlow()
    /** Lecture directe de l'état (Swift). */
    val current: FeedUiState get() = mutable.value
    private var searchJob: Job? = null
    private var pageJob: Job? = null

    fun reload() {
        pageJob?.cancel()
        pageJob = scope.launch {
            val following = runCatching { repository.following() }.getOrDefault(emptySet())
            val suggestions = runCatching { repository.suggestions(8) }.getOrDefault(emptyList())
            val page = runCatching { repository.feed(null, PAGE) }.getOrNull()
            mutable.value = mutable.value.copy(
                following = following,
                suggestions = suggestions,
                flights = page?.items.orEmpty(),
                nextCursor = page?.nextCursor,
                loading = false,
                loadingMore = false,
            )
        }
    }

    /** Appelé quand la fin de la grille approche. Ignoré si une page est déjà en cours ou si le fil est fini. */
    fun loadMore() {
        val s = mutable.value
        if (s.loading || s.loadingMore || s.endReached) return
        mutable.value = s.copy(loadingMore = true)
        pageJob = scope.launch {
            val page = runCatching { repository.feed(s.nextCursor, PAGE) }.getOrNull()
            val latest = mutable.value
            mutable.value = if (page == null) latest.copy(loadingMore = false)
            else latest.copy(flights = latest.flights + page.items.filter { f -> latest.flights.none { it.id == f.id } }, nextCursor = page.nextCursor, loadingMore = false)
        }
    }

    fun onQueryChange(query: String) {
        mutable.value = mutable.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            mutable.value = mutable.value.copy(results = emptyList())
            return
        }
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = runCatching { repository.search(query) }.getOrDefault(emptyList())
            mutable.value = mutable.value.copy(results = results)
        }
    }

    fun toggleFollow(pilotId: String) {
        val follow = pilotId !in mutable.value.following
        scope.launch {
            val following = runCatching { repository.setFollowing(pilotId, follow) }.getOrNull() ?: return@launch
            mutable.value = mutable.value.copy(following = following)
            reload()
        }
    }

    fun open(flight: FeedFlight) { mutable.value = mutable.value.copy(selected = flight) }
    fun close() { mutable.value = mutable.value.copy(selected = null) }

    companion object {
        const val PAGE = 18
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

/** Textes des vignettes du fil, identiques sur Android et iOS. */
object FeedFormat {
    /** « 42 min » ou « 3 h 05 » (String.format("%d h %02d", Locale.FRANCE)). */
    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        return if (h == 0L) "$m min" else "$h h ${m.toInt().zeroPad(2)}"
    }

    /** « 87,4 km », puis sans décimale à partir de 100 km (Locale.FRANCE). */
    fun km(meters: Long): String =
        com.neutronstar.glidy.common.formatFixed(meters / 1000.0, if (meters >= 100_000) 0 else 1, decimalComma = true) + " km"
}
