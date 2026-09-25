package com.neutronstar.glidercopilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neutronstar.glidercopilot.designsystem.GlidyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GliderApp).container
        // rejeu OGN anonymisé pour la démonstration et les captures CI : adb shell am start … --ez glidy.ogn.replay true
        if (intent?.getBooleanExtra("glidy.ogn.replay", false) == true) container.ogn.replay = true
        // rejeu du vol de démonstration (capteurs simulés, OGN rejoué en même temps) : --ez glidy.flight.replay true [--ef glidy.flight.speed 4]
        if (intent?.getBooleanExtra("glidy.flight.replay", false) == true) {
            container.flight.replay = true
            container.flight.replaySpeed = intent.getFloatExtra("glidy.flight.speed", 1f).toDouble().coerceIn(0.5, 20.0)
            container.ogn.replay = true
        }
        // S10 : vol synthétique d'exemple dans Mes vols (captures CI) : --ez glidy.flights.demo true
        if (intent?.getBooleanExtra("glidy.flights.demo", false) == true) container.flights.importDemoFlight()
        if (intent?.getBooleanExtra("glidy.flights.replay3d", false) == true) container.flights.openReplayOnStart = true
        setContent {
            GlidyTheme {
                AppRoot(container)
            }
        }
    }
}
