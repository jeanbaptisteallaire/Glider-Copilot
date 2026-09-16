package com.neutronstar.glidercopilot

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.neutronstar.glidercopilot.precog.FileResponseCache
import com.neutronstar.glidercopilot.precog.PrecogApi
import com.neutronstar.glidercopilot.precog.UrlConnectionHttpClient
import com.neutronstar.glidercopilot.precog.WeatherRepository
import java.io.File

val Context.userPrefs: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Conteneur de dépendances manuel : pas de framework d'injection tant que l'app reste petite. */
class AppContainer(app: Application) {
    val prefs: UserPreferences = LocalUserPreferences(app.userPrefs)
    val clubs: ClubRepository = ClubRepository(app, prefs)
    val weather: WeatherRepository = WeatherRepository(
        PrecogApi(
            UrlConnectionHttpClient(userAgent = "GliderCopilot/${BuildConfig.VERSION_NAME} (Android)"),
            FileResponseCache(File(app.cacheDir, "precog")),
        ),
    )
}

class GliderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
