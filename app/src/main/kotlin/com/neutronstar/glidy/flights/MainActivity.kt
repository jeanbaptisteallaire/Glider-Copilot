package com.neutronstar.glidy.flights

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.neutronstar.glidy.flightarchive.ArchivedFlight
import com.neutronstar.glidy.flightarchive.data.LocalArchiveModule
import com.neutronstar.glidy.myflights.MyFlightsApp
import com.neutronstar.glidy.replay3d.Replay3dScreen

class MainActivity : ComponentActivity() {
    private val archiveRepository by lazy { LocalArchiveModule.create(applicationContext) }
    private val shareGateway by lazy {
        LocalArchiveModule.createShareGateway(applicationContext, archiveRepository)
    }
    private val completedFlightGateway by lazy {
        LocalArchiveModule.createCompletedFlightGateway(applicationContext, archiveRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var replayedFlight by remember { mutableStateOf<ArchivedFlight?>(null) }
            BackHandler(enabled = replayedFlight != null) { replayedFlight = null }
            val replay = replayedFlight
            if (replay == null) {
                MyFlightsApp(
                    repository = archiveRepository,
                    shareGateway = shareGateway,
                    completedFlightGateway = completedFlightGateway,
                    onReplay3d = { replayedFlight = it },
                )
            } else {
                Replay3dScreen(flight = replay, onBack = { replayedFlight = null })
            }
        }
    }
}
