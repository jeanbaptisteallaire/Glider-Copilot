package com.neutronstar.glidy.feed

import com.neutronstar.glidy.social.DemoSocialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `empty feed shows suggestions, following fills the grid`() = runTest(dispatcher.scheduler) {
        val vm = FeedViewModel(DemoSocialRepository())
        advanceUntilIdle()
        assertTrue(vm.state.value.flights.isEmpty())
        assertTrue(vm.state.value.suggestions.isNotEmpty())

        vm.toggleFollow("p01")
        advanceUntilIdle()
        assertTrue("p01" in vm.state.value.following)
        assertTrue(vm.state.value.flights.isNotEmpty())
        assertTrue(vm.state.value.flights.all { it.pilot.id == "p01" })
    }

    @Test
    fun `infinite scroll appends pages until the end`() = runTest(dispatcher.scheduler) {
        val repo = DemoSocialRepository()
        repo.pilots.forEach { repo.setFollowing(it.id, true) }
        val vm = FeedViewModel(repo)
        advanceUntilIdle()
        assertEquals(FeedViewModel.PAGE, vm.state.value.flights.size)
        var guard = 0
        while (!vm.state.value.endReached && guard++ < 50) { vm.loadMore(); advanceUntilIdle() }
        val ids = vm.state.value.flights.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(vm.state.value.endReached)
        assertTrue(ids.size > FeedViewModel.PAGE)
    }

    @Test
    fun `search is debounced and accent insensitive`() = runTest(dispatcher.scheduler) {
        val vm = FeedViewModel(DemoSocialRepository())
        advanceUntilIdle()
        vm.onQueryChange("le")
        vm.onQueryChange("lea")
        advanceTimeBy(FeedViewModel.SEARCH_DEBOUNCE_MS + 10)
        advanceUntilIdle()
        assertEquals(listOf("p03"), vm.state.value.results.map { it.id })
        vm.onQueryChange("")
        assertTrue(vm.state.value.results.isEmpty())
    }
}
