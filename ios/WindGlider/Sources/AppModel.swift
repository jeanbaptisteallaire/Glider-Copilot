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
    @Published var pilot: PilotUi
    @Published var flights: [FlightEntry] = []
    @Published var soundOn: Bool { didSet { UserDefaults.standard.set(soundOn, forKey: "varioSound"); engine.setSound(on: soundOn); soundOn ? tone.start() : tone.stop() } }
    @Published var voiceOn: Bool { didSet { UserDefaults.standard.set(voiceOn, forKey: "voice"); voice.enabled = voiceOn } }
    @Published var autoTakeoff: Bool { didSet { UserDefaults.standard.set(autoTakeoff, forKey: "autoTakeoff"); engine.setAutoTakeoff(on: autoTakeoff) } }
    @Published var finesse: Int = 20 { didSet { engine.setFinesse(value: Int32(finesse)) } }
    @Published var demo = false { didSet { engine.setDemo(on: demo) } }
    private var lastPosition: CLLocationCoordinate2D?

    static let igcDir: String = {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("igc").path
    }()

    init() {
        let json = Bundle.main.path(forResource: "clubs_fr", ofType: "json") ?? ""
        clubs = ClubDirectory(jsonPath: json)
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        engine = IosFlightEngine(igcDir: AppModel.igcDir, appVersion: version)
        library = FlightLibrary(dir: AppModel.igcDir)
        weatherApi = AppModel.makeWeather(version: version)
        pilot = engine.ui()
        let d = UserDefaults.standard
        soundOn = d.bool(forKey: "varioSound")
        voiceOn = d.object(forKey: "voice") as? Bool ?? true
        autoTakeoff = d.bool(forKey: "autoTakeoff")   // Lite : enregistrement manuel par défaut (comme Android)
        sensors = Sensors(engine: engine)
        sensors.onPosition = { [weak self] l in self?.positionChanged(l.coordinate) }

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
        PackStoreHolder.current = packs
        packSink = packs.$active.sink { [weak self] p in self?.engine.setPack(p: p) }
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

    // MARK: cycle de vie

    func startEngine() {
        sensors.requestPermission()
        sensors.start()
        engine.start()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.pilot = self?.engine.ui() ?? self!.pilot }
        }
    }

    // MARK: club

    func choose(_ c: Club) {
        UserDefaults.standard.set(c.id, forKey: "clubId")
        club = c
        engine.setClub(c: c)
        Task { await loadWeather() }
        Task { await packs.refresh(for: c) }
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
        guard let c = club, let pos = c.position else { weather = .failed("Club sans position connue"); return }
        weather = .loading
        do {
            let r = try await weatherApi.loadDay(lat: pos.lat, lon: pos.lon, departement: c.departement)
            if let ok = r as? WeatherResultSuccess {
                weather = .loaded(ok.day)
                engine.setForecast(day: ok.day)
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

    func refreshFlights() { flights = library.list() }

    func delete(_ f: FlightEntry) { library.delete(path: f.path); refreshFlights() }
}

import Combine
import CoreLocation
