import SwiftUI
import UIKit
import WindGliderShared

/// État de l'app Lite iOS : club du jour, météo du jour, moteur de vol commun, carnet de vols.
/// Équivalent de GliderApp.kt + PrevolViewModel côté Android ; tout le calcul reste dans le métier Kotlin partagé.
@MainActor
final class AppModel: ObservableObject {
    enum WeatherState { case idle, loading, failed(String), loaded(DayWeather) }

    let clubs: ClubDirectory
    let engine: IosFlightEngine
    let library: FlightLibrary
    let packs = PackStore()
    private var packSink: AnyCancellable?
    private let weatherApi: WindGliderWeather
    private(set) var sensors: Sensors!
    private let tone = VarioTone()
    private let voice = Voice()
    private var timer: Timer?

    @Published var club: Club?
    @Published var weather: WeatherState = .idle
    /// Jour de la météo Prévol : 0 aujourd'hui, 1 demain, 2 après-demain (aujourd'hui avant 17 h, demain ensuite).
    @Published var dayOffset = Int(WindGliderWeather.companion.defaultOffset())
    /// État du vol publié à 4 Hz : dans un objet à part, observé par Pilotage seulement. Prévol, Mes vols et la racine
    /// ne sont plus réévalués 4 fois par seconde (revue énergie 9).
    let flight: FlightModel
    var pilot: PilotUi {
        get { flight.pilot }
        set {
            flight.pilot = newValue
            if isRecording != newValue.recording {
                isRecording = newValue.recording
                // vol en cours mémorisé : un redémarrage de l'app en vol reprend directement (revue résilience M9)
                if newValue.recording { sensors?.motionWanted = true }   // vol : vario complet, quel que soit l'onglet
            }
        }
    }
    /// Enregistrement en cours : seul champ du vol lu hors de Pilotage, republié seulement quand il change.
    @Published private(set) var isRecording = false
    static let flightInProgressKey = "flightInProgressAt"
    /// Économie (JB, nuit du 23/10) : iPhone chaud (« serious » ou plus) ou mode Économie d'énergie. Carte à 20 i/s,
    /// OGN en pause ; vario sonore, marge et IGC jamais dégradés.
    @Published private(set) var economy = AppModel.economyNow()
    private static func economyNow() -> Bool {
        ProcessInfo.processInfo.isLowPowerModeEnabled || ProcessInfo.processInfo.thermalState.rawValue >= ProcessInfo.ThermalState.serious.rawValue
    }
    private var powerObservers: [NSObjectProtocol] = []
    private func watchPower() {
        let nc = NotificationCenter.default
        for name in [ProcessInfo.thermalStateDidChangeNotification, Notification.Name.NSProcessInfoPowerStateDidChange] {
            powerObservers.append(nc.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in
                    guard let self else { return }
                    let e = AppModel.economyNow()
                    if e != self.economy {
                        self.economy = e
                        if e { self.traffic?.stop() }
                    }
                }
            })
        }
    }
    @Published var flights: [FlightEntry] = []
    @Published var sensorsUi: SensorsUi?
    /// Pastilles de Pilotage, même sens qu'Android (DeviceState.kt) : GPS = autorisation et localisation actives,
    /// BARO = baromètre présent, DATA = réseau avec accès Internet.
    struct Status { var gps = false; var baro = false; var data = false }
    @Published var status = Status()
    private let pathMonitor = NWPathMonitor()
    /// Espaces aériens à moins de 40 km du club (pack hors ligne), pour la carte et Prévol.
    @Published var airspaces: [AirspaceShape] = []
    /// V3 : masque des zones aéronautiques affiché sur la carte de vol (bouton près du zoom)
    @Published var showAirspaces: Bool = UserDefaults.standard.object(forKey: "showAirspaces") as? Bool ?? true {
        didSet { UserDefaults.standard.set(showAirspaces, forKey: "showAirspaces") }
    }
    @Published var soundOn: Bool { didSet { UserDefaults.standard.set(soundOn, forKey: "varioSound"); engine.setSound(on: soundOn); soundOn ? tone.start() : tone.stop() } }
    @Published var voiceOn: Bool { didSet { UserDefaults.standard.set(voiceOn, forKey: "voice"); voice.enabled = voiceOn } }
    @Published var autoTakeoff: Bool { didSet { UserDefaults.standard.set(autoTakeoff, forKey: "autoTakeoff"); engine.setAutoTakeoff(on: autoTakeoff) } }
    /// Finesse (10, 20, 30) et marge d'arrivée au terrain (m), réglées en Prévol et mémorisées (JB, 22/10/2026).
    @Published var finesse: Int = UserDefaults.standard.object(forKey: "glide.finesse") as? Int ?? 20 {
        didSet { engine.setFinesse(value: Int32(finesse)); UserDefaults.standard.set(finesse, forKey: "glide.finesse") }
    }
    @Published var arrivalMargin: Int = UserDefaults.standard.object(forKey: "glide.arrival") as? Int ?? 300 {
        didSet { engine.setArrivalMargin(meters: Int32(arrivalMargin)); UserDefaults.standard.set(arrivalMargin, forKey: "glide.arrival") }
    }
    /// À appeler une fois le moteur créé : il reçoit les réglages mémorisés.
    func applyGlideSettings() {
        engine.setFinesse(value: Int32(finesse)); engine.setArrivalMargin(meters: Int32(arrivalMargin))
    }
    @Published var demo = false { didSet { engine.setDemo(on: demo) } }
    /// Build de développement : `-demoFlight` lance le planeur démo dès l'ouverture (captures au simulateur).
    func applyLaunchDemo() {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-demoFlight"), !demo { demo = true }
        #endif
    }
    private var lastPosition: CLLocationCoordinate2D?
    /// Trafic OGN autour du terrain (même session qu'Android, OgnLiveSession).
    let traffic: IosTraffic?  // jamais nil ; optionnel pour garder les appels existants
    var trafficSnap: TrafficSnapshot? {
        get { flight.trafficSnap }
        set { flight.trafficSnap = newValue }
    }
    private var tick = 0
    /// V3 : position du planeur à chaque image pour la carte fluide (affichage seulement).
    let predictor = OwnshipPredictor()
    private var demoLast: (lat: Double, lon: Double, t: Double)?
    private var demoSpeed = 0.0

    static let igcDir: String = {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("igc").path
    }()

    init() {
        let json = Bundle.main.path(forResource: "clubs_fr", ofType: "json") ?? ""
        clubs = ClubDirectory(jsonPath: json)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        engine = IosFlightEngine(igcDir: AppModel.igcDir, appVersion: version)
        flight = FlightModel(pilot: engine.ui())
        library = FlightLibrary(dir: AppModel.igcDir)
        // vol d'exemple (Saint-Martin-de-Londres), le même qu'Android, importé une seule fois
        if !UserDefaults.standard.bool(forKey: "demoFlightImported"),
           let igc = Bundle.main.path(forResource: "saint-martin-de-londres-vol-synthetique", ofType: "igc"),
           library.importIgc(sourcePath: igc, name: "exemple-saint-martin-de-londres-vol-synthetique.igc") != nil {
            UserDefaults.standard.set(true, forKey: "demoFlightImported")
        }
        weatherApi = AppModel.makeWeather(version: version)
        traffic = IosTraffic(cacheDir: NSTemporaryDirectory(), installId: AppModel.installId, appVersion: version)   // comme la 2.0.4 Lite : trafic OGN dans Pilotage
        // état initial du vol : déjà posé dans FlightModel(pilot:)
        let d = UserDefaults.standard
        soundOn = d.bool(forKey: "varioSound")
        voiceOn = d.object(forKey: "voice") as? Bool ?? true
        autoTakeoff = d.bool(forKey: "autoTakeoff")   // Lite : enregistrement manuel par défaut (comme Android)
        sensors = Sensors(engine: engine)
        sensors.onPosition = { [weak self] l in
            guard let self else { return }
            self.positionChanged(l.coordinate)
            // chaque position GPS va directement à la carte, sans attendre le tour de 0,25 s
            if !self.demo { self.predictor.fix(lat: l.coordinate.latitude, lon: l.coordinate.longitude, speedMs: l.speed, course: l.course, at: l.timestamp.timeIntervalSince1970) }
        }
        sensors.onTurnRate = { [weak self] rate, t in
            guard let self, !self.demo else { return }
            self.predictor.gyro(turnDegS: rate, at: t)
        }

        watchPower()
        engine.onSpeak = { [weak self] text, level in
            self?.voice.speak(text)
            if level.intValue > 0 { UINotificationFeedbackGenerator().notificationOccurred(level.intValue > 1 ? .error : .warning) }
        }
        engine.onTone = { [weak self] v in self?.tone.set(v.doubleValue) }
        engine.onIgcClosed = { [weak self] _ in self?.refreshFlights() }
        engine.setSound(on: soundOn)
        engine.setAutoTakeoff(on: autoTakeoff)
        voice.enabled = voiceOn
        if soundOn { tone.start() }
        // club : choisi à la main, sinon le plus proche (dès la première position), sinon le club par défaut
        club = clubs.current(chosenId: d.string(forKey: "clubId"), lat: 0, lon: 0, hasPosition: false)
        engine.setClub(c: club)
        refreshFlights()
        status.baro = sensors.baroAvailable
        pathMonitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in self?.status.data = path.status == .satisfied }
        }
        pathMonitor.start(queue: .global(qos: .utility))
        PackStoreHolder.current = packs
        packSink = packs.$active.sink { [weak self] p in
            self?.engine.setPack(p: p)
            self?.updateAirspaces(p)
        }
        Task { await packs.refresh(for: club) }
    }

    /// Météo PRECOG ; build de développement lancé avec `-testData` : données d'essai synthétiques, sans réseau.
    private static func makeWeather(version: String) -> WindGliderWeather {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-testData"), let dir = Bundle.main.path(forResource: "fixtures", ofType: nil) {
            return WindGliderWeather.companion.testData(fixturesDir: dir)
        }
        #endif
        return WindGliderWeather(
            apiKey: Bundle.main.object(forInfoDictionaryKey: "PRECOG_API_KEY") as? String ?? "",
            userAgent: "WindGlider/\(version) (iOS)"
        )
    }

    var weatherIsTestData: Bool { weatherApi.isTestData }

    /// Mode démo : le planeur simulé vient du moteur (4 Hz) ; vitesse déduite des positions successives.
    private func feedDemo(_ p: PilotUi) {
        guard p.hasPosition else { return }
        let now = Date().timeIntervalSince1970
        if let l = demoLast {
            guard l.lat != p.lat || l.lon != p.lon else { return }
            let (e, n) = OwnshipPredictor.enu(fromLat: l.lat, lon: l.lon, toLat: p.lat, lon: p.lon)
            let d = hypot(e, n), dt = max(now - l.t, 0.05)
            if d < 2000 { demoSpeed = demoSpeed * 0.5 + d / dt * 0.5 }
        }
        demoLast = (p.lat, p.lon, now)
        predictor.fix(lat: p.lat, lon: p.lon, speedMs: demoSpeed, course: p.trackDeg, at: now)
    }

    /// Identifiant d'installation (indicatif APRS de lecture « GLIDYnnnn », comme installId d'Android).
    static var installId: Int64 {
        let d = UserDefaults.standard
        if let v = d.object(forKey: "installId") as? Int64 { return v }
        let v = Int64.random(in: 0..<Int64.max)
        d.set(v, forKey: "installId")
        return v
    }

    /// Centre du trafic : terrain du club, sinon la position.
    private func startTraffic() {
        guard let traffic else { return }
        if let pos = club?.position { traffic.start(lat: pos.lat, lon: pos.lon) }
        else if let l = lastPosition { traffic.start(lat: l.latitude, lon: l.longitude) }
    }

    // MARK: cycle de vie

    func startEngine() {
        sensors.requestPermission()
        sensors.start()
        #if DEBUG
        BenchMeter.extra = { [weak self] in
            guard let self, let s = self.sensorsUi else { return "" }
            return "baro \(s.baroHz) bruit \(s.baroNoise) · mouvement \(self.sensors.motionAuthorization) \(self.sensors.baroError ?? "") n=\(self.sensors.baroCount) p=\(self.sensors.lastPressureKPa) · accél \(s.accelHz) · \(s.sourceLabel) · \(self.pilot.varioText)"
        }
        BenchMeter.startIfRequested()
        #endif
        applyGlideSettings()
        engine.start()
        // reprise après un redémarrage en vol : nouvel IGC (le précédent est déjà fermé et gardé) et annonce
        if RootView.resumeInFlight && !resumed {
            resumed = true
            engine.resumeInFlight()   // le moteur reprend (et l'annonce) seulement s'il voit > 30 km/h dans les 2 min
        }
        timer?.invalidate()
        // mode « common » : l'écran de vol reste à jour pendant un défilement ou un menu ouvert (revue résilience M15)
        let t = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                // écran éteint en vol : un tour léger à 1 Hz (atterrissage → fermeture de l'IGC), rien n'est publié
                // à SwiftUI ; sécurité et son vario restent pilotés par les capteurs (revue énergie 11)
                if self.inBackground {
                    if self.tick % 4 == 0 { let ui = self.engine.ui(); self.heartbeat(ui.recording) }
                    self.tick += 1
                    return
                }
                self.pilot = self.engine.ui()
                self.heartbeat(self.pilot.recording)
                // vol détecté (même sans REC) : accéléromètre gardé, le vario baro seul est moins précis
                if self.engine.inFlight && self.sensors.motionWanted == false { self.sensors.motionWanted = true }
                if self.demo { self.feedDemo(self.pilot) }
                // carte Capteurs de Prévol : 1 Hz suffit ; état republié seulement s'il change (moins de redessins SwiftUI)
                if self.tick % 4 == 0 {
                    self.sensorsUi = self.engine.sensors(baroPresent: self.sensors.baroAvailable, gpsAllowed: self.sensors.authorized)
                    let g = self.sensors.authorized && CLLocationManager.locationServicesEnabled()
                    if self.status.gps != g { self.status.gps = g }
                }
                self.tick += 1
                if let t = self.traffic, self.tick % 4 == 0 {
                    if !t.running && !self.economy { self.startTraffic() }
                    self.trafficSnap = t.snapshot(ownAltitudeM: self.pilot.hasData ? self.pilot.currentAltM : .nan)
                }
            }
        }
        RunLoop.main.add(t, forMode: .common)
        timer = t
    }

    /// Arrière-plan : capteurs et son coupés hors vol (batterie) ; pendant un vol enregistré, tout continue écran éteint
    /// (localisation en arrière-plan + audio, comme le service de premier plan d'Android).
    func enterBackground() {
        // pas d'audio d'arrière-plan (App Review 2.5.4) : vario et voix se taisent écran éteint, on coupe le moteur audio
        // au lieu de le laisser tourner pour rien ; il repart au retour (enterForeground)
        tone.stop()
        // en vol écran éteint, ou en attente du décollage automatique (téléphone mis dans la poche avant le treuil) :
        // GPS, sécurité et IGC continuent ; OGN s'arrête (l'anticollision reste le rôle du FLARM) et reprend au retour
        guard !engine.inFlight && !autoTakeoff else { traffic?.stop(); inBackground = true; return }
        engine.stop(); sensors.stop(); timer?.invalidate(); timer = nil
        tone.stop()
        traffic?.stop()   // pas de réseau OGN en arrière-plan hors vol (comme OgnLiveRepository.stop)
    }

    /// Écran éteint pendant un vol : le minuteur ne fait plus que le strict nécessaire.
    private var inBackground = false
    private var lastHeartbeat = 0.0

    /// Vol en cours mémorisé comme un battement de cœur (toutes les 30 s pendant l'enregistrement, effacé sinon, en
    /// premier plan comme écran éteint) : un redémarrage n'est pris pour une reprise en vol que dans les 10 min.
    private func heartbeat(_ recording: Bool) {
        let now = Date().timeIntervalSince1970
        if recording {
            if now - lastHeartbeat >= 30 { lastHeartbeat = now; UserDefaults.standard.set(now, forKey: Self.flightInProgressKey) }
        } else if lastHeartbeat != -1 {
            lastHeartbeat = -1; UserDefaults.standard.removeObject(forKey: Self.flightInProgressKey)
        }
    }
    private var resumed = false

    func enterForeground() {
        inBackground = false
        // son du vario relancé dans tous les cas (il était coupé en arrière-plan), même si le moteur tournait encore
        if soundOn { tone.start() }
        guard timer == nil else { return }
        startEngine()
    }

    private func updateAirspaces(_ p: ActivePack?) {
        guard let p, let pos = club?.position else { airspaces = []; return }
        // V3 : toute la région autour du terrain (masque des zones aéronautiques sur la carte)
        airspaces = p.airspacesAround(lat: pos.lat, lon: pos.lon, km: 150, max: 400)
    }

    // MARK: club

    func choose(_ c: Club) {
        UserDefaults.standard.set(c.id, forKey: "clubId")
        club = c
        engine.setClub(c: c)
        startTraffic()
        Task { await loadWeather() }
        Task { await packs.refresh(for: c); updateAirspaces(packs.active) }
    }

    private func positionChanged(_ p: CLLocationCoordinate2D) {
        let first = lastPosition == nil
        lastPosition = p
        guard first, UserDefaults.standard.string(forKey: "clubId") == nil else { return }
        let near = clubs.current(chosenId: nil, lat: p.latitude, lon: p.longitude, hasPosition: true)
        if near?.id != club?.id {
            club = near
            engine.setClub(c: near)
            Task { await loadWeather() }
            Task { await packs.refresh(for: near) }
        }
    }

    // MARK: météo

    func loadWeather() async {
        guard let c = club, let pos = c.position else { weather = .failed(tr("Club sans position connue")); return }
        weather = .loading
        do {
            let r = try await weatherApi.loadDayOffset(lat: pos.lat, lon: pos.lon, departement: c.departement, offsetDays: Int32(dayOffset))
            if let ok = r as? WeatherResultSuccess {
                weather = .loaded(ok.day)
                if dayOffset == Int(WindGliderWeather.companion.defaultOffset()) { engine.setForecast(day: ok.day) }   // le vol suit le jour courant
            } else if let err = r as? WeatherResultError {
                weather = .failed(err.message)
            }
        } catch {
            weather = .failed(error.localizedDescription)
        }
    }

    // MARK: vol

    func toggleRecording() {
        if pilot.recording { engine.stopChrono() } else { engine.startChrono() }
        pilot = engine.ui()
    }

    func testSound() {
        let wasOn = soundOn
        tone.start()
        Task {
            for i in 0...70 { tone.set(-2.5 + 6.5 * Double(i) / 70); try? await Task.sleep(nanoseconds: 100_000_000) }
            tone.set(0)
            if !wasOn { tone.stop() }
        }
    }

    func testVoice() {
        voice.speak("Essai des annonces vocales. " + (sensors.baroAvailable ? "Vario prêt." : "Pas de baromètre sur ce téléphone."), force: true)
    }

    /// Lecture des IGC hors du fil principal (le carnet peut contenir de longs vols).
    func refreshFlights() {
        let lib = library
        Task.detached(priority: .utility) {
            let list = lib.list()
            await MainActor.run { self.flights = list }
        }
    }

    func delete(_ f: FlightEntry) { library.delete(path: f.path); refreshFlights() }
}

import Combine
import CoreLocation
import Network

/// Valeurs du vol rafraîchies 4 fois par seconde (écran Pilotage).
@MainActor
final class FlightModel: ObservableObject {
    @Published var pilot: PilotUi
    @Published var trafficSnap: TrafficSnapshot?
    init(pilot: PilotUi) { self.pilot = pilot }
}
