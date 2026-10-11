package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidy.social.DemoSocialRepository
import com.neutronstar.glidy.social.FeedController
import com.neutronstar.glidy.social.FeedFlight
import com.neutronstar.glidy.social.FeedFormat
import com.neutronstar.glidy.social.FeedUiState
import com.neutronstar.glidy.social.FollowStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Fil (Feed) de l'édition complète iOS : même FeedController qu'Android (core:social), données de démonstration
 * jusqu'aux comptes en ligne. Pilotes suivis gardés dans [dir]. [onChange] est appelé sur le fil principal.
 */
class IosFeed(dir: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val controller = FeedController(DemoSocialRepository(FileFollowStore(dir)), scope)
    var onChange: ((FeedUiState) -> Unit)? = null

    init {
        controller.state.onEach { s -> onChange?.invoke(s) }.launchIn(scope)
        controller.reload()
    }

    val state: FeedUiState get() = controller.current
    fun reload() = controller.reload()
    fun loadMore() = controller.loadMore()
    fun search(query: String) = controller.onQueryChange(query)
    fun toggleFollow(pilotId: String) = controller.toggleFollow(pilotId)
    fun open(flight: FeedFlight) = controller.open(flight)
    fun close() = controller.close()

    fun duration(seconds: Long): String = FeedFormat.duration(seconds)
    fun km(meters: Long): String = FeedFormat.km(meters)

    /** Trace d'une vignette : x, y alternés dans le carré unité. */
    fun routeXY(flight: FeedFlight): List<Float> = flight.route.flatMap { listOf(it.first, it.second) }
}

private class FileFollowStore(dir: String) : FollowStore {
    private val fs = FileSystem.SYSTEM
    private val path = (dir.trimEnd('/') + "/follows.txt").toPath()
    override suspend fun load(): Set<String> = runCatching { fs.read(path) { readUtf8() }.lines().filter { it.isNotBlank() }.toSet() }.getOrDefault(emptySet())
    override suspend fun save(ids: Set<String>) { runCatching { path.parent?.let { fs.createDirectories(it) }; fs.write(path) { writeUtf8(ids.joinToString("\n")) } } }
}
