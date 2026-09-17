package com.neutronstar.glidercopilot.domain.flight

import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import java.time.Instant
import kotlin.math.abs

/** Référence de l'altitude affichée : chaque valeur porte son hypothèse. */
enum class AltitudeRef { BARO_FIELD, BARO_GPS, BARO_ISA, GPS, OGN, NONE }

data class TracePoint(val position: LatLon, val climbMs: Double)

/** Image instantanée du vol, publiée ~4 fois par seconde vers l'interface. */
data class FlightSnapshot(
    val now: Instant,
    val source: VarioSource = VarioSource.NONE,
    /** Vario amorti pour l'affichage (τ ≈ 1 s). */
    val climbMs: Double? = null,
    val altitudeM: Double? = null,
    val altitudeRef: AltitudeRef = AltitudeRef.NONE,
    val gps: GpsFix? = null,
    val gpsAgeS: Double? = null,
    val phase: FlightPhase = FlightPhase.GROUND,
    val recording: Boolean = false,
    val takeoffTime: Instant? = null,
    val flightSeconds: Long = 0,
    val baroHz: Double = 0.0,
    val baroNoiseCm: Double? = null,
    val accelHz: Double = 0.0,
    val circling: Boolean = false,
    val avgSpiralMs: Double? = null,
    val avgThermalMs: Double? = null,
    val avgDayMs: Double? = null,
    val trace: List<TracePoint> = emptyList(),
    val igcFixes: Int = 0,
)

/** Destination des lignes IGC d'un vol (fichier sur Android, liste en test). */
fun interface IgcSinkFactory {
    fun open(start: Instant): ((String) -> Unit)?
}

/**
 * Cœur du moteur de vol, sans Android : fusionne baro + accéléromètre (Kalman), GPS et secours OGN, détecte
 * décollage et atterrissage, écrit l'IGC et décide des annonces vocales. Les horodatages capteurs sont en
 * nanosecondes sur une horloge monotone commune ; [now] donne l'heure UTC correspondante.
 */
class FlightEngineCore(
    private val igc: IgcSinkFactory,
    private val announce: (String) -> Unit = {},
    private val autoTakeoff: () -> Boolean = { true },
    private val fieldElevationM: () -> Double? = { null },
    private val field: () -> LatLon? = { null },
    private val header: (Instant) -> Igc.Header = { Igc.Header(it.atZone(java.time.ZoneOffset.UTC).toLocalDate()) },
) {
    private val filter = VarioFilter()
    private val display = Damper(1.0)
    private val sound = Damper(0.3)
    private val baroStats = SensorStats(4.0)   // fenêtre courte : le bruit, pas les variations de la masse d'air
    private val accelStats = SensorStats(5.0)
    private val detector = TakeoffDetector()
    private val policy = AnnouncementPolicy()

    @Volatile var fastClimb: Double = 0.0; private set
    private var lastBaroNs = 0L
    private var lastAccelNs = 0L
    private var baroAlt = Double.NaN        // altitude pression ISA filtrée
    private var offset: Double? = null       // altitude affichée = baroAlt + offset
    private var offsetRef = AltitudeRef.BARO_ISA
    private val gpsOffsets = ArrayDeque<Double>()
    private var fieldCalibratedAt: Instant? = null
    private var lastFix: GpsFix? = null
    private var lastFixNs = 0L
    private var ognClimb: Double? = null
    private var ognAlt: Double? = null
    private var ognAgeS = Long.MAX_VALUE

    private var recorder: IgcRecorder? = null
    private var manualStart: Instant? = null
    private var manualStop: Instant? = null

    private val trace = ArrayDeque<TracePoint>()
    private val climbWindow = ArrayDeque<Pair<Long, Double>>()   // (s, vario) à 1 Hz
    private val tracks = ArrayDeque<Pair<Long, Double>>()        // (s, route)
    private var circling = false
    private var thermalStartS = 0L
    private var thermalStartAlt = 0.0
    private var lastThermal: Double? = null
    private var dayGain = 0.0
    private var daySeconds = 0L

    // ---------------------------------------------------------------- entrées capteurs

    fun onBaro(hPa: Double, ns: Long) {
        if (hPa !in 100.0..1100.0) return
        val alt = Isa.pressureAltitude(hPa)
        if (lastBaroNs != 0L && ns - lastBaroNs > 2_000_000_000L) filter.reset(alt, ns)
        filter.onBaroAltitude(alt, ns)
        lastBaroNs = ns
        baroStats.add(alt, ns)
        baroAlt = filter.altitude
        display.update(filter.climb, ns)
        fastClimb = sound.update(filter.climb, ns)
    }

    fun onAcceleration(upMs2: Double, ns: Long) {
        filter.onAcceleration(upMs2, ns)
        lastAccelNs = ns
        accelStats.add(upMs2, ns)
    }

    fun onGps(fix: GpsFix, ns: Long) {
        lastFix = fix
        lastFixNs = ns
        calibrate(fix, ns)
        val auto = autoTakeoff()
        val event = if (auto) detector.onFix(fix) else null
        when (event) {
            is FlightEvent.Takeoff -> startRecording(event.time)
            is FlightEvent.Landing -> recorder = null
            null -> Unit
        }
        event?.let { e -> policy.onEvent(e, fix.time)?.let(announce) }
        if (recording) policy.onSource(source(ns), fix.time)?.let(announce)

        val s = fix.time.epochSecond
        val climb = currentClimb(ns)
        if (climb != null) {
            climbWindow.addLast(s to climb)
            while (climbWindow.isNotEmpty() && s - climbWindow.first().first > 25) climbWindow.removeFirst()
        }
        trace.addLast(TracePoint(fix.position, climb ?: 0.0))
        while (trace.size > 600) trace.removeFirst()
        updateCircling(fix, s, ns)

        recorder?.record(
            IgcFix(fix.time, fix.position, (fix.accuracyM ?: 99.0) < 50.0, baroAlt.takeIf { !it.isNaN() && baroFresh(ns) }?.let { Math.round(it).toInt() }, (fix.ellipsoidAltM ?: fix.altitudeM)?.let { Math.round(it).toInt() }),
        )
    }

    /** Mon planeur vu par OGN : secours quand le téléphone n'a pas de baromètre. */
    fun onOgn(climbMs: Double?, altitudeM: Double?, ageS: Long) {
        ognClimb = climbMs; ognAlt = altitudeM; ognAgeS = ageS
    }

    /** Chrono manuel (détection automatique désactivée). */
    fun startManual(now: Instant) {
        if (recorder != null) return
        manualStart = now; manualStop = null
        startRecording(now)
        announce("Chrono lancé")
    }

    fun stopManual(now: Instant) {
        if (manualStart == null) return
        manualStop = now
        recorder = null
        announce("Chrono arrêté")
    }

    val recording: Boolean get() = recorder != null

    private fun startRecording(start: Instant) {
        val sink = igc.open(start) ?: return
        recorder = IgcRecorder(header(start), sink)
    }

    // ---------------------------------------------------------------- altitude

    private fun calibrate(fix: GpsFix, ns: Long) {
        if (!baroFresh(ns) || baroAlt.isNaN()) return
        if (detector.phase == FlightPhase.FLYING || recording) return   // calage figé en vol
        val slow = (fix.groundSpeedKmh ?: 0.0) < 10.0
        val fe = fieldElevationM(); val fp = field()
        if (slow && fe != null && fp != null && Geo.distanceKm(fp, fix.position) < 1.5 && (fix.accuracyM ?: 99.0) < 50) {
            offset = fe - baroAlt; offsetRef = AltitudeRef.BARO_FIELD
            fieldCalibratedAt = fix.time
            return
        }
        // calage terrain récent (roulage, remorqué avant détection) : on le garde
        fieldCalibratedAt?.let { if (java.time.Duration.between(it, fix.time).toMinutes() < 10) return }
        val msl = fix.altitudeM ?: return
        if ((fix.accuracyM ?: 99.0) > 20) return
        gpsOffsets.addLast(msl - baroAlt)
        while (gpsOffsets.size > 60) gpsOffsets.removeFirst()
        if (gpsOffsets.size >= 10) {
            val sorted = gpsOffsets.sorted()
            offset = sorted[sorted.size / 2]; offsetRef = AltitudeRef.BARO_GPS
        }
    }

    private fun baroFresh(ns: Long) = lastBaroNs != 0L && ns - lastBaroNs < 1_000_000_000L

    fun source(ns: Long): VarioSource = when {
        baroFresh(ns) && filter.usesAccelerometer(ns) -> VarioSource.BARO_ACCEL
        baroFresh(ns) -> VarioSource.BARO
        ognClimb != null && ognAgeS <= 60 -> VarioSource.OGN
        else -> VarioSource.NONE
    }

    private fun currentClimb(ns: Long): Double? = when (source(ns)) {
        VarioSource.BARO_ACCEL, VarioSource.BARO -> display.value
        VarioSource.OGN -> ognClimb
        VarioSource.NONE -> null
    }

    // ---------------------------------------------------------------- spirales et moyennes

    private fun updateCircling(fix: GpsFix, s: Long, ns: Long) {
        val track = fix.trackDeg
        if (track != null && (fix.groundSpeedKmh ?: 0.0) > 20) tracks.addLast(s to track)
        while (tracks.isNotEmpty() && s - tracks.first().first > 20) tracks.removeFirst()
        var turn = 0.0
        tracks.toList().zipWithNext().forEach { (a, b) ->
            var d = b.second - a.second
            if (d > 180) d -= 360
            if (d < -180) d += 360
            turn += d
        }
        val alt = altitude(ns).first ?: return
        val nowCircling = abs(turn) > 150 && tracks.size >= 6 && s - tracks.first().first >= 15   // ≥ 7,5°/s soutenu sur 20 s (aussi à la cadence OGN, 2 à 4 s)
        if (nowCircling && !circling) { thermalStartS = s; thermalStartAlt = alt }
        if (!nowCircling && circling) {
            val dur = s - thermalStartS
            if (dur >= 30 && alt > thermalStartAlt) { dayGain += alt - thermalStartAlt; daySeconds += dur }
        }
        circling = nowCircling
        if (circling && s - thermalStartS >= 20) lastThermal = (alt - thermalStartAlt) / (s - thermalStartS)
    }

    private fun altitude(ns: Long): Pair<Double?, AltitudeRef> {
        if (baroFresh(ns) && !baroAlt.isNaN()) {
            val o = offset
            return if (o != null) (baroAlt + o) to offsetRef else baroAlt to AltitudeRef.BARO_ISA
        }
        lastFix?.takeIf { ns - lastFixNs < 5_000_000_000L }?.altitudeM?.let { return it to AltitudeRef.GPS }
        ognAlt?.takeIf { ognAgeS <= 60 }?.let { return it to AltitudeRef.OGN }
        return null to AltitudeRef.NONE
    }

    // ---------------------------------------------------------------- sortie

    fun snapshot(ns: Long, now: Instant): FlightSnapshot {
        val (alt, ref) = altitude(ns)
        val takeoff = if (autoTakeoff() && detector.takeoffTime != null) detector.takeoffTime else manualStart
        val end = if (autoTakeoff() && detector.takeoffTime != null) detector.landingTime else manualStop
        val flightS = takeoff?.let { java.time.Duration.between(it, end ?: now).seconds.coerceAtLeast(0) } ?: 0
        val curDay = if (circling && lastThermal != null) (dayGain + (alt ?: thermalStartAlt) - thermalStartAlt) to (daySeconds + ((lastFix?.time?.epochSecond ?: 0) - thermalStartS)) else dayGain to daySeconds
        return FlightSnapshot(
            now = now,
            source = source(ns),
            climbMs = currentClimb(ns),
            altitudeM = alt,
            altitudeRef = ref,
            gps = lastFix,
            gpsAgeS = if (lastFixNs == 0L) null else (ns - lastFixNs) / 1e9,
            phase = detector.phase,
            recording = recording,
            takeoffTime = takeoff,
            flightSeconds = flightS,
            baroHz = if (baroFresh(ns)) baroStats.rateHz else 0.0,
            baroNoiseCm = baroStats.noise.takeIf { !it.isNaN() && baroFresh(ns) }?.let { it * 100 },
            accelHz = if (ns - lastAccelNs < 1_000_000_000L) accelStats.rateHz else 0.0,
            circling = circling,
            avgSpiralMs = climbWindow.takeIf { it.size >= 5 }?.map { it.second }?.average(),
            avgThermalMs = lastThermal,
            avgDayMs = curDay.takeIf { it.second >= 30 }?.let { it.first / it.second },
            trace = trace.toList(),
            igcFixes = recorder?.fixes ?: 0,
        )
    }
}
