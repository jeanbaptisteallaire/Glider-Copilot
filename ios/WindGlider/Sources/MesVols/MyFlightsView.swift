import Charts
import PhotosUI
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

    /// Photo du pilote : fichier local (Application Support), jamais envoyé en ligne.
    static var photoURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("pilot-photo.jpg")
    }
    static func loadPhoto() -> UIImage? { UIImage(contentsOfFile: photoURL.path) }
    static func savePhoto(_ data: Data) -> UIImage? {
        guard let img = UIImage(data: data), let jpg = img.jpegData(compressionQuality: 0.85) else { return nil }
        try? FileManager.default.createDirectory(at: photoURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? jpg.write(to: photoURL, options: .atomic)
        return img
    }
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
    @State private var photo = PilotProfile.loadPhoto()
    @State private var photoItem: PhotosPickerItem?
    /// « Gérer mon profil » : replié par défaut, ouvert d'office tant que le profil n'est pas créé
    @State private var managing = PilotProfile.load().isEmpty

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    banner
                    stats
                    manageCard
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
                    // sous les vols : ajouter un vol venu d'un autre instrument
                    Button { importing = true } label: {
                        Label(tr("Importer un IGC"), systemImage: "square.and.arrow.down").font(WG.headline).frame(maxWidth: .infinity, minHeight: 50)
                            .background(WG.sky.opacity(0.14), in: RoundedRectangle(cornerRadius: 14)).foregroundStyle(WG.sky)
                    }
                    .padding(.top, 6)
                    if let e = importError { Text(e).font(WG.footnote).foregroundStyle(WG.bad) }
                }
                .padding(16)
            }
            .background(WG.page.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .refreshable { app.refreshFlights() }
            .onAppear {
                app.refreshFlights()
                account.isRecording = { [weak app] in app?.isRecording ?? false }
                account.onShown()
            }
            .onChange(of: account.restoredTick) { _, _ in app.refreshFlights() }
            .sheet(isPresented: $editing) { ProfileEditor(profile: $profile) }
            .onChange(of: photoItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self), let img = PilotProfile.savePhoto(data) { photo = img }
                }
            }
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

    /// Bandeau illustré : nom du pilote (ou « Mon profil »), club et niveau, photo intégrée dans l'image en bas à gauche
    /// (touchable pour la changer). Plus d'avatar à cheval sur le bord : moins de place perdue.
    private var banner: some View {
        ZStack(alignment: .bottomLeading) {
            (wgImage("wg_flights_header") ?? Image(systemName: "photo")).resizable().scaledToFill()
                .frame(height: 196).frame(maxWidth: .infinity).clipped()
            LinearGradient(colors: [.black.opacity(0.35), .clear, .black.opacity(0.45)], startPoint: .top, endPoint: .bottom)
            Text(profile.name.isEmpty ? tr("Mon profil") : profile.name)
                .font(.system(size: 28, weight: .bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.7)
                .shadow(color: .black.opacity(0.4), radius: 5)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(.leading, 18).padding(.top, 12).padding(.trailing, 70)
            LanguageSwitch().frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing).padding(.trailing, 12).padding(.top, 12)
            HStack(alignment: .bottom, spacing: 12) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    ZStack(alignment: .bottomTrailing) {
                        Group {
                            if let photo { Image(uiImage: photo).resizable().scaledToFill() }
                            else { (wgImage("wg_avatar") ?? Image(systemName: "person.circle")).resizable().scaledToFill() }
                        }
                        .frame(width: 128, height: 128).clipShape(Circle())   // JB : deux fois plus grande
                        .overlay(Circle().stroke(.white, lineWidth: 3))
                        Image(systemName: "camera.fill").font(.system(size: 14, weight: .bold)).foregroundStyle(.white)
                            .frame(width: 30, height: 30).background(WG.sky, in: Circle())
                    }
                }
                .accessibilityLabel(tr("Changer la photo de profil"))
                VStack(alignment: .leading, spacing: 3) {
                    if !profile.username.isEmpty { Text("@\(profile.username)").font(WG.subhead.weight(.semibold)).foregroundStyle(.white) }
                    if !profile.club.isEmpty { Text(profile.club).font(WG.caption).foregroundStyle(.white.opacity(0.85)) }
                    if !profile.level.isEmpty {
                        Text(profile.level).font(WG.caption.weight(.semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 8).padding(.vertical, 2).background(WG.sky.opacity(0.85), in: Capsule())
                    }
                }
                .shadow(color: .black.opacity(0.5), radius: 3)
            }
            .padding(14)
        }
        .frame(height: 196)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    /// « Gérer mon profil » : un toucher déplie toutes les options (profil, photo, sauvegarde en ligne, suppression du compte).
    private var manageCard: some View {
        let open = managing
        return VStack(alignment: .leading, spacing: 12) {
            Button { withAnimation(.smooth(duration: 0.3)) { managing.toggle() } } label: {
                HStack {
                    Label(tr("Gérer mon profil"), systemImage: "person.crop.circle").font(WG.headline).foregroundStyle(WG.brandInk)
                    Spacer()
                    Image(systemName: "chevron.down").font(.system(size: 14, weight: .semibold)).foregroundStyle(WG.dim)
                        .rotationEffect(.degrees(open ? 180 : 0))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(open ? tr("Replier") : tr("Déplier"))
            if open {
                // contenu replié vers le haut (sous le titre), en fondu
                VStack(alignment: .leading, spacing: 12) {
                    if profile.isEmpty {
                        Text(tr("Nom, pseudo, club et niveau. Tout reste sur ce téléphone tant que tu ne partages rien."))
                            .font(WG.subhead).foregroundStyle(WG.dim)
                    }
                    Button { editing = true } label: {
                        Text(profile.isEmpty ? tr("Créer mon profil pilote") : tr("Modifier mon profil")).font(WG.headline).frame(maxWidth: .infinity, minHeight: 50)
                            .background(profile.isEmpty ? WG.sky : WG.sky.opacity(0.14), in: RoundedRectangle(cornerRadius: 14))
                            .foregroundStyle(profile.isEmpty ? .white : WG.sky)
                    }
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label(tr("Changer la photo"), systemImage: "camera").font(WG.headline).frame(maxWidth: .infinity, minHeight: 50)
                            .background(WG.sky.opacity(0.14), in: RoundedRectangle(cornerRadius: 14)).foregroundStyle(WG.sky)
                    }
                    // sauvegarde en ligne et suppression du compte (comme Android)
                    AccountCard()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(WG.cardFill, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(WG.line))
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
        .background(WG.page.ignoresSafeArea())
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
