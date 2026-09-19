package com.neutronstar.glidy.myflights

import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightArchiveRepository
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightSummary
import com.neutronstar.glidy.flightarchive.IgcFileRef
import com.neutronstar.glidy.flightarchive.ImportIgcResult
import com.neutronstar.glidy.flightarchive.LocalFileState
import com.neutronstar.glidy.flightarchive.ReconciliationResult
import com.neutronstar.glidy.flightarchive.RemoveFlightResult
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyFlightsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading remains visible until repository responds`() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(flights = listOf(flight(1)), reconciliationGate = gate)
        val viewModel = MyFlightsViewModel(repository)

        runCurrent()
        assertEquals(MyFlightsUiState.Loading, viewModel.state.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is MyFlightsUiState.Ready)
    }

    @Test
    fun `flights are ordered from newest to oldest and missing files remain visible`() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(
            flights = listOf(
                flight(1),
                flight(3, LocalFileState.MISSING),
                flight(2),
            ),
            reconciliation = ReconciliationResult(0, 0, 1, 0),
        )
        val viewModel = MyFlightsViewModel(repository)

        advanceUntilIdle()
        val ready = viewModel.state.value as MyFlightsUiState.Ready
        assertEquals(listOf("vol-3.igc", "vol-2.igc", "vol-1.igc"), ready.flights.map { it.file.fileName })
        assertEquals(LocalFileState.MISSING, ready.flights.first().localState)
        assertEquals("1 fichier(s) IGC manquant(s).", ready.notice)
    }

    @Test
    fun `selection opens and closes a known flight`() = runTest(dispatcher.scheduler) {
        val selected = flight(8)
        val viewModel = MyFlightsViewModel(FakeRepository(listOf(selected)))
        advanceUntilIdle()

        viewModel.selectFlight(selected.id)
        assertEquals(selected.id, (viewModel.state.value as MyFlightsUiState.Ready).selectedFlightId)

        viewModel.closeDetail()
        assertNull((viewModel.state.value as MyFlightsUiState.Ready).selectedFlightId)
    }

    @Test
    fun `repository failure produces a retryable error state`() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList(), failure = IllegalStateException("database unavailable"))
        val viewModel = MyFlightsViewModel(repository)

        advanceUntilIdle()
        assertTrue(viewModel.state.value is MyFlightsUiState.Error)

        repository.failure = null
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is MyFlightsUiState.Ready)
    }

    @Test
    fun `five hundred flights are prepared without truncation`() = runTest(dispatcher.scheduler) {
        val flights = (0 until 500).map(::flight).reversed()
        val viewModel = MyFlightsViewModel(FakeRepository(flights))

        advanceUntilIdle()
        val ready = viewModel.state.value as MyFlightsUiState.Ready
        assertEquals(500, ready.flights.size)
        assertEquals("vol-499.igc", ready.flights.first().file.fileName)
        assertEquals("vol-0.igc", ready.flights.last().file.fileName)
        assertTrue(ready.flights.zipWithNext().all { (first, second) ->
            first.summary!!.startedAt >= second.summary!!.startedAt
        })
    }

    private class FakeRepository(
        flights: List<ArchivedFlight> = emptyList(),
        private val reconciliationGate: CompletableDeferred<Unit>? = null,
        private val reconciliation: ReconciliationResult = ReconciliationResult(0, 0, 0, 0),
        var failure: Exception? = null,
    ) : FlightArchiveRepository {
        private val storedFlights = flights.toMutableList()

        override suspend fun importIgc(fileName: String, source: InputStream): ImportIgcResult =
            ImportIgcResult.Failed("not configured")

        override suspend fun reconcile(): ReconciliationResult {
            reconciliationGate?.await()
            failure?.let { throw it }
            return reconciliation
        }

        override suspend fun listFlights(): List<ArchivedFlight> {
            failure?.let { throw it }
            return storedFlights.toList()
        }

        override suspend fun findFlight(id: FlightId): ArchivedFlight? = storedFlights.firstOrNull { it.id == id }

        override suspend fun removeLocalFlight(id: FlightId): RemoveFlightResult =
            if (storedFlights.removeAll { it.id == id }) RemoveFlightResult.Removed else RemoveFlightResult.NotFound
    }
}

private fun flight(index: Int, state: LocalFileState = LocalFileState.AVAILABLE): ArchivedFlight {
    val start = Instant.parse("2026-01-01T10:00:00Z").plusSeconds(index.toLong() * 3_600L)
    val sha = index.toString(16).padStart(64, '0')
    return ArchivedFlight(
        id = FlightId(UUID.nameUUIDFromBytes("flight-$index".toByteArray()).toString()),
        file = IgcFileRef("$sha.igc", "vol-$index.igc", 1_024L + index, sha),
        summary = FlightSummary(
            startedAt = start,
            endedAt = start.plusSeconds(3_600),
            pointCount = 3_600,
            validPointCount = 3_600,
            distanceMeters = 80_000,
            minimumAltitudeMeters = 190,
            maximumAltitudeMeters = 1_850,
            positiveGainMeters = 2_100,
            bounds = null,
        ),
        pilot = null,
        gliderType = "Discus 2",
        gliderId = "F-CODE",
        localState = state,
    )
}
