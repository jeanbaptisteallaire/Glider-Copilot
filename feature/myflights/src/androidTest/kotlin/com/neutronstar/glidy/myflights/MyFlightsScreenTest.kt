package com.neutronstar.glidy.myflights

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.FlightId
import com.neutronstar.glidy.flightarchive.FlightSummary
import com.neutronstar.glidy.flightarchive.IgcFileRef
import com.neutronstar.glidy.flightarchive.LocalFileState
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyFlightsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateIsVisible() {
        composeRule.setContent {
            MyFlightsScreen(
                state = MyFlightsUiState.Loading,
                onRetry = {},
                onImport = {},
                onFlightSelected = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("loading").assertExists()
    }

    @Test
    fun errorStateOffersRetry() {
        var retryInvoked = false
        composeRule.setContent {
            MyFlightsScreen(
                state = MyFlightsUiState.Error("Erreur de test"),
                onRetry = { retryInvoked = true },
                onImport = {},
                onFlightSelected = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("error").assertExists()
        composeRule.onNodeWithText("RÉESSAYER").performClick()
        composeRule.runOnIdle { assertTrue(retryInvoked) }
    }

    @Test
    fun emptyStateAndImportActionAreVisible() {
        composeRule.setContent {
            MyFlightsScreen(
                state = MyFlightsUiState.Ready(emptyList()),
                onRetry = {},
                onImport = {},
                onFlightSelected = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Votre carnet est vide").assertExists()
        composeRule.onNodeWithText("0 VOLS").assertExists()
        composeRule.onNodeWithTag("import-igc").assertExists()
    }

    @Test
    fun orderedCardsExposeMissingFileAndSelection() {
        val newest = uiFlight(2, LocalFileState.MISSING)
        val older = uiFlight(1)
        var selected: FlightId? = null
        composeRule.setContent {
            MyFlightsScreen(
                state = MyFlightsUiState.Ready(listOf(newest, older)),
                onRetry = {},
                onImport = {},
                onFlightSelected = { selected = it.id },
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("Vol 1 sur 2 : capture-2.igc").assertExists()
        composeRule.onNodeWithTag("flight-list")
            .performScrollToNode(hasContentDescription("Vol 2 sur 2 : capture-1.igc"))
        composeRule.onNodeWithContentDescription("Vol 2 sur 2 : capture-1.igc").assertExists()
        composeRule.onNodeWithTag("flight-list")
            .performScrollToNode(hasTestTag("flight-card-${newest.id.value}"))
        composeRule.onNodeWithText("FICHIER MANQUANT").assertExists()

        composeRule.onNodeWithTag("flight-card-${newest.id.value}").performClick()
        composeRule.runOnIdle { assertEquals(newest.id, selected) }
    }

    @Test
    fun detailSelectionAndBackActionAreRendered() {
        val flight = uiFlight(4)
        var backInvoked = false
        composeRule.setContent {
            MyFlightsScreen(
                state = MyFlightsUiState.Ready(listOf(flight), selectedFlightId = flight.id),
                onRetry = {},
                onImport = {},
                onFlightSelected = {},
                onBack = { backInvoked = true },
            )
        }

        composeRule.onNodeWithTag("flight-detail").assertExists()
        composeRule.onNodeWithTag("back").performClick()
        composeRule.runOnIdle { assertTrue(backInvoked) }
    }

    @Test
    fun lazyListCanReachTheLastOfFiveHundredFlights() {
        val flights = (0 until 500).map(::uiFlight).reversed()
        val oldest = flights.last()
        composeRule.setContent {
            MyFlightsScreen(
                state = MyFlightsUiState.Ready(flights),
                onRetry = {},
                onImport = {},
                onFlightSelected = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("flight-list")
            .performScrollToNode(hasTestTag("flight-card-${oldest.id.value}"))
        composeRule.onNodeWithTag("flight-card-${oldest.id.value}").assertExists()
    }
}

private fun uiFlight(index: Int, state: LocalFileState = LocalFileState.AVAILABLE): ArchivedFlight {
    val startedAt = Instant.parse("2026-03-01T10:00:00Z").plusSeconds(index.toLong() * 3_600L)
    val sha = index.toString(16).padStart(64, '0')
    return ArchivedFlight(
        id = FlightId(UUID.nameUUIDFromBytes("ui-flight-$index".toByteArray()).toString()),
        file = IgcFileRef("$sha.igc", "capture-$index.igc", 2_048L, sha),
        summary = FlightSummary(
            startedAt = startedAt,
            endedAt = startedAt.plusSeconds(4_200L),
            pointCount = 4_200,
            validPointCount = 4_200,
            distanceMeters = 91_000,
            minimumAltitudeMeters = 180,
            maximumAltitudeMeters = 2_050,
            positiveGainMeters = 2_450,
            bounds = null,
        ),
        pilot = "Pilote test",
        gliderType = "Discus 2",
        gliderId = "F-TEST",
        localState = state,
    )
}
