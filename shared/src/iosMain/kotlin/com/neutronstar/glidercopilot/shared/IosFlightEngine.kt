package com.neutronstar.glidercopilot.shared

import com.neutronstar.glidercopilot.domain.Club
import com.neutronstar.glidercopilot.domain.EPOCH
import com.neutronstar.glidercopilot.domain.Geo
import com.neutronstar.glidercopilot.domain.LatLon
import com.neutronstar.glidercopilot.domain.Terrain
import com.neutronstar.glidercopilot.domain.flight.TracePoint
import com.neutronstar.glidercopilot.domain.flight.VarioPalette
import com.neutronstar.glidercopilot.domain.flight.SensorWatchdog
import com.neutronstar.glidercopilot.domain.flight.AltitudeRef
import com.neutronstar.glidercopilot.domain.flight.DemoFlight
import com.neutronstar.glidercopilot.domain.flight.FlightEngineCore
import com.neutronstar.glidercopilot.domain.flight.FlightPhase
import com.neutronstar.glidercopilot.domain.flight.FlightSnapshot
import com.neutronstar.glidercopilot.domain.flight.GpsFix
import com.neutronstar.glidercopilot.domain.flight.Igc
import com.neutronstar.glidercopilot.domain.flight.IgcSinkFactory
import com.neutronstar.glidercopilot.domain.flight.SensorReplay
import com.neutronstar.glidercopilot.domain.flight.SensorSample
import com.neutronstar.glidercopilot.domain.flight.ThermalZoom
import com.neutronstar.glidercopilot.domain.flight.VarioSource
import com.neutronstar.glidercopilot.domain.flight.VerticalAcceleration
import com.neutronstar.glidercopilot.domain.formatFixed
import com.neutronstar.glidercopilot.domain.safety.FieldOption
import com.neutronstar.glidercopilot.domain.safety.SafetyAlert
import com.neutronstar.glidercopilot.domain.safety.SafetyConfig
import com.neutronstar.glidercopilot.domain.safety.SafetyEngine
import com.neutronstar.glidercopilot.domain.safety.SafetyState
import com.neutronstar.glidercopilot.domain.safety.Wind
import com.neutronstar.glidercopilot.domain.safety.WindSource
import com.neutronstar.glidercopilot.domain.secondsBetween
import com.neutronstar.glidercopilot.precog.DayWeather
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import platform.Foundation.NSProcessInfo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.nanoseconds

/** Terrain proposé au choix (Pilotage → appui sur le terrain). */
data class FieldChoice(val id: String, val code: String, val name: String, val distanceText: String, val marginText: String, val reachable: Boolean)

/** Carte « Capteurs » de Prévol (équivalent de SensorsUi d'Android). */
data class SensorsUi(
    val sourceLabel: String,
    val sourceOk: Boolean,
    val baroHz: String,
    val baroNoise: String,
    val accelHz: String,
    val gpsAccuracy: String,
    val altitudeLine: String,
    val flightLine: String,
)

/** Point de trace pour la carte Swift (couleur selon la montée). */
data class TraceDot(val lat: Double, val lon: Double, val climbMs: Double)

/** Point de la coupe (distance, relief) : relief inconnu tant que data:carto n'est pas migré. */
data class ProfileDot(val sKm: Double, val terrainM: Double)

/**
 * Image de l'écran Pilotage iOS, valeurs déjà formatées en français comme sur Android (FlightScreen) ;
 * chaque valeur garde son hypothèse (référence d'altitude, source du vario, source du vent).
 */
data class PilotUi(
    val running: Boolean,
    val demo: Boolean,
    val hasData: Boolean,
    // sécurité
    val marginText: String,
    val marginM: Double,
    val marginKnown: Boolean,
    val altText: String,
    val altRefText: String,
    val secuText: String,
    val distanceText: String,
    val fieldCode: String,
    val fieldName: String,
    val bearingText: String,
    val autoField: Boolean,
    val arrivalText: String,
    val finesse: Int,
    val windText: String,
    val windFromDeg: Double,
    val windKnown: Boolean,
    val profile: List<ProfileDot>,
    val requiredAltM: Double,
    val currentAltM: Double,
    // vario
    val varioLabel: String,
    val varioText: String,
    val varioMs: Double,
    val spiralText: String,
    val thermalText: String,
    val dayText: String,
    val circling: Boolean,
    // carte
    val lat: Double,
    val lon: Double,
    val trackDeg: Double,
    val hasPosition: Boolean,
    val fieldLat: Double,
    val fieldLon: Double,
    val trace: List<TraceDot>,
    val zoomLat: Double,
    val zoomLon: Double,
    val zoomRadiusM: Double,
    val zoomActive: Boolean,
    // capteurs, enregistrement
    val gpsOk: Boolean,
    val baroOk: Boolean,
    val recording: Boolean,
    val chronoText: String,
    val phaseText: String,
    val igcPoints: Int,
    val soundOn: Boolean,
    /** Origine de l'altitude du terrain (hypothèse affichée sous la coupe). */
    val fieldElevText: String,
    /** Relief du pack chargé : coupe et marge sur le relief réel. */
    val reliefLoaded: Boolean,
    /** Coupe : altitude d'arrivée au terrain sur la pente de plané (terrain + hauteur d'arrivée), terrain, distance. */
    val arrivalAltM: Double,
    val fieldElevM: Double,
    val routeKm: Double,
    /** « 3,9 km · LFNL 183 m », comme la coupe Android. */
    val profileEndText: String,
    /** « +300 m · vent 300°/15 » : marge d'arrivée réglée et vent utilisé, au-dessus de la finesse. */
    val finesseNote: String,
    /** Durée du vol (chrono), affichée sur la carte. */
    val flightTimeText: String,
    /** Moyenne de la spirale (m/s), NaN si inconnue : index creux sur la barre vario. */
    val spiralMs: Double = Double.NaN,
    /** Vent : vitesse (km/h) et origine prévision (flèche en pointillés) plutôt que mesure. */
    val windSpeedKmh: Double = 0.0,
    val windForecast: Boolean = false,
    /** Fréquence radio du terrain de référence (openAIP du pack), virgule française ; « — » si inconnue, jamais inventée. */
    val fieldFrequencyText: String = "—",
    /**
     * Version de la trace (instant du dernier point, ms ; 0 = vide). La trace est bornée à 600 points : passé ce cap
     * le nombre ne change plus, seule la version dit qu'elle a avancé (la carte se figeait au bout de 10 min).
     */
    val traceVersion: Long = 0,
    /** Marge calculée avec le relief le long de la route ; faux = relief inconnu sur plus de la moitié du trajet. */
    val reliefChecked: Boolean = true,
)

/**
 * Moteur de vol iOS : même rôle que FlightEngine.kt d'Android, sur le même cœur commun [FlightEngineCore] et
 * [SafetyEngine]. Les capteurs (CoreLocation, CMAltimeter, CMDeviceMotion) sont lus en Swift et poussés ici sur le
 * fil principal ; horloge monotone = `systemUptime` (même base que les horodatages CoreMotion).
 * Édition Lite : enregistrement manuel (REC), pas de suivi OGN ; mode démo = vol simulé autour du terrain du club.
 * Terrains candidats : terrain du club seulement, relief inconnu (en attendant data:carto en KMP).
 * Aucune fonction de sécurité ne dépend du réseau (la prévision de vent n'est qu'un secours avant la première spirale).
 */
class IosFlightEngine(private val igcDir: String, private val appVersion: String) {
    private val scope = MainScope()
    private val fs = FileSystem.SYSTEM

    /** Annonce vocale et alerte (Swift : AVSpeechSynthesizer + vibration). Niveau 0 info, 1 avertissement, 2 urgent. */
    var onSpeak: (String, Int) -> Unit = { _, _ -> }
    /** Vario rapide pour le son (Swift : AVAudioEngine). */
    var onTone: (Double) -> Unit = {}
    /** Fichier IGC fermé (chemin) : carnet Mes vols. */
    var onIgcClosed: (String) -> Unit = {}

    private var running = false
    private var demo = false
    private var finesse = 20
    private var soundOn = false
    private var autoTakeoff = false
    private var club: Club? = null
    private var fields: List<FieldOption> = emptyList()
    private var forecastDay: DayWeather? = null
    private var lastAltitudeM: Double? = null
    private var safetyState: SafetyState? = null
    private var jobs = ArrayList<Job>()
    private var igcSink: okio.BufferedSink? = null
    private var igcPath: String? = null
    private var demoStartNs = 0L
    private var demoStartWall: Instant = EPOCH
    private var lastPhase = FlightPhase.GROUND
    private var lastGpsNs = 0L
    private var lastBaroNs = 0L

    private fun uptimeNs(): Long = (NSProcessInfo.processInfo.systemUptime * 1e9).toLong()
    private fun clockNs(): Long = if (demo) uptimeNs() - demoStartNs else uptimeNs()
    private fun clockNow(): Instant = if (demo) demoStartWall + clockNs().nanoseconds else Clock.System.now()

    private var core = newCore()
    private var safety = newSafety()

    private fun newCore() = FlightEngineCore(
        igc = IgcSinkFactory { start -> if (demo) null else openIgc(start) },
        announce = { onSpeak(it, 0) },
        autoTakeoff = { autoTakeoff || demo },
        igcEnabled = { !demo },
        baroFreshS = 3.0,   // CMAltimeter : ~1 Hz avec retard (mesuré sur iPhone 15)
        // revue résilience M5 : calage « terrain » seulement sur une altitude mesurée (openAIP, relief) ; le sol du modèle
        // ARPEGE (maille ~10 km) fausserait la marge vers les autres terrains et le relief : calage GPS à la place
        fieldElevationM = { fieldElevation.takeIf { fieldElevationSource != "modèle ARPEGE" } },
        field = { club?.position },
        header = { start ->
            Igc.Header(
                date = start.toLocalDateTime(TimeZone.UTC).date,
                appVersion = "WINDGLIDER $appVersion",
                gnssDatum = "ELL",
                platform = "iOS",
            )
        },
    )

    private fun newSafety() = SafetyEngine(
        terrain = { pack?.terrain ?: Terrain.NONE },
        fields = { fields },
        alert = { a: SafetyAlert -> onSpeak(a.text, a.level.ordinal) },
        forecastWind = { forecastWind() },
    )

    // ------------------------------------------------------------------ réglages

    /** Altitude du terrain et son origine ; null = inconnue (la marge n'est alors pas calculée). */
    private var fieldElevation: Double? = null
    private var fieldElevationSource = ""
    /** Altitude du sol du modèle ARPEGE au club : dernier recours sans pack. */
    private var modelElevation: Double? = null
    private var pack: ActivePack? = null

    /** Pack hors ligne de la région (relief + openAIP) ; null = aucun. */
    fun setPack(p: ActivePack?) {
        if (p === pack) return
        pack?.close()
        pack = p
        rebuildFields()
    }

    fun setClub(c: Club?) {
        if (c?.id != club?.id) modelElevation = null
        club = c
        rebuildFields()
    }

    /**
     * En attendant data:carto (openAIP) en KMP : altitude du sol du modèle ARPEGE au point du club, affichée comme
     * hypothèse (« terrain ≈ … m, modèle ARPEGE »). Prévision reçue = copie locale ensuite : pas de dépendance réseau en vol.
     */
    fun setForecast(day: DayWeather?) {
        forecastDay = day
        val g = day?.hours?.firstOrNull()?.analysis?.groundAltitudeM
        if (g != null && day.location == club?.position) {
            modelElevation = g
            rebuildFields()
        }
    }

    /**
     * Même règle qu'Android (FlightEngine.updateField) : terrains openAIP du pack (sans hélistations, hydrobases, terrains
     * fermés) + terrain du club. Altitude du terrain du club : openAIP, sinon relief Copernicus du pack, sinon sol du modèle
     * ARPEGE (hypothèse affichée) ; inconnue = pas de marge calculée.
     */
    /** Un relief ou un pack corrompu ne doit jamais faire planter l'app (revue résilience C4) : terrains vides au pire. */
    private fun rebuildFields() {
        runCatching { rebuildFieldsUnsafe() }.onFailure { fields = emptyList(); frequencies = emptyMap() }
    }

    private fun rebuildFieldsUnsafe() {
        val c = club
        val icao = c?.airfieldIcao
        val terrain = pack?.terrain ?: Terrain.NONE
        val airports = pack?.aero?.airports.orEmpty().filter { it.type !in setOf(4, 7, 8, 10) }
        val clubAirport = icao?.let { i -> airports.firstOrNull { it.icao == i } }
        val clubPos = c?.position
        val reliefElev = clubPos?.let { terrain.elevationM(it) }
        when {
            clubAirport?.elevationM != null -> { fieldElevation = clubAirport.elevationM!!.toDouble(); fieldElevationSource = "openAIP" }
            reliefElev != null -> { fieldElevation = reliefElev; fieldElevationSource = "relief Copernicus" }
            modelElevation != null -> { fieldElevation = modelElevation; fieldElevationSource = "modèle ARPEGE" }
            else -> { fieldElevation = null; fieldElevationSource = "" }
        }
        // terrains de repli : aérodromes officiels à indicatif OACI seulement (JB, 23/10/2026, responsabilité)
        val list = airports.filter { FieldOption.isOfficialAerodrome(it.icao) }.mapNotNull { a ->
            val elev = a.elevationM?.toDouble() ?: terrain.elevationM(a.position) ?: return@mapNotNull null
            FieldOption(a.icao ?: a.id, FieldOption.prettyName(a.name), a.position, elev, isClub = a === clubAirport, icao = a.icao)
        }.toMutableList()
        val e = fieldElevation
        if (clubAirport == null && clubPos != null && e != null && FieldOption.isOfficialAerodrome(icao)) {
            list += FieldOption(icao ?: "CLUB", FieldOption.prettyName(c.airfieldName ?: c.name), clubPos, e, isClub = true, icao = icao)
        }
        fields = list
        // fréquences openAIP des terrains, par identifiant de FieldOption (jamais devinées)
        frequencies = airports.mapNotNull { a -> a.frequency?.takeIf { it.isNotBlank() }?.let { (a.icao ?: a.id) to it } }.toMap()
    }
    private var frequencies: Map<String, String> = emptyMap()

    /** Trace convertie pour Swift, recopiée seulement quand un point est ajouté (1 Hz au lieu de 4 Hz). */
    private var traceCacheKey = -1L
    private var traceCache: List<TraceDot> = emptyList()
    private fun traceDots(trace: List<TracePoint>?): List<TraceDot> {
        if (trace.isNullOrEmpty()) { traceCacheKey = -1L; traceCache = emptyList(); return traceCache }
        val key = trace.last().time.toEpochMilliseconds() * 1000 + trace.size
        if (key != traceCacheKey) {
            traceCacheKey = key
            traceCache = trace.map { TraceDot(it.position.lat, it.position.lon, it.climbMs) }
        }
        return traceCache
    }

    /** Terrains proposés au choix : alternatives calculées par la sécurité, sinon les plus proches. */
    fun fieldChoices(): List<FieldChoice> {
        safetyState?.alternatives?.takeIf { it.isNotEmpty() }?.let { l ->
            return l.take(8).map { r -> FieldChoice(r.field.id, r.field.code, r.field.name, formatFixed(r.distanceKm, 1, decimalComma = true) + " km", signed(r.marginM.roundToInt()) + " m", r.marginM >= 0) }
        }
        val from = core.snapshot(clockNs(), clockNow()).gps?.position ?: club?.position ?: return emptyList()
        return fields.sortedBy { Geo.distanceKm(from, it.position) }.take(8).map { f ->
            FieldChoice(f.id, f.code, f.name, formatFixed(Geo.distanceKm(from, f.position), 1, decimalComma = true) + " km", "", true)
        }
    }

    /** Terrain choisi à la main ; null = choix automatique. */
    fun selectField(id: String?) {
        safety.selector.manual = id?.let { i -> fields.firstOrNull { it.id == i } }
    }
    fun setFinesse(value: Int) { finesse = value }
    /** Marge d'arrivée au terrain réglée en Prévol (m) ; null = règle historique liée à la finesse (300 m en F20). */
    private var arrivalM: Int? = null
    fun setArrivalMargin(meters: Int) { arrivalM = SafetyConfig.arrivalBounded(meters) }
    private fun config(): SafetyConfig = arrivalM?.let { SafetyConfig.of(finesse, it) } ?: SafetyConfig.forFinesse(finesse)
    fun setSound(on: Boolean) { soundOn = on; if (!on) onTone(0.0) }
    fun setAutoTakeoff(on: Boolean) { autoTakeoff = on }

    fun setDemo(on: Boolean) {
        if (on == demo) return
        val wasRunning = running
        stop()
        demo = on
        core = newCore(); safety = newSafety(); safetyState = null
        if (wasRunning) start()
    }

    fun start() {
        if (running) return
        running = true
        if (demo) {
            demoStartNs = uptimeNs(); demoStartWall = Clock.System.now()
            jobs += scope.launch { runDemo() }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        jobs.forEach { it.cancel() }; jobs.clear()
        closeIgc()
        onTone(0.0)
    }

    fun startChrono() { core.startManual(clockNow()) }
    /** Redémarrage de l'app en vol : alertes armées sans délai, enregistrement relancé. */
    fun resumeInFlight() { pendingResumeNs = uptimeNs() }
    private var pendingResumeNs: Long? = null
    /**
     * Arrêt manuel. En départ automatique, toucher REC en vol ne doit pas fermer l'IGC d'un enregistrement qui continue
     * (revue résilience M6 : le vol était perdu sans signe) : le fichier n'est fermé que si l'enregistrement s'arrête.
     */
    fun stopChrono() {
        core.stopManual(clockNow())
        if (!core.recording) closeIgc() else onSpeak("Enregistrement automatique en cours, il s'arrêtera à l'atterrissage", 0)
    }

    // ------------------------------------------------------------------ capteurs (Swift, fil principal)

    /** CMAltimeter : pression en kPa, horodatage en secondes depuis le démarrage. */
    fun onPressure(kPa: Double, uptimeS: Double) {
        if (!running || demo) return
        val ns = (uptimeS * 1e9).toLong()
        lastBaroNs = ns
        core.onBaro(kPa * 10.0, ns)
        if (soundOn) onTone(core.fastClimb)
    }

    /** CMDeviceMotion : accélération utilisateur et gravité en g (convention iOS, gravité vers le bas). */
    fun onMotion(ux: Double, uy: Double, uz: Double, gx: Double, gy: Double, gz: Double, uptimeS: Double) {
        if (!running || demo) return
        val g = VerticalAcceleration.G
        // convention Android : l'accéléromètre lit +g au repos → on inverse le signe et on passe en m/s²
        val up = VerticalAcceleration.compute(-(ux + gx) * g, -(uy + gy) * g, -(uz + gz) * g, -gx * g, -gy * g, -gz * g) ?: return
        core.onAcceleration(up, (uptimeS * 1e9).toLong())
    }

    /**
     * CoreLocation : [altitudeMsl] (altitude au-dessus du niveau moyen de la mer), [ellipsoidAlt] (iOS 15+),
     * vitesse en m/s (< 0 si inconnue), cap en degrés (< 0 si inconnu), précision horizontale en m.
     */
    fun onLocation(lat: Double, lon: Double, altitudeMsl: Double, ellipsoidAlt: Double, hasAltitude: Boolean, speedMs: Double, courseDeg: Double, accuracyM: Double, epochMs: Long) {
        if (!running || demo) return
        val ns = uptimeNs()
        val fix = GpsFix(
            time = Instant.fromEpochMilliseconds(epochMs),
            position = LatLon(lat, lon),
            altitudeM = if (hasAltitude) altitudeMsl else null,
            groundSpeedKmh = if (speedMs >= 0) speedMs * 3.6 else null,
            trackDeg = if (courseDeg >= 0 && speedMs > 1.5) courseDeg else null,
            accuracyM = if (accuracyM >= 0) accuracyM else null,
            ellipsoidAltM = if (hasAltitude) ellipsoidAlt else null,
        )
        lastGpsNs = ns
        core.onGps(fix, ns)
        afterGps(fix, ns)
    }

    private fun afterGps(fix: GpsFix, ns: Long) {
        safety.onFix(fix)
        val snap = core.snapshot(ns, fix.time)
        val alt = snap.altitudeM ?: return
        lastAltitudeM = alt
        // alertes armées dès qu'un vol est détecté ou enregistré, pas seulement après REC (revue résilience C3)
        val altCalibrated = snap.altitudeRef != AltitudeRef.BARO_ISA && snap.altitudeRef != AltitudeRef.NONE
        // jamais d'alerte vocale sur l'altitude pression standard (±200 m) : l'écran, lui, montre des tirets
        val armed = altCalibrated && (demo || snap.recording || snap.phase == FlightPhase.FLYING)
        // reprise après redémarrage : seulement si l'on vole vraiment (> 30 km/h dans les 2 min)
        pendingResumeNs?.let { since ->
            when {
                (fix.groundSpeedKmh ?: 0.0) > 30 -> {
                    pendingResumeNs = null
                    safety.resumeInFlight(); core.startManual(fix.time)
                    if (core.recording) onSpeak("Application redémarrée en vol. Enregistrement repris.", 1)
                }
                ns - since > 120_000_000_000L -> pendingResumeNs = null
            }
        }
        val climb = snap.avgSpiralMs ?: snap.climbMs ?: 0.0
        safetyState = runCatching { safety.update(fix, alt, climb, snap.circling, config(), fix.time, armed) }.getOrNull()
    }

    private fun forecastWind(): Wind? {
        val day = forecastDay ?: return null
        val alt = lastAltitudeM ?: return null
        val now = clockNow()
        val hour = day.hours.minByOrNull { abs(secondsBetween(it.analysis.validTime, now)) } ?: return null
        // revue résilience M12 : la division entière acceptait une prévision décalée de 3 h 59
        if (abs(secondsBetween(hour.analysis.validTime, now)) > 3 * 3600) return null
        val layer = hour.winds.minByOrNull { abs(it.altitudeMslM - alt) } ?: return null
        return Wind(layer.fromDeg, layer.speedKmh, WindSource.FORECAST, hour.analysis.validTime)
    }

    // ------------------------------------------------------------------ démo

    private suspend fun runDemo() {
        val center = club?.position ?: LatLon(43.80028, 3.78167)
        // iOS V3 : spirales sans vent (cercles au même endroit), légèrement irrégulières, quelques mètres de dérive par tour
        // JB (24/10/2026) : la démo part en spirale après 5 s de vol
        val loop = DemoFlight.startingNearFirstSpiral(DemoFlight.generate(center, windKmh = 0.0, spiralWanderMs = 0.35, spiralGainM = 220.0), center)
        val loopNs = loop.size.toLong() * 1_000_000_000L
        var k = 0L
        while (true) {
            val offset = k * loopNs
            for (s in SensorReplay(loop, seed = 7 + k.toInt()).samples()) {
                val t = s.timeNs + offset
                while (clockNs() < t) delay(10)
                when (s) {
                    is SensorSample.Baro -> { core.onBaro(s.hPa, t); if (soundOn) onTone(core.fastClimb) }
                    is SensorSample.Accel -> core.onAcceleration(s.upMs2, t)
                    is SensorSample.Gps -> {
                        val fix = s.fix.copy(time = demoStartWall + t.nanoseconds, ellipsoidAltM = s.fix.altitudeM)
                        lastGpsNs = t
                        core.onGps(fix, t)
                        afterGps(fix, t)
                    }
                }
            }
            k++
        }
    }

    // ------------------------------------------------------------------ IGC

    private fun openIgc(start: Instant): ((String) -> Unit)? {
        closeIgc()
        val dir = igcDir.toPath()
        runCatching { fs.createDirectories(dir) }
        val date = start.toLocalDateTime(TimeZone.UTC).date
        val index = (1..99).firstOrNull { !fs.exists(dir / Igc.fileName(date, it)) } ?: return null
        val path = dir / Igc.fileName(date, index)
        val sink = runCatching { fs.sink(path).buffer() }.getOrNull() ?: return null
        igcSink = sink; igcPath = path.toString()
        return { line -> runCatching { sink.writeUtf8(line); sink.writeUtf8("\r\n"); sink.flush() } }
    }

    private fun closeIgc() {
        runCatching { igcSink?.close() }
        igcSink = null
        val closed = igcPath ?: return
        igcPath = null
        runCatching { onIgcClosed(closed) }
    }

    // ------------------------------------------------------------------ publication (Swift, 4 Hz)

    /**
     * Image de l'écran, 4 Hz. Ne lève jamais d'exception vers Swift (revue résilience C4) : en cas d'erreur de calcul,
     * la dernière image est rendue avec marge, vario et altitude en tirets (l'information fausse doit avoir l'air fausse).
     */
    fun ui(): PilotUi = runCatching { uiUnsafe() }.onSuccess { lastUi = it }.getOrElse {
        lastUi?.copy(hasData = false, marginText = "—", marginKnown = false, altText = "—", secuText = "—", varioText = "—", distanceText = "—",
            varioMs = 0.0, arrivalText = "", bearingText = "—", profile = emptyList(), gpsOk = false, baroOk = false)
            ?: run { val was = running; running = false; try { uiUnsafe() } finally { running = was } }
    }
    private var lastUi: PilotUi? = null
    private val watchdog = SensorWatchdog()

    private fun uiUnsafe(): PilotUi {
        val ns = clockNs()
        val snap: FlightSnapshot? = if (running) core.snapshot(ns, clockNow()) else null
        if (snap != null && snap.phase != lastPhase) {
            lastPhase = snap.phase
            if (snap.phase == FlightPhase.LANDED) closeIgc()
        }
        if (snap != null && snap.source == VarioSource.NONE && soundOn) onTone(0.0)
        // revue résilience M10 : perte ou retour du GPS / du vario annoncés en vol (pas en démo)
        if (!demo) watchdog.update(ns / 1e9, inFlight, snap?.gpsAgeS, snap != null && snap.source != VarioSource.NONE)
            .forEach { onSpeak(it.text, it.level) }
        val st = if (running) safetyState else null
        val gpsOk = snap != null && snap.gps != null && (snap.gpsAgeS ?: 99.0) < 5
        // revue résilience C1 / C2 : pas de marge sur une position de plus de 5 s ni sur l'altitude pression standard
        // (1013 hPa, erreur de ±200 m selon le QNH) : tirets plutôt qu'une valeur figée ou fausse
        val altUsable = snap != null && snap.altitudeRef != AltitudeRef.BARO_ISA && snap.altitudeRef != AltitudeRef.NONE
        val r = st?.choice?.result?.takeIf { gpsOk && altUsable && it.marginM.isFinite() }
        val hasData = snap != null && (snap.source != VarioSource.NONE || snap.altitudeM != null || gpsOk)
        val alt = snap?.altitudeM
        val wind = st?.wind
        val pos = snap?.gps?.position?.takeIf { gpsOk }
        // sans calcul de sécurité, le terrain du club n'est montré que s'il est un aérodrome officiel (indicatif OACI)
        val officialClub = club?.takeIf { FieldOption.isOfficialAerodrome(it.airfieldIcao) }
        // terrain (nom, position, route, fréquence) depuis le dernier choix de la sécurité, même quand les chiffres sont
        // masqués : la voix et l'écran parlent du même terrain (relecture de la nuit du 23/10/2026)
        val chosen = st?.choice?.result?.field
        val fieldPos = chosen?.position ?: officialClub?.position
        val zoom = snap?.let { ThermalZoom.view(it.trace, it.circling, it.circlingSince, it.now) }
        val climb = snap?.climbMs
        return PilotUi(
            running = running,
            demo = demo,
            hasData = hasData,
            marginText = r?.let { signed(it.marginM.roundToInt()) } ?: "—",
            marginM = r?.marginM ?: 0.0,
            marginKnown = r != null,
            altText = alt?.let { grouped(it.roundToInt()) } ?: "—",
            altRefText = refLabel(snap?.altitudeRef ?: AltitudeRef.NONE),
            secuText = r?.let { grouped(it.requiredM.roundToInt()) } ?: "—",
            distanceText = r?.let { formatFixed(it.distanceKm, 1, decimalComma = true) }
                ?: if (pos != null && fieldPos != null) formatFixed(Geo.distanceKm(pos, fieldPos), 1, decimalComma = true) else "—",
            fieldCode = chosen?.code ?: officialClub?.airfieldIcao ?: "—",
            fieldName = chosen?.name ?: club?.airfieldName ?: club?.name ?: "",
            bearingText = r?.let { "${it.bearingDeg.roundToInt()}°" } ?: "—",
            autoField = st?.choice?.auto ?: true,
            arrivalText = r?.let { "arrivée ${signed(it.arrivalAglM.roundToInt())} m/sol" } ?: "",
            finesse = finesse,
            windText = wind?.let { "${(it.fromDeg.roundToInt() + 360) % 360}°/${it.speedKmh.roundToInt()}" + if (it.source == WindSource.FORECAST) " prévu" else "" } ?: "vent —",
            windFromDeg = wind?.fromDeg ?: 0.0,
            windKnown = wind != null,
            // relief inconnu : NaN (trou dans la coupe), jamais un sol plat inventé (revue résilience M4)
            profile = r?.profile?.map { ProfileDot(it.sKm, it.terrainM ?: Double.NaN) }.orEmpty(),
            reliefChecked = r?.terrainKnown ?: true,
            requiredAltM = r?.requiredM ?: 0.0,
            currentAltM = alt ?: 0.0,
            varioLabel = varioLabel(snap?.source),
            varioText = climb?.let { VarioPalette.valueText(it) } ?: "—",   // « 0,0 » et jamais « −0,0 », vrai signe moins
            varioMs = climb ?: 0.0,
            spiralText = snap?.avgSpiralMs?.let { signedMs(it) } ?: "—",
            thermalText = snap?.avgThermalMs?.let { signedMs(it) } ?: "—",
            dayText = snap?.avgDayMs?.let { signedMs(it) } ?: "—",
            circling = snap?.circling ?: false,
            // carte : dernière position connue (le planeur ne saute pas au terrain) ; distance et marge, elles, en tirets
            lat = (snap?.gps?.position ?: fieldPos)?.lat ?: 0.0,
            lon = (snap?.gps?.position ?: fieldPos)?.lon ?: 0.0,
            trackDeg = snap?.gps?.trackDeg ?: 0.0,
            hasPosition = snap?.gps?.position != null,
            fieldLat = fieldPos?.lat ?: 0.0,
            fieldLon = fieldPos?.lon ?: 0.0,
            trace = traceDots(snap?.trace),
            zoomLat = zoom?.centre?.lat ?: 0.0,
            zoomLon = zoom?.centre?.lon ?: 0.0,
            zoomRadiusM = zoom?.radiusM ?: 0.0,
            zoomActive = zoom != null,
            gpsOk = gpsOk,
            // baro utilisé par le vario (l'iPhone livre ~1 Hz : l'ancien seuil « > 3 Hz » le disait toujours absent)
            baroOk = snap?.source == VarioSource.BARO || snap?.source == VarioSource.BARO_ACCEL,
            recording = snap?.recording ?: false,
            chronoText = chrono(snap?.flightSeconds ?: 0),
            phaseText = when {
                snap == null -> "Moteur de vol en pause"
                snap.recording -> "En vol · IGC ${snap.igcFixes} points"
                snap.phase == FlightPhase.FLYING -> if (demo) "Démo en vol · ${chrono(snap.flightSeconds)} · pas d'IGC" else "En vol · non enregistré (REC)"
                snap.phase == FlightPhase.LANDED -> "Posé · vol enregistré"
                autoTakeoff -> "Au sol · départ auto à 50 km/h"
                else -> "Au sol · chrono manuel"
            },
            igcPoints = snap?.igcFixes ?: 0,
            soundOn = soundOn,
            fieldElevText = fieldElevation?.let { "terrain ${if (fieldElevationSource == "openAIP") "" else "≈ "}${it.roundToInt()} m ($fieldElevationSource)" } ?: "altitude du terrain inconnue : marge non calculée",
            reliefLoaded = pack?.reliefLoaded == true,
            arrivalAltM = r?.let { it.field.elevationM + it.arrivalAglM } ?: 0.0,
            fieldElevM = r?.field?.elevationM ?: 0.0,
            routeKm = r?.distanceKm ?: 0.0,
            profileEndText = r?.let { "${formatFixed(it.distanceKm, 1, decimalComma = true)} km · ${it.field.code} ${it.field.elevationM.roundToInt()} m" } ?: "",
            finesseNote = "+${config().arrivalMarginM.roundToInt()} m · " + (wind?.let { "vent ${(it.fromDeg.roundToInt() + 360) % 360}°/${it.speedKmh.roundToInt()}" + if (it.source == WindSource.FORECAST) " prévu" else "" } ?: "vent —"),
            flightTimeText = chrono(snap?.flightSeconds ?: 0),
            spiralMs = snap?.avgSpiralMs ?: Double.NaN,
            windSpeedKmh = wind?.speedKmh ?: 0.0,
            windForecast = wind?.source == WindSource.FORECAST,
            traceVersion = snap?.trace?.lastOrNull()?.time?.toEpochMilliseconds() ?: 0L,
            fieldFrequencyText = (chosen?.id ?: club?.airfieldIcao)?.let { frequencies[it] }?.replace('.', ',') ?: "—",
        )
    }

    /** [baroPresent] : CMAltimeter disponible ; [gpsAllowed] : autorisation de localisation accordée. */
    fun sensors(baroPresent: Boolean, gpsAllowed: Boolean): SensorsUi {
        val snap = if (running) core.snapshot(clockNs(), clockNow()) else null
        return SensorsUi(
            sourceLabel = when {
                snap == null -> "Moteur de vol en pause (arrière-plan)"
                demo -> "Démo : capteurs simulés"
                else -> varioLabel(snap.source) + if (!baroPresent) " · pas de baromètre sur ce téléphone" else ""
            },
            sourceOk = snap?.source == VarioSource.BARO_ACCEL || snap?.source == VarioSource.BARO,
            baroHz = snap?.baroHz?.takeIf { it > 0 }?.let { "${it.roundToInt()} Hz" } ?: if (baroPresent) "—" else "absent",
            baroNoise = snap?.baroNoiseCm?.let { "${it.roundToInt()} cm" } ?: "—",
            accelHz = snap?.accelHz?.takeIf { it > 0 }?.let { "${it.roundToInt()} Hz" } ?: "—",
            gpsAccuracy = (if (snap != null && (snap.gpsAgeS ?: 99.0) < 5) snap.gps?.accuracyM else null)?.let { "±${it.roundToInt()} m" }
                ?: if (gpsAllowed || demo) "attente" else "refusé",
            altitudeLine = snap?.altitudeM?.let { "Altitude ${grouped(it.roundToInt())} m · ${refLabel(snap.altitudeRef)}" + (fieldElevation?.let { e -> " · terrain ${e.roundToInt()} m" } ?: "") } ?: "",
            flightLine = when {
                snap == null -> ""
                snap.recording -> "En vol · ${chrono(snap.flightSeconds)} · IGC ${snap.igcFixes} points"
                snap.phase == FlightPhase.LANDED -> "Posé · vol de ${chrono(snap.flightSeconds)} enregistré"
                autoTakeoff -> "Au sol · départ auto à 50 km/h"
                else -> "Au sol · chrono manuel"
            },
        )
    }

    /** Vrai tant qu'un vol est enregistré ou détecté : l'app garde capteurs, son et sécurité en arrière-plan. */
    val inFlight: Boolean get() = running && !demo && (core.recording || lastPhase == FlightPhase.FLYING)

    private fun varioLabel(src: VarioSource?) = when (src) {
        VarioSource.BARO_ACCEL -> "Vario baro+accél."
        VarioSource.BARO -> "Vario baro"
        VarioSource.OGN -> "Vario OGN"
        else -> "Vario indisponible"
    }

    private fun refLabel(r: AltitudeRef) = when (r) {
        AltitudeRef.BARO_FIELD -> "calé terrain"
        AltitudeRef.BARO_GPS -> "calé GPS"
        AltitudeRef.BARO_ISA -> "alt. pression"
        AltitudeRef.GPS -> "GPS"
        AltitudeRef.OGN -> "OGN"
        AltitudeRef.NONE -> "indisponible"
    }

    companion object {
        fun signed(v: Int): String = (if (v >= 0) "+" else "−") + grouped(abs(v))
        fun signedMs(v: Double): String = (if (v >= 0) "+" else "−") + formatFixed(abs(v), 1, decimalComma = true)
        fun grouped(n: Int): String {
            val g = abs(n).toString().reversed().chunked(3).joinToString(" ").reversed()
            return if (n < 0) "-$g" else g
        }
        fun chrono(s: Long): String = "${s / 3600} h ${(s % 3600 / 60).toString().padStart(2, '0')}"
    }
}
