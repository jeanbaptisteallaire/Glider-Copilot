package com.neutronstar.glidercopilot

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.neutronstar.glidercopilot.ogn.OgnDeviceDatabase
import com.neutronstar.glidercopilot.precog.FileResponseCache
import com.neutronstar.glidercopilot.precog.PrecogApi
import com.neutronstar.glidercopilot.precog.UrlConnectionHttpClient
import com.neutronstar.glidercopilot.precog.WeatherRepository
import java.io.File

val Context.userPrefs: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Conteneur de dépendances manuel : pas de framework d'injection tant que l'app reste petite. */
class AppContainer(app: Application) {
    private val userAgent = "GLIDY/${BuildConfig.VERSION_NAME} (Android)"
    val prefs: UserPreferences = LocalUserPreferences(app.userPrefs)
    val location = LocationSource(app)
    val clubs: ClubRepository = ClubRepository(app, prefs, location)
    val weather: WeatherRepository = WeatherRepository(
        PrecogApi(
            UrlConnectionHttpClient(userAgent = userAgent),
            FileResponseCache(File(app.cacheDir, "precog")),
        ),
    )
    val glider = GliderRepository(
        prefs,
        OgnDeviceDatabase(UrlConnectionHttpClient(userAgent = userAgent, timeoutMs = 30_000), FileResponseCache(File(app.cacheDir, "ogn"))),
    )
    val checklist = ChecklistRepository(prefs)
}

class GliderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.location.refresh()
    }
}
