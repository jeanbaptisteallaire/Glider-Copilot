package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.ogn.AprsClient
import com.neutronstar.glidercopilot.ogn.OgnDeviceDatabase
import com.neutronstar.glidercopilot.ogn.OgnLiveSession
import com.neutronstar.glidercopilot.ogn.TrafficSnapshot
import com.neutronstar.glidercopilot.precog.FileResponseCache
import com.neutronstar.glidercopilot.precog.KtorHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * Trafic OGN de l'édition complète iOS : même session qu'Android (OgnLiveSession, data:ogn), DDB mise en cache dans
 * [cacheDir]. Démarre autour du terrain choisi (ou de la position), s'arrête en arrière-plan hors vol.
 * Aucune exception ne remonte vers Swift.
 */
class IosTraffic(cacheDir: String, installId: Long, appVersion: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ddb = runCatching {
        OgnDeviceDatabase(KtorHttpClient(userAgent = "WindGlider/$appVersion (iOS)", timeoutMs = 30_000), FileResponseCache((cacheDir.trimEnd('/') + "/ogn").toPath()))
    }.getOrNull()
    private val session = OgnLiveSession(ddb, AprsClient.userFor(installId), appVersion)

    val running: Boolean get() = session.running

    fun start(lat: Double, lon: Double) { runCatching { session.start(scope, LatLon(lat, lon)) } }
    fun stop() { runCatching { session.stop() } }

    /** [ownAltitudeM] : NaN si inconnue. */
    fun snapshot(ownAltitudeM: Double): TrafficSnapshot =
        runCatching { session.snapshot(ownAltitudeM.takeIf { !it.isNaN() }) }
            .getOrElse { TrafficSnapshot(emptyList(), emptyList(), false, "Réseau OGN indisponible", null, 0, OgnLiveSession.pill(0, null, 0)) }
}
