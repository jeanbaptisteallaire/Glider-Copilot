package com.neutronstar.glidy.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neutronstar.glidy.social.FeedFlight
import com.neutronstar.glidy.social.FeedPilot
import com.neutronstar.glidy.social.SocialRepository
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
 * S17 — fil : recherche de pilotes (avec délai de frappe), suivre / ne plus suivre, grille paginée
 * (défilement infini par curseur). Données de démonstration jusqu'au branchement Supabase (S18).
 */
class FeedViewModel(private val repository: SocialRepository) : ViewModel() {
    private val mutable = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = mutable.asStateFlow()
    private var searchJob: Job? = null
    private var pageJob: Job? = null

    init { reload() }

    fun reload() {
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
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
        pageJob = viewModelScope.launch {
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
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = runCatching { repository.search(query) }.getOrDefault(emptyList())
            mutable.value = mutable.value.copy(results = results)
        }
    }

    fun toggleFollow(pilotId: String) {
        val follow = pilotId !in mutable.value.following
        viewModelScope.launch {
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

class FeedViewModelFactory(private val repository: SocialRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FeedViewModel::class.java))
        return FeedViewModel(repository) as T
    }
}
