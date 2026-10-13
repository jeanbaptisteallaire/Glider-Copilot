import Charts
import SwiftUI
import UniformTypeIdentifiers
import WindGliderShared

/// Profil pilote local (Android : profil « Crée ton profil pilote ») : reste sur ce téléphone, rien n'est partagé.
struct PilotProfile: Codable, Equatable {
    var name = ""
    var username = ""
    var club = ""
    var level = ""
    var isEmpty: Bool { name.isEmpty && username.isEmpty && club.isEmpty && level.isEmpty }

    static var levels: [String] { [tr("Élève"), tr("Lâché"), tr("Brevet"), tr("Pilote confirmé"), tr("Instructeur")] }

    static func load() -> PilotProfile {
        (UserDefaults.standard.data(forKey: "pilotProfile")).flatMap { try? JSONDecoder().decode(PilotProfile.self, from: $0) } ?? PilotProfile()
    }
    func save() { UserDefaults.standard.set(try? JSONEncoder().encode(self), forKey: "pilotProfile") }
}

/// Onglet Mes vols (édition Lite, comme Android V20) : bandeau illustré, avatar du pilote, chiffres clés, profil local,
/// import d'IGC, carnet en grille de 3 colonnes. Pas de sauvegarde en ligne ni de partage dans l'édition Lite.
struct MyFlightsView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var account: AccountModel
    @State private var profile = PilotProfile.load()
    @State private var editing = false
    @State private var importing = false
    @State private var importError: String?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    banner
                    VStack(alignment: .leading, spacing: 2) {
                        Text(profile.name.isEmpty ? tr("Pilote Wind Glider") : profile.name).font(WG.headline).foregroundStyle(WG.brandInk)
                        if !profile.club.isEmpty { Text(profile.club).font(WG.subhead).foregroundStyle(WG.dim) }
                        if !profile.level.isEmpty {
                            Text(profile.level).font(WG.caption.weight(.semibold)).foregroundStyle(WG.sky)
                                .padding(.horizontal, 8).padding(.vertical, 3).background(WG.sky.opacity(0.12), in: Capsule()).padding(.top, 4)
                        }
                    }
                    .padding(.leading, 4)
                    stats
                    if profile.isEmpty {
                        WGCard(title: tr("Crée ton profil pilote"), systemImage: "scribble", tint: WG.sky) {
                            Text(tr("Nom, pseudo, club et niveau. Tout reste sur ce téléphone tant que tu ne partages rien."))
                                .font(WG.subhead).foregroundStyle(WG.dim)
                        }
                    }
                    HStack(spacing: 8) {
                        Button { editing = true } label: {
                            Text(profile.isEmpty ? tr("Créer mon profil") : tr("Modifier")).font(WG.headline).frame(maxWidth: .infinity, minHeight: 50)
                                .background(profile.isEmpty ? WG.sky : WG.sky.opacity(0.14), in: RoundedRectangle(cornerRadius: 14))
                                .foregroundStyle(profile.isEmpty ? .white : WG.sky)
                        }
                        Button { importing = true } label: {
                            Text(tr("Importer un IGC")).font(WG.headline).frame(maxWidth: .infinity, minHeight: 50)
                                .background(WG.sky.opacity(0.14), in: RoundedRectangle(cornerRadius: 14)).foregroundStyle(WG.sky)
                        }
                    }
                    if let e = importError { Text(e).font(WG.footnote).foregroundStyle(WG.bad) }
                    // comme Android : sauvegarde en ligne dans les deux éditions (le partage reste réservé à la complète)
                    AccountCard()
                    HStack(alignment: .firstTextBaseline) {
                        Text(tr("Vols")).font(WG.title.weight(.bold)).foregroundStyle(WG.brandInk)
                        Spacer()
                        Text(tr(app.flights.count > 1 ? "%d vols" : "%d vol", app.flights.count)).font(WG.subhead).foregroundStyle(WG.dim)
                    }
                    .padding(.top, 6)
                    if app.flights.isEmpty {
                        WGCard(title: tr("Carnet vide"), systemImage: "scribble", tint: WG.sky) {
                            Text(tr("Vos vols apparaissent ici après REC dans Pilotage, ou avec « Importer un IGC ».")).font(WG.subhead).foregroundStyle(WG.dim)
                        }
                    } else {
                        LazyVGrid(columns: columns, spacing: 10) {
                            ForEach(app.flights, id: \.path) { f in
                                NavigationLink { FlightDetail(f: f) } label: { FlightTile(f: f) }
                                    .buttonStyle(.plain)
                                    .contextMenu { Button(tr("Supprimer le vol"), role: .destructive) { app.delete(f) } }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(WG.brandPaper)
            .toolbar(.hidden, for: .navigationBar)
            .refreshable { app.refreshFlights() }
            .onAppear {
                app.refreshFlights()
                account.isRecording = { [weak app] in app?.pilot.recording ?? false }
                account.onShown()
            }
            .onChange(of: account.restoredTick) { _, _ in app.refreshFlights() }
            .sheet(isPresented: $editing) { ProfileEditor(profile: $profile) }
            .fileImporter(isPresented: $importing, allowedContentTypes: [UTType(filenameExtension: "igc") ?? .data, .data, .plainText]) { result in
                importError = nil
                guard case .success(let url) = result else { return }
                let ok = url.startAccessingSecurityScopedResource()
                defer { if ok { url.stopAccessingSecurityScopedResource() } }
                if app.library.importIgc(sourcePath: url.path, name: url.lastPathComponent) == nil {
                    importError = tr("« %@ » n'est pas un fichier IGC lisible.", url.lastPathComponent)
                }
                app.refreshFlights()
            }
        }
    }

    /// Bandeau illustré (wg_flights_header d'Android), titre posé sur le ciel, avatar à cheval sur le bord bas.
    private var banner: some View {
        ZStack(alignment: .bottomLeading) {
            ZStack(alignment: .topLeading) {
                (wgImage("wg_flights_header") ?? Image(systemName: "photo")).resizable().scaledToFill()
                    .frame(height: 196).frame(maxWidth: .infinity).clipped()
                LinearGradient(colors: [Color(red: 0.06, green: 0.16, blue: 0.27).opacity(0.33), .clear], startPoint: .top, endPoint: .center)
                Text(profile.username.isEmpty ? tr("Mon profil") : "@\(profile.username)")
                    .font(Font.custom("Inter", size: 28).weight(.bold)).foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.4), radius: 5).padding(.leading, 18).padding(.top, 12)
                LanguageSwitch().frame(maxWidth: .infinity, alignment: .trailing).padding(.trailing, 12).padding(.top, 12)
            }
            .frame(height: 196)
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .frame(height: 232, alignment: .top)
            (wgImage("wg_avatar") ?? Image(systemName: "person.circle")).resizable().scaledToFill()
                .frame(width: 76, height: 76).clipShape(Circle())
                .overlay(Circle().stroke(.white, lineWidth: 3)).shadow(radius: 4)
                .padding(.leading, 16)
                .accessibilityLabel(tr("Avatar du pilote"))
        }
    }

    private var stats: some View {
        let totalS = app.flights.reduce(Int64(0)) { $0 + $1.durationS }
        let km = app.flights.reduce(0.0) { $0 + $1.distanceKm }
        return WGCard {
            HStack(alignment: .top) {
                stat("\(app.flights.count)", nil, tr("Vols"), WG.sky)
                stat(totalS >= 3600 ? "\(totalS / 3600)" : "0", totalS >= 3600 ? "h \(String(format: "%02d", totalS % 3600 / 60))" : "h \(totalS / 60)", tr("Heures"), WG.good)
                stat(IosFlightEngine.companion.grouped(n: Int32(km.rounded())), "km", tr("Distance"), WG.sun)
            }
        }
    }

    private func stat(_ v: String, _ unit: String?, _ label: String, _ tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(WG.subhead.weight(.semibold)).foregroundStyle(tint)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(v).font(WG.number(26)).foregroundStyle(WG.brandInk)
                if let unit { Text(unit).font(WG.subhead.weight(.semibold)).foregroundStyle(WG.dim) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Vignette de la grille : trace schématique, date courte, « 31 min · 55,9 km ».
struct FlightTile: View {
    let f: FlightEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            TraceSketch(track: f.track).frame(height: 110)
                .background(Color.gray.opacity(0.06), in: RoundedRectangle(cornerRadius: 10))
            Text(FlightDates.short(f.startEpochS)).font(WG.subhead.weight(.semibold)).foregroundStyle(WG.brandInk).lineLimit(1)
            Text(f.gridLine).font(WG.caption).foregroundStyle(WG.dim).lineLimit(1).minimumScaleFactor(0.8)
        }
        .padding(8)
        .background(WG.card, in: RoundedRectangle(cornerRadius: 16))
    }
}

/// Trace dessinée à plat (projection locale), trait rouge et point de départ, comme les vignettes Android.
struct TraceSketch: View {
    let track: [TraceDot]
    var lineWidth: CGFloat = 1.6

    var body: some View {
        GeometryReader { g in
            let pts = track.map { (x: $0.lon * cos($0.lat * .pi / 180), y: $0.lat) }
            if let minX = pts.map(\.x).min(), let maxX = pts.map(\.x).max(), let minY = pts.map(\.y).min(), let maxY = pts.map(\.y).max() {
                let span = max(maxX - minX, maxY - minY, 1e-6)
                let pad: CGFloat = 10
                let s = min(g.size.width, g.size.height) - 2 * pad
                let ox = (g.size.width - CGFloat((maxX - minX) / span) * s) / 2
                let oy = (g.size.height - CGFloat((maxY - minY) / span) * s) / 2
                let map: ((x: Double, y: Double)) -> CGPoint = { p in
                    CGPoint(x: ox + CGFloat((p.x - minX) / span) * s, y: g.size.height - (oy + CGFloat((p.y - minY) / span) * s))
                }
                Path { path in
                    for (i, p) in pts.enumerated() { i == 0 ? path.move(to: map(p)) : path.addLine(to: map(p)) }
                }
                .stroke(WG.bad, style: StrokeStyle(lineWidth: lineWidth, lineJoin: .round))
                if let first = pts.first {
                    Circle().fill(WG.bad).frame(width: 6, height: 6).position(map(first))
                }
            } else {
                Circle().fill(WG.bad).frame(width: 6, height: 6).position(x: 14, y: g.size.height - 14)
            }
        }
    }
}

enum FlightDates {
    private static func fmt(_ pattern: String) -> DateFormatter {
        let f = DateFormatter(); f.locale = Locale(identifier: Lang.en ? "en_GB" : "fr_FR"); f.dateFormat = pattern; return f
    }
    static func short(_ s: Double) -> String { fmt("d MMM yyyy").string(from: Date(timeIntervalSince1970: s)) }
    static func long(_ s: Double) -> String { fmt("d MMMM yyyy").string(from: Date(timeIntervalSince1970: s)) }
    static func weekday(_ s: Double) -> String { fmt("EEEE").string(from: Date(timeIntervalSince1970: s)).capitalized }
}

/// Détail d'un vol (Android) : date en titre, trace, durée, distance, altitude max, rejeu 3D, profil d'altitude.
struct FlightDetail: View {
    @EnvironmentObject var app: AppModel
    let f: FlightEntry
    @State private var replay: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(FlightDates.long(f.startEpochS)).font(WG.largeTitle).foregroundStyle(WG.brandInk).lineLimit(1).minimumScaleFactor(0.6)
                    Text([FlightDates.weekday(f.startEpochS), PilotProfile.load().name.isEmpty ? nil : PilotProfile.load().name, f.fileName]
                        .compactMap { $0 }.joined(separator: " · ")).font(WG.subhead).foregroundStyle(WG.dim).lineLimit(1)
                }
                WGCard(title: tr("Trace"), systemImage: "map", tint: WG.bad) {
                    TraceSketch(track: f.track, lineWidth: 2).frame(height: 300)
                        .background(Color.gray.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(WG.line))
                    HStack(alignment: .top) {
                        kpi(f.durationText.replacingOccurrences(of: " h ", with: " h "), tr("Durée"), WG.good, unit: nil)
                        kpi(IosFlightEngine.companion.grouped(n: Int32(f.distanceKm.rounded())), tr("Distance"), WG.sun, unit: "km")
                        kpi(f.maxAltM > 0 ? IosFlightEngine.companion.grouped(n: Int32(f.maxAltM)) : "—", tr("Alt. max"), WG.altitude, unit: f.maxAltM > 0 ? "m" : nil)
                    }
                    Button { replay = app.library.replayJson(path: f.path) } label: {
                        Text(tr("Revoir le vol en 3D")).font(WG.headline).frame(maxWidth: .infinity, minHeight: 50)
                            .background(f.altProfile.isEmpty ? Color.gray.opacity(0.3) : WG.sky, in: RoundedRectangle(cornerRadius: 14)).foregroundStyle(.white)
                    }
                    .disabled(f.altProfile.isEmpty)
                    if f.altProfile.isEmpty { Text(tr("Rejeu 3D indisponible : vol sans altitude.")).font(WG.caption).foregroundStyle(WG.dim) }
                }
                if !f.altProfile.isEmpty {
                    WGCard {
                        HStack {
                            Label(tr("Profil d'altitude"), systemImage: "scribble").font(WG.headline).foregroundStyle(WG.altitude)
                            Spacer()
                            Text(f.altSource).font(WG.footnote.weight(.semibold)).foregroundStyle(WG.dim)
                                .padding(.horizontal, 10).padding(.vertical, 4).background(Color.gray.opacity(0.12), in: Capsule())
                        }
                        Text(tr("Plage")).font(WG.subhead.weight(.semibold)).foregroundStyle(WG.dim)
                        HStack(alignment: .firstTextBaseline, spacing: 3) {
                            Text("\(IosFlightEngine.companion.grouped(n: Int32(f.minAltM)))–\(IosFlightEngine.companion.grouped(n: Int32(f.maxAltM)))")
                                .font(WG.number(30)).foregroundStyle(WG.brandInk)
                            Text("m").font(WG.headline).foregroundStyle(WG.dim)
                        }
                        Chart {
                            ForEach(Array(f.altProfile.enumerated()), id: \.offset) { _, d in
                                AreaMark(x: .value("min", d.minutes), y: .value("alt", d.altM))
                                    .foregroundStyle(LinearGradient(colors: [WG.altitude.opacity(0.3), WG.altitude.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                                LineMark(x: .value("min", d.minutes), y: .value("alt", d.altM)).foregroundStyle(WG.altitude).lineStyle(StrokeStyle(lineWidth: 2.5))
                            }
                            if let top = f.altProfile.max(by: { $0.altM < $1.altM }) {
                                PointMark(x: .value("min", top.minutes), y: .value("alt", top.altM)).foregroundStyle(WG.altitude)
                            }
                        }
                        .chartYAxis { AxisMarks(position: .trailing, values: .automatic(desiredCount: 3)) }
                        .chartXAxis {
                            AxisMarks(values: .automatic(desiredCount: 3)) { v in
                                AxisGridLine()
                                AxisValueLabel { if let m = v.as(Double.self) { Text(m >= 60 ? "\(Int(m) / 60) h \(String(format: "%02d", Int(m) % 60))" : "\(Int(m)) min") } }
                            }
                        }
                        .frame(height: 200)
                    }
                }
                Text(tr("%@ · %@ · trace indicative (enregistreur non approuvé)", f.fileName, trDyn(f.detail))).font(WG.caption).foregroundStyle(WG.dim)
            }
            .padding(16)
        }
        .background(WG.brandPaper)
        .navigationTitle("")
        .toolbar { ToolbarItem(placement: .principal) { EmptyView() } }
        .navigationBarBackButtonHidden(false)
        .toolbarRole(.editor)
        .fullScreenCover(isPresented: Binding(get: { replay != nil }, set: { if !$0 { replay = nil } })) {
            if let r = replay { ReplayScreen(payload: r) }
        }
    }
}

/// Création ou modification du profil pilote, gardé sur le téléphone.
struct ProfileEditor: View {
    @Binding var profile: PilotProfile
    @Environment(\.dismiss) private var dismiss
    @State private var draft = PilotProfile()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(tr("Nom"), text: $draft.name).textContentType(.name)
                    TextField(tr("Pseudo"), text: $draft.username).textInputAutocapitalization(.never)
                    TextField(tr("Club"), text: $draft.club)
                    Picker(tr("Niveau"), selection: $draft.level) {
                        Text("—").tag("")
                        ForEach(PilotProfile.levels, id: \.self) { Text($0).tag($0) }
                    }
                } footer: { Text(tr("Tout reste sur ce téléphone : rien n'est envoyé ni partagé.")) }
            }
            .navigationTitle(profile.isEmpty ? tr("Créer mon profil") : tr("Modifier le profil"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(tr("Annuler")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(tr("Enregistrer")) { profile = draft; draft.save(); dismiss() } }
            }
            .onAppear { draft = profile }
        }
    }
}
