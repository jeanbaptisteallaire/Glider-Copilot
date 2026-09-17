package com.neutronstar.glidercopilot

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.FileProvider
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.flight.FlightEngineCore
import com.neutronstar.glidercopilot.domain.flight.FlightPhase
import com.neutronstar.glidercopilot.domain.flight.FlightSnapshot
import com.neutronstar.glidercopilot.domain.flight.GpsFix
import com.neutronstar.glidercopilot.domain.flight.Igc
import com.neutronstar.glidercopilot.domain.flight.IgcSinkFactory
import com.neutronstar.glidercopilot.domain.flight.SensorReplay
import com.neutronstar.glidercopilot.domain.flight.SensorSample
import com.neutronstar.glidercopilot.domain.flight.VarioSource
import com.neutronstar.glidercopilot.domain.flight.VerticalAcceleration
import com.neutronstar.glidercopilot.feature.flight.FlightControls
import com.neutronstar.glidercopilot.feature.flight.FlightLive
import com.neutronstar.glidercopilot.feature.flight.VarioTone
import com.neutronstar.glidercopilot.feature.flight.altitudeRefLabel
import com.neutronstar.glidercopilot.feature.flight.chrono
import com.neutronstar.glidercopilot.feature.flight.varioSourceLabel
import com.neutronstar.glidercopilot.feature.prevol.IgcFileUi
import com.neutronstar.glidercopilot.feature.prevol.SensorsSource
import com.neutronstar.glidercopilot.feature.prevol.SensorsUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Moteur de vol Android : baromètre, accéléromètre et gravité, GPS 1 Hz (+ altitude mer NMEA), secours OGN.
 * Tout le calcul tourne sur un fil dédié ; l'interface lit [live] (4 Hz) et [sensors] (Prévol).
 * Le son et les annonces vivent ici pour continuer écran éteint (service de premier plan [FlightService]).
 * Rejeu : extra « glidy.flight.replay » → le vol de démonstration des assets remplace les capteurs.
 */
class FlightEngine(
    private val context: Context,
    private val prefs: UserPreferences,
    private val clubs: ClubRepository,
    private val carto: CartoRepository,
    private val glider: GliderRepository,
    private val ogn: OgnLiveRepository,
) : FlightControls, SensorsSource {
    private val thread = HandlerThread("glidy-flight").apply { start() }
    private val handler = Handler(thread.looper)
    private val scope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher())
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val tone = VarioTone()
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    private val _live = MutableStateFlow(FlightLive())
    val live: StateFlow<FlightLive> = _live.asStateFlow()
    private val _sensors = MutableStateFlow(SensorsUi())
    override val sensors: StateFlow<SensorsUi> = _sensors.asStateFlow()

    @Volatile var replay = false
    @Volatile var replaySpeed = 1.0
    @Volatile private var autoTakeoff = true
    @Volatile private var soundOn = false
    @Volatile private var voiceOn = true
    @Volatile private var testing = false
    @Volatile private var fieldElevation: Double? = null
    @Volatile private var fieldPosition: LatLon? = null

    private var running = false
    private var jobs = ArrayList<Job>()
    private var igcWriter: BufferedWriter? = null
    private var igcFile: File? = null
    private var flights: List<IgcFileUi> = emptyList()

    // horloge : monotone réelle, ou virtuelle (accélérée) pendant le rejeu
    private var replayStartElapsed = 0L
    private var replayStartWall: Instant = Instant.EPOCH
    private fun clockNs(): Long = if (replay) ((SystemClock.elapsedRealtimeNanos() - replayStartElapsed) * replaySpeed).toLong() else SystemClock.elapsedRealtimeNanos()
    private fun clockNow(): Instant = if (replay) replayStartWall.plusNanos(clockNs()) else Instant.now()

    private val core = FlightEngineCore(
        igc = IgcSinkFactory { start -> openIgc(start) },
        announce = { speak(it) },
        autoTakeoff = { autoTakeoff },
        fieldElevationM = { fieldElevation },
        field = { fieldPosition },
        header = { start ->
            val own = glider.ownDevices.value.firstOrNull()
            Igc.Header(
                date = start.atZone(ZoneOffset.UTC).toLocalDate(),
                gliderType = own?.aircraftModel.orEmpty(),
                gliderId = own?.registration.orEmpty(),
                appVersion = "GLIDY ${BuildConfig.VERSION_NAME}",
                comment = if (replay) "GLIDY REJEU du vol de demonstration : capteurs simules" else null,
            )
        },
    )

    val baroPresent: Boolean = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    /** Abonnements aux réglages ; appelé en fin de construction (toutes les propriétés initialisées). */
    private fun wire() {
        scope.launch { prefs.autoTakeoff.collect { autoTakeoff = it; publish() } }
        scope.launch { prefs.varioSound.collect { soundOn = it; applySound() } }
        scope.launch { prefs.voiceAnnouncements.collect { voiceOn = it; publish() } }
        scope.launch { clubs.selectedClub.collect { club = it; fieldPosition = it?.position; updateField() } }
        scope.launch { carto.active.collect { updateField() } }
        scope.launch { refreshFlights() }
    }

    @Volatile private var club: com.neutronstar.glidercopilot.domain.Club? = null

    private fun updateField() {
        val icao = club?.airfieldIcao
        fieldElevation = icao?.let { i -> carto.active.value?.aero?.airports?.firstOrNull { it.icao == i }?.elevationM?.toDouble() }
    }

    // ---------------------------------------------------------------- cycle de vie

    /** Démarre capteurs (ou rejeu), GPS et publication. Sans effet si déjà lancé. */
    fun start() = handler.post {
        if (running) return@post
        running = true
        if (replay) {
            replayStartElapsed = SystemClock.elapsedRealtimeNanos()
            replayStartWall = Instant.now()
            jobs += scope.launch { runReplay() }
        } else {
            registerSensors()
            registerGps()
        }
        jobs += scope.launch { publishLoop() }
        jobs += scope.launch { ogn.traffic.collect { t -> t.own?.let { core.onOgn(it.climbMs, it.altitudeM, it.ageS + (it.latencyS ?: 0.0).roundToInt()) } ?: core.onOgn(null, null, Long.MAX_VALUE) } }
        applySound()
        if (tts == null) initTts()
    }

    /** Arrête tout sauf pendant un vol enregistré (le service garde alors le moteur). */
    fun stop() = handler.post {
        if (!running) return@post
        running = false
        jobs.forEach { it.cancel() }; jobs.clear()
        sensorManager?.unregisterListener(sensorListener)
        runCatching { locationManager?.removeUpdates(locationListener) }
        runCatching { locationManager?.removeNmeaListener(nmeaListener) }
        tone.stop()
        closeIgc()
        publish()
    }

    /** Vrai tant qu'un vol est enregistré : l'app garde capteurs, son et OGN en arrière-plan. */
    val inFlight: Boolean get() = _live.value.snapshot?.recording == true

    // ---------------------------------------------------------------- capteurs

    private var gravity = doubleArrayOf(0.0, 0.0, 0.0)
    private var hasGravitySensor = false
    private var lastMsl: Pair<Long, Double>? = null

    private var timestampOffset: Long? = null

    /** Horodatage capteur ramené sur elapsedRealtimeNanos (certains appareils anciens utilisent uptimeNanos). */
    private fun sensorNs(e: SensorEvent): Long {
        val off = timestampOffset ?: (SystemClock.elapsedRealtimeNanos() - e.timestamp).let { d -> if (kotlin.math.abs(d) > 500_000_000L) d else 0L }.also { timestampOffset = it }
        return e.timestamp + off
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val ns = sensorNs(e)
            when (e.sensor.type) {
                Sensor.TYPE_PRESSURE -> {
                    core.onBaro(e.values[0].toDouble(), ns)
                    if (core.source(ns) != VarioSource.OGN) tone.vario = core.fastClimb
                }
                Sensor.TYPE_GRAVITY -> gravity = doubleArrayOf(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                Sensor.TYPE_ACCELEROMETER -> {
                    val ax = e.values[0].toDouble(); val ay = e.values[1].toDouble(); val az = e.values[2].toDouble()
                    if (!hasGravitySensor) {
                        // pas de capteur de gravité : passe-bas de l'accéléromètre (τ ≈ 1 s à 50 Hz)
                        val k = 0.02
                        gravity = doubleArrayOf(gravity[0] + k * (ax - gravity[0]), gravity[1] + k * (ay - gravity[1]), gravity[2] + k * (az - gravity[2]))
                    }
                    VerticalAcceleration.compute(ax, ay, az, gravity[0], gravity[1], gravity[2])?.let { core.onAcceleration(it, ns) }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun registerSensors() {
        val sm = sensorManager ?: return
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sm.registerListener(sensorListener, it, 20_000, handler) }
        val g = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        hasGravitySensor = g != null
        g?.let { sm.registerListener(sensorListener, it, 20_000, handler) }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(sensorListener, it, 20_000, handler) }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(l: Location) = onLocation(l)
        @Deprecated("API < 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    /** Altitude mer de la trame GGA (champ 9), absente de Location avant Android 14. */
    private val nmeaListener = OnNmeaMessageListener { message, _ ->
        if (message.length > 6 && message.substring(3, 6) == "GGA") {
            val f = message.split(',')
            val alt = f.getOrNull(9)?.toDoubleOrNull()
            if (alt != null && f.getOrNull(10) == "M") lastMsl = SystemClock.elapsedRealtimeNanos() to alt
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerGps() {
        if (!context.hasLocationPermission(fine = true)) return
        val lm = locationManager ?: return
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, thread.looper) }
            .onFailure { Log.w(TAG, "GPS indisponible", it) }
        runCatching { lm.addNmeaListener(nmeaListener, handler) }
    }

    private fun onLocation(l: Location) {
        val ns = l.elapsedRealtimeNanos
        val msl = when {
            Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude() -> l.mslAltitudeMeters
            else -> lastMsl?.takeIf { ns - it.first < 2_000_000_000L }?.second
        }
        val fix = GpsFix(
            time = Instant.ofEpochMilli(l.time),
            position = LatLon(l.latitude, l.longitude),
            altitudeM = msl,
            groundSpeedKmh = if (l.hasSpeed()) l.speed * 3.6 else null,
            trackDeg = if (l.hasBearing() && l.hasSpeed() && l.speed > 1.5f) l.bearing.toDouble() else null,
            accuracyM = if (l.hasAccuracy()) l.accuracy.toDouble() else null,
            ellipsoidAltM = if (l.hasAltitude()) l.altitude else null,
        )
        core.onGps(fix, ns)
    }

    // ---------------------------------------------------------------- rejeu

    private suspend fun runReplay() {
        val text = context.assets.open("flight/demo.igc").bufferedReader().use { it.readText() }
        val bench = SensorReplay(Igc.parse(text))
        for (s in bench.samples()) {
            while (clockNs() < s.timeNs) delay(10)
            when (s) {
                is SensorSample.Baro -> { core.onBaro(s.hPa, s.timeNs); tone.vario = core.fastClimb }
                is SensorSample.Accel -> core.onAcceleration(s.upMs2, s.timeNs)
                is SensorSample.Gps -> core.onGps(s.fix.copy(time = replayStartWall.plusNanos(s.timeNs), ellipsoidAltM = s.fix.altitudeM), s.timeNs)
            }
        }
    }

    // ---------------------------------------------------------------- IGC

    private fun igcDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, if (replay) "igc-rejeu" else "igc").apply { mkdirs() }

    private fun openIgc(start: Instant): ((String) -> Unit)? {
        closeIgc()
        val date = start.atZone(ZoneOffset.UTC).toLocalDate()
        val dir = igcDir()
        val index = (1..99).firstOrNull { !File(dir, Igc.fileName(date, it)).exists() } ?: return null
        val file = File(dir, Igc.fileName(date, index))
        val w = runCatching { file.bufferedWriter(Charsets.US_ASCII) }.getOrElse { Log.w(TAG, "IGC impossible", it); return null }
        igcWriter = w; igcFile = file
        return { line ->
            runCatching { w.write(line); w.write("\r\n"); w.flush() }
        }
    }

    private fun closeIgc() {
        runCatching { igcWriter?.close() }
        igcWriter = null
        if (igcFile != null) { igcFile = null; scope.launch { refreshFlights() } }
    }

    private fun refreshFlights() {
        val files = listOf("igc", "igc-rejeu").flatMap { d -> File(context.getExternalFilesDir(null) ?: context.filesDir, d).listFiles { f -> f.name.endsWith(".igc") }?.toList() ?: emptyList() }
        flights = files.sortedByDescending { it.lastModified() }.take(10).map { f ->
            val fixes = runCatching { Igc.parse(f.readText(Charsets.US_ASCII)) }.getOrDefault(emptyList())
            val dur = if (fixes.size > 1) Duration.between(fixes.first().time, fixes.last().time).seconds else 0
            val start = fixes.firstOrNull()?.time?.atZone(ZoneId.systemDefault())
            val detail = listOfNotNull(
                start?.let { String.format(Locale.FRANCE, "%02d/%02d %02dh%02d", it.dayOfMonth, it.monthValue, it.hour, it.minute) },
                "durée ${chrono(dur)}",
                "${fixes.size} points",
                if (f.parentFile?.name == "igc-rejeu") "rejeu" else null,
            ).joinToString(" · ")
            IgcFileUi(f.name, detail, f.absolutePath)
        }
        publish()
    }

    override fun share(file: IgcFileUi) {
        val f = File(file.path)
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", f) }.getOrNull() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Partager ${f.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------------------------------------------------------------- son et voix

    private fun applySound() {
        if ((soundOn && running) || testing) tone.start() else tone.stop()
        publish()
    }

    override fun setSound(on: Boolean) { scope.launch { prefs.setVarioSound(on) } }
    override fun setVoice(on: Boolean) { scope.launch { prefs.setVoiceAnnouncements(on) } }

    override fun testSound() {
        if (testing) return
        scope.launch {
            testing = true; applySound()
            val steps = 70
            for (i in 0..steps) {       // de −2,5 à +4 m/s en 7 s
                tone.vario = -2.5 + 6.5 * i / steps
                delay(100)
            }
            testing = false
            tone.vario = 0.0
            applySound()
        }
    }

    override fun testVoice() = speak("Essai des annonces vocales. " + when (core.source(clockNs())) {
        VarioSource.BARO_ACCEL, VarioSource.BARO -> "Vario du téléphone actif."
        VarioSource.OGN -> "Pas de baromètre, vario OGN en secours."
        VarioSource.NONE -> if (baroPresent) "Vario prêt." else "Pas de baromètre sur ce téléphone."
    }, force = true)

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRANCE
                tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                ttsReady = true
            }
        }
    }

    private fun speak(text: String, force: Boolean = false) {
        Log.i(TAG, "annonce : $text")
        if (!voiceOn && !force) return
        if (tts == null) initTts()
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "glidy-${text.hashCode()}")
    }

    // ---------------------------------------------------------------- publication

    private suspend fun publishLoop() {
        while (true) {
            publish()
            delay(250)
        }
    }

    private var lastPhase = FlightPhase.GROUND

    private fun publish() {
        val snap: FlightSnapshot? = if (running) core.snapshot(clockNs(), clockNow()) else null
        if (snap != null && snap.source == VarioSource.OGN) tone.vario = snap.climbMs ?: 0.0
        if (snap != null && snap.source == VarioSource.NONE) tone.vario = 0.0
        if (snap != null && snap.phase != lastPhase) {
            lastPhase = snap.phase
            if (snap.phase == FlightPhase.LANDED) closeIgc()
        }
        _live.value = FlightLive(snapshot = snap, replay = replay, soundOn = soundOn, autoTakeoff = autoTakeoff)
        val ognDelay = ogn.traffic.value.own?.let { (it.latencyS ?: 0.0) + it.ageS }
        _sensors.value = SensorsUi(
            running = running,
            replay = replay,
            sourceLabel = when {
                snap == null -> "Moteur de vol à l'arrêt (app en arrière-plan)"
                else -> varioSourceLabel(snap, ognDelay) + if (!baroPresent && !replay) " · pas de baromètre sur ce téléphone" else ""
            },
            sourceOk = snap?.source == VarioSource.BARO_ACCEL || snap?.source == VarioSource.BARO,
            baroPresent = baroPresent || replay,
            baroHz = snap?.baroHz?.takeIf { it > 0 }?.let { "${it.roundToInt()} Hz" } ?: if (baroPresent || replay) "—" else "absent",
            baroNoise = snap?.baroNoiseCm?.let { "${it.roundToInt()} cm" } ?: "—",
            accelHz = snap?.accelHz?.takeIf { it > 0 }?.let { "${it.roundToInt()} Hz" } ?: "—",
            gpsAccuracy = (if (snap != null && (snap.gpsAgeS ?: 99.0) < 5) snap.gps?.accuracyM else null)?.let { "±${it.roundToInt()} m" }
                ?: if (context.hasLocationPermission(fine = true) || replay) "attente" else "refusé",
            altitudeLine = snap?.let { s -> s.altitudeM?.let { "Altitude ${it.roundToInt()} m · ${altitudeRefLabel(s.altitudeRef)}" + (fieldElevation?.let { e -> " · terrain ${e.roundToInt()} m" } ?: "") } } ?: "",
            flightLine = when {
                snap == null -> ""
                snap.recording -> "En vol · ${chrono(snap.flightSeconds)} · IGC ${snap.igcFixes} points"
                snap.phase == FlightPhase.LANDED -> "Posé · vol de ${chrono(snap.flightSeconds)} enregistré"
                autoTakeoff -> "Au sol · chrono et trace au-dessus de 50 km/h"
                else -> "Au sol · chrono manuel (appui sur le chrono de Pilotage)"
            },
            soundOn = soundOn,
            voiceOn = voiceOn,
            testing = testing,
            flights = flights,
        )
    }

    override fun startChrono() { handler.post { core.startManual(clockNow()); publish() } }
    override fun stopChrono() { handler.post { core.stopManual(clockNow()); closeIgc(); publish() } }

    init { wire() }

    companion object { private const val TAG = "GlidyFlight" }
}
