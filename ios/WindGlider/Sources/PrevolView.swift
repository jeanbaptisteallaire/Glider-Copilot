import Charts
import SwiftUI
import WindGliderShared

/// Onglet Prévol (édition Lite) : club du jour, journée thermique ARPEGE, heure par heure, vents, vigilance,
/// sources (bouton « i ») et réglages du vol. Toute valeur affichée porte sa source et son hypothèse.
struct PrevolView: View {
    @EnvironmentObject var app: AppModel
    @State private var picking = false
    @State private var showSources = false
    @State private var hourIndex: Int?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    clubCard
                    if app.weatherIsTestData {
                        Text("DONNÉES D'ESSAI · journée synthétique du 16/09/2026, pas une prévision")
                            .font(WG.caption.weight(.bold)).foregroundStyle(.white)
                            .padding(8).frame(maxWidth: .infinity).background(WG.bad, in: RoundedRectangle(cornerRadius: 10))
                    }
                    switch app.weather {
                    case .idle, .loading:
                        WGCard { HStack { ProgressView(); Text("Météo du jour…").font(WG.subhead) } }
                    case .failed(let message):
                        WGCard(title: "Météo", systemImage: "cloud.sun", tint: WG.sun) {
                            Text(message).font(WG.subhead).foregroundStyle(WG.brandInk)
                            Button("Réessayer") { Task { await app.loadWeather() } }.font(WG.subhead)
                        }
                    case .loaded(let day):
                        DayCard(day: day)
                        HoursCard(day: day, selected: $hourIndex)
                        WindsCard(day: day, index: hourIndex)
                        if let v = day.vigilance { VigilanceCard(v: v) }
                        Text("Prévision \(day.sources.first?.product ?? "ARPEGE") · \(WeatherRepositoryKt.PRECOG_CREDIT)")
                            .font(WG.caption).foregroundStyle(WG.dim)
                    }
                    PackCard()
                    FlightSettingsCard()
                }
                .padding(16)
            }
            .background(WG.brandPaper)
            .navigationTitle("Prévol")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showSources = true } label: { Image(systemName: "info.circle") }
                        .accessibilityLabel("Sources météo")
                }
            }
            .sheet(isPresented: $picking) { ClubPicker().environmentObject(app) }
            .sheet(isPresented: $showSources) { SourcesSheet().environmentObject(app) }
            .task { if case .idle = app.weather { await app.loadWeather() } }
            .refreshable { await app.loadWeather() }
        }
    }

    private var clubCard: some View {
        Button { picking = true } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(app.club?.shortName ?? app.club?.name ?? "Choisir un club").font(WG.headline).foregroundStyle(WG.brandInk)
                    Text([app.club?.airfieldIcao, app.club?.airfieldName ?? app.club?.city].compactMap { $0 }.joined(separator: " · "))
                        .font(WG.footnote).foregroundStyle(WG.dim)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(WG.dim)
            }
            .padding(16)
            .background(WG.card, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - formats

enum Fmt {
    static let paris = TimeZone(identifier: "Europe/Paris")!
    static func date(_ i: Kotlinx_datetimeInstant) -> Date { Date(timeIntervalSince1970: WindGliderWeather.companion.epochSeconds(i: i)) }
    static func hm(_ i: Kotlinx_datetimeInstant?) -> String {
        guard let i else { return "—" }
        let f = DateFormatter(); f.timeZone = paris; f.dateFormat = "HH:mm"
        return f.string(from: date(i))
    }
    static func hour(_ i: Kotlinx_datetimeInstant) -> String {
        let f = DateFormatter(); f.timeZone = paris; f.dateFormat = "H'h'"
        return f.string(from: date(i))
    }
    static func m(_ v: Double) -> String { IosFlightEngine.companion.grouped(n: Int32((v / 10).rounded() * 10)) + " m" }
    static func ms(_ v: Double) -> String { String(format: "%.1f m/s", v).replacingOccurrences(of: ".", with: ",") }
    static func day(_ d: Kotlinx_datetimeLocalDate) -> String {
        var c = DateComponents(); c.year = Int(d.year); c.month = Int(d.monthNumber); c.day = Int(d.dayOfMonth)
        let f = DateFormatter(); f.locale = Locale(identifier: "fr_FR"); f.dateFormat = "EEEE d MMMM"
        return Calendar(identifier: .gregorian).date(from: c).map(f.string(from:)) ?? d.description
    }
}

// MARK: - cartes

struct DayCard: View {
    let day: DayWeather

    var body: some View {
        let s = day.summary
        WGCard(title: Fmt.day(day.date).prefix(1).uppercased() + Fmt.day(day.date).dropFirst(), systemImage: "sun.max", tint: WG.sun) {
            Text(s.quality.label + (s.overdevelopmentRisk ? " · risque de surdéveloppement" : ""))
                .font(WG.subhead).foregroundStyle(WG.brandInk)
            HStack(alignment: .top) {
                kpi(Fmt.hm(s.triggerTime), "Déclenchement", WG.sun)
                kpi(s.best.map { Fmt.m($0.ceilingMslM) } ?? "—", s.best.map { "Plafond \(Fmt.hour($0.validTime))" } ?? "Plafond", WG.altitude)
                kpi(Fmt.hm(s.endTime), "Fin", WG.sun)
            }
            if let g = day.gridDistanceKm {
                Text("Point de grille ARPEGE à \(String(format: "%.1f", g.doubleValue).replacingOccurrences(of: ".", with: ",")) km · plafond en altitude mer (QNH), estimation du modèle")
                    .font(WG.caption).foregroundStyle(WG.dim)
            }
        }
    }

    private func kpi(_ value: String, _ label: String, _ tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(WG.number(22)).foregroundStyle(WG.brandInk)
            Text(label).font(WG.caption).foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct HoursCard: View {
    let day: DayWeather
    @Binding var selected: Int?

    var body: some View {
        let hours = day.hours
        WGCard(title: "Heure par heure", systemImage: "chart.bar", tint: WG.altitude) {
            Chart {
                ForEach(Array(hours.enumerated()), id: \.offset) { i, h in
                    BarMark(x: .value("Heure", Fmt.hour(h.analysis.validTime)), y: .value("Plafond", h.analysis.ceilingMslM))
                        .foregroundStyle(WG.vario(h.analysis.climbMs).opacity(selected == nil || selected == i ? 1 : 0.4))
                        .annotation(position: .top) {
                            Text(String(format: "%.1f", h.analysis.climbMs).replacingOccurrences(of: ".", with: ","))
                                .font(WG.caption).foregroundStyle(WG.dim)
                        }
                }
            }
            .chartYAxisLabel("plafond (m QNH)")
            .frame(height: 180)
            .chartOverlay { proxy in
                GeometryReader { g in
                    Rectangle().fill(.clear).contentShape(Rectangle()).onTapGesture { p in
                        if let label: String = proxy.value(atX: p.x - g[proxy.plotAreaFrame].origin.x) {
                            selected = hours.firstIndex { Fmt.hour($0.analysis.validTime) == label }
                        }
                    }
                }
            }
            Text("Barres : plafond · couleur et chiffre : montée moyenne estimée (m/s). Touchez une heure pour ses vents.")
                .font(WG.caption).foregroundStyle(WG.dim)
        }
    }
}

struct WindsCard: View {
    let day: DayWeather
    let index: Int?

    var body: some View {
        let hours = day.hours
        let best = day.summary.best
        let h = index.flatMap { hours.indices.contains($0) ? hours[$0] : nil }
            ?? hours.first { best != nil && $0.analysis.validTime == best!.validTime } ?? hours.first
        if let h {
            WGCard(title: "Vent par tranches · \(Fmt.hm(h.analysis.validTime))", systemImage: "wind", tint: WG.wind) {
                ForEach(h.winds, id: \.label) { w in
                    HStack {
                        Text(w.label).font(WG.subhead).frame(width: 90, alignment: .leading)
                        Image(systemName: "arrow.up").rotationEffect(.degrees(w.fromDeg + 180)).foregroundStyle(WG.wind)
                        Text("\(Int(w.fromDeg.rounded()))°").font(WG.subhead.monospacedDigit())
                        Spacer()
                        Text("\(Int(w.speedKmh.rounded())) km/h").font(WG.subhead.monospacedDigit()).foregroundStyle(WG.brandInk)
                    }
                }
                Text("Vent moyen du modèle (direction d'où il vient)").font(WG.caption).foregroundStyle(WG.dim)
            }
        }
    }
}

struct VigilanceCard: View {
    let v: VigilanceStatus

    var body: some View {
        let color: Color = [1: .green, 2: .yellow, 3: .orange, 4: .red][Int(v.colorId)] ?? .gray
        WGCard(title: "Vigilance Météo-France · \(v.departement)", systemImage: "exclamationmark.triangle", tint: WG.brandInk) {
            HStack {
                Circle().fill(color).frame(width: 14, height: 14)
                Text(v.colorLabel.capitalized).font(WG.subhead)
                if !v.phenomena.isEmpty { Text("· " + v.phenomena.joined(separator: ", ")).font(WG.subhead).foregroundStyle(WG.dim) }
            }
        }
    }
}

struct FlightSettingsCard: View {
    @EnvironmentObject var app: AppModel

    var body: some View {
        WGCard(title: "Vol", systemImage: "gearshape", tint: WG.brandInk) {
            Toggle("Départ automatique à 50 km/h", isOn: $app.autoTakeoff).font(WG.subhead)
            Toggle("Son du vario", isOn: $app.soundOn).font(WG.subhead)
            Toggle("Annonces vocales", isOn: $app.voiceOn).font(WG.subhead)
            HStack {
                Button("Tester le son") { app.testSound() }
                Spacer()
                Button("Tester la voix") { app.testVoice() }
            }
            .font(WG.subhead)
            Text("Sans départ automatique, l'enregistrement se lance avec REC dans Pilotage. Baromètre : \(app.sensors.baroAvailable ? "présent" : "absent")")
                .font(WG.caption).foregroundStyle(WG.dim)
        }
        .tint(WG.sky)
    }
}

// MARK: - feuilles

struct ClubPicker: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List(app.clubs.search(query: query), id: \.id) { c in
                Button {
                    app.choose(c); dismiss()
                } label: {
                    VStack(alignment: .leading) {
                        Text(c.displayName).font(WG.subhead).foregroundStyle(WG.brandInk)
                        Text([c.airfieldIcao, c.city].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                            .font(WG.caption).foregroundStyle(WG.dim)
                    }
                }
            }
            .searchable(text: $query, prompt: "Sigle, OACI, nom ou ville")
            .navigationTitle("Choix du club")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
    }
}

struct SourcesSheet: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if case .loaded(let day) = app.weather {
                    ForEach(day.sources, id: \.product) { s in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(s.product).font(WG.headline)
                            Text(s.attribution).font(WG.footnote)
                            Text([s.referenceTime.map { "réseau de \(Fmt.hm($0))" }, "reçu à \(Fmt.hm(s.fetchedAt))", s.offline ? "copie locale" : nil]
                                .compactMap { $0 }.joined(separator: " · ")).font(WG.caption).foregroundStyle(WG.dim)
                            if let l = s.license { Text(l).font(WG.caption).foregroundStyle(WG.dim) }
                        }
                    }
                } else {
                    Text("Aucune donnée reçue").font(WG.subhead)
                }
                Section {
                    Text(WeatherRepositoryKt.PRECOG_CREDIT).font(WG.footnote)
                    Text("Données fournies en l'état : estimation d'un modèle, à confronter à votre propre analyse. Ne remplace pas un briefing météo officiel.")
                        .font(WG.caption).foregroundStyle(WG.dim)
                }
            }
            .navigationTitle("Sources météo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("OK") { dismiss() } } }
        }
    }
}

/// Cartes hors ligne : relief Copernicus et terrains openAIP de la région du club (sécurité sans réseau).
struct PackCard: View {
    @EnvironmentObject var app: AppModel
    @ObservedObject private var packs: PackStore

    init() { _packs = ObservedObject(wrappedValue: PackStoreHolder.current) }

    var body: some View {
        WGCard(title: "Carte hors ligne", systemImage: "map", tint: WG.good) {
            if let a = packs.active {
                Text("\(a.name) · \(a.airportCount) terrains · relief \(a.reliefLoaded ? "chargé" : "absent")").font(WG.subhead)
                if a.aeroExpired { Text("Données aéro périmées : mettez la carte à jour avant le vol").font(WG.caption).foregroundStyle(WG.bad) }
            } else if let p = packs.available {
                Text("\(p.name) : relief et terrains, \(packs.neededMB) Mo").font(WG.subhead)
                Text("Sans carte, la marge ne connaît ni le relief ni les terrains de dégagement.").font(WG.caption).foregroundStyle(WG.dim)
            } else {
                Text(packs.catalogError ?? "Aucune carte pour cette région").font(WG.subhead).foregroundStyle(WG.dim)
            }
            if let pr = packs.progress {
                ProgressView(value: pr) { Text("Téléchargement \(Int(pr * 100)) %").font(WG.caption) }
                Button("Annuler") { packs.cancel() }.font(WG.subhead)
            } else if packs.available != nil && (packs.installed == nil || packs.updateAvailable) {
                Button(packs.updateAvailable ? "Mettre à jour" : "Télécharger (\(packs.neededMB) Mo)") { packs.download(club: app.club) }
                    .font(WG.subhead).buttonStyle(.borderedProminent).tint(WG.sky)
            }
            if let e = packs.error { Text(e).font(WG.caption).foregroundStyle(WG.bad) }
            Text("Relief Copernicus GLO-30 · terrains openAIP · vérifiez SUP AIP et NOTAM avant le vol").font(WG.caption).foregroundStyle(WG.dim)
        }
    }
}

/// Accès au PackStore de l'app pour les vues qui l'observent directement.
@MainActor enum PackStoreHolder { static var current: PackStore! }
