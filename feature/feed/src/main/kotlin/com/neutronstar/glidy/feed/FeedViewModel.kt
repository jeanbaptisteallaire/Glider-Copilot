package com.neutronstar.glidy.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neutronstar.glidy.social.FeedController
import com.neutronstar.glidy.social.FeedFlight
import com.neutronstar.glidy.social.SocialRepository
import kotlinx.coroutines.flow.StateFlow

/** État du fil : modèle commun Android / iOS (core:social). */
typealias FeedUiState = com.neutronstar.glidy.social.FeedUiState

/**
 * S17 — fil : enveloppe Android de FeedController (core:social, commun avec iOS) ; la logique (recherche avec délai
 * de frappe, suivre, pagination par curseur) vit là-bas.
 */
class FeedViewModel(repository: SocialRepository) : ViewModel() {
    private val controller = FeedController(repository, viewModelScope)
    val state: StateFlow<FeedUiState> = controller.state

    init { reload() }

    fun reload() = controller.reload()
    fun loadMore() = controller.loadMore()
    fun onQueryChange(query: String) = controller.onQueryChange(query)
    fun toggleFollow(pilotId: String) = controller.toggleFollow(pilotId)
    fun open(flight: FeedFlight) = controller.open(flight)
    fun close() = controller.close()

    companion object {
        const val PAGE = FeedController.PAGE
        const val SEARCH_DEBOUNCE_MS = FeedController.SEARCH_DEBOUNCE_MS
    }
}

class FeedViewModelFactory(private val repository: SocialRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FeedViewModel::class.java))
        return FeedViewModel(repository) as T
    }
}
