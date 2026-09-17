package com.neutronstar.glidercopilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.neutronstar.glidercopilot.domain.flight.VarioSource
import com.neutronstar.glidercopilot.feature.flight.chrono
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Service de premier plan (type localisation) : garde capteurs, GPS, son, annonces et réseau OGN actifs
 * écran éteint, pendant le vol ou tant que l'écran Pilotage est ouvert. La notification rappelle chrono, vario et altitude.
 */
class FlightService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as GliderApp).container
        if (intent?.action == ACTION_STOP) {
            container.flight.stop()
            container.ogn.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val started = runCatching {
            val n = notification("Suivi du vol actif")
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIF_ID, n)
        }
        if (started.isFailure) {
            Log.w(TAG, "service de vol refusé", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        container.flight.start()
        container.ogn.start()
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(5_000)
                val s = container.flight.live.value.snapshot
                val text = if (s == null) "Suivi du vol actif" else listOfNotNull(
                    if (s.recording) "En vol ${chrono(s.flightSeconds)}" else "Au sol",
                    s.climbMs?.takeIf { s.source != VarioSource.NONE }?.let { String.format(Locale.FRANCE, "%+.1f m/s", it) },
                    s.altitudeM?.let { "${it.toInt()} m" },
                ).joinToString(" · ")
                runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notification(text)) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Vol en cours", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Vario, chrono et trace pendant le vol"
                setShowBadge(false)
            },
        )
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, FlightService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_glidy)
            .setContentTitle("GLIDY")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .addAction(0, "Arrêter le suivi", stop)
            .build()
    }

    companion object {
        private const val TAG = "GlidyFlight"
        private const val CHANNEL = "vol"
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "glidy.flight.STOP"

        @Volatile var running = false
            private set

        /** À appeler app visible : Android refuse un service de localisation lancé depuis l'arrière-plan. */
        fun start(context: Context, replay: Boolean) {
            if (running) return
            if (!replay && !context.hasLocationPermission()) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FlightService::class.java)) }
                .onFailure { Log.w(TAG, "démarrage du service impossible", it) }
        }

        fun stop(context: Context) {
            if (!running) return
            runCatching { context.stopService(Intent(context, FlightService::class.java)) }
        }
    }
}
