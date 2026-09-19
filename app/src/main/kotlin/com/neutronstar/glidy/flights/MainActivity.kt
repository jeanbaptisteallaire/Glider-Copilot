package com.neutronstar.glidy.flights

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.neutronstar.glidy.flightarchive.data.LocalArchiveModule
import com.neutronstar.glidy.myflights.MyFlightsApp

class MainActivity : ComponentActivity() {
    private val archiveRepository by lazy { LocalArchiveModule.create(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyFlightsApp(repository = archiveRepository) }
    }
}
