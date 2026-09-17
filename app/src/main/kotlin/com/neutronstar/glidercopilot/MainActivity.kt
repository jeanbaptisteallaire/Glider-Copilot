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
        setContent {
            GlidyTheme {
                AppRoot(container)
            }
        }
    }
}
