package com.neutronstar.glidercopilot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.feature.flight.FlightStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun Context.hasLocationPermission(fine: Boolean = false): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        (!fine && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

/** Dernière position connue du téléphone, pour choisir le club le plus proche. Jamais envoyée hors de l'appareil. */
class LocationSource(private val context: Context) {
    private val _position = MutableStateFlow<LatLon?>(null)
    val position: StateFlow<LatLon?> = _position.asStateFlow()

    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!context.hasLocationPermission()) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val best = runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
        if (best != null) _position.value = LatLon(best.latitude, best.longitude)
    }
}

/**
 * Pastilles de l'écran Pilotage. GPS : permission précise accordée et récepteur activé.
 * BARO : capteur de pression présent. DATA : réseau avec accès Internet validé.
 */
@Composable
fun rememberFlightStatus(): FlightStatus {
    val context = LocalContext.current
    val baro = remember { context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null }
    var gps by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
        while (true) {
            gps = context.hasLocationPermission(fine = true) && runCatching { lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false)
            delay(5_000)
        }
    }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun check() {
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            data = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = check()
            override fun onLost(network: Network) = check()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = check()
        }
        check()
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
    return FlightStatus(gps = gps, baro = baro, data = data)
}
